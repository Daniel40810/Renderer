package com.dan.tree;

import java.util.stream.IntStream;

/**
 * Wind für Asset-Bäume. Ein Modell für kleine Winkel, keine Physik: Der Stamm neigt sich mit h²,
 * jeder Hauptast schwingt mit eigener Phase, Zweige wippen, Blätter zittern entlang ihrer Normale.
 * Die Stärke wächst mit U² (U = 20 m/s ergibt Biegung 1). Böen laufen als Welle mit dem Wind über das
 * Land. {@link #displace} rechnet auf dem Prozessor genau das, was {@code gl/tree.vert} rechnet.
 */
public final class TreeWind {

    /** mittlere Geschwindigkeit in m/s */
    public float speed = 4;
    /** Richtung, aus der der Wind kommt, in Grad (270 = West) */
    public float fromDeg = 250;
    /** Böigkeit 0..1 */
    public float gustiness = 0.5f;
    /** Wind von außen; gesetzt, nimmt jeder Baum Richtung und Stärke 10 m über seinem Fuß daraus */
    public Wind external;

    /** Wohin der Wind weht: {x Ost, z Süd}, Länge 1. */
    public float[] toward() {
        double a = Math.toRadians(fromDeg);
        return new float[]{(float) -Math.sin(a), (float) Math.cos(a)};
    }

    /** Wind am Baum (Fuß x, y, z): {nach x, nach z, Geschwindigkeit}. */
    public float[] at(double x, double y, double z) {
        if (external != null) {
            double[] w = external.at(x, y + 10, z);
            double s = Math.hypot(w[0], w[2]);
            if (s < 1e-6) return new float[]{1, 0, 0};
            return new float[]{(float) (w[0] / s), (float) (w[2] / s), (float) s};
        }
        float[] d = toward();
        return new float[]{d[0], d[1], speed};
    }

    /** Biegung aus der Geschwindigkeit: (U/20)², höchstens 2. */
    public static float bend(float speed) {
        float b = speed / 20f;
        return Math.min(b * b, 2f);
    }

    /** Böe am Ort: 1 im Mittel, läuft mit dem Wind (s = Weg in Windrichtung). */
    public static float gust(float t, float s, float gustiness, float attenuation) {
        float wave = (float) (0.6 * Math.sin(0.7 * t - 0.08 * s) + 0.4 * Math.sin(1.9 * t - 0.21 * s + 1.7));
        return Math.max(0, 1 + gustiness * (1 - attenuation) * wave);
    }

    /**
     * Verschobene Punkte eines Baums in seinem Baumraum nach out (3 je Punkt).
     *
     * @param worldX,worldZ Fuß des Baums (für die Böe)
     * @param yaw           Drehung des Baums um y (Bogenmaß): Welt x = x·cos − z·sin, z = x·sin + z·cos
     */
    public void displace(TreeMesh m, TreeSpecies sp, float gustAttenuation, float t,
                         float worldX, float worldY, float worldZ, float yaw, float[] out) {
        float[] w = at(worldX, worldY, worldZ);
        // Wind in den Baumraum drehen
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float wx = w[0] * c + w[1] * s, wz = -w[0] * s + w[1] * c;
        float g = gust(t, worldX * w[0] + worldZ * w[1], gustiness, gustAttenuation);
        Params p = new Params(bend(w[2]), g, wx, wz, Math.max(m.height, 1), t,
                sp == null ? 1 : sp.flexTrunk, sp == null ? 1 : sp.flexBranch, sp == null ? 1 : sp.flutter,
                hashPhase(worldX, worldZ));
        float[] v = m.v;
        IntStream.range(0, (m.vertices + 4095) / 4096).parallel().forEach(chunk -> {
            int end = Math.min(m.vertices, (chunk + 1) * 4096);
            for (int i = chunk * 4096; i < end; i++) apply(v, i * TreeMesh.STRIDE, p, out, i * 3);
        });
    }

    /** Phase je Baum aus seinem Ort. */
    public static float hashPhase(float x, float z) {
        double h = Math.sin(x * 12.9898 + z * 78.233) * 43758.5453;
        return (float) (h - Math.floor(h));
    }

    /** Werte für einen Baum und ein Bild. */
    public record Params(float bend, float gust, float wx, float wz, float height, float t,
                         float flexTrunk, float flexBranch, float flutter, float treePhase) {}

    /** Die Formel, Punkt für Punkt; gleich im Shader. */
    public static void apply(float[] v, int b, Params p, float[] out, int o) {
        float x = v[b], y = v[b + 1], z = v[b + 2];
        float B = p.bend * p.gust;
        float sB = (float) Math.sqrt(p.bend) * p.gust;
        float t = p.t;
        float dx = 0, dy = 0, dz = 0;

        // Stamm: Neigung mit h², leichtes Pendeln
        float h = Math.max(0, y) / p.height;
        float sway = 1 + 0.25f * (float) Math.sin(6.2832 * 0.22 * t + 6.2832 * p.treePhase);
        float trunk = p.flexTrunk * 0.035f * p.height * B * sway * h * h;
        dx += p.wx * trunk;
        dz += p.wz * trunk;
        dy -= 0.5f * trunk * trunk / Math.max(y, 0.5f) * h;

        // Hauptast: mit dem Wind und quer dazu, eigene Phase
        float limb = v[b + TreeMesh.LIMB] * p.flexBranch;
        if (limb > 0) {
            float ph = 6.2832f * v[b + TreeMesh.PHASE_LIMB];
            float along = 0.10f * B * (0.75f + 0.25f * (float) Math.sin(1.3 * t + ph));
            float across = 0.04f * sB * (float) Math.sin(1.1 * t + ph * 1.7);
            float up = 0.03f * sB * (float) Math.sin(1.7 * t + ph + 1);
            dx += limb * (p.wx * along - p.wz * across);
            dz += limb * (p.wz * along + p.wx * across);
            dy += limb * up;
        }

        // Zweig: wippt in eine eigene Richtung
        float twig = v[b + TreeMesh.TWIG] * p.flexBranch;
        if (twig > 0) {
            float ph = 6.2832f * v[b + TreeMesh.PHASE_TWIG];
            float osc = 0.12f * sB * (float) Math.sin(2.7 * t + ph);
            dx += twig * ((float) Math.cos(ph) * osc + p.wx * 0.08f * B);
            dy += twig * 0.5f * (float) Math.sin(ph * 1.3f) * osc;
            dz += twig * ((float) Math.sin(ph) * osc + p.wz * 0.08f * B);
        }

        // Blatt: zittert entlang der Normale
        float fl = v[b + TreeMesh.FLUTTER] * p.flutter;
        if (fl > 0) {
            float ph = 6.2832f * 7 * v[b + TreeMesh.PHASE_TWIG] + (x + z) * 5;
            float a = fl * 0.025f * (0.3f * sB + 0.7f * B) * (float) Math.sin(11 * t + ph);
            dx += v[b + 3] * a;
            dy += v[b + 4] * a;
            dz += v[b + 5] * a;
        }
        out[o] = x + dx;
        out[o + 1] = y + dy;
        out[o + 2] = z + dz;
    }
}
