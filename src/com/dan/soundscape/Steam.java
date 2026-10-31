package com.dan.soundscape;

/**
 * Seit Brocken Phase 15: eine Dampflok mit ihrem Zug als dauernde Quelle. Die App setzt je Bild, wo der Schornstein ist
 * ({@link #x}, {@link #y}, {@link #z}) und welcher Punkt des Zugs dem Ohr am nächsten ist ({@link #rx}, {@link #ry},
 * {@link #rz}, {@link #nearest} m hinter der Spitze), dazu Fahrt, Auspuffschläge je s, Last, Bremsen und ob sie steht.
 * Alles erzeugt nach Gehör und Lautbeschreibungen [G]:
 * <ul>
 * <li><b>Auspuff</b> am Schornstein: je Schlag ein Stoß aus Rauschen (Mitte 350 Hz, mit der Last heller bis 1,25 kHz),
 * der nach 0,5 / Schläge s abklingt; vier Schläge je Umdrehung, nicht ganz gleich laut. Unter Volllast
 * {@link #chuffDb} in 1 m (112 dB), ohne Dampf 16 dB leiser (der Zug rollt, die Zylinder atmen nur). Beim Anfahren
 * (unter 2 m/s mit Last) zischen die Zylinderhähne mit.</li>
 * <li><b>Rollen</b> der Räder: tiefes Rauschen, {@link #rollDb} in 1 m bei 30 km/h (83 dB), +30 dB je Zehnfaches der
 * Fahrt.</li>
 * <li><b>Schienenstöße</b>: Jede Achse schlägt an jedem Stoß der {@link #jointLength} m langen Schienen (15 m); nahe
 * am Ohr klingt das „ta-tamm ta-tamm“ der Drehgestelle, ferne Achsen leiser.</li>
 * <li><b>Bremsen</b>: unter 5 m/s quietschen die Klötze (2,8 bis 3,6 kHz); <b>Kurven</b> ({@link #squeal}) kreischen
 * die Spurkränze (4 bis 6 kHz).</li>
 * <li><b>Stehend</b>: das Zischen des Hilfsbläsers, die Luftpumpe („tschak … tschak“, in Folgen von 20 bis 40 s), ab
 * und zu bläst das Sicherheitsventil ab (6 bis 15 s, um 112 dB in 1 m).</li>
 * </ul>
 * Pegel mischt {@code Soundscape} nach Abstand, Luft und Wind; dieser Teil heißt dort „Maschine“.
 */
public final class Steam {
    public final String name;
    /** Schornstein (m). */
    public volatile double x, y, z;
    /** Der Punkt des Zugs, der dem Ohr am nächsten ist (m), und wie weit er hinter der Spitze liegt (m). */
    public volatile double rx, ry, rz, nearest;
    /** Fahrt (m/s), Auspuffschläge je s, Last 0..1, Bremsen 0..1, Kurvenkreischen 0..1. */
    public volatile double speed, beats, load, braking, squeal;
    public volatile boolean standing = true;
    /** Zahl der Wagen hinter der Lok, Länge der Schienen (m). */
    public volatile int wagons = 5;
    public volatile double jointLength = 15;
    /** Pegel in 1 m (dB): Auspuff unter Volllast, Rollen bei 30 km/h. */
    public volatile double chuffDb = 112, rollDb = 83;

    public Steam(String name) { this.name = name; }

    // ------------------------------------------------------------------ Zustand (Faden des Mischers)
    private Dsp.Rng rng;
    private Dsp.Pink pink;
    private Dsp.Brown brown;
    private final Dsp.Biquad chuffBp = new Dsp.Biquad(), chuffLp = new Dsp.Biquad(), cockHp = new Dsp.Biquad(), blowHp = new Dsp.Biquad(),
            pumpBp = new Dsp.Biquad(), valveBp = new Dsp.Biquad(), rollLp = new Dsp.Biquad(), rollBp = new Dsp.Biquad(), airC = new Dsp.Biquad(), airR = new Dsp.Biquad();
    private final Soundscape.Pings clacks = new Soundscape.Pings();
    private double beatPh, chuffT = 9, chuffA, travelled, pumpT, pumpOn, pumpPhase, valveT = -1, valveLen, squealF = 3200, squealAm, curveF = 5000;
    private int beat;
    private int builtSr;
    private double[] axles = new double[0];
    private long[] axleJoint = new long[0];
    private static final double[] PATTERN = {1, 0.82, 0.95, 0.76};

    /** Lage der Achsen (m hinter der Spitze): Lok 1'E1' mit sieben Achsen auf 12,6 m, Wagen mit zwei Drehgestellen auf 14 m [G]. */
    static double[] axles(int wagons) {
        double[] lok = {1.2, 2.9, 4.1, 5.3, 6.5, 7.7, 10.4};
        double[] wg = {1.6, 3.4, 10.6, 12.4};
        double[] a = new double[lok.length + wagons * wg.length];
        int i = 0;
        for (double v : lok) a[i++] = v;
        for (int w = 0; w < wagons; w++) for (double v : wg) a[i++] = 12.6 + 14 * w + v;
        return a;
    }

    private void build(int sr, long seed) {
        rng = new Dsp.Rng(seed * 5003 + name.hashCode());
        pink = new Dsp.Pink(seed * 17 + 3);
        brown = new Dsp.Brown(seed * 19 + 7);
        cockHp.highpass(1800, 0.7, sr);
        blowHp.highpass(3200, 0.7, sr);
        pumpBp.bandpass(900, 1.4, sr);
        valveBp.bandpass(2600, 0.6, sr);
        rollBp.bandpass(1100, 0.8, sr);
        clacks.clear();
        builtSr = sr;
    }

    /**
     * Addiert n Werte: am Schornstein mit Richtung gc und Verstärkung ac (für 104 dB in 1 m an der Quelle), am Zug mit
     * gr, ar; Luft als Tiefpass je Ort. Gibt die Energie zurück.
     */
    double render(float[] L, float[] R, int n, int sr, long seed, float[] gc, double ac, double airHzC, float[] gr, double ar, double airHzR) {
        if (builtSr != sr) build(sr, seed);
        int wg = Math.max(0, Math.min(12, wagons));
        if (axles.length != 7 + 4 * wg) {
            axles = axles(wg);
            axleJoint = new long[axles.length];
            for (int i = 0; i < axles.length; i++) axleJoint[i] = (long) Math.floor((travelled - axles[i]) / Math.max(1, jointLength));
        }
        double dt = n / (double) sr;
        double v = Math.max(0, speed), b = Math.max(0, beats), ld = Math.max(0, Math.min(1, load));
        boolean st = standing || v < 0.05;
        airC.lowpass(airHzC, 0.6, sr);
        airR.lowpass(airHzR, 0.6, sr);
        // Auspuff: Lautstärke und Farbe nach der Last
        double chuffLv = chuffDb + 20 * Math.log10(0.16 + 0.84 * ld);
        float aChuff = st ? 0 : (float) (ac * Dsp.amp(chuffLv) * 1.35);
        chuffBp.bandpass(350 + 900 * ld, 0.7, sr);
        chuffLp.lowpass(160, 0.7, sr);
        double tau = Math.max(0.03, Math.min(0.18, 0.5 / Math.max(0.5, b)));
        double sustain = 0.22 * Calls.smooth((b - 3) / 6);   // schnelle Schläge verschmelzen zu einem Rauschen
        float cock = !st && v < 2 && ld > 0.4 ? (float) (0.9 * (1 - v / 2)) : 0;
        // Rollen
        double vr = Math.max(0.01, v);
        float aRoll = v < 0.3 ? 0 : (float) (ar * Dsp.amp(rollDb + 30 * Math.log10(vr / 8.33)) * 2.43 * Calls.smooth((v - 0.3) / 1.2));
        rollLp.lowpass(500 + 45 * v, 0.6, sr);
        // Bremsen und Kurven
        squealAm += -squealAm * dt / 0.3 + 0.6 * Math.sqrt(2 * dt / 0.3) * rng.gauss();
        squealF += (3200 + 400 * Math.sin(travelled * 0.7) - squealF) * Math.min(1, dt * 2) + 30 * rng.gauss();
        curveF += (5000 + 900 * Math.sin(travelled * 0.21) - curveF) * Math.min(1, dt) + 40 * rng.gauss();
        double br = Math.max(0, Math.min(1, braking));
        float aBrake = br > 0.05 && v > 0.15 && v < 5 ? (float) (ar * Dsp.amp(96 + 10 * Math.log10(br)) * Calls.smooth((5 - v) / 2) * Math.max(0, Math.min(1.5, 0.6 + squealAm))) : 0;
        double sq = Math.max(0, Math.min(1, squeal));
        float aCurve = sq > 0.02 && v > 1 ? (float) (ar * Dsp.amp(98 + 10 * Math.log10(sq)) * Math.max(0, Math.min(1.5, 0.5 + squealAm))) : 0;
        // Stehend: Bläser, Luftpumpe, Sicherheitsventil
        float aBlow = st ? (float) (ac * Dsp.amp(80) * 1.6) : 0;
        if (st) {
            pumpT -= dt;
            if (pumpT <= 0) { pumpOn = pumpOn > 0 ? 0 : 1; pumpT = pumpOn > 0 ? 20 + 20 * rng.uniform() : 15 + 40 * rng.uniform(); }
            if (valveT < 0 && rng.uniform() < dt / 240) { valveT = 0; valveLen = 6 + 9 * rng.uniform(); }
        } else pumpOn = 0;
        float aPump = pumpOn > 0 ? (float) (ac * Dsp.amp(90) * 1.75 / (0.577 * Math.sqrt(Math.PI * 900 / (2 * 1.4) / (sr / 2.0)))) : 0;
        float aValve = 0;
        if (valveT >= 0) {
            valveT += dt;
            double env = Calls.smooth(valveT / 0.6) * Calls.smooth((valveLen - valveT) / 1.5);
            aValve = (float) (ac * Dsp.amp(112) * 1.8 * env);
            if (valveT > valveLen) valveT = -1;
        }
        double sqPh = 0, cuPh = 0;
        double e = 0;
        for (int i = 0; i < n; i++) {
            // Schläge
            if (!st && b > 0.05) {
                beatPh += b / sr;
                if (beatPh >= 1) {
                    beatPh -= 1;
                    beat++;
                    chuffT = 0;
                    chuffA = PATTERN[beat & 3] * (0.88 + 0.24 * rng.uniform());
                }
            }
            chuffT += 1.0 / sr;
            double env = chuffA * (1 - Math.exp(-chuffT / 0.004)) * Math.exp(-chuffT / tau) + sustain;
            float w = rng.white();
            float c = 0;
            if (aChuff > 0) c += (float) ((chuffBp.process(w) * 1.6f + chuffLp.process(brown.next()) * 1.2f) * env * aChuff);
            if (cock > 0) c += cockHp.process(rng.white()) * (float) ((0.5 + env) * cock * aChuff * 0.9);
            if (aBlow > 0) c += blowHp.process(rng.white()) * aBlow * 0.6f;
            if (aPump > 0) {
                pumpPhase += 1.0 / (0.7 * sr);
                if (pumpPhase >= 1) pumpPhase -= 1;
                double pe = Math.exp(-pumpPhase * 0.7 / 0.07);
                c += pumpBp.process(rng.white()) * (float) (pe * aPump);
            }
            if (aValve > 0) c += (valveBp.process(rng.white()) * 1.4f + pink.next() * 0.4f) * aValve;
            float rr = 0;
            if (aRoll > 0) rr += (rollLp.process(pink.next()) * 1.3f + rollBp.process(pink.next()) * 0.25f) * aRoll;
            if (aBrake > 0) { sqPh += squealF / sr; rr += (float) Math.sin(Dsp.TAU * sqPh) * aBrake; }
            if (aCurve > 0) { cuPh += curveF / sr; rr += (float) Math.sin(Dsp.TAU * cuPh) * aCurve; }
            c = airC.process(c);
            rr = airR.process(rr);
            L[i] += c * gc[0] + rr * gr[0];
            R[i] += c * gc[1] + rr * gr[1];
            e += 0.5 * ((c * gc[0] + rr * gr[0]) * (c * gc[0] + rr * gr[0]) + (c * gc[1] + rr * gr[1]) * (c * gc[1] + rr * gr[1]));
        }
        // Schienenstöße: jede Achse am Stoß, gewichtet nach dem Abstand zur Stelle am Ohr
        travelled += v * dt;
        if (v > 0.3) {
            double jl = Math.max(1, jointLength), a0 = ar * Dsp.amp(rollDb + 6 + 20 * Math.log10(vr / 8.33)) * 2.4;
            for (int k = 0; k < axles.length; k++) {
                long j = (long) Math.floor((travelled - axles[k]) / jl);
                if (j == axleJoint[k]) continue;
                axleJoint[k] = j;
                double wgt = 1 / (1 + Math.pow((axles[k] - nearest) / 7, 2));
                if (wgt < 0.04) continue;
                double a = a0 * wgt * (k < 7 ? 1.3 : 1);
                clacks.one(170 + 90 * rng.uniform(), 0.022, a, gr[0], gr[1], rng, sr);
                clacks.one(1500 + 1200 * rng.uniform(), 0.005, a * 0.45, gr[0], gr[1], rng, sr);
            }
        } else for (int k = 0; k < axles.length; k++) axleJoint[k] = (long) Math.floor((travelled - axles[k]) / Math.max(1, jointLength));
        e += clacks.render(L, R, n, sr);
        return e;
    }

    /** Wie oft Räder an Schienenstößen geschlagen haben (je Schlag zwei Töne, für Prüfungen). */
    public long clacks() { return clacks.spawned; }
}
