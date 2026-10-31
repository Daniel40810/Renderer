package com.dan.tree.demo;

import com.dan.tree.TreeAsset;
import com.dan.tree.TreeMesh;
import com.dan.tree.TreeSeason;
import com.dan.tree.TreeSpecies;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Einfacher Renderer auf dem Prozessor für die Demos und Selbsttests: Tiefenpuffer, Farbe je Punkt
 * (Sonne, Himmel, Durchscheinen der Blätter), Himmel als Verlauf, Boden mit weichem Schatten unter
 * den Kronen. Ohne JOGL, damit Bilder auch ohne Grafikkarte entstehen.
 */
public final class SoftRenderer {

    public int width, height;
    public BufferedImage image;
    private int[] rgb;
    private float[] depth;
    private float[] fr, fg, fb;

    // Kamera
    private final float[] vp = new float[16];
    private float ex, ey, ez;

    // Licht
    public float[] sunDir = norm(new float[]{-0.45f, 0.75f, 0.35f});   // zur Sonne
    public float[] sun = {2.6f, 2.45f, 2.2f};
    public float[] sky = {0.45f, 0.55f, 0.75f};
    public float[] groundBounce = {0.12f, 0.11f, 0.08f};
    public float exposure = 1.0f;

    /** Schattenflecken unter Bäumen: x, z, Radius, Höhe der Kronenmitte */
    private float[] blobs = new float[0];

    public void resize(int w, int h) {
        if (w == width && h == height && image != null) return;
        width = w;
        height = h;
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        depth = new float[w * h];
        fr = new float[w * h];
        fg = new float[w * h];
        fb = new float[w * h];
    }

    /** Kamera: Auge, Ziel, senkrechter Blickwinkel in Grad. */
    public void camera(float[] eye, float[] target, float fovDeg) {
        ex = eye[0];
        ey = eye[1];
        ez = eye[2];
        float fx = target[0] - eye[0], fy = target[1] - eye[1], fz = target[2] - eye[2];
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0;     // f × up
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] view = {
                sx, ux, -fx, 0,
                sy, uy, -fy, 0,
                sz, uz, -fz, 0,
                -(sx * ex + sy * ey + sz * ez), -(ux * ex + uy * ey + uz * ez), fx * ex + fy * ey + fz * ez, 1};
        float f = (float) (1 / Math.tan(Math.toRadians(fovDeg) / 2));
        float aspect = width / (float) height;
        float near = 0.1f, far = 2000f;
        float[] proj = {
                f / aspect, 0, 0, 0,
                0, f, 0, 0,
                0, 0, (far + near) / (near - far), -1,
                0, 0, 2 * far * near / (near - far), 0};
        mul(proj, view, vp);
    }

    /** Spalten-Matrix a·b (wie OpenGL). */
    static void mul(float[] a, float[] b, float[] out) {
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                out[c * 4 + r] = s;
            }
    }

    public void shadows(float[] xzrh) {
        blobs = xzrh;
    }

    /** Himmel und Tiefenpuffer leeren. */
    public void clear() {
        Arrays.fill(depth, Float.MAX_VALUE);
        IntStream.range(0, height).parallel().forEach(y -> {
            float t = y / (float) height;
            float r = 0.55f + 0.25f * t, g = 0.68f + 0.18f * t, b = 0.86f + 0.06f * t;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                fr[i] = r;
                fg[i] = g;
                fb[i] = b;
            }
        });
    }

    private BufferedImage groundImage;
    private float groundTileX = 2, groundTileZ = 2;

    /** Bodentextur (sRGB; null = einfarbig) und ihre Kachelgröße in m. */
    public void groundTexture(BufferedImage img, float tileX, float tileZ) {
        groundImage = img;
        groundTileX = tileX;
        groundTileZ = tileZ;
    }

    /**
     * Boden als Ringe um den Fußpunkt der Kamera, nah fein, fern grob (bis size/2 um (cx, cz) hinaus),
     * damit nahe Zellen nicht an der Nahebene wegfallen. Farbe linear, mit Textur eine Tönung.
     */
    public void ground(float cx, float cz, float size, float[] color, float snow) {
        int rings = 56, seg = 96;
        float ox = ex, oz = ez;
        float rmax = (float) Math.hypot(ex - cx, ez - cz) + size / 2;
        float rmin = Math.max(0.01f, Math.abs(ey) * 0.04f);
        int nv = 1 + rings * seg;
        float[] pos = new float[nv * 3];
        float[] col = new float[pos.length];
        float[] uv = new float[nv * 2];
        Texture tex = texture(groundImage);
        if (tex != null) color = new float[]{0.8f, 0.8f, 0.8f};
        for (int v = 0; v < nv; v++) {
            float x, z;
            if (v == 0) {
                x = ox;
                z = oz;
            } else {
                int ring = (v - 1) / seg, sgm = (v - 1) % seg;
                float r = (float) (rmin * Math.pow(rmax / rmin, ring / (double) (rings - 1)));
                double ang = 2 * Math.PI * sgm / seg;
                x = ox + (float) Math.cos(ang) * r;
                z = oz + (float) Math.sin(ang) * r;
            }
            int k = v * 3;
            pos[k] = x;
            pos[k + 1] = 0;
            pos[k + 2] = z;
            uv[v * 2] = x / groundTileX;
            uv[v * 2 + 1] = z / groundTileZ;
            float shade = 1;
            for (int b = 0; b < blobs.length; b += 4) {
                float h = blobs[b + 3] / Math.max(sunDir[1], 0.2f);
                float d = (float) Math.hypot(x - blobs[b] + sunDir[0] * h, z - blobs[b + 1] + sunDir[2] * h);
                float r = blobs[b + 2];
                shade *= 1 - 0.55f * smooth(r * 1.15f, r * 0.4f, d);
            }
            float light = sunDir[1] * shade;
            float var = 0.92f + 0.08f * (float) (Math.sin(x * 0.37 + Math.sin(z * 0.23) * 2) * Math.sin(z * 0.31 + x * 0.11));
            for (int e = 0; e < 3; e++) {
                float c = color[e] * var;
                c = c + (0.8f - c) * snow;
                col[k + e] = c * (sun[e] * light + sky[e] * 0.9f);
            }
        }
        int[] tri = new int[(seg + (rings - 1) * seg * 2) * 3];
        int t = 0;
        for (int sgm = 0; sgm < seg; sgm++) {
            tri[t++] = 0; tri[t++] = 1 + (sgm + 1) % seg; tri[t++] = 1 + sgm;
        }
        for (int ring = 0; ring + 1 < rings; ring++)
            for (int sgm = 0; sgm < seg; sgm++) {
                int a = 1 + ring * seg + sgm, b = 1 + ring * seg + (sgm + 1) % seg, c = a + seg, d = b + seg;
                tri[t++] = a; tri[t++] = b; tri[t++] = c;
                tri[t++] = b; tri[t++] = d; tri[t++] = c;
            }
        byte[] which = null;
        if (tex != null) {
            which = new byte[tri.length / 3];
            java.util.Arrays.fill(which, (byte) 1);
        }
        raster(project(pos, nv), col, tri, tri.length / 3, uv, which, new Texture[]{null, tex, null});
    }

    /**
     * Ein Baum. pos: verschobene Punkte im Baumraum (oder null = Ruhelage); Fuß und Drehung in der Welt.
     */
    public void tree(TreeMesh m, float[] pos, TreeSpecies sp, TreeAsset a, TreeSeason.Look look,
                     float x, float y, float z, float yaw, float scale) {
        int n = m.vertices;
        float[] world = new float[n * 3];
        float[] col = new float[n * 3];
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float[] v = m.v;
        boolean[] hidden = new boolean[n];
        IntStream.range(0, (n + 4095) / 4096).parallel().forEach(chunk -> {
            float[] base = new float[3];
            int end = Math.min(n, (chunk + 1) * 4096);
            for (int i = chunk * 4096; i < end; i++) {
                int b = i * TreeMesh.STRIDE;
                float px = pos != null ? pos[i * 3] : v[b], py = pos != null ? pos[i * 3 + 1] : v[b + 1], pz = pos != null ? pos[i * 3 + 2] : v[b + 2];
                float wx = x + (px * c - pz * s) * scale, wy = y + py * scale, wz = z + (px * s + pz * c) * scale;
                world[i * 3] = wx;
                world[i * 3 + 1] = wy;
                world[i * 3 + 2] = wz;
                if (!m.color(i, sp, a, look, base)) {
                    hidden[i] = true;
                    continue;
                }
                float nx = v[b + 3] * c - v[b + 5] * s, ny = v[b + 4], nz = v[b + 3] * s + v[b + 5] * c;
                boolean leaf = m.leaf(i);
                float vx = ex - wx, vy = ey - wy, vz = ez - wz;
                float trans = 0;
                if (leaf) {
                    float dv = nx * vx + ny * vy + nz * vz;
                    float ndl0 = nx * sunDir[0] + ny * sunDir[1] + nz * sunDir[2];
                    if (dv < 0) { nx = -nx; ny = -ny; nz = -nz; ndl0 = -ndl0; }
                    // Gegenlicht: Sonne hinter dem Blatt
                    trans = Math.max(0, -ndl0) * 0.45f;
                }
                float ndl = nx * sunDir[0] + ny * sunDir[1] + nz * sunDir[2];
                float diff = leaf ? Math.max(0, ndl * 0.8f + 0.2f) : Math.max(0, ndl);
                float ao = m.ao(i);
                float hemi = 0.5f + 0.5f * ny;
                for (int e = 0; e < 3; e++) {
                    float light = sun[e] * (diff + trans) * (0.35f + 0.65f * ao) + (sky[e] * hemi + groundBounce[e] * (1 - hemi)) * (0.4f + 0.6f * ao);
                    col[i * 3 + e] = base[e] * light;
                }
            }
        });
        float[] scr = project(world, n);
        // Texturen: je Dreieck 0 keine, 1 Rinde, 2 Laub
        Texture[] texs = {null, m.barkTextured ? texture(sp.texture(a.barkTexture)) : null,
                m.leafTextured ? texture(sp.texture(a.leafTexture)) : null};
        float[] uv = null;
        if (texs[1] != null || texs[2] != null) {
            uv = new float[n * 2];
            for (int i = 0; i < n; i++) {
                uv[i * 2] = v[i * TreeMesh.STRIDE + TreeMesh.U];
                uv[i * 2 + 1] = v[i * TreeMesh.STRIDE + TreeMesh.V];
            }
        }
        // verborgene Blätter: Dreieck auslassen
        int[] tri = m.tri;
        int nt = m.triangles;
        int[] keep = new int[nt * 3];
        byte[] which = new byte[nt];
        int k = 0;
        for (int i = 0; i < nt; i++) {
            int i0 = tri[i * 3], i1 = tri[i * 3 + 1], i2 = tri[i * 3 + 2];
            if (hidden[i0] || hidden[i1] || hidden[i2]) continue;
            if (uv != null && m.textured(i0)) which[k / 3] = (byte) (m.leaf(i0) ? 2 : 1);
            keep[k++] = i0;
            keep[k++] = i1;
            keep[k++] = i2;
        }
        raster(scr, col, keep, k / 3, uv, which, texs);
    }

    /** Welt → Bildpunkte: x, y, z (Tiefe), w (≤ 0: hinter der Kamera). */
    private float[] project(float[] p, int n) {
        float[] out = new float[n * 4];
        IntStream.range(0, (n + 4095) / 4096).parallel().forEach(chunk -> {
            int end = Math.min(n, (chunk + 1) * 4096);
            for (int i = chunk * 4096; i < end; i++) {
                float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
                float cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12];
                float cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13];
                float cz = vp[2] * x + vp[6] * y + vp[10] * z + vp[14];
                float cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15];
                out[i * 4 + 3] = cw;
                if (cw <= 0.05f) continue;
                out[i * 4] = (cx / cw * 0.5f + 0.5f) * width;
                out[i * 4 + 1] = (1 - (cy / cw * 0.5f + 0.5f)) * height;
                out[i * 4 + 2] = cz / cw;
            }
        });
        return out;
    }

    /** Dreiecke in waagerechten Streifen parallel zeichnen. */
    private void raster(float[] s, float[] col, int[] tri, int nt, float[] uv, byte[] which, Texture[] texs) {
        int bands = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
        int bh = (height + bands - 1) / bands;
        IntStream.range(0, bands).parallel().forEach(band -> {
            int y0 = band * bh, y1 = Math.min(height, y0 + bh);
            for (int t = 0; t < nt; t++) {
                int a = tri[t * 3], b = tri[t * 3 + 1], c = tri[t * 3 + 2];
                if (s[a * 4 + 3] <= 0.05f || s[b * 4 + 3] <= 0.05f || s[c * 4 + 3] <= 0.05f) continue;
                float ay = s[a * 4 + 1], by = s[b * 4 + 1], cy = s[c * 4 + 1];
                float minY = Math.min(ay, Math.min(by, cy)), maxY = Math.max(ay, Math.max(by, cy));
                if (maxY < y0 || minY >= y1) continue;
                Texture tex = which == null ? null : texs[which[t]];
                fill(s, col, a, b, c, y0, y1, uv, tex);
            }
        });
    }

    private void fill(float[] s, float[] col, int a, int b, int c, int y0, int y1, float[] uv, Texture tex) {
        float ax = s[a * 4], ay = s[a * 4 + 1], az = s[a * 4 + 2];
        float bx = s[b * 4], by = s[b * 4 + 1], bz = s[b * 4 + 2];
        float cx = s[c * 4], cy = s[c * 4 + 1], cz = s[c * 4 + 2];
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-9f) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(y0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(y1 - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        if (minX > maxX || minY > maxY) return;
        float inv = 1 / area;
        float[] texel = tex != null ? new float[4] : null;
        int level = 0;
        float ua = 0, va = 0, ub = 0, vb = 0, uc = 0, vc = 0;
        if (tex != null) {
            ua = uv[a * 2]; va = uv[a * 2 + 1];
            ub = uv[b * 2]; vb = uv[b * 2 + 1];
            uc = uv[c * 2]; vc = uv[c * 2 + 1];
            float texels = Math.abs((ub - ua) * (vc - va) - (uc - ua) * (vb - va)) * tex.w * tex.h;
            level = tex.level(texels / Math.abs(area));
        }
        // sehr kleine Dreiecke: ein Punkt in der Mitte, damit feine Zweige nicht verschwinden
        boolean tiny = maxX - minX <= 1 && maxY - minY <= 1;
        if (tiny) {
            int px = (int) ((ax + bx + cx) / 3), py = (int) ((ay + by + cy) / 3);
            if (px < 0 || px >= width || py < y0 || py >= y1) return;
            float z = (az + bz + cz) / 3;
            int i = py * width + px;
            if (z < depth[i]) {
                float tr = 1, tg = 1, tb = 1;
                if (tex != null) {
                    tex.sample((ua + ub + uc) / 3, (va + vb + vc) / 3, level, texel);
                    if (texel[3] < 0.5f) return;
                    tr = texel[0]; tg = texel[1]; tb = texel[2];
                }
                depth[i] = z;
                fr[i] = (col[a * 3] + col[b * 3] + col[c * 3]) / 3 * tr;
                fg[i] = (col[a * 3 + 1] + col[b * 3 + 1] + col[c * 3 + 1]) / 3 * tg;
                fb[i] = (col[a * 3 + 2] + col[b * 3 + 2] + col[c * 3 + 2]) / 3 * tb;
            }
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * inv;
                float w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * inv;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = w0 * az + w1 * bz + w2 * cz;
                int i = y * width + x;
                if (z >= depth[i]) continue;
                float tr = 1, tg = 1, tb = 1;
                if (tex != null) {
                    tex.sample(w0 * ua + w1 * ub + w2 * uc, w0 * va + w1 * vb + w2 * vc, level, texel);
                    if (texel[3] < 0.5f) continue;
                    tr = texel[0]; tg = texel[1]; tb = texel[2];
                }
                depth[i] = z;
                fr[i] = (w0 * col[a * 3] + w1 * col[b * 3] + w2 * col[c * 3]) * tr;
                fg[i] = (w0 * col[a * 3 + 1] + w1 * col[b * 3 + 1] + w2 * col[c * 3 + 1]) * tg;
                fb[i] = (w0 * col[a * 3 + 2] + w1 * col[b * 3 + 2] + w2 * col[c * 3 + 2]) * tb;
            }
        }
    }

    // ------------------------------------------------------------------ Texturen

    private final java.util.IdentityHashMap<BufferedImage, Texture> textures = new java.util.IdentityHashMap<>();

    private synchronized Texture texture(BufferedImage img) {
        return img == null ? null : textures.computeIfAbsent(img, Texture::new);
    }

    /** Textur mit Mip-Stufen (Farbe nach Alpha gewichtet gemittelt), Abtastung nächster Punkt, wiederholt. */
    static final class Texture {
        static final float[] LINEAR = new float[256];

        static {
            for (int i = 0; i < 256; i++) {
                double c = i / 255.0;
                LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
            }
        }

        final int w, h;
        final int[][] levels;
        final int[] lw, lh;

        Texture(BufferedImage img) {
            w = img.getWidth();
            h = img.getHeight();
            java.util.List<int[]> ls = new java.util.ArrayList<>();
            java.util.List<Integer> ws = new java.util.ArrayList<>(), hs = new java.util.ArrayList<>();
            int[] cur = img.getRGB(0, 0, w, h, null, 0, w);
            if (!img.getColorModel().hasAlpha()) for (int i = 0; i < cur.length; i++) cur[i] |= 0xFF000000;
            int cw = w, ch = h;
            ls.add(cur);
            ws.add(cw);
            hs.add(ch);
            while (cw > 1 || ch > 1) {
                int nw = Math.max(1, cw / 2), nh = Math.max(1, ch / 2);
                int[] nx = new int[nw * nh];
                for (int y = 0; y < nh; y++)
                    for (int x = 0; x < nw; x++) {
                        float r = 0, g = 0, b = 0, al = 0;
                        for (int k = 0; k < 4; k++) {
                            int sx = Math.min(cw - 1, x * 2 + (k & 1)), sy = Math.min(ch - 1, y * 2 + (k >> 1));
                            int p = cur[sy * cw + sx];
                            float pa = (p >>> 24) / 255f;
                            r += ((p >> 16) & 255) * pa;
                            g += ((p >> 8) & 255) * pa;
                            b += (p & 255) * pa;
                            al += pa;
                        }
                        int ia = Math.round(al / 4 * 255);
                        int ir = al > 0 ? Math.round(r / al) : 0, ig = al > 0 ? Math.round(g / al) : 0, ib = al > 0 ? Math.round(b / al) : 0;
                        nx[y * nw + x] = ia << 24 | ir << 16 | ig << 8 | ib;
                    }
                cur = nx;
                cw = nw;
                ch = nh;
                ls.add(cur);
                ws.add(cw);
                hs.add(ch);
            }
            levels = ls.toArray(new int[0][]);
            lw = ws.stream().mapToInt(Integer::intValue).toArray();
            lh = hs.stream().mapToInt(Integer::intValue).toArray();
        }

        /** Mip-Stufe für so viele Texel je Bildpunkt. */
        int level(float texelsPerPixel) {
            if (!(texelsPerPixel > 1)) return 0;
            int l = (int) (0.5 * Math.log(texelsPerPixel) / Math.log(2));
            return Math.min(levels.length - 1, Math.max(0, l));
        }

        /** rgba nach out, Farbe linear, Alpha 0..1. */
        void sample(float u, float v, int level, float[] out) {
            int W = lw[level], H = lh[level];
            int x = Math.floorMod((int) Math.floor(u * W), W), y = Math.floorMod((int) Math.floor(v * H), H);
            int p = levels[level][y * W + x];
            out[0] = LINEAR[(p >> 16) & 255];
            out[1] = LINEAR[(p >> 8) & 255];
            out[2] = LINEAR[p & 255];
            out[3] = (p >>> 24) / 255f;
        }
    }

    /** Linear → Bild (Belichtung, Reinhard, sRGB). */
    public BufferedImage finish() {
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                rgb[i] = (tone(fr[i]) << 16) | (tone(fg[i]) << 8) | tone(fb[i]);
            }
        });
        return image;
    }

    private int tone(float c) {
        c *= exposure;
        c = c / (1 + c * 0.25f);
        c = (float) Math.pow(Math.max(0, c), 1 / 2.2);
        return Math.min(255, (int) (c * 255 + 0.5f));
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
