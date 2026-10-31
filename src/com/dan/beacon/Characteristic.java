package com.dan.beacon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Die Kennung eines Leuchtfeuers in Seezeichen-Notation (IALA), etwa {@code Fl(3) W 15s}, {@code Oc(3) WRG 12s},
 * {@code Q(6)+LFl W 15s}, {@code Al.Fl WR 4s}, {@code Mo(U) W 15s} oder {@code F WRG}. Der Parser liest den Text und baut
 * daraus die Folge der Lichtzeiten in einer Wiederkehr; {@link #level(double)} sagt für jeden Augenblick, wie hell das
 * Feuer ist (0 bis 1, mit der Trägheit der Lampe), {@link #colourAt(double)} welche Farbe es bei Wechselfeuern hat.
 * <p>
 * Die Notation nennt nur die Art, die Gruppe, die Farben und die Wiederkehr. Die Dauer der einzelnen Blitze steht im
 * Leuchtfeuerverzeichnis und fehlt im Kurztext. Ohne Angabe gelten die Werte aus {@link Timing#STANDARD}: Blitz 0,5 s,
 * Pause in der Gruppe 1 s, langer Blitz 2 s, Funkelfeuer 60/min mit 0,3 s, Unterbrechung 1 s mit 2 s Licht dazwischen.
 * Damit kommt zum Beispiel für die Tegeler Plate ({@code Oc(3) WRG 12s}) genau der Takt aus dem Verzeichnis heraus:
 * (1) + 2 + (1) + 2 + (1) + 5.
 * <p>
 * Deutsche Kürzel werden mitgelesen: Blz = Fl, Ubr = Oc, Glt = Iso, Fkl = Q, SFkl = VQ, Fest = F. Mehrere Farben ohne
 * „Al.“ heißen Sektorenfeuer: Welche Farbe man sieht, sagt dann die {@link SectorTable}.
 */
public final class Characteristic {

    /** Arten der Kennung. */
    public enum Kind {
        F("Festfeuer", "Festfeuer"), FL("Blitz", "Blitzgruppe"), LFL("langer Blitz", "Gruppe langer Blitze"), Q("Funkelfeuer", "Funkelgruppe"),
        VQ("schnelles Funkelfeuer", "schnelle Funkelgruppe"), UQ("ultraschnelles Funkelfeuer", "ultraschnelle Funkelgruppe"),
        IQ("unterbrochenes Funkelfeuer", "unterbrochenes Funkelfeuer"), IVQ("unterbrochenes schnelles Funkelfeuer", "unterbrochenes schnelles Funkelfeuer"),
        IUQ("unterbrochenes ultraschnelles Funkelfeuer", "unterbrochenes ultraschnelles Funkelfeuer"),
        ISO("Gleichtaktfeuer", "Gleichtaktfeuer"), OC("Unterbrochenes Feuer", "Unterbrechungsgruppe"), MO("Morsefeuer", "Morsefeuer"), FFL("Fest- und Blitzfeuer", "Fest- und Blitzgruppe");
        public final String german, group;
        Kind(String g, String gr) { german = g; group = gr; }
    }

    /** Ein Teil der Kennung: Art und Gruppe, z. B. Fl(2+1) → FL, [2, 1]; Mo(U) → MO, letter U. */
    public record Element(Kind kind, int[] groups, char letter) {
        @Override public String toString() {
            String n = switch (kind) { case FL -> "Fl"; case LFL -> "LFl"; case ISO -> "Iso"; case OC -> "Oc"; case MO -> "Mo"; case FFL -> "FFl"; default -> kind.name(); };
            if (kind == Kind.MO) return n + "(" + letter + ")";
            if (groups.length == 0 || (groups.length == 1 && groups[0] == 1 && kind != Kind.Q && kind != Kind.VQ && kind != Kind.UQ)) return n;
            StringBuilder b = new StringBuilder(n).append('(');
            for (int i = 0; i < groups.length; i++) b.append(i > 0 ? "+" : "").append(groups[i]);
            return b.append(')').toString();
        }
    }

    /** Dauern in s, die die Notation offen lässt. */
    public record Timing(double flash, double flashGap, double groupGap, double longFlash, double quick, double quickOn,
                         double eclipse, double dot, double dash, double lamp, double ffBase) {
        /** Blitz 0,5 · Pause 1 · zwischen Teilgruppen 2 · langer Blitz 2 · Funkel 1 s mit 0,3 s Licht · Unterbrechung 1 · Morse 0,5/1,5 · Lampe 0,06 · FFl-Grund 0,15. */
        public static final Timing STANDARD = new Timing(0.5, 1.0, 2.0, 2.0, 1.0, 0.3, 1.0, 0.5, 1.5, 0.06, 0.15);

        /** Dieselben Werte mit anderer Blitzdauer (z. B. aus der Drehgeschwindigkeit einer Linse). */
        public Timing withFlash(double d) { return new Timing(d, flashGap, groupGap, longFlash, quick, Math.min(quickOn, d), eclipse, dot, dash, lamp, ffBase); }
    }

    /** Fehler im Text mit der Stelle (0-basiert), an der der Parser hängen blieb. */
    public static final class ParseException extends Exception {
        public final int position;
        public ParseException(String msg, int position) { super(msg); this.position = position; }
    }

    public final String text;
    public final List<Element> elements;
    public final boolean alternating, sectored;
    public final LightColour[] colours;
    /** Wiederkehr laut Notation in s (0 = keine, Festfeuer oder durchgehendes Funkeln). */
    public final double period;
    /** Länge einer vollen Wiederkehr mit allen Farben (bei Wechselfeuern ein Vielfaches der Periode). */
    public final double cycle;
    /** Lichtzeiten in einer Wiederkehr: Paare Anfang, Ende in s. */
    public final double[] on;
    /** Farbe je Lichtzeit (bei Wechselfeuern verschieden, sonst überall die erste Farbe). */
    public final LightColour[] onColour;
    /** Grundhelligkeit zwischen den Blitzen (nur FFl). */
    public final double base;
    public final Timing timing;

    private Characteristic(String text, List<Element> el, boolean alt, LightColour[] col, double period, double cycle, double[] on,
                           LightColour[] onColour, double base, Timing timing) {
        this.text = text;
        this.elements = List.copyOf(el);
        this.alternating = alt;
        this.sectored = !alt && col.length > 1;
        this.colours = col;
        this.period = period;
        this.cycle = cycle;
        this.on = on;
        this.onColour = onColour;
        this.base = base;
        this.timing = timing;
    }

    // ------------------------------------------------------------------ Parser

    public static Characteristic parse(String s) throws ParseException { return parse(s, Timing.STANDARD); }

    /** Wie {@link #parse(String)}, aber ohne Ausnahme: null bei Fehler. */
    public static Characteristic tryParse(String s) {
        try { return parse(s); } catch (ParseException e) { return null; }
    }

    public static Characteristic parse(String src, Timing tm) throws ParseException {
        if (src == null || src.isBlank()) throw new ParseException("Kennung fehlt", 0);
        Lexer lx = new Lexer(src);
        boolean alt = false;
        lx.skip();
        if (lx.peekWord("Al")) {
            lx.pos += 2;
            if (lx.pos < lx.s.length() && lx.s.charAt(lx.pos) == '.') lx.pos++;
            alt = true;
            lx.skip();
        }
        List<Element> el = new ArrayList<>();
        while (true) {
            lx.skip();
            int at = lx.pos;
            Kind k = lx.kind();
            // „Al.WR“: Wechselfeuer ohne Art heißt Festfeuer mit wechselnder Farbe
            if (k == null && alt && el.isEmpty() && at < lx.s.length() && LightColour.of(String.valueOf(lx.s.charAt(at))) != null) k = Kind.F;
            if (k == null) throw new ParseException(el.isEmpty() ? "Art erwartet (F, Fl, LFl, Q, VQ, Iso, Oc, Mo …)" : "Art nach „+“ erwartet", at);
            int[] groups = {1};
            char letter = 0;
            if (lx.pos < lx.s.length() && lx.s.charAt(lx.pos) == '(') {
                lx.pos++;
                if (k == Kind.MO) {
                    lx.skip();
                    if (lx.pos >= lx.s.length() || !Character.isLetter(lx.s.charAt(lx.pos))) throw new ParseException("Buchstabe für Morse erwartet, z. B. Mo(U)", lx.pos);
                    letter = Character.toUpperCase(lx.s.charAt(lx.pos++));
                    if (Morse.code(letter) == null) throw new ParseException("Kein Morsezeichen für „" + letter + "“", lx.pos - 1);
                } else {
                    List<Integer> g = new ArrayList<>();
                    do {
                        lx.skip();
                        int n = lx.integer();
                        if (n <= 0 || n > 20) throw new ParseException("Gruppe 1 bis 20 erwartet", lx.pos);
                        g.add(n);
                        lx.skip();
                    } while (lx.pos < lx.s.length() && lx.s.charAt(lx.pos) == '+' && ++lx.pos > 0);
                    groups = g.stream().mapToInt(Integer::intValue).toArray();
                }
                lx.skip();
                if (lx.pos >= lx.s.length() || lx.s.charAt(lx.pos) != ')') throw new ParseException("„)“ erwartet", lx.pos);
                lx.pos++;
            } else if (k == Kind.MO) {
                throw new ParseException("Morse braucht einen Buchstaben, z. B. Mo(U)", lx.pos);
            } else if (k == Kind.Q || k == Kind.VQ || k == Kind.UQ) {
                groups = new int[0];
            }
            el.add(new Element(k, groups, letter));
            lx.skip();
            if (lx.pos < lx.s.length() && lx.s.charAt(lx.pos) == '+') { lx.pos++; continue; }
            break;
        }
        // Farben
        lx.skip();
        List<LightColour> cols = new ArrayList<>();
        int colAt = lx.pos;
        while (lx.pos < lx.s.length()) {
            char c = lx.s.charAt(lx.pos);
            if (c == 'B' && lx.pos + 1 < lx.s.length() && lx.s.charAt(lx.pos + 1) == 'u') { cols.add(LightColour.BU); lx.pos += 2; continue; }
            LightColour lc = Character.isLetter(c) && Character.isUpperCase(c) ? LightColour.of(String.valueOf(c)) : null;
            if (lc == null) break;
            if (!cols.contains(lc)) cols.add(lc);
            lx.pos++;
            if (lx.pos < lx.s.length() && lx.s.charAt(lx.pos) == '.') lx.pos++;
        }
        if (cols.isEmpty()) {
            lx.skip();
            if (lx.pos < lx.s.length() && Character.isLetter(lx.s.charAt(lx.pos)))
                throw new ParseException("Farbe erwartet (W, R, G, Y, Bu)", lx.pos);
            cols.add(LightColour.W);
        }
        if (alt && cols.size() < 2) throw new ParseException("Wechselfeuer (Al.) braucht mindestens zwei Farben", colAt);
        // Wiederkehr
        lx.skip();
        double period = 0;
        if (lx.pos < lx.s.length() && (Character.isDigit(lx.s.charAt(lx.pos)))) {
            int at = lx.pos;
            period = lx.number();
            lx.skip();
            if (lx.pos < lx.s.length() && (lx.s.charAt(lx.pos) == 's' || lx.s.charAt(lx.pos) == 'S')) lx.pos++;
            else throw new ParseException("„s“ nach der Wiederkehr erwartet", lx.pos);
            if (period <= 0 || period > 120) throw new ParseException("Wiederkehr 0 bis 120 s", at);
        }
        lx.skip();
        if (lx.pos < lx.s.length()) throw new ParseException("Unerwartet: „" + lx.s.substring(lx.pos) + "“", lx.pos);
        return build(el, alt, cols.toArray(new LightColour[0]), period, tm);
    }

    private static Characteristic build(List<Element> el, boolean alt, LightColour[] cols, double period, Timing tm) throws ParseException {
        boolean fixed = el.size() == 1 && el.get(0).kind() == Kind.F;
        boolean continuousQuick = el.size() == 1 && el.get(0).groups().length == 0;
        boolean needsPeriod = !(fixed || continuousQuick);
        if (needsPeriod && period <= 0) throw new ParseException("Wiederkehr fehlt, z. B. „5s“", 0);
        List<double[]> on = new ArrayList<>();
        double base = 0;
        if (fixed) {
            double p = period > 0 ? period : 0;
            if (alt) {
                double cyc = p > 0 ? p : 2.0 * cols.length;
                for (int i = 0; i < cols.length; i++) on.add(new double[]{cyc * i / cols.length, cyc * (i + 1) / cols.length});
                period = cyc;
            } else {
                on.add(new double[]{0, Double.POSITIVE_INFINITY});
            }
        } else if (continuousQuick) {
            Kind k = el.get(0).kind();
            double[] q = quick(k, tm);
            period = period > 0 ? period : q[0];
            for (double t = 0; t + q[1] <= period + 1e-9; t += q[0]) on.add(new double[]{t, t + q[1]});
        } else {
            double t = 0;
            for (int e = 0; e < el.size(); e++) {
                Element x = el.get(e);
                if (e > 0) t += tm.flashGap();
                t = append(x, t, period, on, tm, el.size() == 1);
                if (x.kind() == Kind.FFL) base = tm.ffBase();
            }
            if (t > period + 1e-6) throw new ParseException(String.format(Locale.GERMAN, "Takt braucht %.1f s, passt nicht in %s s", t, num(period)), 0);
        }
        // Farben je Lichtzeit, bei Wechselfeuern über mehrere Perioden
        double cycle = period;
        LightColour[] oc;
        if (alt && !(fixed)) {
            int n = on.size(), c = cols.length, rep = c / gcd(n, c);
            List<double[]> all = new ArrayList<>();
            for (int r = 0; r < rep; r++) for (double[] iv : on) all.add(new double[]{iv[0] + r * period, iv[1] + r * period});
            on = all;
            cycle = period * rep;
            oc = new LightColour[on.size()];
            for (int i = 0; i < oc.length; i++) oc[i] = cols[i % c];
        } else {
            oc = new LightColour[on.size()];
            for (int i = 0; i < oc.length; i++) oc[i] = alt ? cols[i % cols.length] : cols[0];
        }
        double[] flat = new double[on.size() * 2];
        for (int i = 0; i < on.size(); i++) { flat[2 * i] = on.get(i)[0]; flat[2 * i + 1] = on.get(i)[1]; }
        String text = normalized(el, alt, cols, period, fixed && !alt);
        return new Characteristic(text, el, alt, cols, fixed && !alt ? 0 : period, fixed && !alt ? 0 : cycle, flat, oc, base, tm);
    }

    /** Hängt die Lichtzeiten eines Elements ab t an; gibt das Ende des letzten Lichts zurück. */
    private static double append(Element x, double t, double period, List<double[]> on, Timing tm, boolean alone) throws ParseException {
        switch (x.kind()) {
            case FL, FFL, LFL -> {
                double d = x.kind() == Kind.LFL ? tm.longFlash() : tm.flash();
                double gap = x.kind() == Kind.LFL ? tm.groupGap() : tm.flashGap();
                for (int g = 0; g < x.groups().length; g++) {
                    if (g > 0) t += tm.groupGap();
                    for (int i = 0; i < x.groups()[g]; i++) {
                        if (i > 0) t += gap;
                        on.add(new double[]{t, t + d});
                        t += d;
                    }
                }
                return t;
            }
            case Q, VQ, UQ -> {
                double[] q = quick(x.kind(), tm);
                int n = x.groups().length == 0 ? 1 : x.groups()[0];
                for (int i = 0; i < n; i++) {
                    on.add(new double[]{t, t + q[1]});
                    t += i < n - 1 ? q[0] : q[1];
                }
                return t;
            }
            case IQ, IVQ, IUQ -> {
                Kind base = x.kind() == Kind.IQ ? Kind.Q : x.kind() == Kind.IVQ ? Kind.VQ : Kind.UQ;
                double[] q = quick(base, tm);
                double span = period * 0.65;
                int n = Math.max(1, (int) Math.floor((span - q[1]) / q[0]) + 1);
                for (int i = 0; i < n; i++) {
                    on.add(new double[]{t, t + q[1]});
                    t += i < n - 1 ? q[0] : q[1];
                }
                return t;
            }
            case ISO -> {
                on.add(new double[]{t, t + period / 2});
                return t + period / 2;
            }
            case OC -> {
                int n = x.groups()[0];
                double e = tm.eclipse(), light = 2 * e;
                if (n == 1 && x.groups().length == 1) e = Math.min(period / 4, Math.max(e, period / 4));
                double need = n * e + (n - 1) * light;
                if (need > period - e) {
                    double s = (period - e) / need * 0.999;
                    e *= s;
                    light *= s;
                }
                double c = t;
                for (int i = 0; i < n; i++) {
                    c += e;
                    double end = i < n - 1 ? c + light : period;
                    on.add(new double[]{c, end});
                    c = end;
                }
                return period;
            }
            case MO -> {
                String code = Morse.code(x.letter());
                for (int i = 0; i < code.length(); i++) {
                    if (i > 0) t += tm.dot();
                    double d = code.charAt(i) == '.' ? tm.dot() : tm.dash();
                    on.add(new double[]{t, t + d});
                    t += d;
                }
                return t;
            }
            default -> throw new ParseException(x.kind().german + " lässt sich nicht kombinieren", 0);
        }
    }

    /** Funkeln: {Takt, Lichtdauer} in s. */
    static double[] quick(Kind k, Timing tm) {
        return switch (k) {
            case VQ -> new double[]{tm.quick() / 2, Math.min(tm.quickOn(), 0.2)};
            case UQ -> new double[]{tm.quick() / 4, Math.min(tm.quickOn(), 0.1)};
            default -> new double[]{tm.quick(), tm.quickOn()};
        };
    }

    static int gcd(int a, int b) { return b == 0 ? Math.max(1, a) : gcd(b, a % b); }

    static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String normalized(List<Element> el, boolean alt, LightColour[] cols, double period, boolean noPeriod) {
        StringBuilder b = new StringBuilder(alt ? "Al." : "");
        for (int i = 0; i < el.size(); i++) b.append(i > 0 ? "+" : "").append(el.get(i));
        b.append(' ');
        for (LightColour c : cols) b.append(c.code());
        if (!noPeriod && period > 0 && !(el.size() == 1 && el.get(0).kind() == Kind.F && !alt)) b.append(' ').append(num(period)).append('s');
        return b.toString();
    }

    // ------------------------------------------------------------------ Abfragen

    /**
     * Helligkeit 0..1 zur Zeit t (s): 1 in den Lichtzeiten, sonst {@link #base}. Die Flanken sind so lang wie die
     * Trägheit der Lampe ({@link Timing#lamp}), symmetrisch um die Schaltzeit, damit das Mittel genau der Lichtanteil bleibt.
     */
    public double level(double t) {
        if (cycle <= 0) return 1;
        double tau = timing.lamp();
        double u = t - Math.floor(t / cycle) * cycle, sum = 0;
        for (int k = -1; k <= 1; k++) {
            double x = u + k * cycle;
            for (int i = 0; i < on.length; i += 2) sum += ramp(x - on[i], tau) - ramp(x - on[i + 1], tau);
        }
        return Math.max(base, Math.min(1, sum));
    }

    static double ramp(double x, double tau) {
        if (tau <= 0) return x >= 0 ? 1 : 0;
        double s = Math.max(0, Math.min(1, x / tau + 0.5));
        return s * s * (3 - 2 * s);
    }

    /** Farbe zur Zeit t (bei Wechselfeuern die der nächsten Lichtzeit, sonst die erste Farbe). */
    public LightColour colourAt(double t) {
        if (!alternating || cycle <= 0) return colours[0];
        double u = t - Math.floor(t / cycle) * cycle;
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < on.length; i += 2) {
            double d = u < on[i] ? on[i] - u : u > on[i + 1] ? u - on[i + 1] : 0;
            if (d < bd) { bd = d; best = i / 2; }
        }
        return onColour[best];
    }

    /** Lichtanteil einer Wiederkehr (1 bei Festfeuer). */
    public double dutyCycle() {
        if (cycle <= 0) return 1;
        double s = 0;
        for (int i = 0; i < on.length; i += 2) s += on[i + 1] - on[i];
        return base + (1 - base) * s / cycle;
    }

    /** Zahl der Lichtzeiten in einer Periode (bei Wechselfeuern in der ersten). */
    public int flashesPerPeriod() {
        if (cycle <= 0) return 0;
        int n = 0;
        for (int i = 0; i < on.length; i += 2) if (on[i] < period - 1e-9) n++;
        return n;
    }

    /** Ob die Kennung aus kurzen Blitzen besteht (dann kann sie auch eine drehende Linse erzeugen). */
    public boolean flashing() {
        for (Element e : elements) if (e.kind() == Kind.F || e.kind() == Kind.ISO || e.kind() == Kind.OC || e.kind() == Kind.FFL) return false;
        return cycle > 0;
    }

    /** Längste Lichtzeit in s. */
    public double longestOn() {
        double m = 0;
        for (int i = 0; i < on.length; i += 2) m = Math.max(m, Math.min(on[i + 1], 1e9) - on[i]);
        return m;
    }

    /** „Blitzgruppe (3), weiß, Wiederkehr 15 s“ */
    public String describe() {
        StringBuilder b = new StringBuilder();
        if (alternating) b.append("Wechselfeuer ");
        for (int i = 0; i < elements.size(); i++) {
            Element e = elements.get(i);
            if (i > 0) b.append(" + ");
            boolean grp = e.kind() != Kind.MO && e.groups().length > 0 && !(e.groups().length == 1 && e.groups()[0] == 1);
            b.append(grp ? e.kind().group : e.kind().german);
            if (e.kind() == Kind.MO) b.append(" „").append(e.letter()).append('“');
            else if (grp) {
                b.append(" (");
                for (int g = 0; g < e.groups().length; g++) b.append(g > 0 ? "+" : "").append(e.groups()[g]);
                b.append(')');
            }
        }
        b.append(", ");
        for (int i = 0; i < colours.length; i++) b.append(i > 0 ? "/" : "").append(colours[i].german);
        if (sectored) b.append(" (Sektoren)");
        if (period > 0) b.append(", Wiederkehr ").append(num(period)).append(" s");
        return b.toString();
    }

    /** Die Lichtzeiten als Text: „0,0–0,5 · 1,5–2,0 · …“ */
    public String timesText() {
        if (cycle <= 0) return "dauernd";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < on.length; i += 2) b.append(i > 0 ? " · " : "").append(String.format(Locale.GERMAN, "%.1f–%.1f", on[i], on[i + 1]));
        return b.toString();
    }

    @Override public String toString() { return text; }

    // ------------------------------------------------------------------ Lexer

    private static final class Lexer {
        final String s;
        int pos;
        Lexer(String s) { this.s = s.trim(); }
        void skip() { while (pos < s.length() && (s.charAt(pos) == ' ' || s.charAt(pos) == '\t' || s.charAt(pos) == '.')) pos++; }
        boolean peekWord(String w) { return s.regionMatches(true, pos, w, 0, w.length()) && (pos + w.length() >= s.length() || !Character.isLetter(s.charAt(pos + w.length())) || s.charAt(pos + w.length()) == '.'); }

        static final String[][] WORDS = {{"SFkl", "VQ"}, {"Fest", "F"}, {"Blz", "FL"}, {"Ubr", "OC"}, {"Glt", "ISO"}, {"Fkl", "Q"},
                {"IVQ", "IVQ"}, {"IUQ", "IUQ"}, {"IQ", "IQ"}, {"VQ", "VQ"}, {"UQ", "UQ"}, {"LFl", "LFL"}, {"FFl", "FFL"}, {"Fl", "FL"},
                {"Iso", "ISO"}, {"Oc", "OC"}, {"Mo", "MO"}, {"Q", "Q"}, {"F", "F"}};

        Kind kind() {
            for (String[] w : WORDS) {
                int n = w[0].length();
                if (!s.regionMatches(true, pos, w[0], 0, n)) continue;
                // die Groß- und Kleinschreibung der IALA-Kürzel zählt beim ersten Buchstaben nicht, aber „F“ vor einer Farbe soll kein „Fl“ werden
                boolean caseOk = s.regionMatches(false, pos, w[0], 0, n) || w[0].length() > 1;
                if (!caseOk) continue;
                int after = pos + n;
                if (after < s.length() && Character.isLetter(s.charAt(after)) && Character.isLowerCase(s.charAt(after))) continue;
                pos = after;
                return Kind.valueOf(w[1]);
            }
            return null;
        }

        int integer() throws ParseException {
            int st = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            if (st == pos) throw new ParseException("Zahl erwartet", pos);
            return Integer.parseInt(s.substring(st, pos));
        }

        double number() throws ParseException {
            int st = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == ',' || (s.charAt(pos) == '.' && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))))) pos++;
            if (st == pos) throw new ParseException("Zahl erwartet", pos);
            return Double.parseDouble(s.substring(st, pos).replace(',', '.'));
        }
    }

    /** Morsezeichen (Punkt, Strich) für die Buchstaben, die Seezeichen nutzen. */
    static final class Morse {
        private static final String[] CODE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--",
                "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};
        static String code(char c) { return c >= 'A' && c <= 'Z' ? CODE[c - 'A'] : null; }
    }

    /** Für Tests: die Lichtzeiten als Liste. */
    public List<double[]> intervals() {
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < on.length; i += 2) l.add(Arrays.copyOfRange(on, i, i + 2));
        return l;
    }
}
