package com.dan.weather;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Die Wetterregie: ein Zustandsautomat über die Lagen klar → aufziehend → Front → Sturm → Gewitter → abklingend →
 * aufklarend, dazu seit Phase 13 Seenebel bei ruhigem Hochdruck, seit Glacier Phase 3 der Föhnsturm, seit Rungholt
 * Phase 3 der Orkan und seit Brocken Phase 3 die Lagen des Mittelgebirges (Westlage mit Stau, Inversion, Raueislage),
 * die nur eine App mit eigenem Klima erreicht ({@link #transitions}, {@link #targets}). Er liefert nur Zahlen: Wind, Richtung, Böen, Bedeckung in Achteln, hohe Schicht, Wolkenart, Regen,
 * Sicht, Nebel, Luftdruck, Temperatur, Taupunkt, Dünung und Gewitterstärke.
 * <p>
 * <b>Zeitleiste:</b> Die Regie legt Abschnitte (Lage, Beginn, Dauer) hintereinander, frei gewürfelt (Markow-Kette mit
 * lognormalen Verweildauern) oder nach einem {@link Scenario}. Jede Lage hat Zielwerte; an den Grenzen gehen sie über
 * eine Übergangszeit weich ineinander über. Jede Größe läuft ihrem Ziel mit eigener Trägheit nach.
 * <p>
 * <b>Vorlauf:</b> Der Luftdruck folgt dem Ziel von 5 Stunden später, die Dünung dem von 3 Stunden später und die hohe
 * Schicht (Cirren) dem von 2 Stunden später. So fällt das Barometer, bevor der Sturm kommt, die Dünung läuft dem Wind
 * voraus und die Cirren ziehen vor der Front auf.
 * <p>
 * <b>Böen:</b> ein Ornstein-Uhlenbeck-Vorgang in Echtzeit (auch im Zeitraffer flackert der Wind nicht), Stärke nach
 * der Böigkeit der Lage.
 * <p>
 * <b>Handregler:</b> {@link #set} überschreibt eine Größe sofort, {@link #release()} („Regie weiter“) gibt sie zurück;
 * sie gleitet dann mit ihrer Trägheit wieder zur Regie.
 * <p>
 * Nur Java 21, keine Abhängigkeit zu anderen Packages. Die Wolkenart kommt als eigenes {@link CloudKind}, dessen Namen
 * denen von {@code com.dan.cloud.CloudType} entsprechen; die Gewitterstärke als Zahl 0..4 wie
 * {@code com.dan.lightning.Lightning.Level}.
 */
public final class Weather {

    /** Die Lagen des Automaten (Seenebel seit Phase 13, Föhnsturm seit Glacier Phase 3, Orkan seit Rungholt Phase 3, Stau, Inversion und Raueis seit Brocken Phase 3). */
    public enum Phase {
        KLAR("klar"), AUFZIEHEND("aufziehend"), FRONT("Front"), STURM("Sturm"), GEWITTER("Gewitter"), ABKLINGEND("abklingend"), AUFKLAREND("aufklarend"),
        NEBEL("Seenebel"),
        /**
         * Seit Glacier Phase 3: Föhnsturm im Lee eines Gebirges – stürmischer, trockener, warmer Fallwind, böig, klare
         * Luft mit großer Sicht, wenig tiefe Wolken (die Föhnmauer bleibt über dem Kamm). Die Standardkette erreicht
         * ihn nicht; eine App gibt ihn über {@link #transitions} frei.
         */
        FOEHN("Föhnsturm"),
        /**
         * Seit Rungholt Phase 3: Orkan aus Westnordwest über der Nordsee – Wind um 33 m/s, Böen darüber, geschlossene
         * Decke, Regen und Graupelschauer, niedrige Sicht, tiefer Druck, hohe See; der Windstau einer Sturmflut. Die
         * Standardkette erreicht ihn nicht; eine App gibt ihn über {@link #transitions} frei oder setzt ihn im Szenario.
         * Ältere Tabellen mit neun Zeilen bleiben gültig: fehlende Zeilen und Spalten nehmen die Vorgabe bzw. 0.
         */
        ORKAN("Orkan"),
        /**
         * Seit Brocken Phase 3: Westlage mit Stau – feuchte Luft vom Atlantik wird am Gebirge gehoben, eine tiefe Decke
         * liegt am Berg, der Gipfel steckt in der Wolke, Sprühregen. Die Standardkette erreicht sie nicht.
         */
        STAU("Westlage mit Stau"),
        /**
         * Seit Brocken Phase 3: Hochdruck mit Inversion – unten kalte, feuchte Luft mit Hochnebel (von oben ein
         * Nebelmeer), darüber warme, trockene Luft mit großer Sicht. Die Standardkette erreicht sie nicht.
         */
        INVERSION("Inversion"),
        /**
         * Seit Brocken Phase 3: Raueislage – Frost, Wind und unterkühlter Nebel am Berg; an allem, was im Wind steht,
         * wächst Raueis. Die Standardkette erreicht sie nicht; die freie Regie wählt sie fast nur im Winter.
         */
        RAUEIS("Raueislage");
        public final String label;
        Phase(String l) { label = l; }
    }

    /** Wolkenarten (Namen wie {@code com.dan.cloud.CloudType}). */
    public enum CloudKind { CUMULUS, STRATOCUMULUS, STRATUS, NIMBOSTRATUS, CUMULONIMBUS }

    /** Die Größen der Regie, mit Einheit, Trägheit (h) und Vorlauf (h). */
    public enum Var {
        WIND("Wind", "m/s", 0.8, 0), DIRECTION("Richtung", "°", 1.0, 0), GUST("Böigkeit", "", 0.8, 0), COVER("Bedeckung", "/8", 0.6, 0.6),
        HIGH("hohe Schicht", "", 1.2, 2), CLOUD("Wolkenart", "", 0, 0), RAIN("Regen", "mm/h", 0.3, 0), VISIBILITY("Sicht", "km", 0.8, 0),
        FOG("Nebel", "", 1.0, 0), PRESSURE("Luftdruck", "hPa", 2.0, 5), TEMPERATURE("Temperatur", "°C", 2.5, 0), HUMIDITY("Feuchte", "%", 1.0, 0),
        SWELL("Dünung", "m", 2.5, 3), THUNDER("Gewitter", "", 0, 0);
        public final String label, unit;
        public final double tauH, leadH;
        Var(String l, String u, double tau, double lead) { label = l; unit = u; tauH = tau; leadH = lead; }
    }

    static final int N = Var.values().length;

    /**
     * Zielwerte je Lage (Reihenfolge wie {@link Var}). Wind nach Beaufort-Mitten über See, Luftdruck typisch für ein
     * Tief über der Deutschen Bucht, Sicht in km, Dünung als signifikante Höhe in m.
     */
    static final double[][] TARGET = {
            //  Wind  Richt Böig Bed  hoch Wolke Regen Sicht Nebel Druck  Temp Feuchte Dünung Gewitter
            {5.0, 260, 0.14, 2.5, 0.05, 0, 0.0, 30, 0.0, 1024, 13, 72, 0.5, 0},   // klar
            {7.5, 205, 0.16, 3.5, 0.75, 0, 0.0, 22, 0.0, 1013, 13, 78, 1.4, 0},   // aufziehend
            {13.0, 225, 0.20, 8.0, 0.20, 3, 4.0, 8, 0.0, 1000, 12, 95, 2.4, 0},   // Front
            {22.0, 250, 0.22, 7.5, 0.00, 3, 6.0, 6, 0.0, 987, 11, 92, 3.4, 1},    // Sturm
            {18.0, 265, 0.30, 7.0, 0.00, 4, 14.0, 4, 0.0, 992, 10, 94, 3.0, 3},   // Gewitter
            {11.0, 295, 0.18, 5.0, 0.10, 1, 0.8, 16, 0.0, 1004, 9, 85, 2.4, 0},   // abklingend
            {6.5, 310, 0.15, 3.0, 0.10, 0, 0.0, 40, 0.0, 1015, 9, 75, 1.4, 0},    // aufklarend
            {2.5, 200, 0.08, 1.0, 0.25, 0, 0.0, 14, 0.9, 1022, 12, 99, 0.5, 0},   // Seenebel: schwacher Wind, feuchte Luft über kühlerem Wasser
            {19.0, 285, 0.36, 2.0, 0.10, 0, 0.0, 90, 0.0, 1002, 17, 35, 0.6, 0},  // Föhnsturm: böiger Fallwind, trocken, warm, große Sicht [G]
            {33.0, 295, 0.30, 8.0, 0.00, 3, 8.0, 3, 0.0, 965, 7, 96, 6.0, 1},    // Orkan aus WNW: Sturmflutwetter [G]
            {14.0, 260, 0.20, 8.0, 0.30, 2, 1.0, 12, 0.0, 1010, 11, 97, 2.0, 0},  // Westlage mit Stau: tiefe Decke, Sprühregen [G]
            {3.0, 110, 0.08, 7.5, 0.05, 2, 0.0, 25, 0.2, 1032, 10, 92, 0.4, 0},  // Inversion: Hochnebel unter warmer, trockener Luft [G]
            {12.0, 280, 0.20, 8.0, 0.00, 2, 0.1, 8, 0.3, 1016, 6, 99, 1.5, 0}};  // Raueislage: Frost, Wind, Nebel [G]

    /** Mittlere Verweildauer je Lage in Stunden (freie Regie). */
    static final double[] DWELL = {16, 6, 4, 7, 2, 5, 4, 6, 8, 10, 12, 24, 14};

    /** Übergangswahrscheinlichkeiten der freien Regie (Zeile: von, Spalte: nach). */
    static final double[][] MARKOV = {
            {0, 0.82, 0, 0, 0, 0, 0, 0.18, 0, 0},
            {0.2, 0, 0.8, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0.5, 0.2, 0.3, 0, 0, 0, 0},
            {0, 0, 0, 0, 0.4, 0.6, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
            {0.85, 0, 0, 0, 0, 0, 0, 0.15, 0, 0},
            {0.6, 0.4, 0, 0, 0, 0, 0, 0, 0, 0},
            {0.6, 0.4, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0.3, 0, 0.7, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0.5, 0.5, 0, 0, 0},     // Stau (nur über eigenes Klima erreichbar)
            {0.7, 0.3, 0, 0, 0, 0, 0, 0, 0, 0},     // Inversion
            {0, 0, 0, 0, 0, 0, 1, 0, 0, 0}};        // Raueislage

    /** Ein Abschnitt der Zeitleiste: Lage, Beginn (s seit 1970), Dauer (s), Schritt eines Szenarios oder null. */
    public record Segment(Phase phase, double t0, double dur, Scenario.Step step, int index) {
        public double t1() { return t0 + dur; }
    }

    // ------------------------------------------------------------------ Eingänge
    /** Zufallszahl für die freie Regie und die Böen. */
    public volatile long seed = 1885;
    /** Zeitzone für den Tagesgang der Temperatur. */
    public volatile ZoneId zone = ZoneId.of("Europe/Berlin");
    /** Läuft die Regie? Aus: die Zeitleiste steht, die Größen bleiben, Handregler wirken weiter. */
    public volatile boolean running = true;
    /** Größte Übergangszeit zwischen zwei Lagen in Stunden. */
    public volatile double transitionH = 1.5;
    /** Zeitkonstante der Böen in Sekunden (Echtzeit). */
    public volatile double gustTau = 6;
    /**
     * Seit Haithabu Phase 12: die Regie kennt die Jahreszeit. Monatsmittel der Lufttemperatur (12 Werte, °C, etwa aus
     * {@code com.dan.season.Climate}); die Lagen liegen dann um das Mittel des Tags statt um {@link #TARGET_MEAN}, der
     * Tagesgang ist im Winter flacher, Gewitter kommen fast nur im Sommer, Seenebel öfter im Herbst, Stürme öfter im
     * Winter. null: wie bisher (Lagen wie Mitte Oktober).
     */
    public volatile double[] climate;
    /** Abweichung des Tags vom Monatsmittel (K), etwa aus einem gewürfelten Jahr (nur mit {@link #climate}). */
    public volatile double anomaly;
    /**
     * Seit Glacier Phase 3: eine App kann das Klima der Regie ersetzen – eigene Übergänge ({@code transitions}, je Zeile
     * eine Lage, Spalten wie {@link Phase}), eigene Zielwerte ({@code targets}, je Zeile eine Lage, Spalten wie
     * {@link Var}) und eigene mittlere Verweildauern ({@code dwellHours}). null: die Werte der Deutschen Bucht. Gilt ab
     * dem nächsten Start der Regie und für alle Abschnitte, die danach gewürfelt werden.
     */
    public volatile double[][] transitions, targets;
    public volatile double[] dwellHours;
    /** Mittel der Temperaturen in {@link #TARGET} (°C), um das die Lagen ohne Jahreszeit liegen. */
    public static final double TARGET_MEAN = 11.5;

    // ------------------------------------------------------------------ Höhe (seit Brocken Phase 3)
    /**
     * Seit Brocken Phase 3: die Höhe als Eingang. {@code refHeight} ist die Höhe in m über dem Meer, für die die Werte
     * der Regie gelten (etwa die einer Wetterwarte, deren Klima in {@link #climate} steht). NaN: keine Höhe, alles wie
     * bisher. Mit Höhe rechnet {@link Snapshot} Temperatur, Wolke und Sicht in jeder anderen Höhe.
     */
    public volatile double refHeight = Double.NaN;
    /**
     * Seit Brocken Phase 3: Schichtung je Lage (Zeile wie {@link Phase}, Spalten wie die Konstanten {@code L_…}):
     * Temperaturabnahme in K je km, Wolkenunter- und -obergrenze in m über dem Meer (NaN: Basis aus Temperatur und
     * Taupunkt in der Bezugshöhe, Obergrenze aus der Wolkenart), Unter- und Obergrenze einer Inversion (NaN: keine) und
     * wie viel wärmer es über ihr ist (K). null: {@link #LAYERS}.
     */
    public volatile double[][] layers;
    public static final int L_LAPSE = 0, L_BASE = 1, L_TOP = 2, L_INV_BASE = 3, L_INV_TOP = 4, L_INV_DELTA = 5, L_N = 6;
    private static final double NA = Double.NaN;
    /** Vorgabe der Schichtung: überall 6,5 K/km, Basis aus dem Taupunkt, keine Inversion; bei Inversion Hochnebel bis 600 m [G]. */
    static final double[][] LAYERS = {
            {6.5, NA, NA, NA, NA, 0}, {6.5, NA, NA, NA, NA, 0}, {6.0, NA, NA, NA, NA, 0}, {6.0, NA, NA, NA, NA, 0}, {7.0, NA, NA, NA, NA, 0},
            {6.5, NA, NA, NA, NA, 0}, {6.5, NA, NA, NA, NA, 0}, {5.0, 0, 120, 120, 300, 3}, {9.0, NA, NA, NA, NA, 0}, {6.0, NA, NA, NA, NA, 0},
            {5.5, 400, 1600, NA, NA, 0}, {6.5, 250, 600, 600, 800, 6}, {6.0, 300, 1200, NA, NA, 0}};
    /** Gewöhnliche Dicke der Wolkenarten (m, wie com.dan.cloud.CloudType) für die Obergrenze aus der Art. */
    static final double[] KIND_THICKNESS = {900, 500, 350, 2200, 2500};

    // ------------------------------------------------------------------ Zustand
    private final List<Segment> timeline = new ArrayList<>();
    private Scenario scenario;
    private double scenarioStart = Double.NaN;
    private final double[] x = new double[N], override = new double[N];
    private double time = Double.NaN, frozenOffset;
    private double gust, gustNow;
    private Random rnd = new Random(1885);
    private Random gustRnd = new Random(7);
    /** Verlauf des Luftdrucks (alle 10 min Szenenzeit) für die Tendenz über 3 Stunden; Regen der letzten Stunde. */
    private final double[] pHist = new double[19], rHist = new double[7];
    private double histT = Double.NaN;
    private int histN;
    private volatile Snapshot snapshot;

    public Weather() {
        Arrays.fill(override, Double.NaN);
        snapshot = null;
    }

    // ------------------------------------------------------------------ Steuerung

    /** Startet die freie Regie zur Zeit t (s seit 1970) in der Lage p, die schon dwellH Stunden andauert. */
    public synchronized void startFree(double t, Phase p) {
        time = Double.NaN;   // seit Brocken Phase 16: sonst kürzt extend die neue Zeitleiste nach der alten Zeit (Sprung zurück)
        scenario = null;
        scenarioStart = Double.NaN;
        timeline.clear();
        rnd = new Random(seed);
        gustRnd = new Random(seed * 31 + 7);
        double d = dwell(p);
        timeline.add(new Segment(p, t - d * 0.3, d, null, -1));
        extend(t + 72 * 3600);
        settle(t);
    }

    /** Startet ein Szenario zur Zeit t; danach geht es frei weiter. */
    public synchronized void start(Scenario s, double t) {
        time = Double.NaN;   // seit Brocken Phase 16: ein Szenario vor der zuletzt gerechneten Zeit (Regiebuch springt zurück) bleibt ganz
        scenario = s;
        scenarioStart = t;
        timeline.clear();
        rnd = new Random(seed);
        gustRnd = new Random(seed * 31 + 7);
        double t0 = t;
        int i = 0;
        for (Scenario.Step st : s.steps()) {
            timeline.add(new Segment(st.phase(), t0, st.hours() * 3600, st, i++));
            t0 += st.hours() * 3600;
        }
        extend(t + s.hours() * 3600 + 72 * 3600);
        settle(t);
    }

    /** Das laufende Szenario oder null. */
    public synchronized Scenario scenario() { return scenario; }

    /** Beginn des laufenden Szenarios in s seit 1970 (NaN ohne Szenario). */
    public synchronized double scenarioStart() { return scenarioStart; }

    /** Die Größen, wie sie gerade stehen (Reihenfolge wie {@link Var}). */
    public synchronized double[] values() { return x.clone(); }

    /** Die Handregler (NaN = Regie). */
    public synchronized double[] handValues() { return override.clone(); }

    /** Zielwerte einer Lage (Reihenfolge wie {@link Var}), etwa für benannte Wetterlagen. */
    public static double[] target(Phase p) { return TARGET[p.ordinal()].clone(); }

    /** Handregler: Größe v sofort auf den Wert setzen und dort halten. */
    public synchronized void set(Var v, double value) {
        override[v.ordinal()] = value;
        x[v.ordinal()] = value;
        snapshot = build();
    }

    /** Einen Handregler lösen. */
    public synchronized void release(Var v) { override[v.ordinal()] = Double.NaN; snapshot = build(); }

    /** „Regie weiter“: alle Handregler lösen; die Größen gleiten mit ihrer Trägheit zurück zur Regie. */
    public synchronized void release() { Arrays.fill(override, Double.NaN); snapshot = build(); }

    /** Ist die Größe von Hand gesetzt? */
    public synchronized boolean overridden(Var v) { return !Double.isNaN(override[v.ordinal()]); }

    /** Wie viele Größen von Hand gesetzt sind. */
    public synchronized int overrides() {
        int n = 0;
        for (double o : override) if (!Double.isNaN(o)) n++;
        return n;
    }

    // ------------------------------------------------------------------ Zeit

    /**
     * Weiter bis zur Zeit t (s seit 1970, Szenenzeit) nach realDt Sekunden Echtzeit. Springt die Uhr rückwärts oder um
     * mehr als 6 Stunden, stellt sich das Wetter sofort auf die neue Zeit ein. Ohne Zeitleiste beginnt die freie Regie.
     */
    public synchronized Snapshot update(double t, double realDt) {
        if (timeline.isEmpty()) startFree(t, Phase.KLAR);
        if (!running) {
            if (!Double.isNaN(time)) frozenOffset += t - time;
            time = t;
            stepGust(realDt);
            snapshot = build();
            return snapshot;
        }
        double tt = t - frozenOffset;
        double dt = Double.isNaN(time) ? Double.NaN : t - time;
        time = t;
        extend(tt + 72 * 3600);
        if (Double.isNaN(dt) || dt < 0 || dt > 6 * 3600) settle(tt);
        else integrate(tt, dt);
        stepGust(realDt);
        history(tt);
        snapshot = build();
        return snapshot;
    }

    /** Der zuletzt gerechnete Zustand. */
    public Snapshot snapshot() { return snapshot; }

    /** Szenenzeit der Regie (ohne die Zeit, in der sie stand). */
    synchronized double regieTime() { return time - frozenOffset; }

    private void settle(double t) {
        for (Var v : Var.values()) x[v.ordinal()] = Double.isNaN(override[v.ordinal()]) ? target(v, t + v.leadH * 3600) : override[v.ordinal()];
        histT = Double.NaN;
        histN = 0;
        time = t + frozenOffset;
        snapshot = build();
    }

    private void integrate(double t, double dt) {
        // feine Schritte von höchstens 5 Minuten Szenenzeit, damit auch der Zeitraffer glatt bleibt
        int n = Math.max(1, (int) Math.ceil(dt / 300));
        double h = dt / n;
        for (int s = 1; s <= n; s++) {
            double ts = t - dt + h * s;
            for (Var v : Var.values()) {
                int i = v.ordinal();
                if (!Double.isNaN(override[i])) { x[i] = override[i]; continue; }
                double g = target(v, ts + v.leadH * 3600);
                if (v.tauH <= 0) { x[i] = g; continue; }
                double k = 1 - Math.exp(-h / (v.tauH * 3600));
                if (v == Var.DIRECTION) x[i] = norm(x[i] + angle(g - x[i]) * k);
                else if (v == Var.VISIBILITY) x[i] = Math.exp(Math.log(Math.max(0.05, x[i])) + (Math.log(Math.max(0.05, g)) - Math.log(Math.max(0.05, x[i]))) * k);
                else x[i] += (g - x[i]) * k;
            }
        }
    }

    private void stepGust(double realDt) {
        if (realDt <= 0) return;
        double k = Math.exp(-realDt / gustTau);
        gust = gust * k + Math.sqrt(1 - k * k) * gustRnd.nextGaussian();
    }

    private void history(double t) {
        if (Double.isNaN(histT) || t < histT) { histT = t; Arrays.fill(pHist, x[Var.PRESSURE.ordinal()]); Arrays.fill(rHist, x[Var.RAIN.ordinal()]); histN = 1; return; }
        while (t - histT >= 600) {
            histT += 600;
            System.arraycopy(pHist, 0, pHist, 1, pHist.length - 1);
            System.arraycopy(rHist, 0, rHist, 1, rHist.length - 1);
            pHist[0] = x[Var.PRESSURE.ordinal()];
            rHist[0] = x[Var.RAIN.ordinal()];
            histN = Math.min(pHist.length, histN + 1);
        }
    }

    // ------------------------------------------------------------------ Zeitleiste

    private double dwell(Phase p) {
        double[] dw = dwellHours;
        double m = dw != null && p.ordinal() < dw.length ? dw[p.ordinal()] : DWELL[p.ordinal()];
        return Math.max(0.75, m * Math.exp(0.35 * rnd.nextGaussian() - 0.06)) * 3600;
    }

    private void extend(double until) {
        while (timeline.get(timeline.size() - 1).t1() < until) {
            Segment last = timeline.get(timeline.size() - 1);
            double[][] mk = transitions != null && last.phase().ordinal() < transitions.length ? transitions : MARKOV;
            double[] row = seasonal(java.util.Arrays.copyOf(mk[last.phase().ordinal()], Phase.values().length), last.t1());
            double u = rnd.nextDouble(), acc = 0;
            int next = 0;
            for (int j = 0; j < row.length; j++) { acc += row[j]; if (u < acc) { next = j; break; } }
            Phase p = Phase.values()[next];
            timeline.add(new Segment(p, last.t1(), dwell(p), null, -1));
        }
        // alte Abschnitte vergessen (mehr als zwei Tage zurück), aber nie den laufenden
        while (timeline.size() > 2 && !Double.isNaN(time) && timeline.get(1).t1() < time - frozenOffset - 48 * 3600) timeline.remove(0);
    }

    /** Abschnitt zur Zeit t. */
    public synchronized Segment segmentAt(double t) {
        for (Segment s : timeline) if (t >= s.t0() && t < s.t1()) return s;
        return t < timeline.get(0).t0() ? timeline.get(0) : timeline.get(timeline.size() - 1);
    }

    /** Die Abschnitte von t0 bis t1 (für Vorschau und Kurven). */
    public synchronized List<Segment> segments(double t0, double t1) {
        extend(t1);
        List<Segment> out = new ArrayList<>();
        for (Segment s : timeline) if (s.t1() > t0 && s.t0() < t1) out.add(s);
        return out;
    }

    /**
     * Zielwert der Lage eines Abschnitts (mit den Vorgaben des Szenarios). Seit Brocken Phase 5 in der freien Regie mit
     * {@link #windYear}, {@link #windSpread} und {@link #directionSpread}.
     */
    double base(Segment s, Var v) {
        double o = s.step() != null ? s.step().value(v) : Double.NaN;
        if (!Double.isNaN(o)) return o;
        double val = targetRow(s.phase())[v.ordinal()];
        if (s.step() == null) {
            if (v == Var.WIND) val *= windFactor(s);
            else if (v == Var.DIRECTION && directionSpread > 0) val = norm(val + directionSpread * gauss(s, 2));
        }
        return val;
    }

    // ------------------------------------------------------------------ Wind über das Jahr (seit Brocken Phase 5)
    /**
     * Seit Brocken Phase 5: Faktoren auf den Wind der Lagen je Monat (12 Werte, zwischen den Monatsmitten glatt
     * verbunden), etwa damit die freie Regie den Jahresgang einer Station trifft (im Winter mehr Wind als im Sommer).
     * null: wie bisher. Szenarien bleiben unberührt.
     */
    public volatile double[] windYear;
    /**
     * Seit Brocken Phase 5: Streuung des Winds von Abschnitt zu Abschnitt (Standardabweichung des Logarithmus; 0: keine,
     * wie bisher) und der Richtung (Grad). Gewürfelt aus {@link #seed} und dem Beginn des Abschnitts: die Folge der Lagen
     * bleibt, wie sie ohne Streuung wäre. Das Mittel des Winds bleibt gleich.
     */
    public volatile double windSpread, directionSpread;

    /** Faktor auf den Wind eines freien Abschnitts: Jahresgang mal Streuung. */
    double windFactor(Segment s) {
        double f = 1;
        double[] y = windYear;
        if (y != null && y.length == 12) f *= climateAt(y, dayOfYear(s.t0() + s.dur() / 2));
        double sp = windSpread;
        if (sp > 0) f *= Math.exp(sp * gauss(s, 1) - sp * sp / 2);
        return f;
    }

    /** Eine Normalzahl je Abschnitt und Kanal k, aus seed und Beginn (verbraucht keine Zufallszahl der Regie). */
    double gauss(Segment s, int k) {
        long h = seed * 0x9E3779B97F4A7C15L ^ Double.doubleToLongBits(s.t0()) * 0xC2B2AE3D27D4EB4FL ^ k * 0x165667B19E3779F9L;
        h ^= h >>> 31; h *= 0x7FB5D329728EA185L; h ^= h >>> 27; h *= 0x94D049BB133111EBL; h ^= h >>> 31;
        double u1 = ((h >>> 11) + 0.5) * 0x1.0p-53;
        long g = h * 0xD6E8FEB86659FD93L;
        g ^= g >>> 32;
        double u2 = ((g >>> 11) + 0.5) * 0x1.0p-53;
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    /** Zielwerte einer Lage mit dem Klima der App ({@link #targets}) oder dem der Deutschen Bucht. */
    double[] targetRow(Phase p) {
        double[][] t = targets;
        return t != null && p.ordinal() < t.length && t[p.ordinal()].length == N ? t[p.ordinal()] : TARGET[p.ordinal()];
    }

    /** Seit Brocken Phase 3: Schichtung einer Lage (Spalten {@code L_…}). */
    double[] layerRow(Phase p) {
        double[][] l = layers;
        if (l != null && p.ordinal() < l.length && l[p.ordinal()] != null && l[p.ordinal()].length == L_N) return l[p.ordinal()];
        return p.ordinal() < LAYERS.length ? LAYERS[p.ordinal()] : LAYERS[0];
    }

    /** Seit Brocken Phase 3: Schichtung zur Zeit t, an den Grenzen der Lagen weich überblendet (NaN springt in der Mitte). */
    synchronized double layerAt(int col, double t) {
        int k = indexAt(t);
        Segment s = timeline.get(k);
        double val = layerRow(s.phase())[col];
        double w = Math.min(transitionH * 3600, s.dur() * 0.5);
        if (k > 0 && t < s.t0() + w / 2) {
            Segment p = timeline.get(k - 1);
            double wp = Math.min(w, Math.min(transitionH * 3600, p.dur() * 0.5));
            val = mixLayer(layerRow(p.phase())[col], val, smooth(s.t0() - wp / 2, s.t0() + wp / 2, t));
        } else if (k + 1 < timeline.size() && t > s.t1() - w / 2) {
            Segment n = timeline.get(k + 1);
            double wn = Math.min(w, Math.min(transitionH * 3600, n.dur() * 0.5));
            val = mixLayer(val, layerRow(n.phase())[col], smooth(s.t1() - wn / 2, s.t1() + wn / 2, t));
        }
        return val;
    }

    private static double mixLayer(double a, double b, double f) {
        if (Double.isNaN(a) || Double.isNaN(b)) return f < 0.5 ? a : b;
        return a + (b - a) * f;
    }

    /** Zielwert der Regie zur Zeit t, an den Grenzen weich überblendet. */
    synchronized double target(Var v, double t) {
        int k = indexAt(t);
        Segment s = timeline.get(k);
        double val = base(s, v);
        double w = Math.min(transitionH * 3600, s.dur() * 0.5);
        if (k > 0 && t < s.t0() + w / 2) {
            Segment p = timeline.get(k - 1);
            double wp = Math.min(w, Math.min(transitionH * 3600, p.dur() * 0.5));
            double f = smooth(s.t0() - wp / 2, s.t0() + wp / 2, t);
            val = mix(v, base(p, v), val, f);
        } else if (k + 1 < timeline.size() && t > s.t1() - w / 2) {
            Segment n = timeline.get(k + 1);
            double wn = Math.min(w, Math.min(transitionH * 3600, n.dur() * 0.5));
            double f = smooth(s.t1() - wn / 2, s.t1() + wn / 2, t);
            val = mix(v, val, base(n, v), f);
        }
        if (v == Var.TEMPERATURE) val += diurnal(t, base(s, Var.COVER)) + seasonShift(t, s.phase());
        return val;
    }

    // ------------------------------------------------------------------ Jahreszeit (seit Haithabu Phase 12)

    private static final int[] MONTH_DAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    /** Tag im Jahr (1..365) zur Zeit t in der Zone der Regie. */
    int dayOfYear(double t) {
        int d = Instant.ofEpochMilli((long) (t * 1000)).atZone(zone).getDayOfYear();
        return Math.min(365, d);
    }

    /** Monatsmittel am Tag doy, zwischen den Monatsmitten glatt verbunden (wie com.dan.season.Climate). */
    static double climateAt(double[] c, double doy) {
        double[] mid = new double[12];
        double acc = 0;
        for (int m = 0; m < 12; m++) { mid[m] = acc + MONTH_DAYS[m] / 2.0 + 0.5; acc += MONTH_DAYS[m]; }
        int a = 11;
        for (int m = 0; m < 12; m++) if (mid[m] <= doy) a = m;
        int b = (a + 1) % 12;
        double da = mid[a], db = mid[b] + (b < a ? 365 : 0), x = doy < da ? doy + 365 : doy;
        double f = Math.max(0, Math.min(1, (x - da) / (db - da)));
        f = 0.5 - 0.5 * Math.cos(Math.PI * f);
        return c[a] + (c[b] - c[a]) * f;
    }

    /**
     * Verschiebung der Temperatur gegenüber den Lagen (K): Klima des Tags − {@link #TARGET_MEAN} + Abweichung. Im
     * Winter dreht sich der Unterschied der Lagen um: klare Hochdrucklagen sind kalt (Ausstrahlung), Fronten und Stürme
     * bringen milde Meeresluft [G].
     */
    double seasonShift(double t, Phase p) {
        double[] c = climate;
        if (c == null || c.length != 12) return 0;
        double doy = dayOfYear(t), win = 1 - summer(doy);
        double flip = switch (p) {
            case KLAR, AUFKLAREND, NEBEL, RAUEIS -> -2.5;
            case FRONT, STURM, ABKLINGEND, FOEHN, ORKAN, STAU -> 1.5;
            default -> 0;
        };
        return climateAt(c, doy) - TARGET_MEAN + anomaly + flip * win * win;
    }

    /** Seit Glacier Phase 11: Tag im Jahr mitten im Sommer (Vorgabe 196 für den Norden, 15 für die Südhalbkugel). */
    public volatile int summerDay = 196;

    /** 1 mitten im Sommer … 0 mitten im Winter, nach {@link #summerDay}. */
    double summer(double doy) { return 0.5 + 0.5 * Math.cos(2 * Math.PI * (doy - summerDay) / 365); }

    /** 1 Mitte Juli … 0 Mitte Januar. */
    static double summerness(double doy) { return 0.5 + 0.5 * Math.cos(2 * Math.PI * (doy - 196) / 365); }

    /** Übergänge der freien Regie nach der Jahreszeit gewichtet (ohne {@link #climate} unverändert). */
    double[] seasonal(double[] row, double t) {
        if (climate == null) return row;
        double doy = dayOfYear(t), su = summer(doy), au = 0.5 + 0.5 * Math.cos(2 * Math.PI * (doy - summerDay - 109) / 365);
        double[] w = row.clone();
        w[Phase.GEWITTER.ordinal()] *= 0.04 + 0.96 * su * su;
        w[Phase.NEBEL.ordinal()] *= 0.6 + 1.2 * au;
        w[Phase.STURM.ordinal()] *= 0.7 + 0.6 * (1 - su);
        // seit Brocken Phase 3: Raueis braucht Frost (fast nur im Winter), Inversionen sind im Winterhalbjahr häufiger
        if (w.length > Phase.RAUEIS.ordinal()) {
            w[Phase.RAUEIS.ordinal()] *= 0.02 + 0.98 * (1 - su) * (1 - su);
            w[Phase.INVERSION.ordinal()] *= 0.3 + 1.4 * (1 - su);
        }
        double sum = 0;
        for (double x : w) sum += x;
        if (sum <= 0) return row;
        for (int i = 0; i < w.length; i++) w[i] /= sum;
        return w;
    }

    private int indexAt(double t) {
        for (int i = 0; i < timeline.size(); i++) if (t < timeline.get(i).t1()) return t < timeline.get(i).t0() ? Math.max(0, i - 1) : i;
        return timeline.size() - 1;
    }

    private static double mix(Var v, double a, double b, double f) {
        if (v == Var.DIRECTION) return norm(a + angle(b - a) * f);
        if (v == Var.CLOUD || v == Var.THUNDER) return f < 0.5 ? a : b;
        if (v == Var.VISIBILITY) return Math.exp(Math.log(Math.max(0.05, a)) * (1 - f) + Math.log(Math.max(0.05, b)) * f);
        return a + (b - a) * f;
    }

    /** Tagesgang der Temperatur: ±3 °C um 15 Uhr am wärmsten, unter Wolken schwächer; mit {@link #climate} je nach Jahreszeit. */
    double diurnal(double t, double oktas) {
        var lt = Instant.ofEpochMilli((long) (t * 1000)).atZone(zone);
        double h = lt.getHour() + lt.getMinute() / 60.0;
        double amp = climate == null ? 3 : 3 * (0.55 + 0.75 * summer(dayOfYear(t)));    // Januar ±1,7, Juli ±3,9 °C
        return amp * Math.cos(2 * Math.PI * (h - 15) / 24) * (1 - 0.7 * Math.min(1, oktas / 8));
    }

    // ------------------------------------------------------------------ Ausgang

    /** Unveränderlicher Zustand der Regie. */
    public record Snapshot(double time, Phase phase, String stepText, int step, int steps, String scenario, double wind, double fromDeg,
                           double gustiness, double gustNow, double gustMax, int beaufort, double oktas, double high, CloudKind cloud,
                           double rain, double visibilityKm, double fog, double pressure, double tendency, double temperature,
                           double humidity, double dewPoint, double swellHeight, double swellPeriod, double swellFromDeg, int thunder,
                           double showerEnd, boolean[] overridden, boolean running, Phase next, double nextInH,
                           double refHeight, double lapse, double cloudBase, double cloudTop, double inversionBase, double inversionTop,
                           double inversionDelta) {

        /** Seit Brocken Phase 3: hat die Regie eine Bezugshöhe ({@link Weather#refHeight})? */
        public boolean hasHeight() { return !Double.isNaN(refHeight); }

        /**
         * Seit Brocken Phase 3: Lufttemperatur in der Höhe h (m über dem Meer): von der Bezugshöhe mit der
         * Temperaturabnahme der Lage, in einer Inversion wärmer. Ohne Bezugshöhe die Temperatur der Regie.
         */
        public double temperatureAt(double h) {
            if (!hasHeight()) return temperature;
            double t = temperature - lapse * (h - refHeight) / 1000;
            if (!Double.isNaN(inversionBase) && inversionTop > inversionBase)
                t += inversionDelta * (clamp((h - inversionBase) / (inversionTop - inversionBase), 0, 1)
                        - clamp((refHeight - inversionBase) / (inversionTop - inversionBase), 0, 1));
            return t;
        }

        /**
         * Seit Brocken Phase 3: wie sehr die Höhe h in der Wolke liegt (0..1): zwischen Basis und Obergrenze, nach der
         * Bedeckung (ab 5/8 voll). Ohne Bezugshöhe 0.
         */
        public double cloudAt(double h) {
            if (!hasHeight() || Double.isNaN(cloudBase)) return 0;
            double in = smooth(cloudBase - 40, cloudBase + 40, h) * (1 - smooth(cloudTop - 40, cloudTop + 40, h));
            return in * smooth(2, 5, oktas);
        }

        /** Seit Brocken Phase 3: Sicht in der Höhe h (km): unter den Wolken die der Regie, in der Wolke bis 60 m. */
        public double visibilityAt(double h) {
            double v = visibilityKm * (1 - 0.97 * fog);
            double c = cloudAt(h);
            return Math.max(0.05, Math.exp(Math.log(Math.max(0.06, v)) * (1 - c) + Math.log(0.06) * c));
        }

        /** Seit Brocken Phase 3: Taupunkt in der Höhe h (°C): mit gut 2 K je km abnehmend, nie über der Temperatur. */
        public double dewPointAt(double h) {
            if (!hasHeight()) return dewPoint;
            return Math.min(temperatureAt(h), dewPoint - 2.0 * (h - refHeight) / 1000);
        }

        /** Barometer-Tendenz in Worten (WMO: über 3 Stunden). */
        public String tendencyText() {
            double a = Math.abs(tendency);
            String s = a < 0.1 ? "gleichbleibend" : a < 1.5 ? "langsam" : a < 3.5 ? "" : a < 6 ? "rasch" : "sehr rasch";
            return a < 0.1 ? s : (s.isEmpty() ? "" : s + " ") + (tendency < 0 ? "fallend" : "steigend");
        }

        /** Die Felder eines Dosenbarometers. */
        public String barometerWord() { return pressure < 990 ? "Sturm" : pressure < 1003 ? "Regen" : pressure < 1023 ? "Veränderlich" : "Schön"; }

        /** Sicht mit Nebel (km). */
        public double effectiveVisibilityKm() { return visibilityKm * (1 - 0.97 * fog); }
    }

    private Snapshot build() {
        if (timeline.isEmpty()) return null;
        double t = Double.isNaN(time) ? timeline.get(0).t0() : time - frozenOffset;
        Segment s = segmentAt(t);
        int ki = indexAt(t);
        Segment n = ki + 1 < timeline.size() ? timeline.get(ki + 1) : s;
        double wind = Math.max(0, x[Var.WIND.ordinal()]), gu = Math.max(0, x[Var.GUST.ordinal()]);
        double gNow = wind * (1 + gu * Math.max(-1.2, Math.min(3, gust))), gMax = wind * (1 + 2.5 * gu);
        double tend = histN > 1 ? x[Var.PRESSURE.ordinal()] - pHist[Math.min(histN - 1, 18)] : 0;
        if (histN > 1 && histN < 19) tend *= 18.0 / (histN - 1);
        double rainMax = 0;
        for (int i = 0; i < Math.min(histN, rHist.length); i++) rainMax = Math.max(rainMax, rHist[i]);
        double rain = Math.max(0, x[Var.RAIN.ordinal()]), cover = clamp(x[Var.COVER.ordinal()], 0, 8);
        double shower = clamp(rainMax / 2, 0, 1) * clamp(1 - rain / 1.5, 0, 1) * clamp(1 - (cover - 4) / 4, 0, 1);
        double hum = clamp(x[Var.HUMIDITY.ordinal()], 20, 100), temp = x[Var.TEMPERATURE.ordinal()];
        boolean[] ov = new boolean[N];
        for (int i = 0; i < N; i++) ov[i] = !Double.isNaN(override[i]);
        double swell = Math.max(0, x[Var.SWELL.ordinal()]);
        // seit Brocken Phase 3: Schichtung (Basis aus Temperatur und Taupunkt nach der Faustformel 125 m je K)
        double ref = refHeight, lapse = layerAt(L_LAPSE, t), base = layerAt(L_BASE, t), top = layerAt(L_TOP, t);
        CloudKind kind = CloudKind.values()[(int) clamp(Math.round(x[Var.CLOUD.ordinal()]), 0, 4)];
        if (Double.isNaN(ref)) { base = Double.NaN; top = Double.NaN; }
        else {
            if (Double.isNaN(base)) base = ref + 125 * Math.max(0, temp - dewPoint(temp, hum));
            if (Double.isNaN(top) || top <= base) top = base + KIND_THICKNESS[kind.ordinal()];
        }
        return new Snapshot(t, s.phase(), s.step() != null ? s.step().text() : s.phase().label, s.index(), scenario != null ? scenario.steps().size() : 0,
                scenario != null ? scenario.name() : "", wind, norm(x[Var.DIRECTION.ordinal()]), gu, Math.max(0, gNow), gMax, beaufort(wind), cover,
                clamp(x[Var.HIGH.ordinal()], 0, 1), CloudKind.values()[(int) clamp(Math.round(x[Var.CLOUD.ordinal()]), 0, 4)], rain,
                Math.max(0.05, x[Var.VISIBILITY.ordinal()]), clamp(x[Var.FOG.ordinal()], 0, 1), x[Var.PRESSURE.ordinal()], tend, temp, hum,
                dewPoint(temp, hum), swell, 7 + 2.2 * Math.sqrt(swell * 4), norm(x[Var.DIRECTION.ordinal()] + 25),
                (int) clamp(Math.round(x[Var.THUNDER.ordinal()]), 0, 4), shower, ov, running, n.phase(), (n.t0() - t) / 3600,
                ref, lapse, base, top, layerAt(L_INV_BASE, t), layerAt(L_INV_TOP, t), layerAt(L_INV_DELTA, t));
    }

    // ------------------------------------------------------------------ Hilfen

    /** Beaufort nach WMO (obere Grenzen in m/s). */
    public static int beaufort(double ms) {
        double[] up = {0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7};
        for (int i = 0; i < up.length; i++) if (ms < up[i]) return i;
        return 12;
    }

    /** Mitte einer Beaufort-Stufe in m/s. */
    public static double beaufortMid(int b) {
        double[] mid = {0.15, 0.95, 2.5, 4.45, 6.75, 9.4, 12.35, 15.55, 19.0, 22.65, 26.5, 30.6, 34};
        return mid[Math.max(0, Math.min(12, b))];
    }

    /** Taupunkt nach Magnus (°C). */
    public static double dewPoint(double tC, double rhPct) {
        double a = 17.62, b = 243.12, g = Math.log(Math.max(1, rhPct) / 100) + a * tC / (b + tC);
        return b * g / (a - g);
    }

    static double norm(double a) { double r = a % 360; return r < 0 ? r + 360 : r; }

    static double angle(double d) { return ((d % 360) + 540) % 360 - 180; }

    static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }

    static double smooth(double a, double b, double t) {
        double u = clamp((t - a) / (b - a), 0, 1);
        return u * u * (3 - 2 * u);
    }

    /**
     * Rechnet ein Szenario ohne Uhr durch und gibt alle stepS Sekunden den Zustand zurück (für Kurven und Prüfungen).
     * Böen laufen dabei mit realDt je Schritt.
     */
    public static List<Snapshot> run(Scenario s, double t0, double hours, double stepS, long seed) {
        Weather w = new Weather();
        w.seed = seed;
        w.start(s, t0);
        List<Snapshot> out = new ArrayList<>();
        for (double t = t0; t <= t0 + hours * 3600 + 1e-6; t += stepS) out.add(w.update(t, 1.0));
        return out;
    }
}
