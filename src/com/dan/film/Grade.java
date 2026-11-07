package com.dan.film;

/**
 * Lichtbestimmung im Kopierwerk: Kopierlicht je Farbe in Punkten. Ein Punkt ist 0,025 log E (eine Zwölftelblende) mehr
 * Licht dieser Farbe auf die Kopie; 25-25-25 ist die Mitte. Mehr rotes Licht macht in der Kopie mehr Cyan, also weniger
 * Rot: Wer wärmer will, gibt weniger Rot und mehr Blau. Dichte verschiebt alle drei gleich (mehr Punkte: dunkler). So hat
 * man bis zum Digital Intermediate Filme bestimmt – und mehr als die Balance und die Dichte kann ein Kopierlicht nicht.
 */
public record Grade(String name, double red, double green, double blue, double density) {
    public static final double POINT = 0.025;

    public static final Grade NEUTRAL = new Grade("neutral 25-25-25", 25, 25, 25, 0);
    /** 9.7.1958 abends: warm, der goldene Himmel [G]. */
    public static final Grade ABEND_1958 = new Grade("Abend 1958 (warm) 23-25-27", 23, 25, 27, 0);
    /** Die Welle: kühler und dichter [G]. */
    public static final Grade WELLE = new Grade("Die Welle (kühl, dicht) 26-25-24 +1", 26, 25, 24, 1);
    /** Der Morgen danach: fahl und kühl, heller [G]. */
    public static final Grade MORGEN_DANACH = new Grade("Morgen danach (fahl) 26-25-23 −1", 26, 25, 23, -1);
    public static final Grade HEUTE_ABEND = new Grade("Abend heute 24-25-26", 24, 25, 26, 0);
    /** Nacht: blau und dicht, wie die Nacht im Kino [G]. */
    public static final Grade NACHT = new Grade("Nacht (blau) 27-25-22 +1", 27, 25, 22, 1);

    /** Zwischen zwei Lichtbestimmungen (t = 0: a, t = 1: b), etwa über den Sonnenstand; der Name ist der des näheren. */
    public static Grade mix(Grade a, Grade b, double t) {
        t = Math.max(0, Math.min(1, t));
        if (t == 0) return a;
        if (t == 1) return b;
        return new Grade((t < 0.5 ? a : b).name, a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t,
                a.density + (b.density - a.density) * t);
    }

    /**
     * Aus Text: „24 25 26“, „24-25-26“ oder mit Dichte „24 25 26 +1“ (Punkte, Komma als Dezimalzeichen erlaubt). Wirft
     * IllegalArgumentException mit einer Meldung.
     */
    public static Grade parse(String text) {
        String[] p = text.trim().replace(',', '.').replace('−', '-').split("[\\s/]+|(?<=\\d)-(?=\\d)");
        if (p.length < 3 || p.length > 4) throw new IllegalArgumentException("Kopierlicht: drei Zahlen (Rot Grün Blau), dazu die Dichte: „" + text + "“");
        double[] v = new double[4];
        for (int i = 0; i < p.length; i++) {
            try { v[i] = Double.parseDouble(p[i].replace("+", "")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Kopierlicht: keine Zahl „" + p[i] + "“"); }
        }
        for (int i = 0; i < 3; i++) if (v[i] < 0 || v[i] > 50) throw new IllegalArgumentException("Kopierlicht: Punkte von 0 bis 50, nicht " + p[i]);
        String n = String.format(java.util.Locale.GERMAN, "%s-%s-%s%s", fmt(v[0]), fmt(v[1]), fmt(v[2]), v[3] == 0 ? "" : (v[3] > 0 ? " +" : " −") + fmt(Math.abs(v[3])));
        return new Grade(n, v[0], v[1], v[2], v[3]);
    }

    private static String fmt(double x) { return x == Math.rint(x) ? String.valueOf((long) x) : String.format(java.util.Locale.GERMAN, "%.1f", x); }

    /** Verschiebung der Belichtung der Kopie je Kanal (log E). */
    public double[] offsets() {
        return new double[]{POINT * (red - 25 + density), POINT * (green - 25 + density), POINT * (blue - 25 + density)};
    }

    @Override public String toString() { return name; }
}
