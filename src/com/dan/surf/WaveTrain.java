package com.dan.surf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Die Brandung Welle für Welle (seit Sylt Phase 6, F9 A): ein Wellenzug auf einem Querschnitt vom Rand zur See (x0)
 * bis zum Ufer, {@link #width} m breit (Reihen längs des Kamms). Jede Welle kommt am Rand mit einer eigenen Höhe
 * (Rayleigh, in Gruppen: Hüllkurve über {@link #groupWaves} Wellen) und Periode herein, läuft mit ihrer Geschwindigkeit
 * über den Grund, wird nach dem Shoaling höher und bricht je Reihe, wo sie γ·h erreicht (McCowan, je Welle 0,78); der
 * Kamm ist nicht überall gleich hoch ({@link #crestVar}), darum bricht er nicht überall zugleich, sondern „schält“ sich.
 * Der Brechertyp kommt aus der Iribarren-Zahl ξ_b nach Battjes (1974) mit dem Hang am Ort. Danach läuft die Welle als
 * Walze (Weißwasser) mit einer Höhe von {@link #gammaBore}·h weiter und hinterlässt Schaum ({@link FoamField}).
 * <p>
 * <b>Brandungsschwebung:</b> unter den Gruppen schwingt der Wasserstand mit der Periode der Gruppen (Munk 1949,
 * Tucker 1950); zur Küste hin stärker, gegenphasig zur Hüllkurve [G: am Ufer rund 0,1·H_s].
 * <p>
 * Ereignisse: {@link #onBreaking} (eine Welle beginnt zu brechen: Ort, Höhe, Typ, einmal je Welle) und
 * {@link #onImpact} (die Lippe eines Sturzbrechers schlägt auf: Ort, Wassermenge, Tempo, je Reihe) – für Gischt und
 * Klang. Gleicher {@link #seed}, gleiche Wellen.
 */
public final class WaveTrain {
    /** Grund längs des Querschnitts (m über dem Ruhewasser, unter Wasser negativ). */
    @FunctionalInterface
    public interface Profile { double bed(double x); }

    /** Eine Lippe schlägt auf: Ort (x längs, y quer, z Höhe), Wasser (m³), Tempo (m/s). */
    @FunctionalInterface
    public interface Impact { void impact(double x, double y, double z, double volume, double speed); }

    public Profile profile = x -> -3 + x / 30;
    /** Seit Lighthouse Phase 17: Grund je Reihe (m über dem Ruhewasser), etwa ein Schnitt durch ein Bauwerk im Meer. */
    public interface RowProfile { double bed(double x, int r); }
    /**
     * Seit Lighthouse Phase 17 (Brandung am Sockel): wenn gesetzt, hat jede Reihe ihren eigenen Grund. Die Wellen laufen
     * mit {@link #profile} (der See um das Bauwerk, die Kämme bleiben gerade), Shoaling, Brechen, Walze und Netz nehmen
     * aber die Tiefe der Reihe. Wo eine Reihe trocken ist (im Bauwerk), ruht sie; wo sie hinter dem Bauwerk wieder tief
     * wird, bleibt die Walze so niedrig wie über der flachsten Stelle. null: wie bisher, ein Grund für alle Reihen.
     */
    public RowProfile rowProfile;
    /** Die See am Rand (H_s, T_p), Ruhewasser (m), Rand zur See (x) und Breite längs des Kamms (m). */
    public double hs = 1, tp = 8, level = 0, x0 = 0, width = 60;
    /** Reihen längs des Kamms. */
    public final int rows;
    /** McCowan je Welle, Höhe der Walze im Verhältnis zur Tiefe [G: im Inneren der Brandung 0,5]. */
    public double gamma = 0.78, gammaBore = 0.5;
    /** Gruppen: Wellen je Gruppe, Tiefe der Hüllkurve; Streuung der Perioden; Ungleichheit längs des Kamms und ihre Länge (m). */
    public double groupWaves = 7, groupDepth = 0.45, periodSpread = 0.12, crestVar = 0.10, crestLength = 25;
    /** Länge, über die der Hang für ξ_b gemittelt wird (m). */
    public double slopeBase = 12;
    /** Brandungsschwebung am Ufer (Anteil von H_s). */
    public double beat = 0.10;
    public long seed = 1;
    public Surf.Breaking onBreaking;
    public Impact onImpact;
    public final FoamField foam;

    /** Eine Welle. */
    public static final class Wave {
        public final int id;
        /** Kamm (x), Höhe ungebrochen (m), Periode (s), Geschwindigkeit (m/s). */
        public double x, hu, period, c;
        final double hStart, ksStart, h0;
        /** Typ (beim ersten Brechen), Ort und Höhe dort. */
        public Surf.Breaker type = Surf.Breaker.NONE;
        public double xBreak = Double.NaN, hBreak;
        /** Je Reihe: Beginn des Brechens (Szenenzeit, NaN: noch nicht), Höhe dort, ob die Lippe schon aufschlug. */
        public final double[] onset, hb;
        final boolean[] impacted;
        /** Seit Lighthouse Phase 17: je Reihe die kleinste Tiefe seit dem Rand (nur mit {@link WaveTrain#rowProfile}). */
        final double[] hMin;
        final double phaseY;
        Wave(int id, int rows, double x, double h, double period, double h0, double ks, double phaseY) {
            this.id = id;
            this.x = x;
            this.hu = h;
            this.hStart = h;
            this.period = period;
            this.h0 = h0;
            this.ksStart = ks;
            this.phaseY = phaseY;
            onset = new double[rows];
            hb = new double[rows];
            impacted = new boolean[rows];
            hMin = new double[rows];
            java.util.Arrays.fill(onset, Double.NaN);
            java.util.Arrays.fill(hMin, Double.POSITIVE_INFINITY);
        }
        public boolean broken(int r) { return !Double.isNaN(onset[r]); }
    }

    /** Die Wellen, die See zuerst (aufsteigend in x). */
    public final List<Wave> waves = new ArrayList<>();
    private double time, nextSpawn;
    private int nextId;
    /** Zähler: Wellen hereingekommen, gebrochen je Typ, Aufschläge. */
    public int spawned, impacts;
    public final int[] brokenByType = new int[Surf.Breaker.values().length];
    private final double phase0;

    public WaveTrain(int rows, double x0, double xEnd, double width) {
        this.rows = rows;
        this.x0 = x0;
        this.width = width;
        this.foam = new FoamField(x0, xEnd, 0.5, rows);
        this.phase0 = 0;
    }

    public double time() { return time; }

    /** Lage der Reihe r längs des Kamms (m). */
    public double rowY(int r) { return (r + 0.5) * width / rows; }

    /** Hüllkurve der Gruppen zur Zeit t (Mittel 1). */
    public double envelope(double t) {
        return 1 + groupDepth * Math.sin(2 * Math.PI * t / (groupWaves * tp) + 0.7 * seed);
    }

    /** Wasserstand aus der Brandungsschwebung am Ort x (m): zur Küste stärker, gegenphasig zur Hüllkurve. */
    public double surfBeat(double x) {
        double h0 = level - profile.bed(x0), h = Math.max(0, level - profile.bed(x));
        double shore = Math.max(0, Math.min(1, 1 - h / Math.max(0.1, h0)));
        double tLag = (x - x0) / Math.sqrt(Surf.G * Math.max(0.3, (h0 + h) / 2));
        return -beat * hs * shore * (envelope(time - tLag) - 1) / Math.max(1e-6, groupDepth);
    }

    /** Wasserstand am Ort (Ruhewasser + Schwebung). */
    public double meanLevel(double x) { return level + surfBeat(x); }

    /** Tiefe am Ort mit der Schwebung. */
    public double depth(double x) { return meanLevel(x) - profile.bed(x); }

    /** Grund in Reihe r am Ort x (seit Lighthouse Phase 17; ohne {@link #rowProfile} der gemeinsame). */
    public double bed(double x, int r) { return rowProfile != null ? rowProfile.bed(x, r) : profile.bed(x); }

    /** Tiefe in Reihe r am Ort x mit der Schwebung (seit Lighthouse Phase 17; ohne {@link #rowProfile} wie {@link #depth(double)}). */
    public double depth(double x, int r) { return rowProfile != null ? meanLevel(x) - rowProfile.bed(x, r) : depth(x); }

    /** Ungebrochene Höhe der Welle in Reihe r bei der Tiefe h der Reihe (ohne Ungleichheit des Kamms). */
    private double huRow(Wave w, double h) {
        return rowProfile == null ? w.hu : w.hStart * Surf.shoaling(w.period, Math.max(0.05, h)) / w.ksStart;
    }

    /** Ungleichheit des Kamms in Reihe r (Anteil, um 0). */
    public double delta(Wave w, int r) {
        double y = rowY(r) / crestLength + w.phaseY;
        double n = 0.65 * Math.sin(2 * Math.PI * y) + 0.35 * Math.sin(2 * Math.PI * (2.3 * y + 0.4));
        return crestVar * n;
    }

    /** Dauer des Brechens bis zur Walze (s) für Höhe H und Typ [G: der Sturz der Lippe ~ Fallzeit über 1,5·H]. */
    public double breakDuration(double H, Surf.Breaker type) {
        double fall = Math.sqrt(2 * 1.5 * H / Surf.G);
        return switch (type) {
            case PLUNGING -> 1.5 * fall;
            case SPILLING -> 4 * fall + 1.0;
            case COLLAPSING, SURGING -> 1.2 * fall;
            default -> 1;
        };
    }

    /** Fortschritt des Brechens in Reihe r: NaN ungebrochen, 0..1 Brechen, darüber Walze (in Einheiten der Dauer). */
    public double progress(Wave w, int r) {
        if (!w.broken(r)) return Double.NaN;
        return (time - w.onset[r]) / breakDuration(w.hb[r], w.type);
    }

    /** Höhe der Welle in Reihe r jetzt (m). */
    public double heightRow(Wave w, int r) {
        double p = progress(w, r);
        if (rowProfile != null) {
            double hr = depth(w.x, r);
            if (Double.isNaN(p)) return huRow(w, hr) * (1 + delta(w, r));
            double hbore = gammaBore * Math.max(0, Math.min(hr, w.hMin[r]));
            if (p < 1) return w.hb[r];
            double f = Math.exp(-(p - 1) * 1.2);
            return Math.max(0, Math.min(w.hb[r], hbore + (w.hb[r] - hbore) * f));
        }
        if (Double.isNaN(p)) return w.hu * (1 + delta(w, r));
        double hbore = gammaBore * Math.max(0, depth(w.x));
        if (p < 1) return w.hb[r];
        double f = Math.exp(-(p - 1) * 1.2);
        return Math.max(0, Math.min(w.hb[r], hbore + (w.hb[r] - hbore) * f));
    }

    /** Lineare Phasengeschwindigkeit mit einer Korrektur für die Höhe; gebrochen die Geschwindigkeit einer Walze. */
    double celerity(Wave w, double h) {
        if (h <= 0.02) return 0;
        boolean anyBroken = false;
        double H = 0;
        for (int r = 0; r < rows; r++) { if (w.broken(r)) anyBroken = true; H += heightRow(w, r) / rows; }
        double k = Surf.wavenumber(w.period, h), cl = 2 * Math.PI / w.period / k;
        double shallow = 1 - Math.tanh(k * h);
        if (!anyBroken) return cl * (1 + 0.25 * Math.min(1, H / h) * shallow);
        // Walze: c = √(g·h₂·(h₁ + h₂)/(2·h₁)) mit h₁ = h vor ihr, h₂ = h + H
        double h2 = h + H;
        return Math.max(cl * 0.5, Math.sqrt(Surf.G * h2 * (h + h2) / (2 * h)));
    }

    /** Ein Schritt der Szenenzeit. */
    public void step(double dt) {
        time += dt;
        // neue Wellen am Rand
        while (time >= nextSpawn) spawn(nextSpawn);
        double L0 = Surf.deepLength(tp);
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave w = waves.get(i);
            double h = depth(w.x);
            w.c = celerity(w, h);
            w.x += w.c * dt;
            h = depth(w.x);
            if (h <= 0.03 || w.x > foam.x1) { waves.remove(i); continue; }
            w.hu = w.hStart * Surf.shoaling(w.period, h) / w.ksStart;
            final boolean byRow = rowProfile != null;
            for (int r = 0; r < rows; r++) {
                double hr = h, hur = w.hu;
                if (byRow) {
                    hr = depth(w.x, r);
                    if (hr < w.hMin[r]) w.hMin[r] = Math.max(0, hr);
                    if (hr <= 0.03) continue;     // trocken: im Bauwerk
                    hur = huRow(w, hr);
                }
                if (!w.broken(r) && hur * (1 + delta(w, r)) >= gamma * hr) {
                    w.onset[r] = time;
                    w.hb[r] = hur * (1 + delta(w, r));
                    // je Reihe kann der Grund springen (Kante einer Schüttung): höher als γ·h mit etwas Spiel bricht keine Welle [G]
                    if (byRow) w.hb[r] = Math.min(w.hb[r], 1.25 * gamma * hr);
                    if (w.type == Surf.Breaker.NONE) {
                        double half = slopeBase / 2;
                        double s = byRow ? Math.max(0, (bed(w.x + half, r) - bed(w.x - half, r)) / slopeBase)
                                : Math.max(0, (profile.bed(w.x + half) - profile.bed(w.x - half)) / slopeBase);
                        w.type = Surf.typeAtBreaking(Surf.iribarren(s, w.hb[r], w.period));
                        if (w.type == Surf.Breaker.NONE) w.type = Surf.Breaker.SPILLING;
                        w.xBreak = w.x;
                        w.hBreak = w.hb[r];
                        brokenByType[w.type.ordinal()]++;
                        if (onBreaking != null) onBreaking.breaking(w.x, rowY(r), w.hb[r], w.type);
                    }
                }
                double p = progress(w, r);
                if (Double.isNaN(p)) continue;
                double Hr = heightRow(w, r);
                // Aufschlag der Lippe (Sturzbrecher)
                if (w.type == Surf.Breaker.PLUNGING && p >= 1 && !w.impacted[r]) {
                    w.impacted[r] = true;
                    impacts++;
                    double[] tip = BreakerShape.lipTip(w.hb[r], hr, 1);
                    double xi = w.x + tip[0];
                    foam.deposit(r, xi - 0.5 * w.hb[r], xi + 0.6 * w.hb[r], 1);
                    if (onImpact != null)
                        onImpact.impact(xi, rowY(r), meanLevel(xi) + tip[1], 0.12 * w.hb[r] * w.hb[r] * width / rows, Math.sqrt(2 * Surf.G * 1.5 * w.hb[r]));
                }
                // Schaum: die Walze vorn, beim Schwallbrecher schon während des Brechens
                double startFoam = w.type == Surf.Breaker.SPILLING ? 0.15 : 0.85;
                if (p > startFoam) {
                    double roll = 1.5 * Hr + 0.4;
                    foam.deposit(r, w.x - 0.3 * roll, w.x + roll, Math.min(1, 0.6 + 0.4 * Math.min(1, p)));
                }
            }
        }
        foam.step(dt);
        // Ordnung: die See zuerst
        waves.sort((a, b) -> Double.compare(a.x, b.x));
    }

    private void spawn(double t) {
        int id = nextId++;
        SplittableRandom rnd = new SplittableRandom(seed * 1_000_003L + id * 7919L);
        double T = tp * (1 + periodSpread * (2 * rnd.nextDouble() - 1));
        double hrms = hs / Math.sqrt(2);
        double env = envelope(t) / Math.sqrt(1 + groupDepth * groupDepth / 2);
        double u = Math.max(1e-6, rnd.nextDouble());
        double H = Math.min(2.2 * hrms, hrms * Math.sqrt(-Math.log(u))) * env;
        double h0 = Math.max(0.1, depth(x0));
        Wave w = new Wave(id, rows, x0, H, T, h0, Surf.shoaling(T, h0), rnd.nextDouble() * 10);
        waves.add(0, w);
        spawned++;
        nextSpawn = t + T;
    }

    /** „12 Wellen im Becken · gebrochen: 5 Schwall, 7 Sturz, 0 Reflexion · 31 Aufschläge · Schaum 18 %“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%d Wellen im Becken · gebrochen: %d Schwall, %d Sturz, %d Reflexion · %d Aufschläge · Schaum %.0f %%",
                waves.size(), brokenByType[Surf.Breaker.SPILLING.ordinal()], brokenByType[Surf.Breaker.PLUNGING.ordinal()],
                brokenByType[Surf.Breaker.COLLAPSING.ordinal()] + brokenByType[Surf.Breaker.SURGING.ordinal()], impacts, foam.coverage() * 100);
    }
}
