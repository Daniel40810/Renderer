package com.dan.aurora.demo;

/**
 * Ein paar 4 × 4-Matrizen für die GL-Demos, spaltenweise wie OpenGL: Projektion, Ansicht aus Azimut und Höhe im
 * Koordinatensystem von com.dan.sky (x Ost, y oben, z Süd), Produkt und Inverse.
 */
final class Matrizen {
    private Matrizen() { }

    /** Perspektive (Tiefe −1..1). */
    static float[] perspektive(double fovyGrad, double seitenverhaeltnis, double nah, double fern) {
        double f = 1 / Math.tan(Math.toRadians(fovyGrad) / 2);
        float[] m = new float[16];
        m[0] = (float) (f / seitenverhaeltnis);
        m[5] = (float) f;
        m[10] = (float) ((fern + nah) / (nah - fern));
        m[11] = -1;
        m[14] = (float) (2 * fern * nah / (nah - fern));
        return m;
    }

    /** Ansicht vom Auge aus mit Azimut (Grad, 0 Nord, 90 Ost) und Höhe (Grad). */
    static float[] ansicht(double[] auge, double azimutGrad, double hoeheGrad) {
        double az = Math.toRadians(azimutGrad), el = Math.toRadians(hoeheGrad);
        double[] f = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        double[] r = normiert(kreuz(f, new double[]{0, 1, 0}));
        double[] u = kreuz(r, f);
        float[] m = new float[16];
        m[0] = (float) r[0]; m[4] = (float) r[1]; m[8] = (float) r[2];
        m[1] = (float) u[0]; m[5] = (float) u[1]; m[9] = (float) u[2];
        m[2] = (float) -f[0]; m[6] = (float) -f[1]; m[10] = (float) -f[2];
        m[12] = (float) -(r[0] * auge[0] + r[1] * auge[1] + r[2] * auge[2]);
        m[13] = (float) -(u[0] * auge[0] + u[1] * auge[1] + u[2] * auge[2]);
        m[14] = (float) (f[0] * auge[0] + f[1] * auge[1] + f[2] * auge[2]);
        m[15] = 1;
        return m;
    }

    static double[] kreuz(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] normiert(double[] a) {
        double n = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[]{a[0] / n, a[1] / n, a[2] / n};
    }

    static float[] mal(float[] a, float[] b) {
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                m[c * 4 + r] = (float) s;
            }
        return m;
    }

    /** Inverse über Kofaktoren. */
    static float[] invers(float[] m) {
        double[] inv = new double[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        double det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        float[] o = new float[16];
        for (int i = 0; i < 16; i++) o[i] = (float) (inv[i] / det);
        return o;
    }

    /** Inverse von Projektion mal Ansicht für einen Blick. */
    static float[] invViewProj(double fovyGrad, double seitenverhaeltnis, double[] auge, double azimutGrad, double hoeheGrad) {
        return invers(mal(perspektive(fovyGrad, seitenverhaeltnis, 1, 100_000), ansicht(auge, azimutGrad, hoeheGrad)));
    }
}
