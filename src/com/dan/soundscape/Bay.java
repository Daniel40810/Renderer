package com.dan.soundscape;

import java.util.ArrayList;
import java.util.List;

/**
 * Seit Timmendorf Phase 15: der Klang einer Bucht mit Booten, Hafen, Regatta, Feuerwerk und Promenade. Alles erzeugt,
 * nach Gehör und Beschreibungen gebaut [G], keine Aufnahmen. Zwei Teile:
 * <p>
 * <b>Ereignisse</b> (beim Auslösen einmal gerechnet wie {@link Coast}; Pegel jeweils der lautesten 100 ms in 1 m):
 * <ul>
 * <li><b>Fall</b> ({@code FALL}, tone 0 = Aluminiummast, 1 = Holzmast): ein Fall (Leine mit Schäkel) schlägt gegen den
 * Mast. Am Aluminiumrohr ein heller Anschlag und die Eigentöne der Rohrwand (rund 600 Hz bis 4 kHz, das „Klong“ eines
 * Hafens im Wind), oft ein zweiter Schlag kurz danach; am Holzmast ein dumpfes Klopfen (180 bis 700 Hz). 86 bzw. 78 dB.</li>
 * <li><b>Segel</b> ({@code SEGEL}, tone = Segelfläche m²): das Tuch steht nicht mehr und schlägt, etwa beim Anluven oder
 * in der Wende. Eine Folge von Schlägen, je ein Knall des Tuchs (über 1,5 kHz), ein Klatschen um 400 Hz und bei großen
 * Segeln ein Wummern um 110 Hz; je größer, desto langsamer (3,5 · (10 / A)^0,35 Hz). 84 + 10 · lg(A / 7) dB, 70 bis 106.</li>
 * <li><b>Winsch</b> ({@code WINSCH}, tone = Klicks): die Sperrklinke rattert (14 bis 20 Klicks je s, 3 bis 4,5 kHz),
 * dazu das Tauwerk auf der Trommel. 80 dB.</li>
 * <li><b>Signalhorn</b> ({@code SIGNALHORN}, tone = Dauer s): Druckluftfanfare des Startschiffs, Grundton um 410 Hz,
 * hell und etwas rau. 120 dB [G].</li>
 * <li><b>Startschuss</b> ({@code STARTSCHUSS}): Knall der Signalkanone, eine N-Welle von 1,5 ms, dazu das Grollen über
 * dem Wasser. 140 dB [G].</li>
 * <li><b>Abschuss</b> ({@code ABSCHUSS}, tone = Kaliber mm): die Treibladung im Mörser, ein dumpfer Schlag (120 → 55 Hz)
 * und das Zischen der Zündschnur. 118 + 20 · lg(Kaliber / 75) dB, 100 bis 135.</li>
 * <li><b>Zerlegung</b> ({@code ZERLEGUNG}, tone = Kaliber mm): die Bombe zerlegt sich, eine N-Welle von 2,5 ms · (Kaliber /
 * 75)^⅓ (die Dauer wächst mit der dritten Wurzel der Ladung), das Grollen und die Echos von Ufer und Häusern über dem
 * Wasser. 145 + 20 · lg(Kaliber / 75) dB, 125 bis 160. Laufzeit nach Abstand / c wie jedes Ereignis: erst das Bild, dann
 * der Knall.</li>
 * <li><b>Knistern</b> ({@code KNISTERN}, tone = Sterne): Knistersterne (Crackling) platzen in dichter Folge, je ein
 * winziger Knall. 112 + 10 · lg(Sterne / 100) dB, 95 bis 125.</li>
 * <li><b>Fährhorn</b> ({@code FAEHRHORN}, tone = Dauer s): Typhon eines Schiffs über 200 m, 110 Hz, 143 dB in 1 m
 * (KVR Anlage III: 70 bis 200 Hz, mindestens 143 dB für 200 m und mehr).</li>
 * </ul>
 * <b>Betten</b> (dauernd, in {@link Soundscape} je Block gerechnet, Teile „Tuch und Takelage“ und „Schritte“):
 * <ul>
 * <li><b>Flaggen</b> ({@link Soundscape#flaps}, {@link Flap}): jede Flagge schlägt im Takt ihres Flatterns, die nächsten
 * acht in 120 m. Je Schlag 66 + 10 · lg(A / 1 m²) + 50 · lg(U / 8 m/s) dB in 1 m, unter 2 m/s still.</li>
 * <li><b>Takelage</b> ({@link Soundscape#rigs}, {@link Rig}): ein Hafen voller Masten; jedes Fall schlägt bei
 * 0,015 · (U − 2,5) je s, mehr mit Seegang. Die Schläge verteilen sich über das Becken. Hörbar bis 800 m.</li>
 * <li><b>Schritte</b> ({@link Soundscape#walkers}, {@link Soundscape#footing}): Gehende in 15 m um das Ohr, je 1,8
 * Schritte je s; auf Pflaster ein Absatzklick (62 dB), auf dem Holzdeck einer Seebrücke ein hohles Pochen (68 dB), auf
 * Sand ein leises Knirschen (52 dB).</li>
 * </ul>
 * Nur Java 21.
 */
final class Bay {
    private Bay() { }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Pegel in 1 m (lauteste 100 ms) bei Stärke 1, ohne tone-Abhängigkeit außer wo angegeben. */
    static double level(Soundscape.Kind k, double tone) {
        return switch (k) {
            case FALL -> tone >= 0.5 ? 78 : 86;
            case SEGEL -> clamp(84 + 10 * Math.log10(Math.max(0.5, tone) / 7), 70, 106);
            case WINSCH -> 80;
            case SIGNALHORN -> 120;
            case STARTSCHUSS -> 140;
            case ABSCHUSS -> clamp(118 + 20 * Math.log10(Math.max(20, tone) / 75), 100, 135);
            case ZERLEGUNG -> clamp(145 + 20 * Math.log10(Math.max(20, tone) / 75), 125, 160);
            case KNISTERN -> clamp(112 + 10 * Math.log10(Math.max(5, tone) / 100), 95, 125);
            case FAEHRHORN -> 143;
            default -> throw new IllegalArgumentException("kein Klang der Bucht: " + k);
        };
    }

    // ------------------------------------------------------------------ Ereignisse

    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = clamp(strength, 0, 1), lv = 6 * Math.log10(Math.max(0.05, s));
        switch (k) {
            case FALL -> {
                boolean wood = tone >= 0.5;
                float[] o = new float[(int) ((wood ? 0.5 : 1.4) * sr)];
                hit(o, 0, wood, 1, r, sr);
                if (r.uniform() < 0.45) hit(o, (int) ((0.04 + 0.1 * r.uniform()) * sr), wood, 0.4 + 0.3 * r.uniform(), r, sr);
                Calls.normalize(o, sr);
                return new Calls.Made(wood ? "Fall am Holzmast" : "Fall am Mast", level(k, tone) + lv, o);
            }
            case SEGEL -> {
                double a = clamp(tone, 0.5, 400), big = clamp(Math.log10(a / 5) / 2, 0, 1);
                double rate = clamp(3.5 * Math.pow(10 / a, 0.35), 1.2, 9), len = 1.2 + 1.3 * r.uniform() + 0.8 * big;
                int n = (int) ((len + 0.4) * sr);
                float[] o = new float[n];
                float[] rustle = Coast.unit(Coast.filtered(new Dsp.Pink(seed + 3)::next, new Dsp.Biquad().highpass(2000, 0.7, sr), n));
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    o[i] = (float) (rustle[i] * 0.015 * Calls.smooth(t / 0.2) * Math.exp(-Math.max(0, t - len) / 0.15));
                }
                double t = 0.02 + 0.05 * r.uniform();
                while (t < len) {
                    double fade = t < len * 0.6 ? 1 : 1 - (t - len * 0.6) / (len * 0.4) * 0.7;
                    slap(o, (int) (t * sr), (0.55 + 0.45 * r.uniform()) * fade, big, r, sr);
                    t += 1 / rate * (0.75 + 0.5 * r.uniform());
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Segel schlägt", level(k, a) + lv, o);
            }
            case WINSCH -> {
                int clicks = (int) clamp(Math.round(tone), 3, 60);
                double f = 14 + 6 * r.uniform(), len = clicks / f;
                int n = (int) ((len + 0.2) * sr);
                float[] o = new float[n];
                float[] rope = Coast.unit(Coast.filtered(new Dsp.Pink(seed + 5)::next, new Dsp.Biquad().bandpass(1800, 0.8, sr), n));
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    o[i] = (float) (rope[i] * 0.12 * Calls.smooth(t / 0.05) * (t < len ? 1 : Math.exp(-(t - len) / 0.05)));
                }
                for (int c = 0; c < clicks; c++) {
                    int at = (int) ((c / f + 0.004 * r.gauss() + 0.01) * sr);
                    if (at < 0 || at >= n) continue;
                    Glacial.ring(o, at, 3200 + 1300 * r.uniform(), 0.003, 1.0, sr);
                    Glacial.ring(o, at, 1100 + 200 * r.uniform(), 0.006, 0.5, sr);
                    for (int i = 0; i < (int) (0.0004 * sr) && at + i < n; i++) o[at + i] += r.white() * 0.8f;
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Winsch", level(k, tone) + lv, o);
            }
            case SIGNALHORN -> {
                double len = clamp(tone, 0.2, 6), f0 = 405 + 15 * r.uniform();
                int n = (int) ((len + 0.25) * sr);
                float[] o = new float[n];
                float[] tab = Voice.Horn.harmonics(16, 0.85);
                Dsp.Biquad pk = new Dsp.Biquad().peaking(1700, 1.1, 6, sr), lp = new Dsp.Biquad().lowpass(Math.min(sr * 0.45, 7000), 0.7, sr);
                double ph = 0;
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    double f = f0 * (1 - 0.04 * Math.exp(-t / 0.04)) * (1 + 0.003 * Math.sin(Dsp.TAU * 5.5 * t));
                    ph += f / sr;
                    ph -= Math.floor(ph);
                    double env = Calls.smooth(t / 0.03) * (t < len ? 1 : Math.exp(-(t - len) / 0.05));
                    double rough = 1 + 0.06 * Math.sin(Dsp.TAU * 31 * t) + 0.02 * r.white();
                    o[i] = (float) (lp.process(pk.process(tab[(int) (ph * tab.length) & 2047])) * env * rough);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Signalhorn", level(k, tone) + lv, o);
            }
            case STARTSCHUSS -> {
                int n = (int) (2.2 * sr);
                float[] o = new float[n];
                nwave(o, (int) (0.005 * sr), 0.0015, 1.0, sr);
                float[] boom = Coast.unit(Coast.filtered(new Dsp.Brown(seed + 2)::next, new Dsp.Biquad().lowpass(160, 0.8, sr), n));
                float[] roll = Coast.unit(Coast.filtered(new Dsp.Pink(seed + 4)::next, new Dsp.Biquad().lowpass(700, 0.7, sr), n));
                for (int i = 0; i < n; i++) {
                    double t = i / sr - 0.005;
                    if (t < 0) continue;
                    o[i] += (float) (boom[i] * 0.06 * Math.exp(-t / 0.08) + roll[i] * 0.018 * Calls.smooth(t / 0.1) * Math.exp(-t / 0.7));
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Startschuss", level(k, tone) + lv, o);
            }
            case ABSCHUSS -> {
                double cal = clamp(tone, 20, 300), big = clamp(Math.log10(cal / 50) / Math.log10(6), 0, 1);
                int n = (int) (0.9 * sr);
                float[] o = new float[n];
                float[] burst = Coast.unit(Coast.filtered(r::white, new Dsp.Biquad().lowpass(500, 0.7, sr), n));
                float[] hiss = Coast.unit(Coast.filtered(new Dsp.Pink(seed + 3)::next, new Dsp.Biquad().bandpass(2500, 0.8, sr), n));
                double ph = 0, f1 = 120 - 30 * big, f2 = 55 - 15 * big;
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    double f = f2 + (f1 - f2) * Math.exp(-t / 0.02);
                    ph += Dsp.TAU * f / sr;
                    double thump = Math.sin(ph) * (1 - Math.exp(-t * 400)) * Math.exp(-t / (0.05 + 0.04 * big)) * 3;
                    double b = burst[i] * Math.exp(-t / 0.02) * 1.2;
                    double hs = hiss[i] * Calls.smooth((t - 0.02) / 0.05) * Math.exp(-Math.max(0, t - 0.02) / 0.25) * 0.08;
                    o[i] = (float) (thump + b + hs);
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Abschuss", level(k, cal) + lv, o);
            }
            case ZERLEGUNG -> {
                double cal = clamp(tone, 20, 300), tau = 0.0025 * Math.cbrt(cal / 75);
                int n = (int) (3.6 * sr);
                float[] o = new float[n];
                int at = (int) (0.005 * sr);
                nwave(o, at, tau, 1.0, sr);
                float[] boom = Coast.unit(Coast.filtered(new Dsp.Brown(seed + 2)::next, new Dsp.Biquad().lowpass(150, 0.8, sr), n));
                float[] roll = Coast.unit(Coast.filtered(new Dsp.Pink(seed + 4)::next, new Dsp.Biquad().lowpass(600, 0.7, sr), n));
                double tail = 1.5 + 1.0 * r.uniform();
                for (int i = at; i < n; i++) {
                    double t = (i - at) / sr;
                    o[i] += (float) (boom[i] * 0.05 * Math.exp(-t / 0.35) + roll[i] * 0.02 * Calls.smooth(t / 0.15) * Math.exp(-t / (0.45 * tail)));
                }
                // einzelne Echos von Ufer, Seebrücke und Häusern, weicher
                int echoes = 2 + (int) (r.uniform() * 3);
                for (int e = 0; e < echoes; e++) {
                    int ea = at + (int) ((0.25 + 1.6 * r.uniform()) * sr);
                    nwave(o, ea, tau * 3, Math.pow(10, -(14 + 10 * r.uniform()) / 20), sr);
                }
                Dsp.Biquad soft = new Dsp.Biquad().lowpass(Math.min(sr * 0.45, 9000), 0.7, sr);
                for (int i = 0; i < n; i++) o[i] = soft.process(o[i]);
                Calls.normalize(o, sr);
                return new Calls.Made("Zerlegung", level(k, cal) + lv, o);
            }
            case KNISTERN -> {
                double stars = clamp(tone, 5, 2000), len = 1.2 + 1.0 * r.uniform();
                int n = (int) ((len + 0.4) * sr);
                float[] o = new float[n];
                double rate = clamp(stars / len, 20, 600), t = 0.01;
                while (t < len) {
                    double u = t / len, a = (0.4 + 0.6 * r.uniform()) * (u < 0.3 ? Calls.smooth(u / 0.3) : 1 - 0.6 * (u - 0.3) / 0.7);
                    nwave(o, (int) (t * sr), 0.00025 + 0.0003 * r.uniform(), a * (r.uniform() < 0.5 ? 1 : -1), sr);
                    t += r.exp(rate * (u < 0.5 ? 1.3 : 0.7));
                }
                Calls.normalize(o, sr);
                return new Calls.Made("Knistern", level(k, stars) + lv, o);
            }
            default -> throw new IllegalArgumentException("kein Klang der Bucht: " + k);
        }
    }

    /** Ein Schlag eines Falls an den Mast ab Probe at. */
    static void hit(float[] o, int at, boolean wood, double a, Dsp.Rng r, double sr) {
        int imp = Math.max(2, (int) (0.0005 * sr));
        Dsp.Biquad hp = new Dsp.Biquad().highpass(wood ? 900 : 2500, 0.7, sr);
        for (int i = 0; i < imp && at + i < o.length; i++) o[at + i] += (float) (hp.process(r.white()) * a * 1.6);
        if (wood) {
            double[] f = {190, 330, 520, 690};
            for (int m = 0; m < f.length; m++) Glacial.ring(o, at, f[m] * (0.9 + 0.2 * r.uniform()), 0.025 + 0.02 * r.uniform(), a * (0.9 - 0.15 * m), sr);
        } else {
            double base = 0.9 + 0.2 * r.uniform();
            double[] f = {590, 1240, 1830, 2650, 3900}, tau = {0.26, 0.18, 0.12, 0.08, 0.05}, g = {0.8, 1.0, 0.7, 0.5, 0.35};
            for (int m = 0; m < f.length; m++) Glacial.ring(o, at, f[m] * base * (0.98 + 0.04 * r.uniform()), tau[m] * (0.8 + 0.4 * r.uniform()), a * g[m] * 0.35, sr);
            // der Schäkel: ein heller Ton
            Glacial.ring(o, at, 4800 + 1500 * r.uniform(), 0.015, a * 0.25, sr);
        }
    }

    /** Ein Schlag des Tuchs ab Probe at: Knall, Klatschen, bei großen Segeln ein Wummern. */
    static void slap(float[] o, int at, double a, double big, Dsp.Rng r, double sr) {
        int m = Math.max(2, (int) ((0.0015 + 0.002 * r.uniform()) * sr));
        Dsp.Biquad hp = new Dsp.Biquad().highpass(1500, 0.7, sr), bp = new Dsp.Biquad().bandpass(380 + 120 * r.uniform(), 1.0, sr);
        int body = (int) (0.06 * sr);
        for (int i = 0; i < body && at + i < o.length; i++) {
            float w = r.white();
            double t = i / sr;
            double crack = i < m ? hp.process(w) * 2.2 : 0;
            double clap = bp.process(w) * Math.exp(-t / 0.018) * 2.0;
            o[at + i] += (float) ((crack + clap) * a);
        }
        if (big > 0.05) Glacial.ring(o, at, 95 + 30 * r.uniform(), 0.05, a * 0.9 * big, sr);
    }

    /** Eine N-Welle der Dauer tau (s), Spitze a, ab Probe at. */
    static void nwave(float[] o, int at, double tau, double a, double sr) {
        int m = Math.max(2, (int) Math.round(tau * sr));
        for (int k = 0; k < m && at + k < o.length; k++) if (at + k >= 0) o[at + k] += (float) (a * (1 - 2.0 * k / (m - 1)));
    }

    // ------------------------------------------------------------------ Betten

    /**
     * Flaggen, Takelage und Schritte; Zustand nur im Faden von {@code render}. Jeder Schlag ist ein kurzes Korn aus einem
     * kleinen Vorrat, gemischt mit Richtung, 1/r und einem Tiefpass nach Abstand.
     */
    static final class Bed {
        private int sr;
        private long seed;
        private Dsp.Rng rng;
        private final float[][] flagSlap = new float[6][], alu = new float[8][], wood = new float[4][];
        private final float[][][] step = new float[3][6][];
        private double flagDb = 0, aluDb = 86, woodDb = 78;
        private final java.util.IdentityHashMap<Flap, double[]> flapState = new java.util.IdentityHashMap<>();
        private final List<Grain> grains = new ArrayList<>();
        /** Gezählt seit dem Start (Anzeige, Prüfung). */
        volatile long flagSlaps, rigSlaps, steps;

        static final class Grain {
            float[] d;
            int pos, delay;
            float gl, gr;
            final Dsp.OnePole lp = new Dsp.OnePole();
            boolean steps;
        }

        void configure(long seed, int sr) {
            this.sr = sr;
            this.seed = seed;
            rng = new Dsp.Rng(seed * 6271 + 15);
            java.util.Arrays.fill(flagSlap, null);
            java.util.Arrays.fill(alu, null);
            java.util.Arrays.fill(wood, null);
            for (float[][] s : step) java.util.Arrays.fill(s, null);
            flapState.clear();
            grains.clear();
        }

        private float[] flag(int i) {
            if (flagSlap[i] == null) {
                Dsp.Rng r = new Dsp.Rng(seed * 31 + i * 7 + 3);
                float[] o = new float[(int) (0.09 * sr)];
                slap(o, 0, 1, 0, r, sr);
                Calls.normalize(o, sr);
                flagSlap[i] = o;
            }
            return flagSlap[i];
        }

        private float[] rig(boolean w, int i) {
            float[][] c = w ? wood : alu;
            if (c[i] == null) c[i] = make(Soundscape.Kind.FALL, 1, w ? 1 : 0, seed * 97 + i * 13 + (w ? 5 : 0), sr).data();
            return c[i];
        }

        /** Ein Schritt auf Pflaster (0), Holzdeck (1) oder Sand (2). */
        private float[] stepGrain(int surf, int i) {
            if (step[surf][i] == null) {
                Dsp.Rng r = new Dsp.Rng(seed * 53 + surf * 101 + i);
                float[] o = new float[(int) (0.16 * sr)];
                switch (surf) {
                    case 0 -> {
                        for (int k = 0; k < (int) (0.0006 * sr); k++) o[k] += r.white() * 1.2f;
                        Glacial.ring(o, 0, 1800 + 1700 * r.uniform(), 0.004, 0.8, sr);
                        Glacial.ring(o, 0, 600 + 300 * r.uniform(), 0.01, 0.3, sr);
                        int sc = (int) ((0.05 + 0.03 * r.uniform()) * sr);
                        Dsp.Biquad hp = new Dsp.Biquad().highpass(2500, 0.7, sr);
                        for (int k = 0; k < (int) (0.04 * sr) && sc + k < o.length; k++) o[sc + k] += (float) (hp.process(r.white()) * 0.25 * Math.sin(Math.PI * k / (0.04 * sr)));
                    }
                    case 1 -> {
                        for (int k = 0; k < (int) (0.0008 * sr); k++) o[k] += r.white() * 0.6f;
                        Glacial.ring(o, 0, 140 + 60 * r.uniform(), 0.05, 1.2, sr);
                        Glacial.ring(o, 0, 240 + 60 * r.uniform(), 0.035, 0.8, sr);
                        Glacial.ring(o, 0, 850 + 250 * r.uniform(), 0.012, 0.4, sr);
                    }
                    default -> {
                        int n = (int) (0.09 * sr);
                        double t = 0;
                        while (t < 0.09) {
                            int at = (int) (t * sr);
                            Glacial.ring(o, at, 3000 + 5000 * r.uniform(), 0.0006, 0.4 * Math.sin(Math.PI * t / 0.09), sr);
                            t += r.exp(900);
                        }
                        Dsp.Biquad bp = new Dsp.Biquad().bandpass(500, 0.8, sr);
                        for (int k = 0; k < n; k++) o[k] += (float) (bp.process(r.white()) * 0.2 * Math.sin(Math.PI * k / n));
                    }
                }
                Calls.normalize(o, sr);
                step[surf][i] = o;
            }
            return step[surf][i];
        }

        /** Ein Korn mischen: Daten d mit Pegel lv in 1 m an (x, y, z). */
        private void add(float[] d, double lv, double x, double y, double z, double ex, double ey, double ez, double yaw, double c, Soundscape s, boolean isStep, double lead) {
            double dist = Math.max(1, Math.sqrt(Soundscape.sq(x - ex) + Soundscape.sq(y - ey) + Soundscape.sq(z - ez)));
            double atEar = lv - 20 * Math.log10(dist) - s.obstructionDb(dist) + s.carryDb(x, z, dist);
            if (atEar < 12 || grains.size() > 160) return;
            float[] g = Soundscape.pan(x, z, ex, ez, yaw);
            Grain gr = new Grain();
            gr.d = d;
            double a = Dsp.amp(atEar);
            gr.gl = (float) (a * g[0]);
            gr.gr = (float) (a * g[1]);
            gr.lp.cutoff(Math.min(sr * 0.45, s.airHz(dist) * (g[2] < 0 ? 0.6 : 1)), sr);
            gr.delay = (int) ((dist / c + lead) * sr);
            gr.steps = isStep;
            grains.add(gr);
        }

        /** Je Block: Flaggen, Takelage und Schritte einplanen und mischen. Gibt {Energie Tuch und Takelage, Energie Schritte}. */
        double[] render(Soundscape s, float[] oL, float[] oR, int n, double dt, double ex, double ey, double ez, double yaw, double c, double gust) {
            // Flaggen: die nächsten acht in 120 m
            List<Flap> fl = s.flaps;
            if (!fl.isEmpty()) {
                Flap[] near = new Flap[8];
                double[] nd = new double[8];
                int cnt = 0;
                for (Flap f : fl) {
                    double d = Math.sqrt(Soundscape.sq(f.x - ex) + Soundscape.sq(f.y - ey) + Soundscape.sq(f.z - ez));
                    if (d > 120 || f.area <= 0) continue;
                    if (cnt < 8) { near[cnt] = f; nd[cnt++] = d; continue; }
                    int w = 0;
                    for (int k = 1; k < 8; k++) if (nd[k] > nd[w]) w = k;
                    if (d < nd[w]) { near[w] = f; nd[w] = d; }
                }
                if (flapState.size() > 64) flapState.clear();
                for (int k = 0; k < cnt; k++) {
                    Flap f = near[k];
                    double u = s.windAt(f.x, f.y, f.z) * (1 + gust);
                    double[] st = flapState.computeIfAbsent(f, q -> new double[]{rng.uniform()});
                    if (u < 2 || f.strength <= 0) continue;
                    double hz = Double.isNaN(f.hz) ? Flap.frequency(u, f.area) : f.hz;
                    st[0] -= dt * hz;
                    while (st[0] <= 0) {
                        double lead = Math.max(0, -st[0] / hz);
                        st[0] += 0.8 + 0.4 * rng.uniform();
                        double lv = 66 + 10 * Math.log10(Math.max(0.05, f.area)) + 50 * Math.log10(u / 8) + 10 * Math.log10(Math.max(0.01, f.strength)) + 2 * rng.gauss();
                        add(flag((int) (rng.uniform() * flagSlap.length)), lv, f.x, f.y, f.z, ex, ey, ez, yaw, c, s, false, lead);
                        flagSlaps++;
                    }
                }
            }
            // Takelage: Fallen im Hafen
            for (Rig rg : s.rigs) {
                double d0 = Math.hypot(rg.x - ex, rg.z - ez);
                if (d0 > 800 + rg.radius || rg.masts < 1) continue;
                double u = s.windAt(rg.x, rg.y + 8, rg.z) * (1 + gust);
                double rate = rg.masts * (Math.max(0, 0.015 * (u - 2.5)) + 0.01 * Math.min(3, Math.max(0, s.waveHeight) / 0.3)) * rg.strength;
                double expect = rate * dt;
                int k = (int) Math.floor(expect);
                if (rng.uniform() < expect - k) k++;
                for (int j = 0; j < k; j++) {
                    double ang = Dsp.TAU * rng.uniform(), rad = rg.radius * Math.sqrt(rng.uniform());
                    double x = rg.x + Math.cos(ang) * rad, z = rg.z + Math.sin(ang) * rad, y = rg.y + 3 + 8 * rng.uniform();
                    boolean w = rng.uniform() < rg.woodShare;
                    double lv = (w ? 78 : 86) + 6 * Math.log10(Math.max(0.1, 0.3 + 0.7 * rng.uniform())) + 2 * rng.gauss();
                    add(rig(w, (int) (rng.uniform() * (w ? wood.length : alu.length))), lv, x, y, z, ex, ey, ez, yaw, c, s, false, dt * rng.uniform());
                    rigSlaps++;
                }
            }
            // Schritte um das Ohr
            double wk = Math.max(0, s.walkers);
            if (wk > 0.05) {
                int surf = Math.max(0, Math.min(2, s.footing));
                double expect = wk * 1.8 * dt;
                int k = (int) Math.floor(expect);
                if (rng.uniform() < expect - k) k++;
                double base = surf == 1 ? 68 : surf == 2 ? 52 : 62;
                for (int j = 0; j < k; j++) {
                    double ang = Dsp.TAU * rng.uniform(), rad = 1.5 + 13.5 * Math.sqrt(rng.uniform());
                    double x = ex + Math.cos(ang) * rad, z = ez + Math.sin(ang) * rad, y = ey - 1.6;
                    add(stepGrain(surf, (int) (rng.uniform() * 6)), base + 2.5 * rng.gauss(), x, y, z, ex, ey, ez, yaw, c, s, true, dt * rng.uniform());
                    steps++;
                }
            }
            // mischen
            double eRig = 0, eStep = 0;
            for (int gi = 0; gi < grains.size(); gi++) {
                Grain g = grains.get(gi);
                int start = 0;
                if (g.delay >= n) { g.delay -= n; continue; }
                if (g.delay > 0) { start = g.delay; g.delay = 0; }
                double e = 0;
                for (int i = start; i < n && g.pos < g.d.length; i++) {
                    float v = g.lp.process(g.d[g.pos++]);
                    float l = v * g.gl, r = v * g.gr;
                    oL[i] += l;
                    oR[i] += r;
                    e += 0.5 * (l * l + r * r);
                }
                if (g.steps) eStep += e; else eRig += e;
                if (g.pos >= g.d.length) { grains.remove(gi); gi--; }
            }
            return new double[]{eRig, eStep};
        }
    }
}
