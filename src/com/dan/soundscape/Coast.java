package com.dan.soundscape;

/**
 * Seit Sylt Phase 14: der Klang einer Sandküste an der offenen See. Alles erzeugt, nach Gehör und Beschreibungen gebaut
 * [G], keine Aufnahmen. Zwei Teile:
 * <p>
 * <b>Ereignisse</b> (beim Auslösen einmal gerechnet wie {@link Calls}, {@link Glacial} und {@link Wadden}; Pegel jeweils
 * der lautesten 100 ms in 1 m):
 * <ul>
 * <li><b>Schwallbrecher</b> ({@code SCHWALLBRECHER}, tone = Brecherhöhe m): der Kamm kippt nicht, er schäumt über. Ein
 * Zischen, das in einer halben Sekunde anschwillt und mit der Walze zum Strand rollt (Band um 1,6 kHz), darunter ein
 * Rauschen unter 300 Hz und das Prickeln der Blasen (2 bis 7 kHz). 2 bis 7 s. 102 + 20 · lg(H / 1 m) dB, 84 bis 122: so laut wie die
 * 50 m Kamm, für die ein Ereignis steht, in der fernen Brandung wären (Spitze über dem Mittel).</li>
 * <li><b>Sturzbrecher</b> ({@code STURZBRECHER}, tone = Höhe m): die Lippe schlägt in das Wasser vor dem Kamm. Ein dumpfer
 * Schlag (die eingeschlossene Luft, um 70 Hz fallend auf 28 Hz), ein kurzes Krachen um 450 Hz, das Dröhnen, dann das
 * Zischen der Walze und die Gischt. 112 + 20 · lg(H / 1 m) dB, 94 bis 132.</li>
 * <li><b>Schwall</b> ({@code SCHWALL}, tone = Auflaufhöhe m über dem Ruhewasser): das Wasser rauscht den Strand hinauf
 * (Band um 900 Hz), steht einen Augenblick und läuft zurück. Der Rücklauf über Sand zischt hell (über 2,5 kHz) und
 * knistert dicht von platzenden Blasen, die der Sand ausatmet; kein Klappern wie über Kies. 78 + 14 · lg(h / 0,2 m)
 * dB, 60 bis 98.</li>
 * <li><b>Sanderling</b> ({@code SANDERLING}, tone = Vögel): kurzes, flüssiges „plit“ oder „twick“ um 4,2 kHz, ein- bis
 * viermal; im Trupp durcheinander. 82 + 10 · lg(Vögel) dB, höchstens 96.</li>
 * <li><b>Abbruch</b> ({@code ABBRUCH}, tone = Volumen m³): die Grasnarbe reißt (dichte Folge kleiner Knackse, 1,5 bis
 * 4,5 kHz), der Lehm bricht (dumpfere Risse, 300 bis 1 200 Hz), die Masse rutscht und grollt (unter 90 Hz), schlägt auf
 * den Strand (35 → 22 Hz, Bröckeln) und rieselt nach. 112 + 8 · lg(V / 100 m³) dB, 96 bis 130.</li>
 * <li><b>Erdbrocken</b> ({@code ERDBROCKEN}, tone = Volumen m³): ein Klumpen Lehm schlägt auf Sand, dumpf und ohne
 * Klang (60 bis 110 Hz), bröckelt, Sand spritzt. 92 + 10 · lg(V / 0,1 m³) dB, 70 bis 115.</li>
 * <li><b>Rieseln</b> ({@code RIESELN}, tone = Dauer s): Sand rinnt eine Wand hinab, Zischen über 2,5 kHz mit einzelnen
 * Körnern. 58 + 12 · Stärke dB.</li>
 * </ul>
 * <b>Betten</b> (dauernd, in {@link Soundscape} je Block gerechnet):
 * <ul>
 * <li><b>Ferne Brandung</b> ({@link Soundscape#surfLine}): die Brecherzone als Linie aus Stücken, jedes mit seiner
 * Schallleistung je m Küste. Akustisch strahlt eine Brandung einen sehr kleinen Teil der Leistung ab, die in ihr
 * vergeht: {@link #EFFICIENCY} = 3·10⁻⁷ [G, so gewählt, dass eine Brandung mit 1 m hohen Brechern in 30 m um 73 dB klingt und
 * eine Sturmbrandung mit 3 m in 50 m um 83 dB]. Jedes Stück strahlt in den Halbraum (I = P / 2πr²); drei Bänder: Rollen
 * und Donnern unter 160 Hz (mehr bei Sturzbrechern), Rauschen um 700 Hz, Zischen über 2,2 kHz (mehr bei
 * Schwallbrechern). Die Luft nimmt die Höhen (0,02 dB/m über 2 kHz, 0,003 um 700 Hz, 0,0005 unter 160 Hz, nach ISO
 * 9613-1 gerundet für 10 °C und 70 % Feuchte). Jedes Stück atmet im Takt seiner Wellen (die Phase wandert die Küste
 * entlang), nah hört man die Schläge, fern verschwimmen sie zum Grollen.</li>
 * <li><b>Sand am Ohr</b> ({@link Soundscape#sandFlux}, kg/(m·s)): Flugsand prasselt; Zischen über 2,5 kHz und Ticks der
 * Körner (3 bis 10 kHz). 60 + 10 · lg(q / 0,05) dB am Ohr [G].</li>
 * <li><b>Strandhafer</b> ({@link Soundscape#marramNear} 0..1): steife Halme rascheln und schlagen aneinander, heller und
 * trockener als Schilf (Band um 5,5 kHz, dazu Ticks). 16 + 46 · lg(U) + 10 · lg(Anteil) dB.</li>
 * </ul>
 * Nur Java 21.
 */
final class Coast {
    private Coast() { }

    /** Anteil der Leistung, die in einer Brandung vergeht und als Schall abstrahlt [G]. */
    static final double EFFICIENCY = 3e-7;

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ Ereignisse

    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = clamp(strength, 0, 1), lv = 6 * Math.log10(Math.max(0.05, s));
        switch (k) {
            case SCHWALLBRECHER -> {
                double h = clamp(tone, 0.1, 4), len = 2.2 + 1.6 * clamp(h, 0.2, 3);
                int n = (int) ((len + 1.5) * sr);
                float[] hiss = unit(filtered(new Dsp.Pink(seed + 1)::next, new Dsp.Biquad().bandpass(1500 + 300 * r.uniform(), 0.45, sr), n));
                float[] body = unit(filtered(new Dsp.Brown(seed + 2)::next, new Dsp.Biquad().lowpass(300, 0.7, sr), n));
                float[] splash = unit(filtered(r::white, new Dsp.Biquad().bandpass(800, 0.8, sr), n));
                float[] o = new float[n];
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    double env = Calls.smooth(t / 0.5) * (t < len * 0.6 ? 1 : Math.exp(-(t - len * 0.6) / (0.25 * len)));
                    double sp = t < 0.25 ? (1 - t / 0.25) * 0.5 : 0;
                    o[i] = (float) (hiss[i] * env + body[i] * 0.4 * env + splash[i] * sp);
                }
                bubbles(o, r, 0.15, len * 1.1, 260 * clamp(h, 0.3, 2), 2000, 7000, 0.25, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Schwallbrecher", clamp(102 + 20 * Math.log10(h), 84, 122) + lv, o);
            }
            case STURZBRECHER -> {
                double h = clamp(tone, 0.1, 5), tail = 2 + 1.2 * clamp(h, 0.2, 3);
                int n = (int) ((tail + 1.5) * sr);
                float[] crack = unit(filtered(r::white, new Dsp.Biquad().bandpass(420 + 80 * r.uniform(), 0.7, sr), n));
                float[] boom = unit(filtered(new Dsp.Brown(seed + 2)::next, new Dsp.Biquad().lowpass(120, 0.8, sr), n));
                float[] hiss = unit(filtered(new Dsp.Pink(seed + 1)::next, new Dsp.Biquad().bandpass(1500, 0.45, sr), n));
                float[] spray = unit(filtered(r::white, new Dsp.Biquad().highpass(3000, 0.7, sr), n));
                float[] o = new float[n];
                double ph = 0, f1 = 55 + 25 / Math.max(0.5, h);
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    double f = 28 + (f1 - 28) * Math.exp(-t * 9);
                    ph += Dsp.TAU * f / sr;
                    double thump = Math.sin(ph) * (1 - Math.exp(-t * 120)) * Math.exp(-t / 0.22) * 3.2;
                    double bm = boom[i] * Math.exp(-t / 0.7) * 1.6;
                    double cr = crack[i] * (1 - Math.exp(-t * 300)) * Math.exp(-t / 0.07) * 1.4;
                    double t2 = Math.max(0, t - 0.05);
                    double ww = hiss[i] * Calls.smooth(t2 / 0.3) * Math.exp(-t2 / (0.45 * tail)) * 0.55;
                    double sy = spray[i] * Math.exp(-t2 / 0.4) * 0.3 * Calls.smooth(t2 / 0.05);
                    o[i] = (float) (thump + bm + cr + ww + sy);
                }
                bubbles(o, r, 0.1, tail, 320, 2000, 7000, 0.2, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Sturzbrecher", clamp(112 + 20 * Math.log10(h), 94, 132) + lv, o);
            }
            case SCHWALL -> {
                double h = clamp(tone, 0.03, 2.5), big = Math.min(1, h / 0.8);
                double up = 0.8 + 1.2 * big, back = 1.5 + 2.2 * big, len = up + 0.3 + back;
                int n = (int) ((len + 0.6) * sr);
                float[] rush = unit(filtered(new Dsp.Pink(seed + 1)::next, new Dsp.Biquad().bandpass(850 + 200 * r.uniform(), 0.7, sr), n));
                float[] low = unit(filtered(new Dsp.Brown(seed + 2)::next, new Dsp.Biquad().lowpass(280, 0.7, sr), n));
                float[] hiss = unit(filtered(r::white, new Dsp.Biquad().highpass(2500, 0.7, sr), n));
                float[] suck = unit(filtered(r::white, new Dsp.Biquad().bandpass(380, 1.2, sr), n));
                float[] o = new float[n];
                for (int i = 0; i < n; i++) {
                    double t = i / sr;
                    double eu = Calls.smooth(t / (0.4 * up)) * (t < up ? 1 : Math.exp(-(t - up) / 0.25));
                    double tb = t - up - 0.2, eb = tb < 0 ? 0 : Calls.smooth(tb / 0.4) * Math.exp(-tb / (0.45 * back));
                    double am = 0.7 + 0.3 * Math.sin(Dsp.TAU * 14 * t + 3 * Math.sin(Dsp.TAU * 0.9 * t));
                    o[i] = (float) (rush[i] * eu + low[i] * eu * 0.35 + hiss[i] * eb * 0.5 + suck[i] * eb * am * 0.12);
                }
                bubbles(o, r, up + 0.1, len, 900 * (0.4 + 0.6 * big), 2500, 9000, 0.2, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Schwall", clamp(78 + 14 * Math.log10(h / 0.2), 60, 98) + lv, o);
            }
            case SANDERLING -> {
                int birds = (int) clamp(Math.round(tone), 1, 10);
                float[][] parts = new float[birds][];
                double[] at = new double[birds];
                for (int b = 0; b < birds; b++) {
                    int m = 1 + (int) (r.uniform() * 4);
                    Calls.Note[] ns = new Calls.Note[m];
                    double t = 0, f = 3900 + 700 * r.uniform();
                    for (int i = 0; i < m; i++) {
                        ns[i] = new Calls.Note(t, 0.035 + 0.02 * r.uniform(), f * 1.06, f * 1.1, f * 0.82, 0.25, 0.06, 0, 0, 0, 0, 0.003, 0.012, 0.85 + 0.15 * r.uniform());
                        t += 0.09 + 0.07 * r.uniform();
                    }
                    parts[b] = Calls.synth(ns, new Calls.Formant[]{new Calls.Formant(4300, 2, 1.2)}, 0.7, 1.6, 1500, sr, seed + b * 17);
                    at[b] = b == 0 ? 0 : 0.6 * r.uniform();
                }
                float[] o = birds == 1 ? parts[0] : Wadden.chorus(parts, at, sr);
                return new Calls.Made(birds == 1 ? "Sanderling" : "Sanderlinge", Math.min(96, 82 + 10 * Math.log10(birds)) + lv, o);
            }
            case ABBRUCH -> {
                double v = Math.max(1, tone), big = clamp(Math.log10(v / 10) / 3, 0, 1);
                double slide = 2 + 3 * big, fall = 0.8 + 1.2 + big, trickle = 4 + 3 * big, total = fall + trickle + 1;
                float[] o = new float[(int) (total * sr)];
                // die Grasnarbe reißt: dichte Folge kleiner Knackse, schneller werdend
                double t = 0.02;
                while (t < 0.9) {
                    int at = (int) (t * sr);
                    double a = 0.25 + 0.35 * r.uniform();
                    int imp = (int) (0.0008 * sr);
                    for (int i = 0; i < imp && at + i < o.length; i++) o[at + i] += (float) (r.white() * a * 1.5);
                    Glacial.ring(o, at, 1500 + 3000 * r.uniform(), 0.004 + 0.004 * r.uniform(), a * 0.6, sr);
                    t += 1 / (20 + 70 * t / 0.9) * (0.5 + r.uniform());
                }
                // der Lehm bricht: wenige dumpfe Risse
                int cracks = 3 + (int) (r.uniform() * 4);
                for (int c = 0; c < cracks; c++) {
                    int at = (int) ((0.15 + 0.6 * r.uniform()) * sr);
                    for (int m = 0; m < 3; m++) Glacial.ring(o, at, 300 + 900 * r.uniform(), 0.02 + 0.02 * r.uniform(), 0.7, sr);
                    Glacial.ring(o, at, 80 + 40 * r.uniform(), 0.06, 0.5, sr);
                }
                // die Masse rutscht und grollt, dann schlägt sie auf, bröckelt und rieselt nach
                float[] rumble = unit(filtered(new Dsp.Brown(seed + 3)::next, new Dsp.Biquad().lowpass(90, 0.8, sr), o.length));
                float[] hiss = unit(filtered(new Dsp.Pink(seed + 4)::next, new Dsp.Biquad().highpass(3000, 0.7, sr), o.length));
                double ou = 0, ph = 0;
                for (int i = 0; i < o.length; i++) {
                    double tt = i / sr;
                    ou += -ou / (0.25 * sr) + Math.sqrt(2.0 / (0.25 * sr)) * r.gauss();
                    double er = tt < 0.5 ? 0 : Calls.smooth((tt - 0.5) / 0.8) * Math.exp(-Math.max(0, tt - 0.5 - slide) / 0.8) * (tt < fall + 1.5 ? 1 : Math.exp(-(tt - fall - 1.5) / 0.6));
                    double ti = tt - fall, thud = 0;
                    if (ti >= 0) {
                        double f = 22 + 13 * Math.exp(-ti * 3);
                        ph += Dsp.TAU * f / sr;
                        thud = Math.sin(ph) * (1 - Math.exp(-ti * 60)) * Math.exp(-ti / (0.45 + 0.4 * big)) * 2.6;
                    }
                    double et = ti < 0 ? 0 : Calls.smooth(ti / 0.6) * Math.exp(-ti / (0.4 * trickle));
                    o[i] += (float) (rumble[i] * er * (1 + 0.5 * Math.tanh(ou)) * 1.6 + thud + hiss[i] * et * 0.25);
                }
                // Bröckeln beim Aufschlag, Körner im Rieseln
                pings(o, r, fall, fall + 1.6, 160 * (0.5 + big), 200, 900, 0.008, 0.03, 0.9, sr);
                bubbles(o, r, fall + 0.3, total - 0.2, 300, 3000, 9000, 0.12, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Abbruch", clamp(112 + 8 * Math.log10(v / 100), 96, 130) + lv, o);
            }
            case ERDBROCKEN -> {
                double v = Math.max(0.001, tone), big = clamp(Math.log10(v / 0.01) / 3, 0, 1);
                float[] o = new float[(int) (1.2 * sr)];
                double f = 110 - 50 * big, ph = 0, dec = 0.12 + 0.2 * big;
                Dsp.Biquad spray = new Dsp.Biquad().highpass(2500, 0.7, sr), thump = new Dsp.Biquad().lowpass(400, 0.7, sr);
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr;
                    ph += Dsp.TAU * f * (1 + 0.4 * Math.exp(-t * 30)) / sr;
                    float w = r.white();
                    o[i] = (float) (Math.sin(ph) * (1 - Math.exp(-t * 200)) * Math.exp(-t / dec) * 2.0 + thump.process(w) * Math.exp(-t / 0.05) * 1.2
                            + spray.process(w) * Calls.smooth(t / 0.02) * Math.exp(-t / 0.25) * 0.3);
                }
                pings(o, r, 0.01, 0.35, 120, 300, 1500, 0.004, 0.015, 0.6, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Erdbrocken", clamp(92 + 10 * Math.log10(v / 0.1), 70, 115) + lv, o);
            }
            case RIESELN -> {
                double len = clamp(tone, 0.5, 20);
                float[] o = new float[(int) ((len + 0.3) * sr)];
                float[] hiss = unit(filtered(new Dsp.Pink(seed + 1)::next, new Dsp.Biquad().highpass(2500, 0.7, sr), o.length));
                for (int i = 0; i < o.length; i++) {
                    double t = i / sr;
                    double env = Calls.smooth(t / 0.3) * (t < 0.6 * len ? 1 : Math.exp(-(t - 0.6 * len) / (0.2 * len)));
                    o[i] = (float) (hiss[i] * env);
                }
                bubbles(o, r, 0.05, len, 400, 3000, 9000, 0.6, sr);
                Calls.normalize(o, sr);
                return new Calls.Made("Rieseln", 58 + 12 * s, o);
            }
            default -> throw new IllegalArgumentException("kein Klang der Küste: " + k);
        }
    }

    /** n Werte einer Quelle durch ein Filter (die ersten 2 048 zum Einschwingen verworfen). */
    static float[] filtered(java.util.function.DoubleSupplier src, Dsp.Biquad f, int n) {
        for (int i = 0; i < 2048; i++) f.process((float) src.getAsDouble());
        float[] o = new float[n];
        for (int i = 0; i < n; i++) o[i] = f.process((float) src.getAsDouble());
        return o;
    }

    /** Auf Effektivwert 1 über die ganze Länge. */
    static float[] unit(float[] x) {
        double e = 0;
        for (float v : x) e += v * v;
        float g = (float) (1 / Math.sqrt(Math.max(1e-20, e / Math.max(1, x.length))));
        for (int i = 0; i < x.length; i++) x[i] *= g;
        return x;
    }

    /** Dichtes Prickeln (Blasen, Körner): kurze Eigentöne zwischen f0 und f1 Hz, rate je s, von t0 bis t1, Amplitude a. */
    static void bubbles(float[] o, Dsp.Rng r, double t0, double t1, double rate, double f0, double f1, double a, double sr) {
        pings(o, r, t0, t1, rate, f0, f1, 0.0004, 0.0016, a, sr);
    }

    /** Kurze abklingende Töne (Abklingzeit d0 bis d1 s) mit Poisson-Abständen; dichter am Anfang, dünner am Ende. */
    static void pings(float[] o, Dsp.Rng r, double t0, double t1, double rate, double f0, double f1, double d0, double d1, double a, double sr) {
        double t = t0;
        while (true) {
            double u = (t - t0) / Math.max(1e-6, t1 - t0);
            double rt = rate * Math.max(0.05, 1 - 0.8 * u);
            t += r.exp(rt);
            if (t >= t1) break;
            int at = (int) (t * sr);
            if (at >= o.length) break;
            Glacial.ring(o, at, f0 * Math.pow(f1 / f0, r.uniform()), d0 + (d1 - d0) * r.uniform(), a * (0.3 + 0.9 * r.uniform()) * (1 - 0.6 * u), sr);
        }
    }

    // ------------------------------------------------------------------ Betten

    /** Ferne Brandung, Sand am Ohr, Strandhafer; Zustand nur im Faden von {@code render}. */
    static final class Bed {
        private Dsp.Brown lowL, lowR;
        private Dsp.Pink midL, midR, hiL, hiR, sandL, sandR, grassL, grassR;
        private final Dsp.Biquad[] lowLp = new Dsp.Biquad[2], midBp = new Dsp.Biquad[2], hiHp = new Dsp.Biquad[2], hiLp = new Dsp.Biquad[2],
                sandHp = new Dsp.Biquad[2], sandPk = new Dsp.Biquad[2], grassHp = new Dsp.Biquad[2], grassPk = new Dsp.Biquad[2];
        private float nLow = 1, nMid = 1, nHi = 1, nSand = 1, nGrass = 1;
        private final double[] gain = new double[6], target = new double[6];
        private final Soundscape.Pings grains = new Soundscape.Pings(), blades = new Soundscape.Pings();
        private Dsp.Rng rng;
        private double group = 1, grassAm;
        private int sr;
        /** Für Anzeige und Prüfung: Pegel der fernen Brandung am Ohr (dB) und der Bänder (Rollen, Rauschen, Zischen). */
        volatile double surfDb = -99, lowDb = -99, midDb = -99, hiDb = -99, nearest = Double.NaN;

        void configure(long seed, int sr) {
            this.sr = sr;
            rng = new Dsp.Rng(seed * 4099 + 21);
            lowL = new Dsp.Brown(seed + 101); lowR = new Dsp.Brown(seed + 102);
            midL = new Dsp.Pink(seed + 103); midR = new Dsp.Pink(seed + 104);
            hiL = new Dsp.Pink(seed + 105); hiR = new Dsp.Pink(seed + 106);
            sandL = new Dsp.Pink(seed + 107); sandR = new Dsp.Pink(seed + 108);
            grassL = new Dsp.Pink(seed + 109); grassR = new Dsp.Pink(seed + 110);
            for (int k = 0; k < 2; k++) {
                lowLp[k] = new Dsp.Biquad().lowpass(160, 0.7, sr);
                midBp[k] = new Dsp.Biquad().bandpass(700, 0.6, sr);
                hiHp[k] = new Dsp.Biquad().highpass(2200, 0.7, sr);
                hiLp[k] = new Dsp.Biquad().lowpass(sr * 0.45, 0.6, sr);
                sandHp[k] = new Dsp.Biquad().highpass(2500, 0.7, sr);
                sandPk[k] = new Dsp.Biquad().peaking(Math.min(6000, sr * 0.4), 0.9, 6, sr);
                grassHp[k] = new Dsp.Biquad().highpass(2200, 0.7, sr);
                grassPk[k] = new Dsp.Biquad().peaking(Math.min(5500, sr * 0.4), 0.8, 7, sr);
            }
            // Normierung: jedes Band mit Effektivwert 1 (einmal an eigenen Generatoren gemessen)
            nLow = norm(new Dsp.Brown(seed + 201)::next, new Dsp.Biquad().lowpass(160, 0.7, sr), null);
            nMid = norm(new Dsp.Pink(seed + 202)::next, new Dsp.Biquad().bandpass(700, 0.6, sr), null);
            nHi = norm(new Dsp.Pink(seed + 203)::next, new Dsp.Biquad().highpass(2200, 0.7, sr), null);
            nSand = norm(new Dsp.Pink(seed + 204)::next, new Dsp.Biquad().highpass(2500, 0.7, sr), new Dsp.Biquad().peaking(Math.min(6000, sr * 0.4), 0.9, 6, sr));
            nGrass = norm(new Dsp.Pink(seed + 205)::next, new Dsp.Biquad().highpass(2200, 0.7, sr), new Dsp.Biquad().peaking(Math.min(5500, sr * 0.4), 0.8, 7, sr));
            java.util.Arrays.fill(gain, 0);
            grains.clear();
            blades.clear();
            group = 1;
        }

        private float norm(java.util.function.DoubleSupplier src, Dsp.Biquad a, Dsp.Biquad b) {
            int n = Math.max(8192, sr);
            double e = 0;
            for (int i = 0; i < n + 2048; i++) {
                float y = a.process((float) src.getAsDouble());
                if (b != null) y = b.process(y);
                if (i >= 2048) e += y * y;
            }
            return (float) (1 / Math.sqrt(Math.max(1e-20, e / n)));
        }

        /**
         * Die ferne Brandung je Block: Bänder aus allen Stücken der Linie (Energie mit Richtung), geglättet über 50 ms.
         * Gibt die Energie (für den Teil „Brandung fern“) zurück.
         */
        double surf(float[] oL, float[] oR, int n, double dt, double time, double[][] line, double gainDb, double ex, double ey, double ez, double yaw, double level) {
            if (line == null || line.length == 0) {
                java.util.Arrays.fill(target, 0, 3, 0);
                surfDb = lowDb = midDb = hiDb = -99;
                nearest = Double.NaN;
                if (gain[0] + gain[1] + gain[2] < 1e-12) return 0;
            } else {
                group += -(group - 1) * dt / 30 + 0.3 * Math.sqrt(2 * dt / 30) * rng.gauss();
                group = clamp(group, 0.5, 1.7);
                double[] eL = new double[3], eR = new double[3];
                double dMin = Double.MAX_VALUE, gk = Math.pow(10, gainDb / 10);
                for (int i = 0; i < line.length; i++) {
                    double[] p = line[i];
                    double w = p[2], len = p.length > 5 ? p[5] : 25;
                    if (!(w > 0)) continue;
                    double dx = p[0] - ex, dz = p[1] - ez, dy = level - ey;
                    double d = Math.max(Math.max(3, len * 0.5), Math.sqrt(dx * dx + dy * dy + dz * dz));
                    dMin = Math.min(dMin, d);
                    double inten = w * len / (2 * Math.PI * d * d) * gk;
                    double pl = clamp(p[3], 0, 1), tp = Math.max(3, p.length > 4 ? p[4] : 8);
                    // Takt der Wellen: die Phase wandert die Küste entlang (schräger Einfall), dazu ein fester Versatz je Stück
                    double phi = i * len * 0.004 + 0.3 * tp * smoothHash(i * len / 300);
                    double u = ((time - phi) / tp) % 1;
                    if (u < 0) u += 1;
                    double burst = Math.exp(-u / 0.28) * (1 - Math.exp(-u / 0.02)) / 0.272;
                    double env = (0.2 + 0.8 * burst) * (d < 600 ? group : 1);
                    double sl = 0.22 + 0.33 * pl, sh = 0.33 - 0.2 * pl, sm = 1 - sl - sh;
                    double[] sh3 = {sl * Math.pow(10, -0.0005 * d / 10) * (0.6 + 0.4 * env + 0.5 * pl * (env - 1)), sm * Math.pow(10, -0.003 * d / 10) * env,
                            sh * Math.pow(10, -0.02 * d / 10) * (0.5 + 0.5 * env)};
                    float[] g = Soundscape.pan(p[0], p[1], ex, ez, yaw);
                    if (g[2] < 0) sh3[2] *= 0.5;
                    for (int b = 0; b < 3; b++) { eL[b] += inten * sh3[b] * g[0] * g[0]; eR[b] += inten * sh3[b] * g[1] * g[1]; }
                }
                // Intensität (W/m²) → Amplitude des Signals (Effektivwert 1 = 104 dB); je Ohr die Hälfte der Energie
                double ref = Math.pow(10, -Soundscape.REF_DB / 10) / 1e-12;
                double tot = 0;
                double[] bandDb = new double[3];
                for (int b = 0; b < 3; b++) {
                    target[b * 2] = Math.sqrt(eL[b] * 2 * ref);
                    target[b * 2 + 1] = Math.sqrt(eR[b] * 2 * ref);
                    tot += eL[b] + eR[b];
                    bandDb[b] = 10 * Math.log10(Math.max(1e-30, (eL[b] + eR[b]) / 1e-12));
                }
                surfDb = 10 * Math.log10(Math.max(1e-30, tot / 1e-12));
                lowDb = bandDb[0]; midDb = bandDb[1]; hiDb = bandDb[2];
                nearest = dMin;
                // die Höhen fern: Grenze nach dem Abstand des nächsten Stücks
                double cut = Math.max(2500, Math.min(sr * 0.45, 16000 / (1 + dMin / 250)));
                hiLp[0].lowpass(cut, 0.6, sr);
                hiLp[1].lowpass(cut, 0.6, sr);
            }
            double k = Math.min(1, dt / 0.05), e = 0;
            double[] g0 = gain.clone();
            for (int j = 0; j < 6; j++) gain[j] += (target[j] - gain[j]) * k;
            for (int i = 0; i < n; i++) {
                double f = (i + 1.0) / n;
                float lL = (float) (g0[0] + (gain[0] - g0[0]) * f), lR = (float) (g0[1] + (gain[1] - g0[1]) * f);
                float mL = (float) (g0[2] + (gain[2] - g0[2]) * f), mR = (float) (g0[3] + (gain[3] - g0[3]) * f);
                float hL = (float) (g0[4] + (gain[4] - g0[4]) * f), hR = (float) (g0[5] + (gain[5] - g0[5]) * f);
                float l = lowLp[0].process(lowL.next()) * nLow * lL + midBp[0].process(midL.next()) * nMid * mL + hiLp[0].process(hiHp[0].process(hiL.next())) * nHi * hL;
                float r = lowLp[1].process(lowR.next()) * nLow * lR + midBp[1].process(midR.next()) * nMid * mR + hiLp[1].process(hiHp[1].process(hiR.next())) * nHi * hR;
                oL[i] += l;
                oR[i] += r;
                e += 0.5 * (l * l + r * r);
            }
            return e;
        }

        /** Flugsand am Ohr (q in kg/(m·s)). */
        double sand(float[] oL, float[] oR, int n, double dt, double q, Soundscape.Quality qu) {
            double e = 0;
            if (q > 1e-4) {
                double lv = 60 + 10 * Math.log10(q / 0.05);
                float a = (float) (Dsp.amp(lv - 2) * nSand);
                for (int i = 0; i < n; i++) {
                    float l = sandPk[0].process(sandHp[0].process(sandL.next())) * a, r = sandPk[1].process(sandHp[1].process(sandR.next())) * a;
                    oL[i] += l;
                    oR[i] += r;
                    e += 0.5 * (l * l + r * r);
                }
                grains.spawn(Math.min(qu.drops * 6, 400 * q / 0.05), dt, 3000, Math.min(10_000, sr * 0.42), 0.0002, 0.0008, Dsp.amp(lv - 4) * 2.2, 1.0, rng, sr);
            }
            return e + grains.render(oL, oR, n, sr);
        }

        /** Strandhafer am Ohr (Anteil 0..1) im Wind u (m/s) mit der Böe. */
        double grass(float[] oL, float[] oR, int n, double dt, double m, double u, Soundscape.Quality qu) {
            double e = 0;
            grassAm += -grassAm * dt / 0.5 + 0.45 * Math.sqrt(2 * dt / 0.5) * rng.gauss();
            if (m > 0.01 && u > 1) {
                double lv = 16 + 46 * Math.log10(u) + 10 * Math.log10(m);
                float a = (float) (Dsp.amp(lv) * nGrass * Math.exp(0.5 * grassAm));
                for (int i = 0; i < n; i++) {
                    float l = grassPk[0].process(grassHp[0].process(grassL.next())) * a, r = grassPk[1].process(grassHp[1].process(grassR.next())) * a;
                    oL[i] += l;
                    oR[i] += r;
                    e += 0.5 * (l * l + r * r);
                }
                blades.spawn(Math.min(qu.drops * 2, 90 * m * u / 10 * Math.exp(0.6 * grassAm)), dt, 2500, Math.min(7000, sr * 0.42), 0.0006, 0.002, Dsp.amp(lv - 4) * 2, 1.0, rng, sr);
            }
            return e + blades.render(oL, oR, n, sr);
        }

        /** Glattes Rauschen 0..1 über x (Stützstellen je 1). */
        static double smoothHash(double x) {
            int i = (int) Math.floor(x);
            double f = x - i, a = hash(i), b = hash(i + 1);
            return a + (b - a) * f * f * (3 - 2 * f);
        }

        static double hash(int i) {
            long h = (i + 1L) * 0x9E3779B97F4A7C15L;
            h ^= h >>> 29;
            h *= 0xBF58476D1CE4E5B9L;
            h ^= h >>> 32;
            return (h >>> 11) * 0x1.0p-53;
        }
    }

    /** Schallleistung je m Küste (W/m) aus der Leistung, die in der Brandung je m Küste vergeht (W/m). */
    static double acoustic(double dissipatedWattsPerMetre) { return EFFICIENCY * Math.max(0, dissipatedWattsPerMetre); }
}
