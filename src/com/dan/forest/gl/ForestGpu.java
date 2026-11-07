package com.dan.forest.gl;

import com.dan.forest.ForestCulture;
import com.dan.forest.ForestField;
import com.dan.forest.Ground;
import com.dan.forest.Octahedral;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.dan.forest.TreeStates;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Ein Wald mit Millionen Bäumen auf der Grafikkarte (seit LituyaBay1958 Phase 6, Werkbuch 6.4), neben {@link ForestGl},
 * das unverändert bleibt:
 * <ul>
 *   <li><b>Kultur auf der Grafikkarte:</b> je Bild würfelt {@code forestgpu_cull.comp} für jeden Platz des Pflanzgitters
 *   in Reichweite den Baum aus Dichte, Startwert und Zustand aus (wie {@link ForestCulture}, die der Spiegel auf dem
 *   Prozessor ist), prüft Sichtkegel und Ausdünnung, wählt die Stufe und legt die Instanz in ihren Eimer. Keine Kachel,
 *   kein Speicher je Baum, kein Hochladen je Bild.</li>
 *   <li><b>Zeichnen:</b> zwei {@code glMultiDrawElementsIndirect} für alle Netze (Stufe 0, 1, 2 und die Gerippe der
 *   geschädigten Bäume) mit {@code forest.vert}/{@code forest.frag}, einer für die Impostors.</li>
 *   <li><b>Oktaeder-Impostors:</b> stehende Bäume in der Ferne als Viereck, das aus N × N gebackenen Ansichten der oberen
 *   Halbkugel (Vorgabe 8 × 8, {@link Octahedral}) die vier nächsten mischt; Farbe und Normale, Licht wie nah.</li>
 *   <li><b>Zustand je Baum:</b> {@link TreeStates} (stehend, gebrochen, entrindet, umgelegt mit Richtung, fort) als
 *   Textur; jede Art bekommt ihre Schadensformen aus {@link Species#damaged}.</li>
 * </ul>
 * Typ = (Zustand · Arten + Art) · Varianten + Variante; Eimer = Stufe · Typen + Typ (4 Stufen).
 */
public final class ForestGpu {
    /** Qualitätsstufen (Paketvertrag 7): Reichweite, Stufe 2 bis (m), Bildpunkte je Ansicht des Impostors, Instanzen höchstens. */
    public enum Quality {
        LOW(2000, 100, 64, 600_000), MEDIUM(3000, 150, 96, 1_200_000), HIGH(4500, 220, 128, 2_000_000), ULTRA(6000, 300, 160, 3_000_000);
        public final double radius, lod2;
        public final int framePx, capacity;
        Quality(double radius, double lod2, int framePx, int capacity) { this.radius = radius; this.lod2 = lod2; this.framePx = framePx; this.capacity = capacity; }
    }

    /** Ansichten je Kante des Halb-Oktaeders. */
    public static final int FRAMES = 8;
    static final int LODS = 4, VSTRIDE = ForestGl.VSTRIDE;

    private final List<Species> base;
    private final Species[] forms;
    private final int S, V, T;
    private final ForestCulture culture;
    private final TreeModel[] models;
    private final float[] tinfo;
    /** Reichweite (m), Beginn der Ausdünnung, Stufen 0 und 1 (je 20 m Baumhöhe, wie ForestField), Stufe 2 (m). */
    public double radius = 3000, thinFrom = 0.4, lod0 = 12, lod1 = 30, lod2 = 150;
    /**
     * Nahschnitt je Art (m, Vorgabe 0 = keiner; seit LituyaBay1958 W6 Runde 3): stehende Bäume dieser Art, die näher am
     * Auge stehen, zeichnet forest nicht. Die App holt genau diese Bäume mit {@link #nearTrees} und zeichnet sie selbst,
     * etwa mit einem feinen Modell aus com.dan.tree. Im Band nearBand (m) um den Schnitt wird je Platz gewürfelt, beide
     * Seiten mit demselben Wurf, so dass jeder Baum genau einmal steht.
     */
    public double[] nearCut;
    public double nearBand = 6;
    /**
     * Seit LituyaBay1958 W9c Runde 2: die Erdkrümmung wie im Gelände, 1/(2R') (1/m; R' der wirksame Erdradius mit der
     * Brechung, etwa 6 371 km / 0,87). Jeder Baum sinkt um r² · curvature, r sein Abstand zum Auge über den Boden, wie die
     * Scheitel einer Clipmap mit Erdkrümmung: in 4,5 km 1,4 m. Vorgabe 0 (flach). {@link #nearTrees} und {@link #cullCpu}
     * geben den Fuß ohne Krümmung (nah unter 0,1 mm).
     */
    public double curvature;
    /** Seit W9c Runde 2: kein Baum unter diesem Grund (m), siehe {@link ForestCulture#minGround}; Vorgabe −∞. */
    public double minGround = Double.NEGATIVE_INFINITY;
    /**
     * Feine Nadelbürsten in Stufe 0 (Vorgabe false; seit LituyaBay1958 W6 Runde 3): siehe
     * {@link TreeMesh#build(TreeModel, int, int, boolean)}. Vor {@link #init} setzen (wirkt beim nächsten Backen).
     */
    public boolean fineNeedles;
    /**
     * Seit LituyaBay1958 W6 Runde 5: Rinde mit Muster in der Nähe (Furchen, Schuppen, Platten, Korkporen, Flechten nach
     * {@link Species#barkStyle}, mit Relief für das Licht); Vorgabe aus, dann wie bisher.
     */
    public boolean barkDetail;
    /**
     * Seit LituyaBay1958 W6 Runde 5: Nadeln als Triebe beleuchten (ein Volumen aus Nadeln: hell, wenn man von der
     * Sonnenseite schaut, dunkel im Gegenlicht, kaum Durchscheinen) statt als einzelne Blattebenen. Nimmt den gekreuzten
     * Bürsten der Stufen 1 und 2 die hellen Flocken. Vorgabe aus, dann wie bisher.
     */
    public boolean shootLight;
    /**
     * Seit LituyaBay1958 W6 Runde 6: Bartflechten in Stufe 0 an den Ästen der Arten mit {@link com.dan.forest.Species#beard}
     * &gt; 0 (zwei gekreuzte, sich verjüngende Bänder je Strähne). Vor init bzw. vor dem Backen setzen. Vorgabe aus.
     */
    public boolean beardLichen;
    /**
     * Seit LituyaBay1958 W6 Runde 7: mit {@link #beardLichen} die Bartflechten als Karten ({@link TreeMesh#CARD}) in
     * Stufe 0 und 1, Maße je Ebene aus beardCards, Bild aus {@link #beardTexture} (GL_TEXTURE_2D_ARRAY, rgb Albedo in sRGB,
     * a Deckung; etwa aus {@code com.dan.lichen.gl.LichenGl}). beardTexture 0: Fäden aus Rauschen. Vor dem Backen setzen.
     */
    public TreeMesh.Cards beardCards;
    public int beardTexture;
    private int framePx = 96, capacity = 1_200_000;

    private int cullProg, scanProg, meshProg, shadowProg, impProg, bakeProg;
    private int meshVao, impVao, vbo, ibo, quadVbo, quadIbo, instBuf, cntBuf, cmdBuf, typeBuf, meshBuf, statBuf;
    private int denTex, hgtTex, stTex, albTex, nrmTex, bakeFbo, bakeDepth;
    private int stVersion = -1, impPx;
    private boolean uploaded, denDirty = true, hgtDirty = true;
    private final ExecutorService baker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wald (GPU) backen");
        t.setDaemon(true);
        return t;
    });
    private Future<float[][][]> pending;
    private int bakedDay = -999, pendingDay;
    private float bakedSnow = -1, pendingSnow;
    private float[][][] lastMeshes;
    private int[] meshCount, meshFirst, meshBase;
    private long vboBytes, iboBytes;
    private double windToX = 1, windToZ = 0, windSpeed = 4, gust = 0.5;
    private int[] lastStats = new int[0];
    private int frame;
    private String info = "Wald (GPU): wird gebacken";

    /**
     * Wald aus diesen Arten (höchstens 16), je Art {@code variants} Varianten, Pflanzabstand (m), Startwert. Die Formen je
     * Zustand (gebrochen, entrindet, umgelegt) baut der Wald aus {@link Species#damaged}.
     */
    public ForestGpu(List<Species> species, int variants, double spacing, long seed) {
        base = new ArrayList<>(species);
        S = base.size();
        V = Math.max(1, variants);
        T = 4 * S * V;
        culture = new ForestCulture(S, V, spacing, seed);
        forms = new Species[4 * S];
        for (int s = 0; s < S; s++) {
            Species b = base.get(s);
            forms[s] = b;
            forms[S + s] = Species.damaged(b, Species.Damage.BROKEN);
            forms[2 * S + s] = Species.damaged(b, Species.Damage.STRIPPED);
            forms[3 * S + s] = Species.damaged(b, Species.Damage.FELLED);
        }
        models = new TreeModel[T];
        tinfo = new float[4 * T];
        for (int st = 0; st < 4; st++)
            for (int s = 0; s < S; s++)
                for (int v = 0; v < V; v++) {
                    int t = type(st, s, v);
                    TreeModel m = TreeGenerator.grow(forms[st * S + s], seed + s * 1009L + v * 7919L, 1f);
                    models[t] = m;
                    tinfo[4 * t] = Math.max(m.height, m.length);
                }
    }

    public int type(int state, int s, int v) { return (state * S + s) * V + v; }
    public int types() { return T; }
    public int species() { return S; }
    public int variants() { return V; }
    /** Die Kultur (Dichte, Höhe, Zustände, Drehung und Größe je Art); Änderungen danach mit {@link #densityChanged} usw. melden. */
    public ForestCulture culture() { return culture; }
    public TreeModel model(int type) { return models[type]; }
    public Species form(int type) { return forms[type / V]; }
    public boolean ready() { return uploaded; }
    public String info() { return info; }
    public int framePx() { return framePx; }

    /** Qualität: Reichweite, Stufe 2, Auflösung der Impostors (bäckt neu) und Größe des Instanzpuffers. */
    public void apply(Quality q) {
        radius = q.radius;
        lod2 = q.lod2;
        framePx = q.framePx;
        capacity = q.capacity;
    }

    /** Dichte je Art (Schichten = Arten) über ein Quadrat; siehe {@link ForestCulture.Grid#density}. */
    public void setDensity(ForestCulture.Grid g) {
        if (g.layers != S) throw new IllegalArgumentException("Dichte: " + g.layers + " Schichten statt " + S);
        culture.density = g;
        denDirty = true;
    }

    public void setDensity(ForestField.Density d, double x0, double z0, double size, int n) { setDensity(ForestCulture.Grid.density(d, S, x0, z0, size, n)); }

    /** Bodenhöhe als Gitter (R32F auf der Grafikkarte). */
    public void setTerrain(ForestCulture.Grid g) { culture.height = g; hgtDirty = true; }

    public void setTerrain(Ground g, double x0, double z0, double size, int n) { setTerrain(ForestCulture.Grid.heights(g, x0, z0, size, n)); }

    /** Zustände je Platz (Abstand muss der des Walds sein); null = alle stehen. Änderungen darin werden je Bild übernommen. */
    public void setStates(TreeStates s) {
        if (s == culture.states) return;
        if (s != null && Math.abs(s.spacing - culture.spacing) > 1e-9) throw new IllegalArgumentException("Zustände: Abstand " + s.spacing + " statt " + culture.spacing);
        culture.states = s;
        stVersion = -1;
    }

    public void densityChanged() { denDirty = true; }
    public void terrainChanged() { hgtDirty = true; }

    private int[] hgtRegion;

    /**
     * Seit LituyaBay1958 W9c Runde 2: nur ein Rechteck des Bodengitters hat sich geändert (Szene x0, z0 bis x1, z1, m);
     * beim nächsten {@link #update} geht nur dieses Stück auf die Grafikkarte (glTexSubImage2D), nicht das ganze Gitter
     * (4 096² sind 64 MB). Mehrere Rechtecke vor einem update werden zu ihrer Hülle.
     */
    public void terrainChanged(double x0, double z0, double x1, double z1) {
        ForestCulture.Grid g = culture.height;
        if (g == null) return;
        int i0 = Math.max(0, (int) Math.floor((Math.min(x0, x1) - g.x0) / g.size * g.n) - 1), i1 = Math.min(g.n, (int) Math.ceil((Math.max(x0, x1) - g.x0) / g.size * g.n) + 1);
        int j0 = Math.max(0, (int) Math.floor((Math.min(z0, z1) - g.z0) / g.size * g.n) - 1), j1 = Math.min(g.n, (int) Math.ceil((Math.max(z0, z1) - g.z0) / g.size * g.n) + 1);
        if (i0 >= i1 || j0 >= j1) return;
        int[] r = hgtRegion;
        hgtRegion = r == null ? new int[]{i0, j0, i1, j1} : new int[]{Math.min(r[0], i0), Math.min(r[1], j0), Math.max(r[2], i1), Math.max(r[3], j1)};
    }

    /** Wind wie {@link ForestGl#setWind}. */
    public void setWind(double toX, double toZ, double speed, double gustiness) {
        double l = Math.hypot(toX, toZ);
        windToX = l > 1e-6 ? toX / l : 1;
        windToZ = l > 1e-6 ? toZ / l : 0;
        windSpeed = speed;
        gust = gustiness;
    }

    /** Tag im Jahr und Schnee; bäckt Netze (im Hintergrund) und Impostors (im nächsten {@link #update}) neu. */
    public void setSeason(int day, float snow) {
        if (pending != null) return;
        if (Math.abs(day - bakedDay) < 2 && Math.abs(snow - bakedSnow) < 0.08f) return;
        pendingDay = day;
        pendingSnow = snow;
        pending = baker.submit(() -> bake(day, snow));
    }

    /** Wartet, bis die Netze im Hintergrund gebacken sind (für Standbilder und Prüfungen). */
    public void awaitBake() {
        Future<float[][][]> p = pending;
        if (p == null) return;
        try {
            p.get();
        } catch (Exception e) {
            throw new IllegalStateException("forest (GPU): Backen fehlgeschlagen", e);
        }
    }

    // ------------------------------------------------------------------ Backen der Netze (Hintergrund)

    /** [Eimer][Ecken, Dreiecke]: Stufe 0, 1, 2 und für geschädigte Bäume Stufe 3 (Gerippe); stehende Stufe 3 leer (Impostor). */
    float[][][] bake(int day, float snow) {
        float[][][] out = new float[LODS * T][][];
        for (int t = 0; t < T; t++) {
            TreeModel m = models[t];
            Season se = Season.of(m.species, day, snow);
            long k = culture.seed + t * 31L;
            TreeMesh mid = TreeMesh.build(m, 1, 2, fineNeedles);
            boolean karten = beardLichen && beardCards != null;   // Runde 7: Bartflechten als Karten, Stufe 0 und 1
            out[t] = ForestGl.pack(karten ? TreeMesh.build(m, 0, 0, fineNeedles, beardCards) : TreeMesh.build(m, 0, 0, fineNeedles, beardLichen), se, k, 1, 1);
            out[T + t] = ForestGl.pack(karten ? TreeMesh.build(m, 1, 2, fineNeedles, beardCards) : mid, se, k + 1, 1, 1);
            out[2 * T + t] = ForestGl.pack(mid, se, k + 2, 0.15f, 2.0f);
            boolean standing = t < S * V;
            out[3 * T + t] = standing ? new float[][]{new float[0], new float[0]} : ForestGl.pack(TreeMesh.build(m, 3), se, k + 3, 1, 1);
        }
        return out;
    }

    // ------------------------------------------------------------------ GL

    public void init(GL4 gl) {
        cullProg = compute(gl, "forestgpu_cull.comp");
        scanProg = compute(gl, "forestgpu_scan.comp");
        meshProg = link(gl, "forest.vert", "forest.frag", "");
        shadowProg = link(gl, "forest.vert", "forest_shadow.frag", "#define DEPTH_ONLY\n");
        impProg = link(gl, "forestgpu_imp.vert", "forestgpu_imp.frag", "");
        bakeProg = link(gl, "forestgpu_bake.vert", "forestgpu_bake.frag", "");
        int[] b = new int[12];
        gl.glGenBuffers(12, b, 0);
        vbo = b[0]; ibo = b[1]; quadVbo = b[2]; quadIbo = b[3]; instBuf = b[4]; cntBuf = b[5]; cmdBuf = b[6]; typeBuf = b[7]; meshBuf = b[8]; statBuf = b[9];
        int[] va = new int[2];
        gl.glGenVertexArrays(2, va, 0);
        meshVao = va[0];
        impVao = va[1];
        int bins = LODS * T;
        alloc(gl, cntBuf, bins * 4L, GL4.GL_DYNAMIC_COPY);
        alloc(gl, cmdBuf, bins * 20L, GL4.GL_DYNAMIC_COPY);
        alloc(gl, statBuf, (bins + 2) * 4L, GL4.GL_DYNAMIC_READ);
        alloc(gl, instBuf, (long) capacity * 32, GL4.GL_DYNAMIC_COPY);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cntBuf);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, bins * 4L, Buffers.newDirectIntBuffer(bins));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        // Viereck der Impostors
        gl.glBindVertexArray(impVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, quadVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 32, Buffers.newDirectFloatBuffer(new float[]{-1, -1, 1, -1, 1, 1, -1, 1}), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, quadIbo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, 24, Buffers.newDirectIntBuffer(new int[]{0, 1, 2, 0, 2, 3}), GL.GL_STATIC_DRAW);
        instAttribs(gl);
        gl.glBindVertexArray(0);
        int[] tx = new int[5];
        gl.glGenTextures(5, tx, 0);
        denTex = tx[0]; hgtTex = tx[1]; stTex = tx[2]; albTex = tx[3]; nrmTex = tx[4];
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        bakeFbo = f[0];
        gl.glGenRenderbuffers(1, f, 0);
        bakeDepth = f[0];
    }

    private static void alloc(GL4 gl, int buf, long bytes, int usage) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, Math.max(16, bytes), null, usage);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private void instAttribs(GL4 gl) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instBuf);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(4 + a);
            gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, 32, a * 16L);
            gl.glVertexAttribDivisor(4 + a, 1);
        }
    }

    private void upload(GL4 gl, float[][][] meshes) {
        int bins = LODS * T;
        meshCount = new int[bins];
        meshFirst = new int[bins];
        meshBase = new int[bins];
        long nv = 0, ni = 0;
        for (int b = 0; b < bins; b++) { nv += meshes[b][0].length / VSTRIDE; ni += meshes[b][1].length; }
        FloatBuffer vb = Buffers.newDirectFloatBuffer((int) Math.max(1, nv * VSTRIDE));
        IntBuffer ib = Buffers.newDirectIntBuffer((int) Math.max(1, ni));
        int baseV = 0, first = 0;
        for (int b = 0; b < bins; b++) {
            float[] v = meshes[b][0], tf = meshes[b][1];
            vb.put(v);
            for (float x : tf) ib.put(Float.floatToRawIntBits(x));
            meshCount[b] = tf.length;
            meshFirst[b] = first;
            meshBase[b] = baseV;
            first += tf.length;
            baseV += v.length / VSTRIDE;
        }
        // Impostors (Stufe 3 der stehenden): das Viereck
        for (int t = 0; t < S * V; t++) { meshCount[3 * T + t] = 6; meshFirst[3 * T + t] = 0; meshBase[3 * T + t] = 0; }
        vb.flip();
        ib.flip();
        vboBytes = nv * VSTRIDE * 4L;
        iboBytes = ni * 4L;
        gl.glBindVertexArray(meshVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4, vboBytes), vb, GL.GL_STATIC_DRAW);
        int st = VSTRIDE * 4;
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, Math.max(4, iboBytes), ib, GL.GL_STATIC_DRAW);
        instAttribs(gl);
        gl.glBindVertexArray(0);
        int[] mi = new int[3 * bins];
        for (int b = 0; b < bins; b++) { mi[3 * b] = meshCount[b]; mi[3 * b + 1] = meshFirst[b]; mi[3 * b + 2] = meshBase[b]; }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, meshBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, mi.length * 4L, Buffers.newDirectIntBuffer(mi), GL.GL_STATIC_DRAW);
        // Hülle je Typ aus Stufe 1: Mitte über dem Fuß und Radius (für Sichtkegel und Impostor)
        for (int t = 0; t < T; t++) {
            float[] v = meshes[T + t][0];
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < v.length; i += VSTRIDE) { lo = Math.min(lo, v[i + 1]); hi = Math.max(hi, v[i + 1]); }
            if (v.length == 0) lo = hi = 0;
            float cy = (lo + hi) * 0.5f, r = 0.5f;
            for (int i = 0; i < v.length; i += VSTRIDE) {
                float dx = v[i], dy = v[i + 1] - cy, dz = v[i + 2];
                r = Math.max(r, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            tinfo[4 * t + 1] = r * 1.02f;
            tinfo[4 * t + 2] = cy;
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, typeBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, tinfo.length * 4L, Buffers.newDirectFloatBuffer(tinfo), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        lastMeshes = meshes;
        bakeImpostors(gl);
    }

    /** Backt die Atlanten der Impostors: je stehendem Typ eine Schicht, N × N Ansichten zu {@link #framePx} Bildpunkten. */
    private void bakeImpostors(GL4 gl) {
        int F = framePx, A = F * FRAMES, layers = Math.max(1, S * V);
        int levels = Math.max(1, Integer.numberOfTrailingZeros(Integer.highestOneBit(F)) - 1);
        if (impPx != F) {
            int[] tx = {albTex, nrmTex};
            gl.glDeleteTextures(2, tx, 0);
            gl.glGenTextures(2, tx, 0);
            albTex = tx[0];
            nrmTex = tx[1];
            for (int k = 0; k < 2; k++) {
                gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tx[k]);
                gl.glTexStorage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, levels, k == 0 ? GL.GL_SRGB8_ALPHA8 : GL.GL_RGBA8, A, A, layers);
                gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
                gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL2ES3.GL_TEXTURE_MAX_LEVEL, levels - 1);
            }
            gl.glBindRenderbuffer(GL.GL_RENDERBUFFER, bakeDepth);
            gl.glRenderbufferStorage(GL.GL_RENDERBUFFER, GL.GL_DEPTH_COMPONENT24, A, A);
            impPx = F;
            impLevels = levels;
        }
        int[] vp = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        int[] prevFbo = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        // seit LituyaBay1958 W6 Runde 6: das Backen rechnet mit Tiefe −1..1 (forestgpu_bake.vert: die dem Auge zugewandte
        // Hälfte des Baums hat z < 0). Stellt die App glClipControl auf 0..1 (umgekehrte Tiefe), fiel genau diese Hälfte weg:
        // die Impostors zeigten den Baum von innen, mit freien Stämmen. Für das Backen zurückstellen, danach wiederherstellen.
        int[] clip = new int[2], st = new int[1];
        gl.glGetIntegerv(SunShadowMap.CLIP_ORIGIN, clip, 0);
        gl.glGetIntegerv(SunShadowMap.CLIP_DEPTH_MODE, clip, 1);
        gl.glGetIntegerv(GL.GL_DEPTH_FUNC, st, 0);
        boolean depthOn = gl.glIsEnabled(GL.GL_DEPTH_TEST), cullOn = gl.glIsEnabled(GL.GL_CULL_FACE), blendOn = gl.glIsEnabled(GL.GL_BLEND);
        // Runde 6b: auch Schreibmaske der Tiefe und der Farben, Schere. Mit glDepthMask(false) aus einem Durchgang davor
        // (Lituya: durchsichtige Durchgänge) blieb die Tiefe beim Backen leer, der zuletzt gezeichnete Teil lag oben: Stämme
        // und Äste vor der Krone, je nachdem, ob die Sonnenschattenkarte (sie setzt die Maske) vorher lief
        byte[] dm = new byte[1], cm = new byte[4];
        gl.glGetBooleanv(GL.GL_DEPTH_WRITEMASK, dm, 0);
        gl.glGetBooleanv(GL.GL_COLOR_WRITEMASK, cm, 0);
        boolean scissorOn = gl.glIsEnabled(GL.GL_SCISSOR_TEST);
        int[] ff = new int[1];
        gl.glGetIntegerv(GL.GL_FRONT_FACE, ff, 0);
        gl.glFrontFace(GL.GL_CCW);   // Runde 7b: das Spiegelbild der App dreht die Wicklung um
        gl.glDepthMask(true);
        gl.glColorMask(true, true, true, true);
        gl.glDisable(GL.GL_SCISSOR_TEST);
        boolean umstellen = clip[1] != SunShadowMap.NEGATIVE_ONE_TO_ONE && clip[1] != 0;
        if (umstellen) gl.glClipControl(clip[0], SunShadowMap.NEGATIVE_ONE_TO_ONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bakeFbo);
        gl.glFramebufferRenderbuffer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_RENDERBUFFER, bakeDepth);
        gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL2ES2.GL_COLOR_ATTACHMENT1}, 0);
        gl.glUseProgram(bakeProg);
        gl.glBindVertexArray(meshVao);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL.GL_FRAMEBUFFER_SRGB);
        for (int t = 0; t < S * V; t++) {
            gl.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, albTex, 0, t);
            gl.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL2ES2.GL_COLOR_ATTACHMENT1, nrmTex, 0, t);
            gl.glViewport(0, 0, A, A);
            // Hintergrund: mittlere Farbe der Krone ohne Deckung, damit die Mip-Stufen keinen dunklen Saum bekommen
            float[] mc = meanLeafColor(lastMeshes[T + t][0]);
            gl.glClearBufferfv(GL2ES3.GL_COLOR, 0, new float[]{mc[0], mc[1], mc[2], 0}, 0);
            gl.glClearBufferfv(GL2ES3.GL_COLOR, 1, new float[]{0.5f, 1f, 0.5f, 0}, 0);
            gl.glClearBufferfv(GL2ES3.GL_DEPTH, 0, new float[]{1}, 0);
            int b = T + t;
            if (meshCount[b] == 0) continue;
            gl.glUniform3f(loc(gl, bakeProg, "uC"), 0, tinfo[4 * t + 2], 0);
            gl.glUniform1f(loc(gl, bakeProg, "uR"), tinfo[4 * t + 1]);
            for (int fy = 0; fy < FRAMES; fy++)
                for (int fx = 0; fx < FRAMES; fx++) {
                    float[] d = Octahedral.frameDir(fx, fy, FRAMES);
                    float[][] ru = Octahedral.frameBasis(d);
                    gl.glViewport(fx * F, fy * F, F, F);
                    gl.glUniform3f(loc(gl, bakeProg, "uDir"), d[0], d[1], d[2]);
                    gl.glUniform3f(loc(gl, bakeProg, "uRight"), ru[0][0], ru[0][1], ru[0][2]);
                    gl.glUniform3f(loc(gl, bakeProg, "uUp"), ru[1][0], ru[1][1], ru[1][2]);
                    gl.glDrawElementsBaseVertex(GL.GL_TRIANGLES, meshCount[b], GL.GL_UNSIGNED_INT, meshFirst[b] * 4L, meshBase[b]);
                }
        }
        gl.glDisable(GL.GL_FRAMEBUFFER_SRGB);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glDrawBuffers(1, new int[]{prevFbo[0] == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0}, 0);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (umstellen) gl.glClipControl(clip[0], clip[1]);
        gl.glDepthFunc(st[0]);
        if (!depthOn) gl.glDisable(GL.GL_DEPTH_TEST);
        if (cullOn) gl.glEnable(GL.GL_CULL_FACE);
        if (blendOn) gl.glEnable(GL.GL_BLEND);
        gl.glDepthMask(dm[0] != 0);
        gl.glColorMask(cm[0] != 0, cm[1] != 0, cm[2] != 0, cm[3] != 0);
        if (scissorOn) gl.glEnable(GL.GL_SCISSOR_TEST);
        gl.glFrontFace(ff[0]);
        impBakes++;
        for (int tex : new int[]{albTex, nrmTex}) {
            gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, tex);
            gl.glGenerateMipmap(GL2ES3.GL_TEXTURE_2D_ARRAY);
        }
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
    }

    private int impLevels = 1;
    /** Wie oft die Impostors gebacken wurden (Prüfung, Protokoll). */
    public int impBakes;

    private static float[] meanLeafColor(float[] v) {
        double r = 0, g = 0, b = 0;
        int n = 0;
        for (int i = 0; i < v.length; i += VSTRIDE)
            if (v[i + 10] > 0.25f && v[i + 10] < 1.5f) { r += v[i + 6]; g += v[i + 7]; b += v[i + 8]; n++; }   // ohne Karten (Runde 7)
        if (n == 0) for (int i = 0; i < v.length; i += VSTRIDE) { r += v[i + 6]; g += v[i + 7]; b += v[i + 8]; n++; }
        n = Math.max(1, n);
        return new float[]{(float) (r / n), (float) (g / n), (float) (b / n)};
    }

    private void uploadGrids(GL4 gl) {
        if (denDirty && culture.density != null) {
            ForestCulture.Grid g = culture.density;
            ByteBuffer bb = Buffers.newDirectByteBuffer(g.v.length);
            for (float x : g.v) bb.put((byte) Math.round(Math.max(0, Math.min(1, x)) * 255));
            bb.flip();
            gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, denTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, GL2ES2.GL_R8, g.n, g.n, g.layers, 0, GL2ES2.GL_RED, GL.GL_UNSIGNED_BYTE, bb);
            nearest(gl, GL2ES3.GL_TEXTURE_2D_ARRAY);
            denDirty = false;
        }
        if (hgtDirty && culture.height != null) {
            ForestCulture.Grid g = culture.height;
            gl.glBindTexture(GL.GL_TEXTURE_2D, hgtTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_R32F, g.n, g.n, 0, GL2ES2.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(g.v));
            nearest(gl, GL.GL_TEXTURE_2D);
            hgtDirty = false;
            hgtRegion = null;
        } else if (hgtRegion != null && culture.height != null) {
            // W9c Runde 2: nur das geänderte Rechteck
            ForestCulture.Grid g = culture.height;
            int[] r = hgtRegion;
            int w = r[2] - r[0], h = r[3] - r[1];
            java.nio.FloatBuffer fb = Buffers.newDirectFloatBuffer(w * h);
            for (int j = r[1]; j < r[3]; j++) fb.put(g.v, j * g.n + r[0], w);
            fb.flip();
            gl.glBindTexture(GL.GL_TEXTURE_2D, hgtTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, r[0], r[1], w, h, GL2ES2.GL_RED, GL.GL_FLOAT, fb);
            hgtRegion = null;
        }
        TreeStates s = culture.states;
        if (s != null && s.version() != stVersion) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, stTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            // seit LituyaBay1958 Phase 14: 16 Bit je Platz, oben der Nachwuchs (TreeStates.setGrowth)
            byte[] dat = s.data(), gro = s.growthData();
            java.nio.ShortBuffer sb = Buffers.newDirectShortBuffer(dat.length);
            for (int k = 0; k < dat.length; k++) sb.put((short) ((dat[k] & 0xff) | (gro == null ? 0 : (gro[k] & 0xff) << 8)));
            sb.flip();
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL2ES3.GL_R16UI, s.w, s.h, 0, GL2ES3.GL_RED_INTEGER, GL.GL_UNSIGNED_SHORT, sb);
            nearest(gl, GL.GL_TEXTURE_2D);
            stVersion = s.version();
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
    }

    private static void nearest(GL4 gl, int target) {
        gl.glTexParameteri(target, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(target, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    }

    // ------------------------------------------------------------------ je Bild

    /** Übernimmt Gebackenes und rechnet die Kultur für diesen Blick (Projektion·Blick und Auge aus v). */
    public void update(GL4 gl, ForestGl.View v) {
        if (pending != null && pending.isDone()) {
            try {
                upload(gl, pending.get());
                uploaded = true;
            } catch (Exception e) {
                System.err.println("forest (GPU): Backen fehlgeschlagen: " + e);
            }
            bakedDay = pendingDay;
            bakedSnow = pendingSnow;
            pending = null;
        }
        if (!uploaded) { info = "Wald (GPU): wird gebacken"; return; }
        if (impPx != framePx) bakeImpostors(gl);
        ensureCapacity(gl);
        uploadGrids(gl);
        cull(gl, v.viewProj, v.eye);
        if (frame++ % 30 == 0) readStats(gl);
    }

    private long instBytes;

    private void ensureCapacity(GL4 gl) {
        long want = (long) capacity * 32;
        if (want == instBytes) return;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instBuf);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, want, null, GL4.GL_DYNAMIC_COPY);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        instBytes = want;
    }

    /** Rechnet die Kultur für Projektion·Blick vp und Auge eye (die Eimer liegen danach auf der Grafikkarte). */
    public void cull(GL4 gl, float[] vp, double[] eye) {
        double sp = culture.spacing;
        int n = 2 * (int) Math.ceil((radius + sp) / sp) + 2;
        cullInto(gl, vp, eye, eye[0], eye[2], n, cntBuf, cmdBuf, instBuf, statBuf, capacity);
        slots = (long) n * n;
    }

    /**
     * Seit LituyaBay1958 W6 Runde 6: eine eigene Auswahl für eine Schattenkarte, mit den Seitenebenen von lightVP (Licht,
     * senkrecht projiziert) statt des Blicks, in eigenen Puffern: Bäume außerhalb des Blicks werfen ihre Schatten hinein.
     * Stufen und Reichweite wie im Bild (nach dem Auge eye); das Fenster der geprüften Plätze ist ein Quadrat der halben
     * Kante half (m) um (cx, cz) am Boden. Danach {@link #drawShadowCulled}.
     */
    public void cullShadow(GL4 gl, float[] lightVP, double[] eye, double cx, double cz, double half) {
        if (!uploaded) return;
        double sp = culture.spacing;
        int n = 2 * (int) Math.ceil((half + sp) / sp) + 2;
        int cap = Math.max(4096, Math.min(capacity, n * n));
        if (shVao == 0 || cap > shCap) shadowBuffers(gl, cap);
        cullInto(gl, lightVP, eye, cx, cz, n, shCnt, shCmd, shInst, shStat, shCap);
        shadowCulled = true;
    }

    /** Bäume der eigenen Schattenauswahl ({@link #cullShadow}), die ersten levels Stufen, in die gebundene Karte. */
    public void drawShadowCulled(GL4 gl, float[] lightVP, double t, int levels) {
        if (!uploaded || !shadowCulled || levels <= 0) return;
        gl.glUseProgram(shadowProg);
        common(gl, shadowProg, lightVP, new double[]{0, 0, 0}, t);
        ForestGl.cardUniforms(gl, shadowProg, beardTexture, 0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, shCmd);
        gl.glBindVertexArray(shVao);
        gl.glMultiDrawElementsIndirect(GL.GL_TRIANGLES, GL.GL_UNSIGNED_INT, 0, Math.min(2, levels) * T, 20);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glUseProgram(0);
    }

    private int shCnt, shCmd, shInst, shStat, shVao, shCap;
    private int uTimeFrame;
    private boolean shadowCulled;

    private void shadowBuffers(GL4 gl, int cap) {
        int bins = LODS * T;
        if (shVao == 0) {
            int[] b = new int[4];
            gl.glGenBuffers(4, b, 0);
            shCnt = b[0]; shCmd = b[1]; shInst = b[2]; shStat = b[3];
            alloc(gl, shCnt, bins * 4L, GL4.GL_DYNAMIC_COPY);
            alloc(gl, shCmd, bins * 20L, GL4.GL_DYNAMIC_COPY);
            alloc(gl, shStat, (bins + 2) * 4L, GL4.GL_DYNAMIC_READ);
            int[] va = new int[1];
            gl.glGenVertexArrays(1, va, 0);
            shVao = va[0];
        }
        alloc(gl, shInst, (long) cap * 32, GL4.GL_DYNAMIC_COPY);
        shCap = cap;
        gl.glBindVertexArray(shVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        int st = VSTRIDE * 4;
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, shInst);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(4 + a);
            gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, 32, a * 16L);
            gl.glVertexAttribDivisor(4 + a, 1);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private void cullInto(GL4 gl, float[] vp, double[] eye, double cx, double cz, int n, int cnt, int cmd, int inst, int stat, int cap) {
        double sp = culture.spacing;
        int i0 = (int) Math.floor(cx / sp) - n / 2, j0 = (int) Math.floor(cz / sp) - n / 2;
        float[] planes = planes(vp);
        gl.glUseProgram(cullProg);
        int p = cullProg;
        gl.glUniform1f(loc(gl, p, "uSpacing"), (float) sp);
        gl.glUniform1ui(loc(gl, p, "uSeed"), culture.seed32());
        gl.glUniform2i(loc(gl, p, "uSV"), S, V);
        gl.glUniform3f(loc(gl, p, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform4fv(loc(gl, p, "uPlanes"), 4, planes, 0);
        gl.glUniform4f(loc(gl, p, "uRange"), (float) radius, (float) thinFrom, (float) lod0, (float) lod1);
        gl.glUniform1f(loc(gl, p, "uLod2"), (float) lod2);
        float[] nc = new float[16];
        if (nearCut != null) for (int s = 0; s < Math.min(S, nearCut.length); s++) nc[s] = (float) Math.max(0, nearCut[s]);
        gl.glUniform1fv(loc(gl, p, "uNear"), 16, nc, 0);
        gl.glUniform1f(loc(gl, p, "uNearBand"), (float) nearBand);
        culture.minGround = (float) minGround;
        gl.glUniform1f(loc(gl, p, "uMinGround"), culture.minGround == Float.NEGATIVE_INFINITY ? -3.0e38f : culture.minGround);
        gl.glUniform1f(loc(gl, p, "uCurv"), (float) curvature);
        float[] yw = new float[16], ys = new float[16], yo = new float[16], sz = new float[16];
        for (int s = 0; s < S; s++) {
            yo[s] = Double.isNaN(culture.yaw[s]) ? 0 : 1;
            yw[s] = yo[s] > 0 ? (float) culture.yaw[s] : 0;
            ys[s] = (float) culture.yawSpread[s];
            sz[s] = (float) culture.sizeFactor[s];
        }
        gl.glUniform1fv(loc(gl, p, "uYaw"), 16, yw, 0);
        gl.glUniform1fv(loc(gl, p, "uYawSpread"), 16, ys, 0);
        gl.glUniform1fv(loc(gl, p, "uYawOn"), 16, yo, 0);
        gl.glUniform1fv(loc(gl, p, "uSize"), 16, sz, 0);
        ForestCulture.Grid dg = culture.density, hg = culture.height;
        TreeStates st = culture.states;
        gl.glUniform1i(loc(gl, p, "uDenOn"), dg != null ? 1 : 0);
        if (dg != null) gl.glUniform4f(loc(gl, p, "uDenRect"), (float) dg.x0, (float) dg.z0, (float) dg.size, dg.n);
        gl.glUniform1i(loc(gl, p, "uHgtOn"), hg != null ? 1 : 0);
        if (hg != null) gl.glUniform4f(loc(gl, p, "uHgtRect"), (float) hg.x0, (float) hg.z0, (float) hg.size, hg.n);
        gl.glUniform1i(loc(gl, p, "uStOn"), st != null ? 1 : 0);
        if (st != null) gl.glUniform4i(loc(gl, p, "uStRect"), st.i0, st.j0, st.w, st.h);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, denTex);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hgtTex);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, stTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, p, "uDensity"), 0);
        gl.glUniform1i(loc(gl, p, "uHeight"), 1);
        gl.glUniform1i(loc(gl, p, "uStates"), 2);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, cnt);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, cmd);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, inst);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, typeBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 4, meshBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 5, stat);
        int groups = (n + 15) / 16;
        // Zähler auf 0 (der Schreiblauf des letzten Bilds hat sie wieder hochgezählt)
        if (zeros == null) zeros = Buffers.newDirectIntBuffer(LODS * T);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cnt);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, LODS * T * 4L, zeros);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glUniform4i(loc(gl, p, "uWin"), i0, j0, n, 0);
        gl.glDispatchCompute(groups, groups, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        gl.glUseProgram(scanProg);
        gl.glUniform1ui(loc(gl, scanProg, "uBins"), LODS * T);
        gl.glUniform1ui(loc(gl, scanProg, "uCap"), cap);
        gl.glDispatchCompute(1, 1, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        gl.glUseProgram(cullProg);
        gl.glUniform4i(loc(gl, p, "uWin"), i0, j0, n, 1);
        gl.glDispatchCompute(groups, groups, 1);
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_COMMAND_BARRIER_BIT | GL4.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        gl.glUseProgram(0);
    }

    private long slots;
    private IntBuffer zeros;

    /** Liest die Zähler der letzten Kultur (wartet auf die Grafikkarte; selten aufrufen). */
    /**
     * Seit LituyaBay1958 W6 Runde 7: die Zählung der letzten Prüfung ohne Warten. Kopiert sie auf der Grafikkarte in einen
     * von drei dauerhaft abgebildeten Puffern und gibt die jüngste fertige Kopie eines früheren Aufrufs zurück (oder null),
     * zusammen mit der Nummer des Aufrufs in {@link #asyncStatsCall}. Für Protokolle je Bild; {@link #readStats} wartet.
     */
    public int[] statsAsync(GL4 gl) {
        int bins = LODS * T, bytes = (bins + 2) * 4;
        if (asBuf == null) {
            asBuf = new int[3];
            asMap = new java.nio.IntBuffer[3];
            asFence = new long[3];
            asCall = new long[3];
            gl.glGenBuffers(3, asBuf, 0);
            int fl = GL4.GL_MAP_READ_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT;
            for (int k = 0; k < 3; k++) {
                gl.glBindBuffer(GL4.GL_COPY_WRITE_BUFFER, asBuf[k]);
                gl.glBufferStorage(GL4.GL_COPY_WRITE_BUFFER, bytes, null, fl);
                asMap[k] = gl.glMapBufferRange(GL4.GL_COPY_WRITE_BUFFER, 0, bytes, fl).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
            }
            gl.glBindBuffer(GL4.GL_COPY_WRITE_BUFFER, 0);
        }
        int[] out = null;
        long best = -1;
        int[] st = new int[1];
        for (int k = 0; k < 3; k++) {
            if (asFence[k] == 0) continue;
            gl.glGetSynciv(asFence[k], GL4.GL_SYNC_STATUS, 1, null, 0, st, 0);
            if (st[0] != GL4.GL_SIGNALED) continue;
            gl.glDeleteSync(asFence[k]);
            asFence[k] = 0;
            if (asCall[k] > best) {
                best = asCall[k];
                out = new int[bins + 2];
                asMap[k].position(0);
                asMap[k].get(out);
            }
        }
        if (best >= 0) asyncStatsCall = best;
        for (int k = 0; k < 3; k++) {
            if (asFence[k] != 0) continue;
            gl.glBindBuffer(GL4.GL_COPY_READ_BUFFER, statBuf);
            gl.glBindBuffer(GL4.GL_COPY_WRITE_BUFFER, asBuf[k]);
            gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glCopyBufferSubData(GL4.GL_COPY_READ_BUFFER, GL4.GL_COPY_WRITE_BUFFER, 0, 0, bytes);
            gl.glMemoryBarrier(GL4.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
            gl.glBindBuffer(GL4.GL_COPY_READ_BUFFER, 0);
            gl.glBindBuffer(GL4.GL_COPY_WRITE_BUFFER, 0);
            asFence[k] = gl.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            asCall[k] = ++asCalls;
            break;
        }
        return out;
    }

    /** Nummer des Aufrufs von {@link #statsAsync}, dessen Zählung zuletzt zurückkam. */
    public long asyncStatsCall = -1;
    private int[] asBuf;
    private java.nio.IntBuffer[] asMap;
    private long[] asFence, asCall;
    private long asCalls;

    public int[] readStats(GL4 gl) {
        int bins = LODS * T;
        IntBuffer ib = Buffers.newDirectIntBuffer(bins + 2);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, statBuf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (bins + 2) * 4L, ib);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        int[] s = new int[bins + 2];
        ib.get(s);
        lastStats = s;
        long[] lod = lodCounts();
        long tris = 0;
        for (int b = 0; b < bins; b++) if (b < 3 * T || b >= 3 * T + S * V) tris += (long) s[2 + b] * (meshCount[b] / 3);
        tris += lod[3] * 2;
        triangles = tris;
        info = String.format(Locale.GERMAN, "Wald (GPU): %,d Bäume (%,d / %,d / %,d / %,d je Stufe, Impostors %,d), %,d Dreiecke, %,d Plätze geprüft%s, %d MB, Tag %d",
                s[0], lod[0], lod[1], lod[2], lod[3], impostors(), tris, slots, s[1] > 0 ? String.format(Locale.GERMAN, ", %,d verworfen (Puffer voll)", s[1]) : "",
                vramBytes() >> 20, bakedDay);
        return s;
    }

    private long triangles;

    public long triangles() { return triangles; }

    /** Seit W6 Runde 6: die Impostors sofort neu backen (Prüfungen; sonst backt {@link #update} selbst). */
    public void rebakeImpostors(GL4 gl) { if (uploaded) bakeImpostors(gl); }

    /** Seit W6 Runde 6: der ganze Farbatlas (RGBA8, alle Schichten, Stufe 0); wartet auf die Grafikkarte, nur für Prüfungen. */
    public byte[] atlasBytes(GL4 gl) {
        int A = impPx * FRAMES, layers = S * V;
        if (impPx == 0) return new byte[0];
        ByteBuffer bb = Buffers.newDirectByteBuffer(A * A * 4 * layers);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, albTex);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        ((com.jogamp.opengl.GL2GL3) gl).glGetTexImage(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        byte[] out = new byte[bb.capacity()];
        bb.get(out);
        return out;
    }

    /** Anteil gedeckter Bildpunkte (Alpha ≥ 0,5) je Schicht des Farbatlas, Stufe 0; wartet auf die Grafikkarte, nur für Prüfungen. */
    public double[] atlasCoverage(GL4 gl) {
        int A = impPx * FRAMES, layers = S * V;
        if (impPx == 0) return new double[0];
        ByteBuffer bb = Buffers.newDirectByteBuffer(A * A * 4 * layers);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, albTex);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        ((com.jogamp.opengl.GL2GL3) gl).glGetTexImage(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, 0);
        double[] c = new double[layers];
        for (int l = 0; l < layers; l++) {
            long n = 0;
            for (int p = 0; p < A * A; p++) if ((bb.get((l * A * A + p) * 4 + 3) & 255) >= 128) n++;
            c[l] = n / (double) (A * A);
        }
        return c;
    }

    /** Die Instanzen der letzten Kultur je Eimer (je Baum 8 Werte wie im Puffer); wartet auf die Grafikkarte, nur für Prüfungen. */
    public float[][] readInstances(GL4 gl) { return readInstances(gl, cmdBuf, instBuf); }

    /** Wie {@link #readInstances}, aber die der letzten {@link #cullShadow} (null ohne); nur für Prüfungen. */
    public float[][] readShadowInstances(GL4 gl) { return shadowCulled ? readInstances(gl, shCmd, shInst) : null; }

    private float[][] readInstances(GL4 gl, int cmdBuf, int instBuf) {
        int bins = LODS * T;
        IntBuffer cb = Buffers.newDirectIntBuffer(5 * bins);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, cmdBuf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, 20L * bins, cb);
        int[] cmd = new int[5 * bins];
        cb.get(cmd);
        long total = 0;
        for (int b = 0; b < bins; b++) total = Math.max(total, (long) cmd[5 * b + 4] + cmd[5 * b + 1]);
        FloatBuffer fb = Buffers.newDirectFloatBuffer((int) Math.max(1, total * 8));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, instBuf);
        if (total > 0) gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, total * 32, fb);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        float[] all = new float[(int) (total * 8)];
        fb.get(all);
        float[][] out = new float[bins][];
        for (int b = 0; b < bins; b++) out[b] = java.util.Arrays.copyOfRange(all, cmd[5 * b + 4] * 8, (cmd[5 * b + 4] + cmd[5 * b + 1]) * 8);
        return out;
    }

    /**
     * Dieselbe Auswahl wie {@code forestgpu_cull.comp} auf dem Prozessor (über {@link ForestCulture}), je Eimer die
     * Instanzen; für Prüfungen. Gilt nach dem ersten {@link #update} (die Hülle je Typ kommt aus den Netzen).
     */
    public float[][] cullCpu(float[] vp, double[] eye) {
        culture.minGround = (float) minGround;
        int bins = LODS * T;
        float[][] out = new float[bins][];
        int[] n = new int[bins];
        for (int b = 0; b < bins; b++) out[b] = new float[64];
        double sp = culture.spacing;
        int w = 2 * (int) Math.ceil((radius + sp) / sp) + 2;
        int i0 = (int) Math.floor(eye[0] / sp) - w / 2, j0 = (int) Math.floor(eye[2] / sp) - w / 2;
        float[] pl = planes(vp);
        float ex = (float) eye[0], ey = (float) eye[1], ez = (float) eye[2], spf = (float) sp, R = (float) radius;
        ForestCulture.Tree t = new ForestCulture.Tree();
        for (int gj = 0; gj < w; gj++)
            for (int gi = 0; gi < w; gi++) {
                int i = i0 + gi, j = j0 + gj;
                float cx = (i + 0.5f) * spf - ex, cz = (j + 0.5f) * spf - ez;
                if (cx * cx + cz * cz > (R + spf) * (R + spf)) continue;
                if (!culture.at(i, j, t)) continue;
                int h = ForestCulture.slotHash(i, j, culture.seed32());
                float dx = t.x - ex, dy = t.y + 8 - ey, dz = t.z - ez;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > R) continue;
                if (cutNear(t, h, dist)) continue;
                float widen = 1, thin0 = R * (float) thinFrom;
                if (dist > thin0) {
                    float u = Math.max(0, Math.min(1, (dist - thin0) / (R - thin0)));
                    float p = 1 - u * u * (3 - 2 * u) * 0.75f;
                    if (ForestCulture.u01(h, 9) > p) continue;
                    widen = (float) Math.min(1.7, 1 / Math.sqrt(p));
                }
                float cy = t.y + tinfo[4 * t.type + 2] * t.scale, rad = tinfo[4 * t.type + 1] * t.scale * widen + 1;
                boolean vis = true;
                for (int k = 0; k < 4 && vis; k++) vis = pl[4 * k] * t.x + pl[4 * k + 1] * cy + pl[4 * k + 2] * t.z + pl[4 * k + 3] >= -rad;
                if (!vis) continue;
                float hf = tinfo[4 * t.type] * t.scale / 20, e = dist / Math.max(0.6f, hf);
                int lod = e < lod0 ? 0 : e < lod1 ? 1 : dist < lod2 ? 2 : 3;
                int b = lod * T + t.type;
                if (meshCount != null && meshCount[b] == 0) continue;
                if (8 * n[b] + 8 > out[b].length) out[b] = java.util.Arrays.copyOf(out[b], out[b].length * 2);
                float[] o = out[b];
                int q = 8 * n[b]++;
                o[q] = t.x; o[q + 1] = t.y; o[q + 2] = t.z; o[q + 3] = t.yaw;
                o[q + 4] = t.scale; o[q + 5] = t.tint; o[q + 6] = lod == 3 && t.state == 0 ? t.type : t.phase; o[q + 7] = widen;
            }
        for (int b = 0; b < bins; b++) out[b] = java.util.Arrays.copyOf(out[b], 8 * n[b]);
        return out;
    }

    /** Fällt der Baum unter den Nahschnitt? Gleiche Rechnung wie {@code forestgpu_cull.comp}. */
    private boolean cutNear(ForestCulture.Tree t, int h, float dist) {
        if (nearCut == null || t.state != 0 || t.species >= nearCut.length || nearCut[t.species] <= 0) return false;
        return dist < nearCut[t.species] + nearBand * (ForestCulture.u01(h, 10) - 0.5);
    }

    /**
     * Die Bäume, die der Nahschnitt um das Auge eye wegnimmt (alle Arten mit nearCut &gt; 0), auf dem Prozessor aus
     * {@link ForestCulture}: je Baum Art, Variante, Fuß x, y, z, Drehung (rad), Maßstab und Höhe (m) des forest-Modells
     * in out (8 Werte). Ohne Sichtkegel: die App prüft selbst. Rückgabe: Anzahl. Kostet bei 40 m Schnitt und 4 m
     * Abstand rund 400 Plätze.
     */
    public int nearTrees(double[] eye, java.util.List<float[]> out) {
        out.clear();
        culture.minGround = (float) minGround;
        if (nearCut == null) return 0;
        double maxCut = 0;
        for (int s = 0; s < Math.min(S, nearCut.length); s++) maxCut = Math.max(maxCut, nearCut[s]);
        if (maxCut <= 0) return 0;
        double sp = culture.spacing, r = maxCut + nearBand;
        int i0 = (int) Math.floor((eye[0] - r) / sp), i1 = (int) Math.floor((eye[0] + r) / sp);
        int j0 = (int) Math.floor((eye[2] - r) / sp), j1 = (int) Math.floor((eye[2] + r) / sp);
        ForestCulture.Tree t = new ForestCulture.Tree();
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                if (!culture.at(i, j, t)) continue;
                int h = ForestCulture.slotHash(i, j, culture.seed32());
                double dx = t.x - eye[0], dy = t.y + 8 - eye[1], dz = t.z - eye[2];
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!cutNear(t, h, dist)) continue;
                float hgt = tinfo[4 * t.type] * t.scale;
                out.add(new float[]{t.species, t.variant, t.x, t.y, t.z, t.yaw, t.scale, hgt});
            }
        return out.size();
    }

    /** Bäume je Stufe (0..3) aus dem letzten {@link #readStats}. */
    public long[] lodCounts() {
        long[] n = new long[LODS];
        if (lastStats.length < 2 + LODS * T) return n;
        for (int b = 0; b < LODS * T; b++) n[b / T] += lastStats[2 + b];
        return n;
    }

    public long impostors() {
        long n = 0;
        if (lastStats.length < 2 + LODS * T) return 0;
        for (int t = 0; t < S * V; t++) n += lastStats[2 + 3 * T + t];
        return n;
    }

    /** Sichtbare Bäume im letzten {@link #readStats}. */
    public long visible() { return lastStats.length > 0 ? lastStats[0] : 0; }

    /** Belegter Grafikspeicher (Byte): Netze, Instanzen, Atlanten, Gitter. */
    public long vramBytes() {
        long atlas = 0;
        if (impPx > 0) {
            long a = (long) impPx * FRAMES;
            atlas = a * a * 8L * S * V * 4 / 3;
        }
        long grids = 0;
        if (culture.density != null) grids += culture.density.v.length;
        if (culture.height != null) grids += culture.height.v.length * 4L;
        if (culture.states != null) grids += culture.states.data().length;
        return vboBytes + iboBytes + instBytes + atlas + grids + LODS * T * 40L;
    }

    /** Zeichnet die Bäume der letzten Kultur (nach {@link #update}). */
    public void draw(GL4 gl, ForestGl.View v, ForestGl.Light l, double t) {
        if (!uploaded) return;
        // Runde 6c: Tiefe schreiben, was die App auch zuvor eingestellt hat (danach wie vorher). Mit glDepthMask(false) aus
        // einem durchsichtigen Durchgang davor lagen Stämme vor den Kronen, je nach Reihenfolge der Eimer
        byte[] dm = new byte[1];
        gl.glGetBooleanv(GL.GL_DEPTH_WRITEMASK, dm, 0);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, cmdBuf);
        gl.glUseProgram(meshProg);
        common(gl, meshProg, v.viewProj, v.eye, t);
        light(gl, meshProg, v, l);
        gl.glUniform1i(loc(gl, meshProg, "uBark"), barkDetail ? 1 : 0);   // seit W6 Runde 5
        gl.glUniform1i(loc(gl, meshProg, "uShoot"), shootLight ? 1 : 0);
        gl.glUniform3f(loc(gl, meshProg, "uMossSide"), (float) l.mossSide[0], (float) l.mossSide[1], (float) l.mossSide[2]);
        ForestGl.mossUniforms(gl, meshProg, l);
        ForestGl.cardUniforms(gl, meshProg, beardTexture, uTimeFrame++);
        gl.glBindVertexArray(meshVao);
        gl.glMultiDrawElementsIndirect(GL.GL_TRIANGLES, GL.GL_UNSIGNED_INT, 0, 3 * T, 20);
        if (T - S * V > 0) gl.glMultiDrawElementsIndirect(GL.GL_TRIANGLES, GL.GL_UNSIGNED_INT, (3L * T + S * V) * 20, T - S * V, 20);
        gl.glUseProgram(impProg);
        gl.glUniformMatrix4fv(loc(gl, impProg, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, impProg, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1f(loc(gl, impProg, "uFrames"), FRAMES);
        gl.glUniform1f(loc(gl, impProg, "uFramePx"), impPx);
        light(gl, impProg, v, l);
        gl.glActiveTexture(GL.GL_TEXTURE8);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, albTex);
        gl.glActiveTexture(GL.GL_TEXTURE9);
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, nrmTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, impProg, "uAlb"), 8);
        gl.glUniform1i(loc(gl, impProg, "uNrm"), 9);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, typeBuf);
        gl.glBindVertexArray(impVao);
        gl.glMultiDrawElementsIndirect(GL.GL_TRIANGLES, GL.GL_UNSIGNED_INT, 3L * T * 20, S * V, 20);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glUseProgram(0);
        gl.glDepthMask(dm[0] != 0);
    }

    /** Nahe Bäume (Stufe 0 und 1 der letzten Kultur) in eine gebundene Tiefen-Schattenkarte (lightVP: Welt → Licht). */
    public void drawShadow(GL4 gl, float[] lightVP, double t) { drawShadow(gl, lightVP, t, 2); }

    /**
     * Seit LituyaBay1958 W6 Runde 5: nur die ersten levels Stufen der letzten Prüfung (1 = Stufe 0, 2 = Stufen 0 und 1)
     * in die gebundene Schattenkarte. Die Auswahl ist die des letzten {@link #cull}/{@link #update}: Bäume außerhalb des
     * Blicks werfen keinen Schatten herein.
     */
    public void drawShadow(GL4 gl, float[] lightVP, double t, int levels) {
        if (!uploaded || levels <= 0) return;
        gl.glUseProgram(shadowProg);
        common(gl, shadowProg, lightVP, new double[]{0, 0, 0}, t);
        ForestGl.cardUniforms(gl, shadowProg, beardTexture, 0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, cmdBuf);
        gl.glBindVertexArray(meshVao);
        gl.glMultiDrawElementsIndirect(GL.GL_TRIANGLES, GL.GL_UNSIGNED_INT, 0, Math.min(2, levels) * T, 20);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL4.GL_DRAW_INDIRECT_BUFFER, 0);
        gl.glUseProgram(0);
    }

    private void common(GL4 gl, int p, float[] vp, double[] eye, double t) {
        gl.glUniformMatrix4fv(loc(gl, p, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(loc(gl, p, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(loc(gl, p, "uTime"), (float) (t % 3600));
        gl.glUniform4f(loc(gl, p, "uWind"), (float) windToX, (float) windToZ, (float) windSpeed, (float) gust);
        gl.glUniform4f(loc(gl, p, "uRime"), 0, 0, 0, -1);
        gl.glUniform2f(loc(gl, p, "uRimeBand"), -1e5f, -1e5f + 1);
    }

    private static void light(GL4 gl, int p, ForestGl.View v, ForestGl.Light l) {
        gl.glUniform3f(loc(gl, p, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(loc(gl, p, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, p, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(loc(gl, p, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform3f(loc(gl, p, "uMoonDir"), (float) l.moonDir[0], (float) l.moonDir[1], (float) l.moonDir[2]);
        gl.glUniform3f(loc(gl, p, "uMoonColor"), (float) l.moonColor[0], (float) l.moonColor[1], (float) l.moonColor[2]);
        gl.glUniform1f(loc(gl, p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, p, "uSkyWeight"), (float) l.skyWeight);
        gl.glUniform1f(loc(gl, p, "uWet"), (float) l.wetness);
        gl.glUniform2f(loc(gl, p, "uTone"), v.tonemap ? 1f : 0f, (float) v.exposure);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.sunShadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, p, "uShadow"), 5);
        gl.glUniform4f(loc(gl, p, "uShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1], (float) Math.max(1, l.shadowRect[2]), l.shadowTexture != 0 ? 1f : 0f);
        gl.glUniform1i(loc(gl, p, "uSunShadow"), 6);
        gl.glUniform1i(loc(gl, p, "uSky"), 7);
        gl.glUniform1i(loc(gl, p, "uSkyOn"), l.skyTexture != 0 ? 1 : 0);
        boolean ss = l.sunShadowTexture != 0 && l.sunShadowMatrix != null;
        gl.glUniformMatrix4fv(loc(gl, p, "uSunShadowM"), 1, false, ss ? l.sunShadowMatrix : new float[16], 0);
        gl.glUniform2f(loc(gl, p, "uSunShadowP"), ss ? (l.sunShadowTaps == 1 ? 2f : 1f) : 0f, (float) l.sunShadowBias);
    }

    /** Die vier Seitenebenen aus Projektion·Blick (Spalten), normiert: a, b, c, d je Ebene. */
    static float[] planes(float[] m) {
        float[] out = new float[16];
        for (int p = 0; p < 4; p++) {
            int row = p >> 1;
            float s = (p & 1) == 0 ? 1 : -1;
            double[] q = new double[4];
            for (int c = 0; c < 4; c++) q[c] = m[c * 4 + 3] + s * m[c * 4 + row];
            double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            for (int c = 0; c < 4; c++) out[4 * p + c] = (float) (q[c] / l);
        }
        return out;
    }

    public void dispose(GL4 gl) {
        baker.shutdownNow();
        if (cullProg == 0) return;
        for (int p : new int[]{cullProg, scanProg, meshProg, shadowProg, impProg, bakeProg}) gl.glDeleteProgram(p);
        gl.glDeleteBuffers(10, new int[]{vbo, ibo, quadVbo, quadIbo, instBuf, cntBuf, cmdBuf, typeBuf, meshBuf, statBuf}, 0);
        gl.glDeleteVertexArrays(2, new int[]{meshVao, impVao}, 0);
        if (shVao != 0) { gl.glDeleteVertexArrays(1, new int[]{shVao}, 0); gl.glDeleteBuffers(4, new int[]{shCnt, shCmd, shInst, shStat}, 0); shVao = 0; }
        if (asBuf != null) { for (long fe : asFence) if (fe != 0) gl.glDeleteSync(fe); gl.glDeleteBuffers(3, asBuf, 0); asBuf = null; }
        gl.glDeleteTextures(5, new int[]{denTex, hgtTex, stTex, albTex, nrmTex}, 0);
        gl.glDeleteFramebuffers(1, new int[]{bakeFbo}, 0);
        gl.glDeleteRenderbuffers(1, new int[]{bakeDepth}, 0);
        cullProg = 0;
        uploaded = false;
    }

    // ------------------------------------------------------------------ Shader

    private static int loc(GL4 gl, int p, String name) { return gl.glGetUniformLocation(p, name); }

    private static int compute(GL4 gl, String file) {
        int sh = compile(gl, GL4.GL_COMPUTE_SHADER, file, "");
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, sh);
        gl.glLinkProgram(p);
        gl.glDeleteShader(sh);
        checkLink(gl, p, file);
        return p;
    }

    private static int link(GL4 gl, String vert, String frag, String defs) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert, defs), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag, defs);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        checkLink(gl, p, vert + " + " + frag);
        return p;
    }

    private static void checkLink(GL4 gl, int p, String what) {
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] != 0) return;
        int[] len = new int[1];
        gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
        throw new IllegalStateException("forest (GPU): " + what + " lässt sich nicht binden: " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
    }

    private static int compile(GL4 gl, int type, String file, String defs) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + defs + ForestGl.source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("forest (GPU): " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return sh;
    }
}
