package com.dan.surf;

/**
 * Das Netz der Brandung (seit Sylt Phase 6, F9 A): je Reihe längs des Kamms eine Linie über den ganzen Querschnitt,
 * zusammengesetzt aus Ruhewasser vor dem ersten Kamm und je Welle ihrer Form ({@link BreakerShape}) zwischen den
 * Mitten zu den Nachbarn; weil jede Form gleich viele Punkte hat, passen die Reihen Punkt für Punkt zusammen, auch wo
 * die Lippe überschlägt. Unter dem Grund liegt Wasser unsichtbar knapp unter dem Sand.
 * <p>
 * Ecken: Lage (x zur Küste, y nach oben, z längs des Kamms), Normale, Schaum, „dünn“ (Lippe), Wassertiefe unter dem
 * Punkt, Grund – {@link #STRIDE} Zahlen. Gerechnet auf dem Prozessor, je Bild neu (für drei Becken mit je 48 Reihen
 * einige Millisekunden).
 */
public final class BreakerMesh {
    public static final int STRIDE = 10;
    /** Punkte Ruhewasser vor dem ersten Kamm und hinter dem letzten. */
    static final int LEAD = 12;
    public float[] vertices = new float[0];
    public int[] indices = new int[0];
    public int ns, ny, vertexCount, indexCount;
    /** Zeit für den letzten Bau (ms). */
    public double ms;
    /** Ob irgendwo eine Lippe überschlägt. */
    public boolean folds;
    private double[] px = new double[0], pz = new double[0], foam = new double[0], thin = new double[0];

    /** Baut das Netz für den Wellenzug bis xEnd (Ufer und Strand dahinter). */
    public void build(WaveTrain t, double xEnd) { build(t, xEnd, null); }

    /**
     * Wie {@link #build(WaveTrain, double)}, ab dem Rand des Schwalls (seit Sylt Phase 7) aber mit dem Wasser aus
     * {@link Swash}: die Formen der Wellen enden am Rand (Punkte dahinter liegen auf ihm), danach folgt je Zelle des
     * Schwalls ein Punkt mit seiner Oberfläche, Schaum und Tiefe; wo er trocken ist, liegt das Wasser unter dem Sand.
     */
    public void build(WaveTrain t, double xEnd, Swash sw) {
        long t0 = System.nanoTime();
        int nW = t.waves.size();
        final double xb = sw == null ? Double.POSITIVE_INFINITY : sw.boundaryX();
        final int c0 = sw == null ? 0 : sw.cellAt(xb + 1e-6), nSw = sw == null ? 0 : sw.nx - c0;
        ns = LEAD * 2 + nW * BreakerShape.K + nSw;
        ny = t.rows;
        int nv = ns * ny;
        if (vertices.length < nv * STRIDE) vertices = new float[nv * STRIDE];
        if (px.length < nv) { px = new double[nv]; pz = new double[nv]; foam = new double[nv]; thin = new double[nv]; }
        java.util.Arrays.fill(foam, 0, nv, 0);
        java.util.Arrays.fill(thin, 0, nv, 0);
        final double[] px = this.px, pz = this.pz, foam = this.foam, thin = this.thin;
        boolean[] rowFolds = new boolean[ny];
        // Reihen unabhängig: parallel gebaut (gleiches Ergebnis wie nacheinander)
        java.util.stream.IntStream.range(0, ny).parallel().forEach(r -> {
            int o = r * ns;
            double xStart = t.x0;
            // Grenzen der Wellen: Mitten zu den Nachbarn
            double[] backs = new double[nW], fronts = new double[nW];
            for (int i = 0; i < nW; i++) {
                double x = t.waves.get(i).x;
                double prev = i == 0 ? xStart : t.waves.get(i - 1).x, next = i == nW - 1 ? xEnd : t.waves.get(i + 1).x;
                backs[i] = i == 0 ? Math.max(0.5, x - xStart) : Math.max(0.3, (x - prev) / 2);
                fronts[i] = i == nW - 1 ? Math.max(0.5, next - x) : Math.max(0.3, (next - x) / 2);
            }
            // Ruhewasser vor dem ersten Kamm
            double firstBack = nW > 0 ? t.waves.get(0).x - backs[0] : xEnd;
            int k = o;
            for (int i = 0; i < LEAD; i++) {
                double x = xStart + (firstBack - xStart) * i / LEAD;
                px[k] = x;
                pz[k] = t.meanLevel(x);
                k++;
            }
            for (int i = 0; i < nW; i++) {
                WaveTrain.Wave w = t.waves.get(i);
                BreakerShape.Curve c = shape(t, w, r, backs[i], fronts[i]);
                if (c.folds()) rowFolds[r] = true;
                double lev = t.meanLevel(w.x);
                int a = k;
                for (int j = 0; j < BreakerShape.K; j++) {
                    px[k] = w.x + c.x[j];
                    pz[k] = lev + c.z[j];
                    foam[k] = c.foam[j];
                    thin[k] = c.thin[j];
                    k++;
                }
                // Nahtstellen: an den Enden auf das Mittel der Nachbarn ziehen (linear über die Form)
                double ja = i == 0 ? t.meanLevel(px[a]) : 0.5 * (pz[a] + pz[a - 1]);
                double jb = pz[k - 1];
                if (i < nW - 1) {
                    WaveTrain.Wave nwv = t.waves.get(i + 1);
                    double hN = Math.max(0.05, t.depth(nwv.x, r)), HN = t.heightRow(nwv, r);
                    double atN = t.meanLevel(nwv.x) + (BreakerShape.crestShare(HN, hN) - 1) * HN;
                    jb = 0.5 * (pz[k - 1] + atN);
                }
                double da = ja - pz[a], db = jb - pz[k - 1];
                for (int j = 0; j < BreakerShape.K; j++) {
                    double f = (double) j / (BreakerShape.K - 1);
                    pz[a + j] += da * (1 - f) + db * f;
                }
            }
            double lastEnd = nW > 0 ? px[k - 1] : firstBack;
            for (int i = 1; i <= LEAD; i++) {
                double x = lastEnd + (Math.max(lastEnd + 0.1, xEnd) - lastEnd) * i / LEAD;
                px[k] = x;
                pz[k] = t.meanLevel(x);
                k++;
            }
            // Schaumteppich dazu
            for (int i = o; i < k; i++) foam[i] = Math.max(foam[i], t.foam.at(px[i], r));
            if (sw != null) {
                // Übergabe: was hinter dem Rand liegt, auf den Rand legen; über 1,5 m vor ihm zum Schwall überblenden
                double eb = sw.surface(r, xb + 0.5 * sw.cell);
                for (int i = o; i < k; i++) {
                    if (px[i] >= xb) { px[i] = xb; if (!Double.isNaN(eb)) pz[i] = eb; thin[i] = 0; continue; }
                    double w = (px[i] - (xb - 1.5)) / 1.5;
                    if (w > 0 && !Double.isNaN(eb)) pz[i] = pz[i] * (1 - w) + eb * w;
                }
                for (int j = 0; j < nSw; j++) {
                    int c = c0 + j;
                    double x = sw.x(c), e = sw.surface(r, x);
                    px[k] = x;
                    pz[k] = Double.isNaN(e) ? sw.bed[c] - 0.03 : e;
                    foam[k] = Math.max(sw.foam[r * sw.nx + c], 0.0);
                    thin[k] = 0;
                    k++;
                }
            }
        });
        folds = false;
        for (boolean f : rowFolds) folds |= f;
        // Ecken: Lage, Normale (aus den Nachbarn), Schaum, dünn, Tiefe, Grund
        java.util.stream.IntStream.range(0, ny).parallel().forEach(r -> {
            double z = t.rowY(r);
            for (int i = 0; i < ns; i++) {
                int q = r * ns + i;
                double bed = t.bed(px[q], r);
                double y = pz[q];
                double depth = y - bed;
                if (depth < 0.01) y = bed - 0.03;     // trocken: das Wasser liegt unsichtbar unter dem Sand
                int v = q * STRIDE;
                vertices[v] = (float) px[q];
                vertices[v + 1] = (float) y;
                vertices[v + 2] = (float) z;
                vertices[v + 6] = (float) Math.min(1, foam[q]);
                vertices[v + 7] = (float) thin[q];
                vertices[v + 8] = (float) Math.max(0, depth);
                vertices[v + 9] = (float) bed;
            }
        });
        java.util.stream.IntStream.range(0, ny).parallel().forEach(r -> {
            for (int i = 0; i < ns; i++) {
                int q = r * ns + i;
                int i0 = Math.max(0, i - 1), i1 = Math.min(ns - 1, i + 1), r0 = Math.max(0, r - 1), r1 = Math.min(ny - 1, r + 1);
                int a = (r * ns + i0) * STRIDE, b = (r * ns + i1) * STRIDE, c = (r0 * ns + i) * STRIDE, d = (r1 * ns + i) * STRIDE;
                double sx = vertices[b] - vertices[a], sy = vertices[b + 1] - vertices[a + 1], sz = vertices[b + 2] - vertices[a + 2];
                double tx = vertices[d] - vertices[c], ty = vertices[d + 1] - vertices[c + 1], tz = vertices[d + 2] - vertices[c + 2];
                // Normale = quer (längs des Kamms) × längs der Linie: auf glattem Wasser nach oben
                double nx = ty * sz - tz * sy, nyy = tz * sx - tx * sz, nz = tx * sy - ty * sx;
                double l = Math.sqrt(nx * nx + nyy * nyy + nz * nz);
                if (l < 1e-12) { nx = 0; nyy = 1; nz = 0; l = 1; }
                int v = q * STRIDE;
                vertices[v + 3] = (float) (nx / l);
                vertices[v + 4] = (float) (nyy / l);
                vertices[v + 5] = (float) (nz / l);
            }
        });
        vertexCount = nv;
        int ni = (ns - 1) * (ny - 1) * 6;
        if (indices.length < ni) indices = new int[ni];
        int n = 0;
        for (int r = 0; r < ny - 1; r++)
            for (int i = 0; i < ns - 1; i++) {
                int a = r * ns + i, b = a + 1, c = a + ns, d = c + 1;
                indices[n++] = a; indices[n++] = c; indices[n++] = b;
                indices[n++] = b; indices[n++] = c; indices[n++] = d;
            }
        indexCount = ni;
        ms = (System.nanoTime() - t0) / 1e6;
    }

    /**
     * Der Sand unter dem Becken als Netz (gleiches Format, Schaum 0): nx Punkte längs von x0 bis xEnd, ny Reihen; in
     * der Tiefe steht die Höhe über dem Ruhewasser (für den nassen Strand nach {@code wetLine}, ohne Schwall).
     */
    public static BreakerMesh bed(WaveTrain t, double xEnd, int nx, int ny) {
        BreakerMesh m = new BreakerMesh();
        m.ns = nx;
        m.ny = ny;
        m.vertexCount = nx * ny;
        m.vertices = new float[nx * ny * STRIDE];
        for (int r = 0; r < ny; r++)
            for (int i = 0; i < nx; i++) {
                double x = t.x0 + (xEnd - t.x0) * i / (nx - 1), z = t.width * r / (ny - 1);
                double y = t.profile.bed(x), e = (xEnd - t.x0) / (nx - 1);
                double gx = (t.profile.bed(x + e) - t.profile.bed(x - e)) / (2 * e), l = Math.sqrt(1 + gx * gx);
                int v = (r * nx + i) * STRIDE;
                m.vertices[v] = (float) x; m.vertices[v + 1] = (float) y; m.vertices[v + 2] = (float) z;
                m.vertices[v + 3] = (float) (-gx / l); m.vertices[v + 4] = (float) (1 / l); m.vertices[v + 5] = 0;
                m.vertices[v + 8] = (float) (y - t.level); m.vertices[v + 9] = (float) y;
            }
        int ni = (nx - 1) * (ny - 1) * 6, n = 0;
        m.indices = new int[ni];
        for (int r = 0; r < ny - 1; r++)
            for (int i = 0; i < nx - 1; i++) {
                int a = r * nx + i, b = a + 1, c = a + nx, d = c + 1;
                m.indices[n++] = a; m.indices[n++] = c; m.indices[n++] = b;
                m.indices[n++] = b; m.indices[n++] = c; m.indices[n++] = d;
            }
        m.indexCount = ni;
        return m;
    }

    /** Erste Spalte des Sandes, die zum Schwall gehört (−1: Sand ohne Schwall). */
    public int swashColumn = -1;

    /**
     * Der Sand mit Nässe (seit Sylt Phase 7): je Reihe des Wellenzugs eine Linie, seewärts grob (nCoarse Punkte bis zum
     * Anfang des Schwalls), danach je Zelle des Schwalls ein Punkt. Merkmale je Ecke: Nässe, Wasserfilm, Blasen,
     * Spülsaum ({@link #updateBed}).
     */
    public static BreakerMesh bed(WaveTrain t, Swash sw, int nCoarse) {
        BreakerMesh m = new BreakerMesh();
        int nx = nCoarse + sw.nx, ny = t.rows;
        m.ns = nx;
        m.ny = ny;
        m.vertexCount = nx * ny;
        m.vertices = new float[nx * ny * STRIDE];
        m.swashColumn = nCoarse;
        double[] xs = new double[nx], ys = new double[nx];
        for (int i = 0; i < nCoarse; i++) xs[i] = t.x0 + (sw.xa - t.x0) * i / nCoarse;
        for (int i = 0; i < sw.nx; i++) xs[nCoarse + i] = sw.x(i);
        for (int i = 0; i < nx; i++) ys[i] = i >= nCoarse ? sw.bed[i - nCoarse] : t.profile.bed(xs[i]);
        for (int r = 0; r < ny; r++)
            for (int i = 0; i < nx; i++) {
                double x = xs[i], y = ys[i], z = t.rowY(r);
                double xl = xs[Math.max(0, i - 1)], xr = xs[Math.min(nx - 1, i + 1)];
                double gx = (ys[Math.min(nx - 1, i + 1)] - ys[Math.max(0, i - 1)]) / Math.max(1e-6, xr - xl), l = Math.sqrt(1 + gx * gx);
                int v = (r * nx + i) * STRIDE;
                m.vertices[v] = (float) x; m.vertices[v + 1] = (float) y; m.vertices[v + 2] = (float) z;
                m.vertices[v + 3] = (float) (-gx / l); m.vertices[v + 4] = (float) (1 / l); m.vertices[v + 5] = 0;
                m.vertices[v + 6] = y < t.level ? 1 : 0;
                m.vertices[v + 9] = 0;
            }
        int ni = (nx - 1) * (ny - 1) * 6, n = 0;
        m.indices = new int[ni];
        for (int r = 0; r < ny - 1; r++)
            for (int i = 0; i < nx - 1; i++) {
                int a = r * nx + i, b = a + 1, c = a + nx, d = c + 1;
                m.indices[n++] = a; m.indices[n++] = c; m.indices[n++] = b;
                m.indices[n++] = b; m.indices[n++] = c; m.indices[n++] = d;
            }
        m.indexCount = ni;
        m.updateBed(sw);
        return m;
    }

    /** Nässe, Film, Blasen und Spülsaum aus dem Schwall in den Sand schreiben (je Bild oder seltener). */
    public void updateBed(Swash sw) {
        if (swashColumn < 0) return;
        for (int r = 0; r < ny; r++)
            for (int i = 0; i < sw.nx; i++) {
                int k = r * sw.nx + i, v = (r * ns + swashColumn + i) * STRIDE;
                boolean under = sw.d[k] > 0.002;
                vertices[v + 6] = under ? 1 : sw.moist[k];
                vertices[v + 7] = under ? 1 : sw.film[k];
                vertices[v + 8] = sw.bubbles[k];
                vertices[v + 9] = sw.wrack[k];
            }
    }

    /** Die Form der Welle w in Reihe r. */
    static BreakerShape.Curve shape(WaveTrain t, WaveTrain.Wave w, int r, double back, double front) {
        double h = Math.max(0.05, t.depth(w.x, r));
        double p = t.progress(w, r);
        double H = t.heightRow(w, r);
        if (Double.isNaN(p)) {
            double near = Math.max(0, Math.min(1, H / (t.gamma * h)));
            return BreakerShape.unbroken(H, h, back, front, near * near);
        }
        double hb = w.hb[r];
        BreakerShape.Curve start = BreakerShape.unbroken(hb, h, back, front, 1);
        switch (w.type) {
            case PLUNGING -> {
                if (p < 0.12) return BreakerShape.blend(start, BreakerShape.plunging(hb, h, back, front, p), p / 0.12);
                if (p < 1) return BreakerShape.plunging(hb, h, back, front, p);
                if (p < 1.6) return BreakerShape.blend(BreakerShape.plunging(hb, h, back, front, 1), BreakerShape.bore(H, h, back, front), (p - 1) / 0.6);
                return BreakerShape.bore(H, h, back, front);
            }
            case SPILLING -> {
                if (p < 0.1) return BreakerShape.blend(start, BreakerShape.spilling(hb, h, back, front, p), p / 0.1);
                if (p < 1) return BreakerShape.spilling(hb, h, back, front, p);
                if (p < 2) return BreakerShape.blend(BreakerShape.spilling(hb, h, back, front, 1), BreakerShape.bore(H, h, back, front), p - 1);
                return BreakerShape.bore(H, h, back, front);
            }
            default -> {
                if (p < 0.12) return BreakerShape.blend(start, BreakerShape.collapsing(hb, h, back, front, p), p / 0.12);
                if (p < 1) return BreakerShape.collapsing(hb, h, back, front, p);
                if (p < 1.5) return BreakerShape.blend(BreakerShape.collapsing(hb, h, back, front, 1), BreakerShape.bore(H, h, back, front), (p - 1) / 0.5);
                return BreakerShape.bore(H, h, back, front);
            }
        }
    }
}
