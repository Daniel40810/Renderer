package com.dan.ocean;

import java.util.ArrayList;
import java.util.List;

/**
 * Gletschermilch (aus LituyaBay1958, Werkbuch Welle W2 „Spiegel“): Gesteinsmehl aus Gletscherbächen und kalbenden
 * Fronten (Schluff, rund 1–10 µm) bleibt lange im Wasser schweben und streut das Licht grünlich-türkis zurück; das Wasser
 * eines Fjords oder Gletschersees ist darum milchig statt klar, am stärksten bei den Zuflüssen.
 * <p>
 * Hier als Anteil 0..1 je Ort: ein Grundton im ganzen Wasser ({@link #base}) und je Quelle ein Beitrag
 * s · e<sup>−r²/L²</sup> mit Stärke s und Reichweite L, zusammen begrenzt auf 1 [G]. Das Streulicht im Wasser ist dann
 * Farbe · Bestrahlung von oben · k · Anteil · (1 − F), mit dem Fresnel-Anteil F der Spiegelung ({@link #color}, {@link #k})
 * [G]. Die Shader lesen es über {@code gl/milk.glsl} ({@code oceanMilk}, {@code oceanMilkLight}), die Uniforms setzt
 * {@code gl/MilkGl.bind}.
 * <p>
 * In LituyaBay1958: Grundton 0,35, Gilbert Inlet (Reichweite 5,5 km, Stärke 1) und Crillon Inlet (4,5 km, 0,8).
 */
public final class GlacialMilk {
    /** Höchstens so viele Quellen nimmt der Shader. */
    public static final int MAX_SOURCES = 4;

    /** Eine Quelle: Ort x, z (Szene), Reichweite (m), Stärke. */
    public record Source(double x, double z, double reach, double strength) { }

    /** Grundton im ganzen Wasser (0..1). */
    public double base = 0.35;
    /** Farbe des Streulichts (linear, je Bestrahlung) und Faktor k [G]. */
    public double[] color = {0.10, 0.20, 0.18};
    public double k = 0.11;
    public final List<Source> sources = new ArrayList<>();

    /** Eine Quelle dazu (höchstens {@link #MAX_SOURCES}); gibt sich selbst zurück. */
    public GlacialMilk add(double x, double z, double reach, double strength) {
        if (sources.size() >= MAX_SOURCES) throw new IllegalStateException("ocean: höchstens " + MAX_SOURCES + " Quellen der Gletschermilch");
        sources.add(new Source(x, z, reach, strength));
        return this;
    }

    /** Anteil 0..1 am Ort (x, z), wie {@code oceanMilk} im Shader. */
    public double at(double x, double z) {
        double s = base;
        for (Source q : sources) {
            double dx = x - q.x, dz = z - q.z;
            s += q.strength * Math.exp(-(dx * dx + dz * dz) / (q.reach * q.reach));
        }
        return Math.max(0, Math.min(1, s));
    }

    /** Streulicht (RGB) bei der Bestrahlung von oben eDown, Fresnel-Anteil F, am Ort (x, z). */
    public double[] light(double x, double z, double[] eDown, double F) {
        double m = at(x, z);
        return new double[]{color[0] * eDown[0] * k * m * (1 - F), color[1] * eDown[1] * k * m * (1 - F), color[2] * eDown[2] * k * m * (1 - F)};
    }

    /** Klares Wasser ohne Gesteinsmehl. */
    public static GlacialMilk clear() { GlacialMilk g = new GlacialMilk(); g.base = 0; return g; }
}
