package com.dan.beacon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sektoren eines Leuchtfeuers wie im Verzeichnis: Farbe und Grenzen als <b>rechtweisende Peilung von See zum Feuer</b>
 * (so stehen sie in jedem Leuchtfeuerverzeichnis). Ein Schiff, das das Feuer in 200° peilt, liegt also im Azimut 20°
 * vom Turm aus gesehen. {@link #at(double)} nimmt die Peilung, {@link #atAzimuth(double)} den Azimut vom Feuer.
 * <p>
 * Jeder Sektor trägt eine Nenntragweite in sm, aus der die Lichtstärke folgt ({@link Range#intensityForNominal}).
 * Ein Schwachlicht-Sektor hat eine kleine Tragweite. An den Grenzen gehen die Farben über eine schmale Unschärfe
 * ineinander über (Standard 0,5°), wie hinter echten Farbscheiben.
 * <p>
 * Textform: {@code W 167.5-185 9, R 185-225 7, ...} (Farbe, von, bis, Tragweite in sm; die Tragweite darf fehlen,
 * dann gilt die des Feuers). Lücken bleiben dunkel.
 */
public final class SectorTable {

    /** Ein Sektor: Farbe, Peilung von See von … bis (Grad, im Uhrzeigersinn), Nenntragweite in sm, Name. */
    public record Sector(LightColour colour, double from, double to, double rangeNm, String name) {
        /** Breite in Grad. */
        public double width() { double w = to - from; return w <= 0 ? w + 360 : w; }

        /** Liegt die Peilung b (Grad) im Sektor? */
        public boolean contains(double b) { return offsetIn(b) >= 0; }

        /** Abstand der Peilung vom nächsten Rand nach innen (Grad), negativ außerhalb. */
        double offsetIn(double b) {
            double u = norm(b - from), w = width();
            if (u <= w) return Math.min(u, w - u);
            return -Math.min(u - w, 360 - u);
        }
    }

    private final List<Sector> sectors;
    /** Unschärfe an den Grenzen in Grad (volle Breite des Übergangs). */
    public final double blur;

    public SectorTable(List<Sector> sectors, double blur) {
        this.sectors = List.copyOf(sectors);
        this.blur = blur;
    }

    /** Ein Sektor über den ganzen Kreis. */
    public static SectorTable all(LightColour c, double rangeNm) {
        return new SectorTable(List.of(new Sector(c, 0, 360, rangeNm, "rundum")), 0.5);
    }

    public List<Sector> sectors() { return sectors; }

    public boolean isAllRound() { return sectors.size() == 1 && sectors.get(0).width() >= 360 - 1e-9; }

    /** Der Sektor zur Peilung von See (Grad), null in einer Lücke. */
    public Sector at(double bearing) {
        double b = norm(bearing);
        Sector best = null;
        double bo = -1e9;
        for (Sector s : sectors) {
            if (s.width() >= 360 - 1e-9) return s;
            double o = s.offsetIn(b);
            if (o >= 0 && o > bo) { bo = o; best = s; }
        }
        return best;
    }

    /** Der Sektor für einen Beobachter im Azimut az vom Feuer (Grad von Nord): Peilung = az + 180. */
    public Sector atAzimuth(double az) { return at(az + 180); }

    /**
     * Lichtstärke in cd als lineares RGB für die Peilung von See, mit weichen Grenzen. intensity(range) liefert die
     * Lichtstärke zur Nenntragweite eines Sektors (die Farbe steckt dann im RGB, die Leuchtdichte ist diese Stärke).
     */
    public double[] rgbAt(double bearing, java.util.function.DoubleUnaryOperator intensity) {
        double b = norm(bearing);
        double[] out = new double[3];
        double wsum = 0;
        for (Sector s : sectors) {
            double w;
            if (s.width() >= 360 - 1e-9) w = 1;
            else {
                double o = s.offsetIn(b);
                w = blur <= 0 ? (o >= 0 ? 1 : 0) : smooth(-blur / 2, blur / 2, o);
            }
            if (w <= 0) continue;
            double i = intensity.applyAsDouble(s.rangeNm());
            double[] c = s.colour().rgb();
            for (int k = 0; k < 3; k++) out[k] += w * i * c[k];
            wsum += w;
        }
        if (wsum > 1) for (int k = 0; k < 3; k++) out[k] /= wsum;
        return out;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double norm(double a) { a %= 360; return a < 0 ? a + 360 : a; }

    /** Größte Nenntragweite aller Sektoren. */
    public double maxRangeNm() { return sectors.stream().mapToDouble(Sector::rangeNm).max().orElse(0); }

    /** Die Farben der Sektoren ohne Wiederholung, in Reihenfolge. */
    public List<LightColour> colours() {
        List<LightColour> l = new ArrayList<>();
        for (Sector s : sectors) if (!l.contains(s.colour())) l.add(s.colour());
        return l;
    }

    // ------------------------------------------------------------------ Text

    /** Liest „W 167.5-185 9, R 185-225 7, …“; defaultRange gilt für Sektoren ohne eigene Tragweite. */
    public static SectorTable parse(String text, double defaultRange) {
        List<Sector> l = new ArrayList<>();
        for (String part : text.split("[;,\\n]+(?![0-9])")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] t = p.split("\\s+");
            if (t.length < 2) throw new IllegalArgumentException("Sektor „" + p + "“: Farbe und Bereich erwartet");
            LightColour c = LightColour.of(t[0]);
            if (c == null) throw new IllegalArgumentException("Sektor „" + p + "“: unbekannte Farbe " + t[0]);
            String[] ft = t[1].replace('–', '-').split("-");
            if (ft.length != 2) throw new IllegalArgumentException("Sektor „" + p + "“: Bereich als von-bis");
            double f = Double.parseDouble(ft[0].replace(',', '.')), to = Double.parseDouble(ft[1].replace(',', '.'));
            double r = t.length > 2 ? Double.parseDouble(t[2].replace(',', '.')) : defaultRange;
            String name = t.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(t, 3, t.length)) : "";
            l.add(new Sector(c, norm(f), norm(to) == 0 && to != 0 ? 360 : norm(to), r, name));
        }
        if (l.isEmpty()) throw new IllegalArgumentException("keine Sektoren");
        return new SectorTable(l, 0.5);
    }

    /** „W 167,5–185° 9 sm · R …“ */
    public String describe() {
        StringBuilder b = new StringBuilder();
        for (Sector s : sectors) {
            if (b.length() > 0) b.append(" · ");
            b.append(String.format(Locale.GERMAN, "%s %s–%s° %s sm%s", s.colour().code(), Characteristic.num(s.from()).replace('.', ','),
                    Characteristic.num(s.to()).replace('.', ','), Characteristic.num(s.rangeNm()).replace('.', ','), s.name().isEmpty() ? "" : " (" + s.name() + ")"));
        }
        return b.toString();
    }
}
