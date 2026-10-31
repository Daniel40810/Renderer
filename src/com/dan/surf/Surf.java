package com.dan.surf;

/**
 * Die Brandung (Sylt, Werkbuch Abschnitt 5): Seegang über dem Grund, Brechen, Brechertyp, später Strömung, Brecher im
 * Bild und Schwall. <b>Stand: Sylt Phase 5, das Wellenfeld ({@link WaveField}, {@link Bars}).</b> Ohne Feld wie die Hülle aus Phase 1: je Ort ohne Nachbarn: lineare Dispersion,
 * Shoaling über die Gruppengeschwindigkeit (ohne Refraktion), Brechen nach McCowan (H ≤ γ·h) und den Brechertyp aus
 * der Iribarren-Zahl nach Battjes (1974). Das Wellenfeld auf dem Gitter mit Refraktion und Battjes-Janssen kommt in
 * Sylt Phase 5 (F7 A), die Brecher in Phase 6, Auflauf und Rücklauf in Phase 7.
 * <p>
 * Der Kern kennt nur Java 21. Grund, Pegel, Wind und die See draußen kommen über die eigenen kleinen Schnittstellen
 * {@link Bed}, {@link Level}, {@link Wind} und {@link Offshore} (Paketvertrag Punkt 4); die App reicht dort
 * {@code ocean}, {@code tide} und {@code wind} hindurch.
 * <p>
 * Raum wie in allen Szenen: x nach Osten, z nach Süden, y nach oben, Meter. Höhen und Pegel in derselben Bezugshöhe
 * (auf Sylt NHN).
 */
public final class Surf {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7). */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Brechertyp nach Battjes (1974). NONE: die Welle bricht hier (noch) nicht. */
    public enum Breaker { NONE, SPILLING, PLUNGING, COLLAPSING, SURGING }

    /** Höhe des Grunds über der Bezugshöhe (unter Wasser negativ), in m. */
    @FunctionalInterface
    public interface Bed { double height(double x, double z); }

    /** Wasserstand über der Bezugshöhe zur Szenenzeit t (Tide + Windstau), in m. */
    @FunctionalInterface
    public interface Level { double level(double t); }

    /** Wind 10 m über Grund: Stärke in m/s und Richtung, aus der er kommt (Grad von Nord). */
    public interface Wind { double speed(double x, double z); double fromDeg(double x, double z); }

    /** Die See draußen über tiefem Wasser: signifikante Höhe H_s (m), Spitzenperiode T_p (s), Richtung, aus der sie kommt. */
    public interface Offshore { double hs(); double tp(); double fromDeg(); }

    /** Ereignis nach außen: an diesem Ort beginnt eine Welle zu brechen (für Gischt und Klang). */
    @FunctionalInterface
    public interface Breaking { void breaking(double x, double z, double height, Breaker type); }

    public static final double G = 9.81;

    /** Startwert des Zufalls (Punkt 6); die Hülle braucht ihn erst mit der Brandungsschwebung (Phase 6). */
    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Die See draußen als offene Werte (Punkt 5), wenn kein {@link #offshore} gesetzt ist. */
    public double hs = 1.0, tp = 8.0, seaFromDeg = 285;
    /** Brechindex γ = H/h nach McCowan (1894) für die einzelne Welle; 0,78 [belegt]. */
    public double gamma = 0.78;
    /** Länge, über die der Hang für die Iribarren-Zahl gemittelt wird, in m [G]. */
    public double slopeBase = 20;

    public Bed bed = (x, z) -> -5;
    public Level level = t -> 0;
    public Wind wind;
    public Offshore offshore;
    public Breaking onBreaking;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    public static Surf calm() { return beaufort(1); }
    public static Surf breeze() { return beaufort(4); }
    public static Surf gale() { return beaufort(8); }
    public static Surf storm() { return beaufort(10); }

    /**
     * Preset nach Beaufort 0..12: voll ausgereifte Windsee nach Pierson-Moskowitz aus dem Wind in 19,5 m
     * (H_s = 0,21·U²/g, T_p = 7,14·U/g), höchstens 9 m und 14 s [G für die Nordsee mit begrenztem Fetch];
     * darunter eine Grunddünung von 0,3 m und 7 s [G].
     */
    public static Surf beaufort(int bft) {
        Surf s = new Surf();
        bft = Math.max(0, Math.min(12, bft));
        double u = BFT_MID[bft] * 1.07;                      // 10 m → 19,5 m, logarithmisches Profil [G]
        s.hs = Math.max(0.3, Math.min(9, 0.21 * u * u / G));
        s.tp = Math.max(7, Math.min(14, 7.14 * u / G));
        return s;
    }

    /** Sylter Weststrand: Dünung aus West-Nordwest, 1 m, 8 s [G bis zur Seegangsmessung Westerland]. */
    public static Surf sylt() { Surf s = new Surf(); s.hs = 1.0; s.tp = 8; s.seaFromDeg = 285; return s; }

    /** Ostseestrand (für Thunder): kurze Windsee ohne Dünung, 0,6 m, 4 s, aus Nordost [G]. */
    public static Surf ostsee() { Surf s = new Surf(); s.hs = 0.6; s.tp = 4; s.seaFromDeg = 45; return s; }

    /** Sockel eines Turms im Meer (für Lighthouse): Nordsee draußen, 2 m, 9 s [G]. */
    public static Surf sockel() { Surf s = new Surf(); s.hs = 2.0; s.tp = 9; s.seaFromDeg = 300; return s; }

    /** Mitte jeder Beaufort-Stufe in m/s (WMO), eigene Kopie. */
    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    // ------------------------------------------------------------------ Lineare Wellen

    public double hs() { return offshore != null ? offshore.hs() : hs; }
    public double tp() { return offshore != null ? offshore.tp() : tp; }
    /** Richtung, aus der die See draußen kommt (Grad von Nord). */
    public double fromDeg() { return offshore != null ? offshore.fromDeg() : seaFromDeg; }

    /**
     * Seit Sylt Phase 5: das Wellenfeld über dem Grund ({@link WaveField}); ist es gesetzt und gerechnet, antworten
     * {@link #heightAt} und {@link #typeAt} innerhalb des Felds aus ihm (mit Refraktion und Dissipation), außerhalb
     * wie die Hülle.
     */
    public WaveField field;

    /** Wellenzahl k (1/m) aus der Dispersion ω² = g·k·tanh(k·h), Newton-Verfahren; h ≤ 0 gibt NaN. */
    public static double wavenumber(double period, double depth) {
        if (!(depth > 0) || !(period > 0)) return Double.NaN;
        double w = 2 * Math.PI / period, w2 = w * w;
        double k0 = w2 / G, k = k0 / Math.sqrt(Math.tanh(k0 * depth));   // gute Startnäherung (Eckart)
        for (int i = 0; i < 30; i++) {
            double th = Math.tanh(k * depth), f = G * k * th - w2;
            double df = G * th + G * k * depth * (1 - th * th);
            double dk = f / df;
            k -= dk;
            if (Math.abs(dk) < 1e-14 * k) break;
        }
        return k;
    }

    /** Wellenlänge über tiefem Wasser L₀ = g·T²/2π. */
    public static double deepLength(double period) { return G * period * period / (2 * Math.PI); }

    /** Gruppengeschwindigkeit c_g = n·c mit n = ½(1 + 2kh / sinh 2kh). */
    public static double groupVelocity(double period, double depth) {
        double k = wavenumber(period, depth), w = 2 * Math.PI / period;
        if (Double.isNaN(k)) return Double.NaN;
        double kh2 = 2 * k * depth;
        double n = kh2 > 600 ? 0.5 : 0.5 * (1 + kh2 / Math.sinh(kh2));
        return n * w / k;
    }

    /** Shoaling-Koeffizient K_s = √(c_g0 / c_g), c_g0 = g·T / 4π (Erhaltung des Energieflusses ohne Refraktion). */
    public static double shoaling(double period, double depth) {
        return Math.sqrt(G * period / (4 * Math.PI) / groupVelocity(period, depth));
    }

    /** Iribarren-Zahl ξ = tan β / √(H/L₀). */
    public static double iribarren(double slope, double height, double period) {
        return slope / Math.sqrt(height / deepLength(period));
    }

    /** Brechertyp aus ξ₀ (mit der Höhe draußen) nach Battjes (1974): Schwall < 0,5 < Sturz < 3,3 < Reflexion/Brandung. */
    public static Breaker typeOffshore(double xi0) {
        return xi0 < 0.5 ? Breaker.SPILLING : xi0 < 3.3 ? Breaker.PLUNGING : xi0 < 5 ? Breaker.COLLAPSING : Breaker.SURGING;
    }

    /** Brechertyp aus ξ_b (mit der Brechhöhe) nach Battjes (1974): Schwall < 0,4 < Sturz < 2,0 < Reflexion/Brandung. */
    public static Breaker typeAtBreaking(double xiB) {
        return xiB < 0.4 ? Breaker.SPILLING : xiB < 2.0 ? Breaker.PLUNGING : xiB < 3.0 ? Breaker.COLLAPSING : Breaker.SURGING;
    }

    // ------------------------------------------------------------------ Abfragen am Ort (Hülle, ohne Nachbarn)

    /** Wassertiefe am Ort zur Zeit t in m (≤ 0: trocken). */
    public double depthAt(double x, double z, double t) { return level.level(t) - bed.height(x, z); }

    /**
     * Signifikante Wellenhöhe am Ort: H_s draußen mal Shoaling, begrenzt durch γ·h. Die Hülle kennt keine
     * Refraktion und keine Dissipation vor dem Ort; das ist die Obergrenze, die Phase 5 mit dem Wellenfeld ersetzt.
     */
    public double heightAt(double x, double z, double t) {
        WaveField f = field;
        if (f != null && f.result() != null && f.gridOf(x, z) != null) return f.hsAt(x, z);
        double h = depthAt(x, z, t);
        if (h <= 0) return 0;
        return Math.min(hs() * shoaling(tp(), h), gamma * h);
    }

    /** Ob die Welle hier bricht: Höhe nach Shoaling erreicht γ·h. */
    public boolean breakingAt(double x, double z, double t) {
        double h = depthAt(x, z, t);
        return h > 0 && hs() * shoaling(tp(), h) >= gamma * h;
    }

    /** Hang in Laufrichtung der See (Anstieg zum Ufer, ≥ 0), über {@link #slopeBase} gemittelt. */
    public double slopeAt(double x, double z) {
        double a = Math.toRadians(seaFromDeg), dx = -Math.sin(a), dz = Math.cos(a);   // Laufrichtung (zur Küste)
        double half = slopeBase / 2;
        double up = bed.height(x + dx * half, z + dz * half), down = bed.height(x - dx * half, z - dz * half);
        return Math.max(0, (up - down) / slopeBase);
    }

    /** Brechertyp am Ort: NONE, solange die Welle nicht bricht, sonst aus ξ_b mit dem Hang am Ort. */
    public Breaker typeAt(double x, double z, double t) {
        WaveField f = field;
        if (f != null && f.result() != null && f.gridOf(x, z) != null) return f.typeAt(x, z);
        if (!breakingAt(x, z, t)) return Breaker.NONE;
        double hb = Math.max(1e-3, gamma * depthAt(x, z, t)), s = slopeAt(x, z);
        return typeAtBreaking(iribarren(s, hb, tp()));
    }

    /**
     * Sucht längs einer Linie vom Meer zum Ufer die erste Stelle, an der die Welle bricht, und meldet sie über
     * {@link #onBreaking}. Gibt die Entfernung vom Startpunkt zurück (NaN: bricht nicht).
     */
    public double breakPointAlong(double x0, double z0, double x1, double z1, double t) {
        int n = switch (quality) { case LOW -> 100; case MEDIUM -> 250; case HIGH -> 500; case ULTRA -> 1000; };
        double len = Math.hypot(x1 - x0, z1 - z0);
        for (int i = 0; i <= n; i++) {
            double f = (double) i / n, x = x0 + (x1 - x0) * f, z = z0 + (z1 - z0) * f;
            if (breakingAt(x, z, t)) {
                if (onBreaking != null) onBreaking.breaking(x, z, heightAt(x, z, t), typeAt(x, z, t));
                return f * len;
            }
        }
        return Double.NaN;
    }
}
