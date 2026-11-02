package com.dan.forest;

/**
 * Richtungen der Oktaeder-Impostors (seit LituyaBay1958 Phase 6): die obere Halbkugel als Raute auf einem Quadrat
 * (Halb-Oktaeder), N × N Ansichten. Ansicht (fx, fy) schaut aus der Richtung {@link #frameDir} auf den Baum; ihr Bild
 * hat die Achsen {@link #frameBasis}. Gleich in {@code gl/forestgpu_imp.vert} und beim Backen in {@code gl.ForestGpu}.
 */
public final class Octahedral {
    private Octahedral() { }

    /** Richtung (y ≥ 0, normiert) → Punkt im Quadrat −1..1. */
    public static float[] encode(float x, float y, float z) {
        float l = Math.abs(x) + Math.abs(Math.max(y, 0)) + Math.abs(z);
        float px = x / l, pz = z / l;
        return new float[]{px + pz, px - pz};
    }

    /** Punkt im Quadrat −1..1 → Richtung (normiert, y ≥ 0). */
    public static float[] decode(float u, float v) {
        float px = (u + v) * 0.5f, pz = (u - v) * 0.5f;
        float y = 1 - Math.abs(px) - Math.abs(pz);
        float l = (float) Math.sqrt(px * px + y * y + pz * pz);
        return new float[]{px / l, y / l, pz / l};
    }

    /** Richtung der Ansicht (fx, fy) von n × n (zum Betrachter hin). */
    public static float[] frameDir(int fx, int fy, int n) { return decode((fx + 0.5f) / n * 2 - 1, (fy + 0.5f) / n * 2 - 1); }

    /** Achsen des Bildes einer Ansicht aus Richtung d: rechts, oben (je 3 Werte). Senkrecht von oben: rechts = +x, oben = −z. */
    public static float[][] frameBasis(float[] d) {
        float rx = d[2], rz = -d[0];   // cross((0,1,0), d) = (d.z, 0, −d.x)
        float l = (float) Math.sqrt(rx * rx + rz * rz);
        if (l < 1e-4f) { rx = 1; rz = 0; l = 1; }
        rx /= l; rz /= l;
        // oben = cross(d, rechts)
        float ux = d[1] * rz, uy = d[2] * rx - d[0] * rz, uz = -d[1] * rx;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        return new float[][]{{rx, 0, rz}, {ux / ul, uy / ul, uz / ul}};
    }
}
