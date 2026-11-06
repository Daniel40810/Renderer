package com.dan.lichen;

/**
 * Ein Büschel als Bild für eine Karte (seit LituyaBay1958 W6 Runde 7): die Fäden von vorn, senkrecht projiziert, mit
 * Deckung im Alpha. Gerechnet dreifach überabgetastet; vorn liegende Fäden decken hintere, im Inneren des Büschels wird
 * es dunkler (Verdeckung durch die Fäden davor) [G]. Farben linear (Albedo · Verdeckung).
 * <p>
 * Das Büschel sitzt mit dem Ansatz (0, 0, 0) bei u = 0,5 und v = {@link #attachV} (v von oben nach unten).
 */
public final class LichenCard {
    public final int w, h;
    /** RGBA je Bildpunkt (Zeile für Zeile, oben zuerst): rgb linear, a Deckung 0..1. */
    public final float[] rgba;
    /** Breite und Höhe der Karte (m), Lage des Ansatzes (Anteil der Höhe von oben). */
    public float widthM, heightM, attachV;
    /** Mittlere Farbe der gedeckten Bildpunkte (linear) und mittlere Deckung. */
    public final float[] meanColor = new float[3];
    public float meanAlpha;

    private LichenCard(int w, int h) { this.w = w; this.h = h; rgba = new float[4 * w * h]; }

    /** Karte w × h Bildpunkte für das Büschel b; der Rand um das Büschel ist 4 % der Höhe. */
    public static LichenCard bake(BeardLichen b, int w, int h) {
        final int S = 3;
        int W = w * S, H = h * S;
        float halfW = Math.max(Math.abs(b.box[0]), Math.abs(b.box[1]));
        float top = Math.max(b.box[3], 0), bottom = Math.min(b.box[2], 0);
        float hm = (top - bottom) * 1.08f, wm = Math.max(2 * halfW * 1.08f, hm * w / (float) h);
        hm = Math.max(hm, wm * h / (float) w);   // gleicher Maßstab in beide Richtungen
        float y0 = top + 0.04f * hm;
        float px = hm / H;
        float[] col = new float[3 * W * H], zb = new float[W * H], cov = new float[W * H];
        java.util.Arrays.fill(zb, -Float.MAX_VALUE);
        for (BeardLichen.Strand s : b.strands)
            for (int i = 1; i < s.points(); i++) {
                float ax = s.p[3 * i - 3], ay = s.p[3 * i - 2], az = s.p[3 * i - 1];
                float bx = s.p[3 * i], by = s.p[3 * i + 1], bz = s.p[3 * i + 2];
                float ra = s.r[i - 1], rb = s.r[i];
                float[] ca = {s.col[3 * i - 3], s.col[3 * i - 2], s.col[3 * i - 1]};
                segment(W, H, px, wm, y0, ax, ay, az, bx, by, bz, ra, rb, ca, col, zb, cov);
            }
        // Verdeckung im Büschel: je tiefer (z hinten) gegenüber dem Vordersten in der Nachbarschaft, desto dunkler
        LichenCard c = new LichenCard(w, h);
        c.widthM = wm; c.heightM = hm; c.attachV = (y0 - 0) / hm;
        float zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
        for (int i = 0; i < W * H; i++) if (cov[i] > 0) { zmin = Math.min(zmin, zb[i]); zmax = Math.max(zmax, zb[i]); }
        float zr = Math.max(1e-6f, zmax - zmin);
        double sa = 0, sr = 0, sg = 0, sbl = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float a = 0, r = 0, g = 0, bl = 0;
                for (int dy = 0; dy < S; dy++)
                    for (int dx = 0; dx < S; dx++) {
                        int q = (y * S + dy) * W + x * S + dx;
                        if (cov[q] <= 0) continue;
                        float occ = 0.72f + 0.28f * (zb[q] - zmin) / zr;
                        a += cov[q];
                        r += col[3 * q] * occ * cov[q]; g += col[3 * q + 1] * occ * cov[q]; bl += col[3 * q + 2] * occ * cov[q];
                    }
                int o = 4 * (y * w + x);
                float A = a / (S * S);
                c.rgba[o + 3] = Math.min(1, A);
                if (a > 0) { c.rgba[o] = r / a; c.rgba[o + 1] = g / a; c.rgba[o + 2] = bl / a; }
                sa += A; sr += r / (S * S); sg += g / (S * S); sbl += bl / (S * S);
            }
        c.meanAlpha = (float) (sa / (w * h));
        if (sa > 0) { c.meanColor[0] = (float) (sr / sa); c.meanColor[1] = (float) (sg / sa); c.meanColor[2] = (float) (sbl / sa); }
        // Säume: leere Bildpunkte bekommen die Farbe ihrer gedeckten Nachbarn (sonst dunkle Ränder in den Mip-Stufen)
        bleed(c);
        return c;
    }

    private static void segment(int W, int H, float px, float wm, float y0, float ax, float ay, float az, float bx, float by, float bz,
                                float ra, float rb, float[] c, float[] col, float[] zb, float[] cov) {
        // Bild: u = (x + wm/2)/px, v = (y0 − y)/px
        float ux = (ax + wm / 2) / px, uy = (y0 - ay) / px, vx = (bx + wm / 2) / px, vy = (y0 - by) / px;
        float r0 = Math.max(0.55f, ra / px), r1 = Math.max(0.55f, rb / px);
        // Fäden dünner als ein Bildpunkt (beim Überabtasten): die Deckung nach der echten Dicke
        float k0 = Math.min(1, ra / px / 0.55f), k1 = Math.min(1, rb / px / 0.55f);
        int x0 = (int) Math.floor(Math.min(ux, vx) - Math.max(r0, r1) - 1), x1 = (int) Math.ceil(Math.max(ux, vx) + Math.max(r0, r1) + 1);
        int yA = (int) Math.floor(Math.min(uy, vy) - Math.max(r0, r1) - 1), yB = (int) Math.ceil(Math.max(uy, vy) + Math.max(r0, r1) + 1);
        float dx = vx - ux, dy = vy - uy, L2 = Math.max(1e-9f, dx * dx + dy * dy);
        for (int y = Math.max(0, yA); y <= Math.min(H - 1, yB); y++)
            for (int x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) {
                float px0 = x + 0.5f - ux, py0 = y + 0.5f - uy;
                float t = Math.max(0, Math.min(1, (px0 * dx + py0 * dy) / L2));
                float qx = px0 - t * dx, qy = py0 - t * dy;
                float rr = r0 + (r1 - r0) * t, d = (float) Math.sqrt(qx * qx + qy * qy);
                if (d > rr + 0.5f) continue;
                float a = Math.min(1, rr + 0.5f - d) * (k0 + (k1 - k0) * t);
                float z = az + (bz - az) * t;
                int i = y * W + x;
                if (z >= zb[i] || cov[i] < 0.5f) {
                    // vorn: Farbe übernehmen; die Deckung sammelt sich
                    if (z >= zb[i]) { zb[i] = z; col[3 * i] = c[0]; col[3 * i + 1] = c[1]; col[3 * i + 2] = c[2]; }
                }
                cov[i] = 1 - (1 - cov[i]) * (1 - a);
            }
    }

    private static void bleed(LichenCard c) {
        int w = c.w, h = c.h;
        float[] p = c.rgba;
        boolean[] has = new boolean[w * h];
        for (int i = 0; i < w * h; i++) has[i] = p[4 * i + 3] > 0;
        for (int pass = 0; pass < 16; pass++) {
            boolean[] nh = has.clone();
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (has[i]) continue;
                    float r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                            int j = yy * w + xx;
                            if (!has[j]) continue;
                            r += p[4 * j]; g += p[4 * j + 1]; b += p[4 * j + 2]; n++;
                        }
                    if (n > 0) { p[4 * i] = r / n; p[4 * i + 1] = g / n; p[4 * i + 2] = b / n; nh[i] = true; }
                }
            has = nh;
        }
        for (int i = 0; i < w * h; i++) if (!has[i]) { p[4 * i] = c.meanColor[0]; p[4 * i + 1] = c.meanColor[1]; p[4 * i + 2] = c.meanColor[2]; }
    }
}
