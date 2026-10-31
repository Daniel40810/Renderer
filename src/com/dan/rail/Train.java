package com.dan.rail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein Zug auf einem {@link Track} (neu für Brocken, F10 A, F12 A): Stelle, Geschwindigkeit, Länge, Masse.
 * <p>
 * Seit Brocken Phase 13 mit Fahrdynamik: Zugkraft aus der Leistung am Rad, begrenzt durch die Reibung der Treibachsen;
 * Widerstände aus Rollen und Luft, Steigung und Bogen (für Schmalspur nach Röckl [G]); Bremsen mit fester Verzögerung.
 * Gefahren wird nach einer Sollgeschwindigkeit: Höchstgeschwindigkeit, Bogengrenze voraus (Querbeschleunigung höchstens
 * {@link #querbeschleunigung}) und Bremskurve zum nächsten Halt. Halte ({@link Halt}) mit Aufenthalt; Richtung auf dem
 * Gleis vorwärts oder rückwärts (bergab vom Gipfel). Die Dampflok gibt ihre Auspuffschläge ({@link #schlaege}) und ihre
 * Last ({@link #last}) für Dampf und Klang; Pfiffe beim Abfahren und an {@link #pfiffe} meldet {@link Pfiff}.
 */
public final class Train {
    /** Eigene kleine Schnittstelle: Ereignis nach außen (Ankunft am Ende des Gleises oder am letzten Halt). */
    @FunctionalInterface
    public interface Ankunft { void angekommen(Train t, double sekunde); }

    /** Ein Pfiff: wo (m) und warum („Abfahrt“, „Bahnübergang“ …). */
    @FunctionalInterface
    public interface Pfiff { void pfeift(Train t, double s, String grund); }

    /**
     * Eigene kleine Schnittstelle: das Signal am Ende eines Halts. Die App sagt, ob der Zug in den nächsten Abschnitt
     * fahren darf (etwa auf eingleisiger Strecke nur, wenn ihm keiner entgegenkommt); sonst wartet er weiter.
     */
    @FunctionalInterface
    public interface Signal { boolean frei(Train t, Halt h); }

    /** Eigene kleine Schnittstelle: der Zug steht am Halt (gerade angekommen). */
    @FunctionalInterface
    public interface AmHalt { void haelt(Train t, Halt h, double sekunde); }

    /**
     * Ein Halt: Stelle (m), Aufenthalt (s), frühste Abfahrt in der Zeit des Zuges ({@link #zeit()}, s; 0 = keine), Name.
     */
    public record Halt(double s, double aufenthalt, double abfahrt, String name) {
        public Halt(double s, double aufenthalt, String name) { this(s, aufenthalt, 0, name); }
    }

    public final String name;
    /** Stelle der Zugspitze (m ab Anfang des Gleises) und Geschwindigkeit (m/s, Betrag). */
    public double s, v;
    /** Fahrtrichtung auf dem Gleis: +1 zu wachsendem s, −1 zurück. */
    public int richtung = 1;
    /** Höchstgeschwindigkeit (m/s), Anfahren (m/s², ohne Dynamik) und Bremsen (m/s²), Länge (m), Masse (t). */
    public double vMax = 40 / 3.6, anfahren = 0.15, bremsen = 0.4, laenge = 60, masse = 180;
    /** Fahrdynamik (seit Phase 13): Leistung am Rad (W), Masse auf den Treibachsen (t), Haftreibwert, Zuschlag für drehende Massen. */
    public double leistung = 450e3, reibMasse = 51, haftung = 0.25, drehmassen = 1.06;
    /** Rollen und Luft: Grundwiderstand (‰ der Gewichtskraft) und Zuschlag je (km/h)² (‰). */
    public double grundWiderstand = 2.5, luftWiderstand = 0.0006;
    /** Größte Querbeschleunigung im Bogen (m/s²) für die Bogengrenze [G]. */
    public double querbeschleunigung = 0.5;
    /** Durchmesser der Treibräder (m) und Schläge je Umdrehung (zwei Zylinder, doppelt wirkend: 4). */
    public double treibrad = 1.0;
    public int schlaegeJeUmdrehung = 4;
    /** Ohne Fahrdynamik (nur Anfahren und Bremsen wie in der Hülle). */
    public boolean dynamik = true;
    /** Läuft der Zug? */
    public boolean faehrt = true;
    public final List<Halt> halte = new ArrayList<>();
    /** Stellen, an denen gepfiffen wird (m), etwa vor Bahnübergängen. */
    public final List<double[]> pfiffe = new ArrayList<>();
    public Ankunft onAnkunft;
    public Pfiff onPfiff;
    public Signal signal;
    public AmHalt onHalt;
    /**
     * Seitlicher Versatz des befahrenen Gleises (m, + rechts von wachsendem s) je Stelle, etwa in einem Ausweichgleis
     * neben dem Hauptgleis; null = auf der Achse. Nur für die Zeichnung, die Fahrdynamik nimmt das Hauptgleis.
     */
    public java.util.function.DoubleUnaryOperator versatz;
    private double zeit, wartet = -1, last, schlagPhase;
    private int naechsterHalt;
    private Halt amHalt;
    private final List<Double> gepfiffen = new ArrayList<>();
    private String zustand = "", haltName = "";

    public Train(String name) { this.name = name; }

    /**
     * Preset: Dampflok der Baureihe 99.23–24 (1′E1′h2t, 64,5 t, 700 PS, Treibräder 1 000 mm, 40 km/h) mit fünf
     * besetzten Wagen zu 22 t und 14 m [G]; Leistung am Rad 430 kW (gut 80 % der 700 PSi) [G].
     */
    public static Train brockenzug(String name) { return brockenzug(name, 5); }

    /** Wie {@link #brockenzug(String)} mit so vielen Wagen. */
    public static Train brockenzug(String name, int wagen) {
        Train t = new Train(name);
        t.vMax = 40 / 3.6;
        t.leistung = 430e3;
        t.laenge = LOK_LAENGE + wagen * WAGEN_LAENGE;
        t.masse = 64.5 + wagen * WAGEN_MASSE;
        return t;
    }

    /** Masse eines besetzten Wagens (t) [G]. */
    public static final double WAGEN_MASSE = 22;

    /** Länge der Lok und eines Wagens über Puffer (m) [G]. */
    public static final double LOK_LAENGE = 12.6, WAGEN_LAENGE = 14;

    /** Zahl der Wagen (aus der Länge). */
    public int wagen() { return (int) Math.max(0, Math.round((laenge - LOK_LAENGE) / WAGEN_LAENGE)); }

    /** Einen Schritt von dt Sekunden auf dem Gleis fahren. */
    public void step(Track track, double dt) {
        zeit += dt;
        if (!faehrt) return;
        if (wartet >= 0) {
            wartet -= dt;
            last = 0;
            zustand = "hält in " + haltName;
            if (wartet < 0) {
                if (signal != null && amHalt != null && !signal.frei(this, amHalt)) {
                    wartet = 0;
                    zustand = "wartet in " + haltName + " auf Kreuzung";
                    return;
                }
                wartet = -1;
                if (onPfiff != null) onPfiff.pfeift(this, s, "Abfahrt");
            }
            return;
        }
        double ziel = zielStelle(track), rest = Math.max(0, (ziel - s) * richtung);
        if (!dynamik) {
            double brems = v * v / (2 * bremsen);
            if (rest <= brems + 0.5) v = Math.max(0, v - bremsen * dt);
            else v = Math.min(vMax, v + anfahren * dt);
            last = v < vMax ? 1 : 0.3;
        } else {
            double soll = Math.min(vMax, bogenGrenze(track));
            // Bremskurve zum Halt mit 85 % der Bremsverzögerung (Reserve für die Regelung)
            soll = Math.min(soll, Math.sqrt(2 * 0.85 * bremsen * Math.max(0, rest - 0.3)));
            Track.Pose p = track.at(s - richtung * laenge / 2);
            double steig = p.gradient() * richtung;                     // + bergauf in Fahrtrichtung
            double r = p.radius(), kmh = v * 3.6;
            double wBogen = Double.isInfinite(r) || r < 25 ? 0 : 400 / (r - 20);   // ‰, Röckl für Schmalspur [G]
            double gewicht = masse * 1000 * 9.81;
            double widerstand = gewicht * (steig + (grundWiderstand + luftWiderstand * kmh * kmh + wBogen) / 1000);
            double zugkraftMax = Math.min(leistung / Math.max(1.5, v), haftung * reibMasse * 1000 * 9.81);
            double m = masse * 1000 * drehmassen;
            // gewünschte Beschleunigung: die Sollgeschwindigkeit in rund 5 s erreichen (darüber nicht schneller als bremsen)
            double aWunsch = Math.max(-bremsen, Math.min(0.5, (soll - v) / 5));
            // die Verzögerung, die zum Halten nötig ist (bis 25 % über der Bremsverzögerung, dann eben stärker)
            double noetig = v * v / (2 * Math.max(0.05, rest - 0.3));
            if (noetig > 0.7 * bremsen) aWunsch = Math.min(aWunsch, -Math.min(1.25 * bremsen, noetig));
            double f = widerstand + m * aWunsch, a;
            if (f > 0 && soll > 0.05) {
                f = Math.min(f, zugkraftMax);
                last = zugkraftMax > 0 ? f / zugkraftMax : 0;
                a = (f - widerstand) / m;
                zustand = last > 0.95 ? "volle Kraft" : "zieht";
            } else {
                double frei = -widerstand / m;
                a = Math.max(-1.25 * bremsen, Math.min(frei, aWunsch));
                last = 0;
                zustand = a < frei - 1e-6 ? "bremst" : "rollt";
            }
            v = Math.max(0, v + a * dt);
            if (rest < 0.3) v = 0;
        }
        s += richtung * v * dt;
        s = Math.max(0, Math.min(track.length(), s));
        schlagPhase += v / (Math.PI * treibrad) * schlaegeJeUmdrehung * dt;
        for (double[] pf : pfiffe)
            if (!gepfiffen.contains(pf[0]) && (s - pf[0]) * richtung >= 0 && (s - pf[0]) * richtung < 50) {
                gepfiffen.add(pf[0]);
                if (onPfiff != null) onPfiff.pfeift(this, pf[0], "Bahnübergang");
            }
        if (rest < 0.6 && v < 0.05) {
            v = 0;
            if (naechsterHalt < halte.size()) {
                Halt h = halte.get(naechsterHalt++);
                boolean letzter = naechsterHalt >= halte.size() && endeIstHalt(track, h);
                amHalt = h;
                if (onHalt != null) onHalt.haelt(this, h, zeit);
                if (!letzter) { wartet = Math.max(h.aufenthalt(), h.abfahrt() - zeit); haltName = h.name(); return; }
            }
            faehrt = false;
            last = 0;
            zustand = "angekommen";
            if (onAnkunft != null) onAnkunft.angekommen(this, zeit);
        }
    }

    private boolean endeIstHalt(Track track, Halt h) {
        double ende = richtung > 0 ? track.length() : 0;
        return Math.abs(h.s() - ende) < 200;
    }

    /** Wo der Zug als Nächstes hält: der nächste Halt voraus oder das Ende des Gleises. */
    double zielStelle(Track track) {
        for (int i = naechsterHalt; i < halte.size(); i++) {
            double hs = halte.get(i).s();
            if ((hs - s) * richtung > -3) return hs;
        }
        return richtung > 0 ? track.length() : 0;
    }

    /** Die kleinste Bogengrenze bis zum Bremsweg voraus (m/s). */
    double bogenGrenze(Track track) {
        double weit = Math.max(60, v * v / (2 * bremsen) + 40), g = vMax;
        for (double d = -laenge; d <= weit; d += 10) {
            Track.Pose p = track.at(s + richtung * d);
            double r = p.radius();
            if (Double.isInfinite(r)) continue;
            double lim = Math.sqrt(querbeschleunigung * r);
            // vor dem Bogen darf es noch schneller sein, solange der Bremsweg reicht
            double erlaubt = d <= 0 ? lim : Math.sqrt(lim * lim + 2 * bremsen * d);
            g = Math.min(g, erlaubt);
        }
        return g;
    }

    /** Last der Lok 0..1 (Anteil der größten Zugkraft; 0 beim Rollen, Bremsen und Halten). */
    public double last() { return last; }

    /** Auspuffschläge je Sekunde (aus Treibrad und Geschwindigkeit). */
    public double schlaege() { return v / (Math.PI * treibrad) * schlaegeJeUmdrehung; }

    /** Phase der Schläge (zählt hoch; der Bruchteil ist die Lage im Takt). */
    public double schlagPhase() { return schlagPhase; }

    /** „zieht“, „volle Kraft“, „rollt“, „bremst“, „hält“, „angekommen“. */
    public String zustand() { return zustand; }

    /** Sekunden seit dem Start. */
    public double zeit() { return zeit; }

    /** Die Uhr des Zuges stellen (s; etwa beim Einsetzen nach Fahrplan, wenn {@link Halt#abfahrt()} in Uhrzeit gilt). */
    public void zeit(double sekunde) { zeit = sekunde; }

    /** Hält der Zug gerade an einem Halt? */
    public boolean haelt() { return wartet >= 0; }

    /** Der letzte Halt, an dem der Zug stand oder steht (null: noch keiner). */
    public Halt amHalt() { return amHalt; }

    /** Restliche Wartezeit am Halt (s; 0, wenn er fährt). */
    public double wartet() { return Math.max(0, wartet); }

    /** Die Lage der Mitte jedes Fahrzeugs (Lok zuerst) für eine Zeichnung: {s der Mitte, Länge}. */
    public double[][] fahrzeuge() {
        int n = wagen();
        double[][] f = new double[n + 1][];
        f[0] = new double[]{s - richtung * LOK_LAENGE / 2, LOK_LAENGE};
        for (int i = 0; i < n; i++) f[i + 1] = new double[]{s - richtung * (LOK_LAENGE + (i + 0.5) * WAGEN_LAENGE), WAGEN_LAENGE};
        return f;
    }

    /** Lage der Zugspitze. */
    public Track.Pose spitze(Track track) { return track.at(s); }

    /** Lage des Zugendes. */
    public Track.Pose ende(Track track) { return track.at(s - richtung * laenge); }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s · %.0f m · %.0f km/h%s", name, s, v * 3.6, faehrt ? (zustand.isEmpty() ? "" : " · " + zustand) : " · steht");
    }
}
