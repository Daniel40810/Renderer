package com.dan.soundscape;

/**
 * Seit Brocken Phase 15: Klänge eines Mittelgebirges mit Bergbahn, beim Auslösen einmal gerechnet wie {@link Calls}
 * und {@link Wadden}. Alles erzeugt, nach Gehör und Lautbeschreibungen gebaut [G], keine Aufnahmen; Pegel gelten für die
 * lautesten 100 ms in 1 m.
 * <ul>
 * <li><b>Dampfpfeife</b>: ein heller Glockenton (Grundton 560 Hz, dazu Oktave und Duodezime), der mit einem Zischen
 * anspringt, sich in 0,1 s auf seine Höhe zieht und beim Schließen etwas absinkt; tone = Dauer (s, 0 = 1,6 s).
 * 118 dB in 1 m.</li>
 * <li><b>Kolkrabe</b>: tiefes, sonores „krok“ (Grundton um 380 Hz, rau) ein- bis dreimal, manchmal ein klingendes
 * „klong“. 95 dB.</li>
 * <li><b>Ringdrossel</b>: zwei bis vier gleiche, laute Flötentöne „tjü-tjü-tjü“ (2,6 bis 3,4 kHz, fallend), oft eine
 * zweite Reihe in anderer Höhe; schwach (unter 0,4) nur das harte „tack“. 90 dB.</li>
 * <li><b>Wiesenpieper</b>: der Singflug, eine schneller werdende Reihe dünner „zip“ (4 → 5,5 kHz) im Steigen, dann im
 * Fallen gezogene „zia“ (6 → 3,5 kHz) und ein Triller; schwach nur „ist ist“. 84 dB.</li>
 * <li><b>Hausrotschwanz</b>: ein gepresstes „jirr-titititi“, Pause, dann das Knirschen wie zerknülltes Papier und
 * einige Töne. 86 dB.</li>
 * <li><b>Buchfink</b>: die fallende Strophe (4,8 → 3,2 kHz), schneller werdend, mit dem Schnörkel „tschi-u-iit“ am
 * Ende. 90 dB.</li>
 * <li><b>Fichtenkreuzschnabel</b>: „gip gip“ im Flug (3 bis 3,6 kHz); tone = Zahl der Vögel. 80 + 6 · lg(Vögel) dB.</li>
 * <li><b>Wintergoldhähnchen</b>: sehr hohes „sri-sri-sri“ (6,8 bis 7,2 kHz) mit kurzem Triller. 76 dB.</li>
 * <li><b>Raufußkauz</b>: nachts eine Reihe von fünf bis neun weichen „pu“ (650 bis 720 Hz), steigend und lauter
 * werdend. 88 dB.</li>
 * <li><b>Eis</b>: ein Stück Raueis fällt von Mast, Geländer oder Ast: dumpfer Aufschlag in den Schnee, dann
 * springende, klirrende Splitter (2 bis 7 kHz), schwere Stücke brechen zweimal; tone = Masse (kg, 0 = 0,5 kg).
 * 88 + 10 · lg(kg / 0,5) dB, 70 bis 104.</li>
 * <li><b>Lachen</b>, <b>Kreischen</b>, <b>Trommel</b>: die Stimmen der Walpurgisnacht (die Sage, kein Messwert):
 * ein meckerndes „hihihi-hahaha“ (600 → 380 Hz), ein gezogener, hoher Schrei (900 → 1 600 → 700 Hz) und zwei Takte
 * einer Rahmentrommel (zwei Takte zu vier Vierteln) („DUM tak tak DUM-DUM tak“, 55 bis 70 Hz); tone bei der Trommel = Schläge je min (0 = 108).
 * 92, 100 und 118 dB in 1 m.</li>
 * </ul>
 * Nur Java 21.
 */
final class Highland {
    private Highland() { }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Calls.Note n(double t, double len, double f0, double f1, double f2, double peak, double noise, double amHz, double amD, double vibHz, double vibD, double att, double rel, double amp) {
        return new Calls.Note(t, len, f0, f1, f2, peak, noise, amHz, amD, vibHz, vibD, att, rel, amp);
    }

    private static Calls.Formant fm(double f, double q, double g) { return new Calls.Formant(f, q, g); }

    static Calls.Made make(Soundscape.Kind k, double strength, double tone, long seed, double sr) {
        Dsp.Rng r = new Dsp.Rng(seed);
        double s = clamp(strength, 0, 1), lv = 6 * Math.log10(Math.max(0.05, s));
        switch (k) {
            case PFEIFE -> {
                double len = tone > 0.1 ? clamp(tone, 0.2, 8) : 1.6, f = 560 * (0.99 + 0.02 * r.uniform());
                Calls.Note[] ns = {
                        n(0, 0.14, f * 0.8, f * 0.8, f * 0.8, 0.5, 0.92, 0, 0, 0, 0, 0.01, 0.04, 0.7),
                        n(0.07, len, f * 0.83, f, f * 0.96, Math.min(0.5, 0.12 / len), 0.14, 0, 0, 5.5, 0.003, 0.05, 0.22, 1)};
                float[] o = Calls.synth(ns, new Calls.Formant[]{fm(f, 7, 1.2), fm(2 * f, 6, 0.55), fm(3 * f, 6, 0.3), fm(3400, 0.9, 0.35)}, 0.75, 1.1, 250, sr, seed);
                return new Calls.Made("Dampfpfeife", 118 + lv, o);
            }
            case KOLKRABE -> {
                if (r.uniform() < 0.3) {
                    double f = 500 + 80 * r.uniform();
                    Calls.Note[] ns = {n(0, 0.16, f, f * 1.12, f * 0.92, 0.3, 0.05, 0, 0, 0, 0, 0.01, 0.06, 1)};
                    return new Calls.Made("Kolkrabe (klong)", 95 + lv, Calls.synth(ns, new Calls.Formant[]{fm(1500, 4, 1.2), fm(3000, 4, 0.4)}, 0.6, 1.0, 250, sr, seed));
                }
                int m = 1 + (int) (r.uniform() * 3);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double f = 340 + 80 * r.uniform();
                    ns[i] = n(t, 0.18 + 0.1 * r.uniform(), f, f * 1.12, f * 0.86, 0.3, 0.22, 55 + 15 * r.uniform(), 0.35, 0, 0, 0.012, 0.06, 1);
                    t += 0.45 + 0.25 * r.uniform();
                }
                return new Calls.Made("Kolkrabe", 95 + lv, Calls.synth(ns, new Calls.Formant[]{fm(1100, 3, 1.3), fm(2300, 3, 0.7), fm(520, 2, 0.6)}, 0.3, 0.9, 180, sr, seed));
            }
            case RINGDROSSEL -> {
                if (s < 0.4) {
                    int m = 2 + (int) (r.uniform() * 4);
                    Calls.Note[] ns = new Calls.Note[m];
                    for (int i = 0; i < m; i++) ns[i] = n(i * (0.2 + 0.08 * r.uniform()), 0.03, 2100, 2300, 1900, 0.3, 0.5, 0, 0, 0, 0, 0.002, 0.012, 1);
                    return new Calls.Made("Ringdrossel (tack)", 84, Calls.synth(ns, new Calls.Formant[]{fm(2300, 1.5, 1.2)}, 0.4, 1.0, 900, sr, seed));
                }
                int rows = 1 + (r.uniform() < 0.6 ? 1 : 0);
                java.util.List<Calls.Note> l = new java.util.ArrayList<>();
                double t = 0;
                for (int row = 0; row < rows; row++) {
                    int m = 2 + (int) (r.uniform() * 3);
                    double f = 2600 + 800 * r.uniform();
                    for (int i = 0; i < m; i++) {
                        l.add(n(t, 0.17 + 0.03 * r.uniform(), f * 1.05, f, f * 0.84, 0.25, 0.03, 0, 0, 0, 0, 0.012, 0.05, 1));
                        t += 0.3 + 0.04 * r.uniform();
                    }
                    t += 0.35;
                }
                return new Calls.Made("Ringdrossel", 90 + lv, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(3000, 1.6, 1.0)}, 0.9, 2.0, 1200, sr, seed));
            }
            case WIESENPIEPER -> {
                java.util.List<Calls.Note> l = new java.util.ArrayList<>();
                if (s < 0.4) {
                    int m = 2 + (int) (r.uniform() * 2);
                    for (int i = 0; i < m; i++) l.add(n(i * 0.16, 0.05, 6200, 6400, 5800, 0.3, 0.06, 0, 0, 0, 0, 0.004, 0.02, 1));
                    return new Calls.Made("Wiesenpieper (ist)", 76, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(6200, 2, 1.0)}, 0.8, 2.0, 2500, sr, seed));
                }
                double t = 0, gap = 0.15;
                int up = 12 + (int) (r.uniform() * 8), down = 10 + (int) (r.uniform() * 6);
                for (int i = 0; i < up; i++) {
                    double f = 4000 + 1500 * i / (double) up;
                    l.add(n(t, 0.04, f * 0.95, f * 1.05, f, 0.5, 0.04, 0, 0, 0, 0, 0.003, 0.015, 0.7 + 0.3 * i / up));
                    t += 0.04 + gap;
                    gap = Math.max(0.035, gap * 0.92);
                }
                t += 0.1;
                for (int i = 0; i < down; i++) {
                    double f = 6000 - 2500 * i / (double) down;
                    l.add(n(t, 0.085, f, f * 0.97, f * 0.88, 0.3, 0.05, 0, 0, 0, 0, 0.006, 0.03, 1));
                    t += 0.13;
                }
                for (int i = 0; i < 8; i++) { l.add(n(t, 0.028, 4100, 4200, 3900, 0.4, 0.05, 0, 0, 0, 0, 0.003, 0.01, 0.8)); t += 0.045; }
                return new Calls.Made("Wiesenpieper", 84 + lv, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(5000, 1.2, 1.0)}, 0.8, 2.0, 2000, sr, seed));
            }
            case HAUSROTSCHWANZ -> {
                java.util.List<Calls.Note> l = new java.util.ArrayList<>();
                l.add(n(0, 0.32, 3500, 4000, 3300, 0.3, 0.2, 70, 0.5, 0, 0, 0.01, 0.05, 1));
                double t = 0.36;
                for (int i = 0; i < 4; i++) { l.add(n(t, 0.04, 4600, 4800, 4400, 0.4, 0.05, 0, 0, 0, 0, 0.003, 0.012, 0.8)); t += 0.07; }
                t += 0.35 + 0.2 * r.uniform();
                l.add(n(t, 0.5, 5000, 5200, 4800, 0.5, 0.95, 90, 0.8, 0, 0, 0.02, 0.1, 0.8));
                t += 0.55;
                for (int i = 0; i < 3; i++) { l.add(n(t, 0.05, 4300, 4500, 4100, 0.4, 0.05, 0, 0, 0, 0, 0.003, 0.012, 0.8)); t += 0.09; }
                return new Calls.Made("Hausrotschwanz", 86 + lv, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(4600, 1.0, 1.0)}, 0.7, 1.6, 1500, sr, seed));
            }
            case BUCHFINK -> {
                java.util.List<Calls.Note> l = new java.util.ArrayList<>();
                int m = 6 + (int) (r.uniform() * 4);
                double t = 0, gap = 0.11;
                for (int i = 0; i < m; i++) {
                    double f = 4800 - 1600 * i / (double) m;
                    l.add(n(t, 0.055, f * 1.04, f, f * 0.93, 0.3, 0.04, 0, 0, 0, 0, 0.004, 0.015, 0.8 + 0.2 * i / m));
                    t += gap;
                    gap = Math.max(0.075, gap * 0.95);
                }
                for (int i = 0; i < 3; i++) { l.add(n(t, 0.045, 3100, 3300, 2900, 0.4, 0.06, 0, 0, 0, 0, 0.003, 0.012, 1)); t += 0.07; }
                l.add(n(t + 0.02, 0.26, 3600, 5200, 2600, 0.35, 0.04, 0, 0, 0, 0, 0.01, 0.05, 1));
                return new Calls.Made("Buchfink", 90 + lv, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(4000, 1.3, 1.0)}, 0.8, 1.8, 1500, sr, seed));
            }
            case KREUZSCHNABEL -> {
                int birds = (int) clamp(Math.round(tone), 1, 10);
                float[][] parts = new float[birds][];
                double[] at = new double[birds];
                for (int b = 0; b < birds; b++) {
                    int m = 2 + (int) (r.uniform() * 4);
                    Calls.Note[] ns = new Calls.Note[m];
                    double t = 0, f = 3000 + 600 * r.uniform();
                    for (int i = 0; i < m; i++) { ns[i] = n(t, 0.045, f, f * 1.18, f * 0.9, 0.3, 0.05, 0, 0, 0, 0, 0.003, 0.015, 1); t += 0.18 + 0.12 * r.uniform(); }
                    parts[b] = Calls.synth(ns, new Calls.Formant[]{fm(3300, 1.6, 1.0)}, 0.8, 1.8, 1200, sr, seed + b);
                    at[b] = b == 0 ? 0 : r.uniform() * 0.8;
                }
                return new Calls.Made("Fichtenkreuzschnabel", 80 + 6 * Math.log10(birds) + lv, Wadden.chorus(parts, at, sr));
            }
            case WINTERGOLDHAEHNCHEN -> {
                java.util.List<Calls.Note> l = new java.util.ArrayList<>();
                int m = 5 + (int) (r.uniform() * 3);
                double t = 0;
                for (int i = 0; i < 2 * m; i++) { double f = i % 2 == 0 ? 7200 : 6800; l.add(n(t, 0.06, f, f * 1.02, f * 0.97, 0.4, 0.04, 0, 0, 0, 0, 0.004, 0.02, 0.9)); t += 0.095; }
                for (int i = 0; i < 6; i++) { double f = 6500 - 180 * i; l.add(n(t, 0.03, f, f, f * 0.97, 0.5, 0.05, 0, 0, 0, 0, 0.003, 0.01, 1)); t += 0.045; }
                return new Calls.Made("Wintergoldhähnchen", 76 + lv, Calls.synth(l.toArray(new Calls.Note[0]), new Calls.Formant[]{fm(7000, 2, 1.0)}, 0.8, 2.2, 3000, sr, seed));
            }
            case RAUFUSSKAUZ -> {
                int m = 5 + (int) (r.uniform() * 5);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double f = 650 + 70 * i / (double) m + 15 * r.uniform();
                    ns[i] = n(t, 0.12, f * 0.97, f, f * 0.98, 0.4, 0.03, 0, 0, 0, 0, 0.02, 0.05, 0.6 + 0.4 * i / (m - 1.0));
                    t += 0.27;
                }
                return new Calls.Made("Raufußkauz", 88 + lv, Calls.synth(ns, new Calls.Formant[]{fm(700, 4, 1.0)}, 0.9, 2.5, 200, sr, seed));
            }
            case EIS -> {
                double kg = tone > 0.001 ? clamp(tone, 0.02, 50) : 0.5;
                float[] o = ice(kg, r, sr);
                return new Calls.Made("Eis fällt", clamp(88 + 10 * Math.log10(kg / 0.5), 70, 104) + lv, o);
            }
            case LACHEN -> {
                int m = 6 + (int) (r.uniform() * 7);
                Calls.Note[] ns = new Calls.Note[m];
                double t = 0;
                for (int i = 0; i < m; i++) {
                    double u = i / (m - 1.0), f = 600 - 220 * u + 30 * r.uniform();
                    ns[i] = n(t, 0.09, f * 1.08, f, f * 0.85, 0.2, 0.35, 0, 0, 0, 0, 0.008, 0.035, 1 - 0.3 * u);
                    t += 0.125 + 0.02 * r.uniform();
                }
                return new Calls.Made("Lachen", 92 + lv, Calls.synth(ns, new Calls.Formant[]{fm(820, 5, 1.4), fm(1250, 5, 1.0), fm(2600, 4, 0.5)}, 0.2, 0.9, 200, sr, seed));
            }
            case KREISCHEN -> {
                double len = 0.8 + 0.6 * r.uniform();
                Calls.Note[] ns = {n(0, len, 900, 1600, 700, 0.35, 0.3, 0, 0, 7, 0.03, 0.08, 0.3, 1)};
                return new Calls.Made("Kreischen", 100 + lv, Calls.synth(ns, new Calls.Formant[]{fm(2500, 3, 1.2), fm(1200, 3, 0.6)}, 0.3, 1.0, 400, sr, seed));
            }
            case TROMMEL -> {
                double bpm = tone > 10 ? clamp(tone, 60, 200) : 108;
                return new Calls.Made("Trommel", 118 + lv, drum(bpm, r, sr));
            }
            default -> throw new IllegalArgumentException(k.name());
        }
    }

    /** Ein fallendes Stück Eis: Aufschlag, springende Splitter, bei schweren Stücken ein zweiter Bruch. */
    static float[] ice(double kg, Dsp.Rng r, double sr) {
        double len = 0.9 + 0.25 * Math.log10(1 + kg * 4);
        float[] o = new float[(int) (len * sr)];
        Dsp.Biquad thump = new Dsp.Biquad().lowpass(380 + 600 / (1 + kg), 0.7, sr);
        Dsp.Brown br = new Dsp.Brown(r.next());
        int hits = kg > 1.5 ? 2 : 1;
        for (int h = 0; h < hits; h++) {
            double t0 = h * (0.06 + 0.05 * r.uniform()), a = h == 0 ? 1 : 0.6;
            int i0 = (int) (t0 * sr);
            for (int i = 0; i < (int) (0.09 * sr) && i0 + i < o.length; i++) {
                double t = i / sr, env = (1 - Math.exp(-t / 0.002)) * Math.exp(-t / 0.025);
                o[i0 + i] += (float) (thump.process(br.next() * 3f + (float) r.white() * 0.4f) * env * 2.5 * a);
            }
        }
        // Splitter: abklingende Töne, die mit kürzer werdenden Abständen springen
        int shards = 6 + (int) (Math.min(10, 4 * Math.log10(1 + kg * 20)) * (0.6 + 0.6 * r.uniform()));
        double t = 0.015;
        for (int k = 0; k < shards; k++) {
            double f = 2000 * Math.pow(3.5, r.uniform()), dec = 0.012 + 0.04 * r.uniform(), a = 0.5 * (0.4 + r.uniform()) * Math.exp(-t / 0.4);
            double f2 = f * (1.47 + 0.3 * r.uniform());
            int i0 = (int) (t * sr);
            for (int i = 0; i < (int) (dec * 7 * sr) && i0 + i < o.length; i++) {
                double tt = i / sr, env = Math.exp(-tt / dec);
                o[i0 + i] += (float) (a * env * (Math.sin(Dsp.TAU * f * tt) + 0.5 * Math.sin(Dsp.TAU * f2 * tt) + 0.3 * r.white() * Math.exp(-tt / 0.002)));
            }
            t += 0.02 + 0.12 * r.uniform() * Math.exp(-k / 5.0);
        }
        Calls.normalize(o, sr);
        return o;
    }

    /** Zwei Takte einer Rahmentrommel (zwei Takte zu vier Vierteln): tiefe Schläge in der Mitte des Fells, helle am Rand. */
    static float[] drum(double bpm, Dsp.Rng r, double sr) {
        double beat = 60 / bpm;
        // ein Takt aus vier Vierteln: DUM, tak, tak, DUM, DUM, tak
        double[] at = {0, 1, 1.5, 2, 2.5, 3};
        boolean[] low = {true, false, false, true, true, false};
        float[] o = new float[(int) ((8 * beat + 1.2) * sr)];
        Dsp.Biquad skin = new Dsp.Biquad().bandpass(320, 1.2, sr), rim = new Dsp.Biquad().bandpass(1400, 1.5, sr);
        for (int bar = 0; bar < 2; bar++)
            for (int k = 0; k < at.length; k++) {
                double t0 = (bar * 4 + at[k]) * beat + 0.012 * r.gauss();
                int i0 = (int) (Math.max(0, t0) * sr);
                double a = (low[k] ? 1 : 0.45) * (0.85 + 0.3 * r.uniform()) * (k == 0 ? 1.15 : 1);
                double f = low[k] ? 66 + 6 * r.uniform() : 210 + 30 * r.uniform();
                double dec = low[k] ? 0.32 : 0.07, ph = 0;
                for (int i = 0; i < (int) ((dec * 5) * sr) && i0 + i < o.length; i++) {
                    double t = i / sr, env = (1 - Math.exp(-t / 0.002)) * Math.exp(-t / dec);
                    double fi = f * (1 + 0.35 * Math.exp(-t / 0.03));   // das Fell zieht die Tonhöhe beim Anschlag hinauf
                    ph += fi / sr;
                    float noise = (float) r.white() * (float) Math.exp(-t / 0.03);
                    o[i0 + i] += (float) (a * (env * Math.sin(Dsp.TAU * ph) + (low[k] ? skin : rim).process(noise) * 1.6));
                }
            }
        Calls.normalize(o, sr);
        return o;
    }
}
