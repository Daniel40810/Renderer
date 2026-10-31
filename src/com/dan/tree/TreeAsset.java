package com.dan.tree;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

/**
 * Ein Baum, wie ihn {@code tools/tree/usd2dtree.py} (Unreal PVE: Skelett, Zweige) oder
 * {@code tools/tree/fbx2dtree.py} (FBX: Stamm und Laubkarten mit Textur, ohne Skelett) als {@code .dtree} schreibt.
 * Reine Daten: Skelett, Stamm und Äste als Netz, Zweig-Prototypen (Holz als Knotengraph,
 * Blätter als Sechseck), Zweig-Instanzen, Laubkarten und Namen der Texturen.
 * Alle Längen in Metern, y oben, Fuß bei (0, 0, 0).
 * Alle Felder sind öffentlich; {@link TreeBaker} macht daraus Netze in Stufen.
 */
public final class TreeAsset {

    public static final int MAGIC = 0x44545245; // 'DTRE'
    public static final int VERSION = 2;

    public String name;
    /** Name der USD-Datei, aus der der Baum stammt. */
    public String source;
    /** Aus der DynamicWind-JSON von Unreal: Dämpfung der Böen (0 = keine). */
    public float gustAttenuation;
    /** Aus der DynamicWind-JSON: Bodendecker (Strauch, Gras) statt Baum. */
    public boolean groundCover;
    /** Grundfarben aus den UsdPreviewSurface-Materialien, linear. */
    public float[] barkColor, leafColor;

    // Skelett
    public int[] jointParent;
    /** je Gelenk x, y, z */
    public float[] jointPos;
    /** je Gelenk Radius des Asts an dieser Stelle */
    public float[] jointRadius;

    /** Stamm und Äste im Original. */
    public Mesh trunk;
    /** UVs des Stamms (2 je Punkt, Bildzeilen von oben) oder null */
    public float[] trunkUv;
    /** Laubkarten mit Alpha-Textur (FBX-Bäume) oder null */
    public Cards cards;
    /** Texturen neben der .dtree-Datei (leer: keine) */
    public String barkTexture = "", leafTexture = "";

    public Twig[] twigs;

    // Zweig-Instanzen
    public short[] instProto;
    /**
     * je Instanz 12 Werte, Zeilenvektoren wie in USD:
     * x' = x·m0 + y·m3 + z·m6 + m9, y' = x·m1 + y·m4 + z·m7 + m10, z' = x·m2 + y·m5 + z·m8 + m11
     */
    public float[] instMatrix;
    /** je Instanz zwei Gelenke und Gewichte (Summe 1), an denen der Zweig hängt */
    public short[] instJoint;
    public float[] instWeight;

    /** Netz mit zwei Gelenken je Punkt. */
    public static final class Mesh {
        public float[] pos, nrm;
        public short[] joint;
        public float[] weight;
        public int[] tri;

        public int vertices() { return pos.length / 3; }
        public int triangles() { return tri.length / 3; }
    }

    /** Laubkarten: Dreiecke mit UVs in die Laubtextur, Normalen je Punkt. */
    public static final class Cards {
        public float[] pos, nrm, uv;
        public int[] tri;

        public int vertices() { return pos.length / 3; }
        public int triangles() { return tri.length / 3; }
    }

    /** Ein Zweig-Prototyp, Ursprung am Ansatz, wächst etwa entlang +y. */
    public static final class Twig {
        public String name;
        /** größter Abstand eines Punkts vom Ansatz */
        public float length;
        /** Holz als Baum aus Knoten: Eltern vor Kindern, Wurzel 0 mit Eltern −1 */
        public int[] nodeParent;
        public float[] nodePos, nodeRadius;
        /** Punkte am Rand eines Blatts (6: Spitze zuerst, dann gegen den Uhrzeigersinn um die Normale) */
        public int ring;
        /** je Blatt 3 Werte */
        public float[] leafBase, leafCenter, leafNormal;
        /** je Blatt ring · 3 Werte */
        public float[] leafRing;

        public int nodes() { return nodeParent.length; }
        public int leaves() { return leafCenter.length / 3; }
    }

    public int instances() { return instProto.length; }
    /** false für FBX-Bäume: nur ein Gelenk am Fuß, der Wind kommt aus der Geometrie */
    public boolean hasSkeleton() { return jointParent.length > 1; }
    public int joints() { return jointParent.length; }

    /** Höhe des höchsten Punkts (Stamm, Zweige, Blätter). */
    public float height() {
        float h = 0;
        for (int i = 1; i < trunk.pos.length; i += 3) h = Math.max(h, trunk.pos[i]);
        if (cards != null) for (int i = 1; i < cards.pos.length; i += 3) h = Math.max(h, cards.pos[i]);
        float[] p = new float[3];
        for (int k = 0; k < instances(); k++) {
            Twig t = twigs[instProto[k]];
            for (int n = 0; n < t.leaves(); n++) {
                transform(k, t.leafCenter, n * 3, p, 0);
                h = Math.max(h, p[1]);
            }
        }
        return h;
    }

    /** Punkt src[o..o+2] mit der Matrix der Instanz k nach dst[d..d+2]. */
    public void transform(int k, float[] src, int o, float[] dst, int d) {
        float[] m = instMatrix;
        int b = k * 12;
        float x = src[o], y = src[o + 1], z = src[o + 2];
        dst[d] = x * m[b] + y * m[b + 3] + z * m[b + 6] + m[b + 9];
        dst[d + 1] = x * m[b + 1] + y * m[b + 4] + z * m[b + 7] + m[b + 10];
        dst[d + 2] = x * m[b + 2] + y * m[b + 5] + z * m[b + 8] + m[b + 11];
    }

    /** Richtung src[o..o+2] mit der Matrix der Instanz k drehen (ohne Verschiebung, nicht normiert). */
    public void rotate(int k, float[] src, int o, float[] dst, int d) {
        float[] m = instMatrix;
        int b = k * 12;
        float x = src[o], y = src[o + 1], z = src[o + 2];
        dst[d] = x * m[b] + y * m[b + 3] + z * m[b + 6];
        dst[d + 1] = x * m[b + 1] + y * m[b + 4] + z * m[b + 7];
        dst[d + 2] = x * m[b + 2] + y * m[b + 5] + z * m[b + 8];
    }

    /** Gleichmäßiger Maßstab der Instanz k (Länge der ersten Spalte). */
    public float scale(int k) {
        int b = k * 12;
        float[] m = instMatrix;
        return (float) Math.sqrt(m[b] * m[b] + m[b + 1] * m[b + 1] + m[b + 2] * m[b + 2]);
    }

    // ------------------------------------------------------------------ Lesen

    /** Liest eine Ressource neben der Klasse {@code owner}, etwa {@code load(BlackAlder.class, "alder_a.dtree")}. */
    public static TreeAsset load(Class<?> owner, String resource) {
        try (InputStream in = owner.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Ressource fehlt: " + resource + " neben " + owner.getName());
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static TreeAsset read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16));
        if (in.readInt() != MAGIC) throw new IOException("keine .dtree-Datei");
        int version = in.readInt();
        if (version < 1 || version > VERSION) throw new IOException("Version " + version + " wird nicht unterstützt");
        TreeAsset a = new TreeAsset();
        a.name = in.readUTF();
        a.source = in.readUTF();
        a.gustAttenuation = in.readFloat();
        a.groundCover = in.readBoolean();
        a.barkColor = floats(in, 3);
        a.leafColor = floats(in, 3);

        int nj = in.readInt();
        a.jointParent = ints(in, nj);
        a.jointPos = floats(in, nj * 3);
        a.jointRadius = floats(in, nj);

        a.trunk = mesh(in);

        a.twigs = new Twig[in.readInt()];
        for (int i = 0; i < a.twigs.length; i++) {
            Twig t = new Twig();
            t.name = in.readUTF();
            t.length = in.readFloat();
            int n = in.readInt();
            t.nodeParent = ints(in, n);
            t.nodePos = floats(in, n * 3);
            t.nodeRadius = floats(in, n);
            t.ring = in.readInt();
            int nl = in.readInt();
            t.leafBase = new float[nl * 3];
            t.leafCenter = new float[nl * 3];
            t.leafNormal = new float[nl * 3];
            t.leafRing = new float[nl * t.ring * 3];
            for (int l = 0; l < nl; l++) {
                for (int k = 0; k < 3; k++) t.leafBase[l * 3 + k] = in.readFloat();
                for (int k = 0; k < 3; k++) t.leafCenter[l * 3 + k] = in.readFloat();
                for (int k = 0; k < 3; k++) t.leafNormal[l * 3 + k] = in.readFloat();
                for (int k = 0; k < t.ring * 3; k++) t.leafRing[l * t.ring * 3 + k] = in.readFloat();
            }
            a.twigs[i] = t;
        }

        int ni = in.readInt();
        a.instProto = new short[ni];
        a.instMatrix = new float[ni * 12];
        a.instJoint = new short[ni * 2];
        a.instWeight = new float[ni * 2];
        for (int k = 0; k < ni; k++) {
            a.instProto[k] = in.readShort();
            for (int m = 0; m < 12; m++) a.instMatrix[k * 12 + m] = in.readFloat();
            a.instJoint[k * 2] = in.readShort();
            a.instJoint[k * 2 + 1] = in.readShort();
            a.instWeight[k * 2] = in.readUnsignedByte() / 255f;
            a.instWeight[k * 2 + 1] = in.readUnsignedByte() / 255f;
        }
        if (version >= 2) {
            if (in.readInt() != 0) a.trunkUv = floats(in, a.trunk.vertices() * 2);
            int nc = in.readInt();
            if (nc > 0) {
                Cards c = new Cards();
                c.pos = floats(in, nc * 3);
                c.nrm = new float[nc * 3];
                for (int i = 0; i < nc * 3; i++) c.nrm[i] = in.readByte() / 127f;
                c.uv = floats(in, nc * 2);
                c.tri = ints(in, in.readInt() * 3);
                a.cards = c;
            }
            a.barkTexture = in.readUTF();
            a.leafTexture = in.readUTF();
        }
        return a;
    }

    private static Mesh mesh(DataInputStream in) throws IOException {
        Mesh m = new Mesh();
        int nv = in.readInt();
        m.pos = floats(in, nv * 3);
        m.nrm = new float[nv * 3];
        for (int i = 0; i < nv * 3; i++) m.nrm[i] = in.readByte() / 127f;
        m.joint = new short[nv * 2];
        for (int i = 0; i < nv * 2; i++) m.joint[i] = in.readShort();
        m.weight = new float[nv * 2];
        for (int i = 0; i < nv * 2; i++) m.weight[i] = in.readUnsignedByte() / 255f;
        m.tri = ints(in, in.readInt() * 3);
        return m;
    }

    private static float[] floats(DataInputStream in, int n) throws IOException {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = in.readFloat();
        return f;
    }

    private static int[] ints(DataInputStream in, int n) throws IOException {
        int[] f = new int[n];
        for (int i = 0; i < n; i++) f[i] = in.readInt();
        return f;
    }
}
