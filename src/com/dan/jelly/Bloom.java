package com.dan.jelly;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Ohrenquallen (<i>Aurelia aurita</i>) in einem Feld um den Blick (seit Timmendorf Phase 17). Jede Qualle hat Ort, Tiefe,
 * Schirm, Schlag und Richtung; sie schwimmt langsam, der Wind treibt sie (Oberflächenstrom rund 3 % des Winds), bei
 * Seegang sinkt sie tiefer, im flachen Wasser strandet sie. Das Feld ist eine Kachel um den Blickpunkt: wer sie verlässt,
 * kommt auf der anderen Seite wieder herein. So reichen ein paar hundert Tiere für eine ganze Bucht.
 * <p>
 * Die Jahreszeit kommt als Tag im Jahr und Wassertemperatur herein: in der westlichen Ostsee erscheinen die Medusen im Mai
 * mit wenigen Zentimetern, wachsen bis August auf 15 bis 25 cm, sind Ende August und im September am häufigsten und
 * vergehen im Oktober und November [G nach Möller 1980, Hansson 1997, Lucas 2001: Größe und Zeit, nicht die Zahl].
 * <p>
 * Der Kern kennt nur Java 21. Grund und Wasser kommen über die eigenen Schnittstellen {@link Bed} und {@link Surface}.
 */
public final class Bloom {
    /** Qualitätsstufen, eigene Kopie: Größe der Kachel und höchste Zahl der Tiere. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Höhe des Grunds (m) am Ort; null = überall tief. */
    @FunctionalInterface
    public interface Bed { double heightAt(double x, double z); }

    /** Höhe des Wasserspiegels mit Wellen (m) am Ort; null = {@link #waterLevel}. */
    @FunctionalInterface
    public interface Surface { double heightAt(double x, double z); }

    /** Ereignis: eine Qualle ist gestrandet (Ort, Durchmesser in m). */
    @FunctionalInterface
    public interface Stranding { void stranded(double x, double z, double diameter); }

    /** Eine Qualle. Felder offen, gelesen von der Darstellung. */
    public static final class Jelly {
        /** Ort (m), Höhe, in der sie gezeichnet wird (Spiegel oder Grund), Tiefe unter dem Spiegel (m). */
        public double x, z, y, depth;
        /** Durchmesser des Schirms in m (ohne Schlag), Faktor der Größe 0,6…1,3. */
        public double diameter, size;
        /** Richtung des Schwimmens (rad, von +x gegen den Uhrzeigersinn um y gesehen von oben), Phase des Schlags 0..1. */
        public double heading, phase;
        /** Schläge je s, Zusammenziehen jetzt 0..1. */
        public double rate, contraction;
        /** Farbe der Gonaden 0 violett … 1 rosa; Rang 0..1 (aktiv, wenn kleiner als der Anteil der Jahreszeit). */
        public double hue, rank;
        /** Bevorzugte Tiefe (m), Ziel der Tiefe jetzt. */
        double prefDepth, targetDepth;
        /** Gestrandet seit (s, 0 = schwimmt), über Land eingesetzt: bis zur nächsten Runde verborgen. */
        public double strandedFor;
        public boolean hidden;

        public boolean stranded() { return strandedFor > 0; }

        /** Durchmesser jetzt: zieht sich beim Schlag um bis zu 18 % zusammen. */
        public double diameterNow() { return diameter * (1 - 0.18 * contraction); }
    }

    // ------------------------------------------------------------------ offene Werte
    public long seed = 1;
    /** Tiere je m² an der Oberfläche auf der Höhe des Jahres (Vorgabe: ein Sommer, 0,1 je m²). */
    public double density = 0.1;
    /** Wind in m/s und Richtung, aus der er kommt (Grad, von Nord im Uhrzeigersinn). */
    public double windSpeed = 4, windFromDeg = 240;
    /** Strom ohne Wind (m/s, nach x und z). */
    public double currentX, currentZ;
    /** Signifikante Wellenhöhe (m): bei Seegang gehen die Quallen tiefer. */
    public double hs = 0.2;
    /** Tag im Jahr 1..366 und Wassertemperatur (°C). */
    public int dayOfYear = 230;
    public double waterTemp = 18;
    /** Ruhiger Pegel (m), falls keine {@link #surface} angeschlossen ist. */
    public double waterLevel = 0;
    /** Sichttiefe im Wasser (m): so tief sieht man eine Qualle noch halb (westliche Ostsee im Sommer rund 3–5 m). */
    public double visibility = 3.5;
    /** Gestrandete trocknen; nach so vielen s sind sie weg (Vorgabe 6 h). */
    public double strandedLife = 6 * 3600;
    public Quality quality = Quality.HIGH;
    public Bed bed;
    public Surface surface;
    public Stranding onStranding;
    /** Abschalten ohne Neuaufbau. */
    public boolean enabled = true;

    private Jelly[] all = new Jelly[0];
    private double cx = Double.NaN, cz = Double.NaN, tile = 120, time;
    private int strandings;
    private SplittableRandom rnd;
    private long builtSeed = Long.MIN_VALUE;
    private Quality builtQuality;
    private double builtDensity = -1;

    // ------------------------------------------------------------------ Presets
    /** Keine Quallen (Winter, Frühjahr). */
    public static Bloom none() { Bloom b = new Bloom(); b.density = 0; return b; }
    /** Ein paar, wie im Juni. */
    public static Bloom few() { Bloom b = new Bloom(); b.density = 0.015; b.dayOfYear = 170; b.waterTemp = 15; return b; }
    /** Ein gewöhnlicher Spätsommer: 0,1 je m². */
    public static Bloom summer() { return new Bloom(); }
    /** Massenauftreten: über eine Qualle je m², wie in manchen Jahren in der Lübecker Bucht. */
    public static Bloom bloom() { Bloom b = new Bloom(); b.density = 1.2; b.dayOfYear = 240; b.waterTemp = 19; return b; }

    /** Preset nach Beaufort 0..12 (ein Spätsommer): Wind und Seegang, die Quallen gehen bei Seegang tiefer. */
    public static Bloom beaufort(int bft) {
        Bloom b = summer();
        bft = Math.max(0, Math.min(12, bft));
        b.windSpeed = BFT_MID[bft];
        b.hs = HS[bft];
        return b;
    }

    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};
    /** Seegang in der Ostsee nach Beaufort (m), grob [G nach der Douglas-Skala, kurze Streichlänge]. */
    static final double[] HS = {0, 0.05, 0.15, 0.4, 0.7, 1.1, 1.6, 2.2, 2.8, 3.4, 4.0, 4.6, 5.2};

    // ------------------------------------------------------------------ Jahreszeit
    /** Anteil der Tiere, die jetzt da sind, 0..1: aus Tag und Wassertemperatur. */
    public double seasonFactor() {
        double d = dayOfYear;
        double f = smooth(125, 200, d) * (1 - smooth(275, 330, d));
        double peak = 0.55 + 0.45 * Math.exp(-sq((d - 240) / 35));
        return Math.max(0, f * peak * smooth(6, 10, waterTemp));
    }

    /** Durchmesser der mittleren Qualle jetzt (m): 3 cm im Mai, 22 cm im August, im Herbst kleiner. */
    public double meanDiameter() {
        double d = dayOfYear;
        double grow = 0.03 + 0.19 * smooth(130, 235, d);
        return grow * (1 - 0.25 * smooth(250, 320, d));
    }

    /** Schläge je s für einen Schirm (m) bei der Temperatur: kleine schlagen schneller, Wärme beschleunigt [G nach McHenry und Jed 2003]. */
    public static double pulseRate(double diameter, double tempC) {
        double base = 0.9 * Math.pow(Math.max(0.02, diameter) / 0.1, -0.35);
        return Math.max(0.2, Math.min(2.2, base * Math.pow(2, (tempC - 16) / 10)));
    }

    /** Zusammenziehen 0..1 aus der Phase: schnell zusammen (ein Drittel), langsam auf. */
    public static double contraction(double phase) {
        phase -= Math.floor(phase);
        if (phase < 0.3) {
            double u = phase / 0.3;
            return Math.sin(u * Math.PI / 2);
        }
        double u = (phase - 0.3) / 0.7;
        return Math.pow(1 - u, 2.2);
    }

    // ------------------------------------------------------------------ Rechnen
    /** Kantenlänge der Kachel um den Blick (m). */
    public double tileSize() { return tile; }

    /** Höchste Zahl der Tiere nach der Stufe. */
    public static int capacity(Quality q) {
        return switch (q) { case LOW -> 300; case MEDIUM -> 800; case HIGH -> 1600; case ULTRA -> 3000; };
    }

    static double tileFor(Quality q) {
        return switch (q) { case LOW -> 70; case MEDIUM -> 100; case HIGH -> 130; case ULTRA -> 170; };
    }

    /** Alle Tiere (auch verborgene, gestrandete, nicht aktive); nur lesen. */
    public Jelly[] all() { return all; }

    /** Ist die Qualle jetzt zu sehen (aktiv in der Jahreszeit, nicht verborgen)? */
    public boolean visible(Jelly j) { return enabled && !j.hidden && j.rank < seasonFactor(); }

    /** Zahl der sichtbaren Tiere, davon gestrandet. */
    public int visibleCount() {
        int n = 0;
        double sf = seasonFactor();
        for (Jelly j : all) if (enabled && !j.hidden && j.rank < sf) n++;
        return n;
    }

    public int strandedCount() {
        int n = 0;
        for (Jelly j : all) if (visible(j) && j.stranded()) n++;
        return n;
    }

    /** Wie viele seit dem Start gestrandet sind. */
    public int strandings() { return strandings; }

    /** Wie viele sichtbare im Kreis (m) um den Ort. */
    public int countNear(double x, double z, double r) {
        int n = 0;
        for (Jelly j : all) if (visible(j) && sq(j.x - x) + sq(j.z - z) < r * r) n++;
        return n;
    }

    /**
     * Ein Schritt (s) um den Blickpunkt (fx, fz). Baut das Feld neu, wenn seed, Stufe oder Dichte sich geändert haben,
     * und setzt es um, wenn der Blick weiter als eine Kachel springt.
     */
    public void step(double dt, double fx, double fz) {
        if (all.length == 0 || builtSeed != seed || builtQuality != quality || Math.abs(builtDensity - density) > 1e-9) build(fx, fz);
        if (Double.isNaN(cx) || Math.abs(fx - cx) > tile || Math.abs(fz - cz) > tile) {
            cx = fx;
            cz = fz;
            for (Jelly j : all) place(j, fx + (rnd.nextDouble() - 0.5) * tile, fz + (rnd.nextDouble() - 0.5) * tile);
        }
        cx = fx;
        cz = fz;
        time += dt;
        if (!enabled || dt <= 0) { settle(); return; }
        double sf = seasonFactor(), mean = meanDiameter();
        double a = Math.toRadians(windFromDeg + 180);
        double dx = Math.sin(a) * windSpeed * 0.03 + currentX, dz = -Math.cos(a) * windSpeed * 0.03 + currentZ;
        double rough = 0.5 * hs + Math.max(0, windSpeed - 8) * 0.12;
        double half = tile / 2;
        for (Jelly j : all) {
            j.diameter = mean * j.size;
            if (j.rank >= sf) continue;
            if (j.stranded()) {
                j.strandedFor += dt;
                j.contraction = 0;
                if (j.strandedFor > strandedLife) j.hidden = true;
                j.y = bedAt(j.x, j.z) + 0.005;
                continue;
            }
            j.rate = pulseRate(j.diameter, waterTemp);
            j.phase += j.rate * dt;
            j.phase -= Math.floor(j.phase);
            j.contraction = contraction(j.phase);
            // Schwimmen: ein paar cm je s im Schlag, die Richtung wandert langsam
            j.heading += (rnd.nextDouble() - 0.5) * 0.4 * Math.sqrt(dt);
            double swim = 0.25 * j.diameter * j.rate * (0.4 + 0.6 * j.contraction);
            j.x += (Math.cos(j.heading) * swim + dx) * dt;
            j.z += (-Math.sin(j.heading) * swim + dz) * dt;
            // in der Kachel halten: wer hinaus treibt, kommt gegenüber herein (und ist über Land verborgen)
            if (j.x < cx - half || j.x > cx + half || j.z < cz - half || j.z > cz + half) {
                double nx = j.x < cx - half ? j.x + tile : j.x > cx + half ? j.x - tile : j.x;
                double nz = j.z < cz - half ? j.z + tile : j.z > cz + half ? j.z - tile : j.z;
                place(j, nx, nz);
                continue;
            }
            double level = levelAt(j.x, j.z), h = level - bedAt(j.x, j.z);
            j.targetDepth = j.prefDepth + rough * (0.6 + 0.8 * j.rank);
            j.depth += (j.targetDepth - j.depth) * Math.min(1, dt / 40);
            if (j.hidden) continue;
            if (h < 0.12 + 0.3 * j.diameter) {
                j.strandedFor = 1e-3;
                j.depth = 0;
                strandings++;
                if (onStranding != null) onStranding.stranded(j.x, j.z, j.diameter);
                j.y = bedAt(j.x, j.z) + 0.005;
                continue;
            }
            j.depth = Math.min(j.depth, Math.max(0.05, h - 0.1));
            j.y = level + lift();
        }
    }

    /** Ohne Zeit: Höhe und Tiefe nach den Eingängen setzen. */
    private void settle() {
        for (Jelly j : all) {
            if (j.stranded()) j.y = bedAt(j.x, j.z) + 0.005;
            else j.y = levelAt(j.x, j.z) + lift();
        }
    }

    private void build(double fx, double fz) {
        builtSeed = seed;
        builtQuality = quality;
        builtDensity = density;
        rnd = new SplittableRandom(seed);
        tile = tileFor(quality);
        int n = (int) Math.min(capacity(quality), Math.round(Math.max(0, density) * tile * tile));
        if (n > 0 && n < 4) n = 4;
        all = new Jelly[n];
        cx = fx;
        cz = fz;
        for (int i = 0; i < n; i++) {
            Jelly j = new Jelly();
            j.rank = (i + rnd.nextDouble()) / n;
            j.size = 0.6 + 0.7 * rnd.nextDouble() * rnd.nextDouble() + 0.15 * rnd.nextDouble();
            j.hue = rnd.nextDouble();
            j.heading = rnd.nextDouble() * Math.PI * 2;
            j.phase = rnd.nextDouble();
            // die meisten nahe der Oberfläche (0,1–1,5 m), ein paar tiefer
            j.prefDepth = 0.1 + 1.4 * Math.pow(rnd.nextDouble(), 1.6);
            j.depth = j.prefDepth;
            all[i] = j;
            place(j, fx + (rnd.nextDouble() - 0.5) * tile, fz + (rnd.nextDouble() - 0.5) * tile);
        }
        // Dichte ehrlich halten: wenn die Stufe deckelt, wird die Kachel kleiner
        if (n > 0 && density * tile * tile > n) {
            double t2 = Math.sqrt(n / density);
            double s = Math.max(12, t2) / tile;
            tile = Math.max(12, t2);
            for (Jelly j : all) place(j, fx + (j.x - fx) * s, fz + (j.z - fz) * s);
        }
    }

    /** Setzt eine Qualle an einen Ort: über Land oder zu flachem Grund verborgen bis zur nächsten Runde. */
    private void place(Jelly j, double x, double z) {
        j.x = x;
        j.z = z;
        j.strandedFor = 0;
        double level = levelAt(x, z), h = level - bedAt(x, z);
        j.hidden = h < 0.4;
        j.depth = Math.min(j.prefDepth, Math.max(0.05, h - 0.1));
        j.y = level + lift();
    }

    /**
     * Wie weit über dem Spiegel (m) die Darstellung den Schirm zeichnet: knapp darüber, bei Seegang höher, damit
     * Wellenkämme der GPU-See ihn nicht verdecken (Befund Timmendorf Phase 17: im Foto „Ohrenquallen“ keine Qualle).
     */
    public double lift() { return 0.03 + 0.35 * Math.max(0, hs); }

    double levelAt(double x, double z) { return surface != null ? surface.heightAt(x, z) : waterLevel; }

    double bedAt(double x, double z) { return bed != null ? bed.heightAt(x, z) : waterLevel - 100; }

    /** Wie viel einer Qualle in dieser Tiefe man noch sieht, 0..1 (Lambert-Beer mit der Sichttiefe). */
    public double seen(double depth) { return Math.exp(-Math.max(0, depth) / Math.max(0.2, visibility) * 0.69); }

    /** „Quallen: 412 sichtbar in 130 m, 3 gestrandet · Schirm 22 cm · Jahr 92 %“ */
    public String describe() {
        if (!enabled) return "Quallen: aus";
        return String.format(Locale.GERMAN, "Quallen: %d sichtbar in %.0f m, %d gestrandet · Schirm %.0f cm · Jahr %.0f %%",
                visibleCount(), tile, strandedCount(), meanDiameter() * 100, seasonFactor() * 100);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double sq(double v) { return v * v; }
}
