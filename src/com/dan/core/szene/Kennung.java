package com.dan.core.szene;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Kennung eines Feuers in kurzer Schreibweise (Seegang Werkbuch, S7e1, Datei 99; SF148 A): {@code Fl G 4s},
 * {@code Fl(2) R 9s}, {@code Fl(5) Y 20s}, {@code Q G}, {@code VQ(3) 5s}, {@code Iso 8s}, {@code Oc R 4s},
 * {@code LFl 10s}, {@code Mo(A) 6s}, {@code F R}. Kopie von {@code buoyancy.Rhythm} in den Kern (SF3 A), mit deutschen
 * Namen. Blitz 0,5 s, langer Blitz 2 s, Q 1 s Periode (0,3 s an), VQ 0,5 s (0,2 s an), Morse-Punkt 0,5 s, Strich 1,5 s;
 * Pause zwischen den Blitzen einer Gruppe 1 s [G].
 */
public final class Kennung {
    public static final String VERSION = "0.1";
    /** Weicher Rand eines Blitzes (s): Anstieg und Abfall [G]. */
    public static final double RAND = 0.05;

    public final String text;
    /** Farbe: 'W', 'R', 'G', 'Y'. */
    public final char farbe;
    public final double periode;
    /** Art (F, Fl, LFl, Q, VQ, Iso, Oc, Mo) und Zahl der Blitze einer Gruppe (1 ohne Gruppe). */
    public final String art;
    public final int gruppe;
    private final double[] an;   // Paare Beginn, Dauer innerhalb der Periode

    static final Pattern P = Pattern.compile("^\\s*(F|Fl|LFl|Q|VQ|Iso|Oc|Mo)\\s*(?:\\(\\s*([0-9A-Za-z]+)\\s*\\))?\\s*([WRGY])?\\s*(?:(\\d+(?:[.,]\\d+)?)\\s*s)?\\s*$");
    static final String[] MORSE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};

    public Kennung(String text) {
        this.text = text.strip();
        Matcher m = P.matcher(this.text);
        if (!m.matches()) throw new IllegalArgumentException("Kennung nicht lesbar: „" + text + "“ (Beispiele: Fl G 4s, Fl(2) R 9s, Q R, Iso 8s)");
        String k = m.group(1), grp = m.group(2);
        art = k;
        farbe = m.group(3) != null ? m.group(3).charAt(0) : 'W';
        double per = m.group(4) != null ? Double.parseDouble(m.group(4).replace(',', '.')) : Double.NaN;
        java.util.List<Double> l = new java.util.ArrayList<>();
        int g = 1;
        switch (k) {
            case "F" -> { per = 1; l.add(0.0); l.add(1.0); }
            case "Iso" -> { if (Double.isNaN(per)) per = 4; l.add(0.0); l.add(per / 2); }
            case "Oc" -> { if (Double.isNaN(per)) per = 4; l.add(0.0); l.add(per * 0.75); }
            case "Mo" -> {
                if (grp == null) throw new IllegalArgumentException("Kennung: Mo braucht einen Buchstaben, etwa Mo(A) 6s");
                double t = 0;
                for (char c : grp.toUpperCase(Locale.ROOT).toCharArray()) {
                    String code = MORSE[c - 'A'];
                    for (int i = 0; i < code.length(); i++) {
                        double d = code.charAt(i) == '.' ? 0.5 : 1.5;
                        l.add(t); l.add(d);
                        t += d + 0.5;
                    }
                    t += 1;
                }
                if (Double.isNaN(per)) per = t + 2;
            }
            default -> {
                int n = grp != null ? Integer.parseInt(grp) : 1;
                g = n;
                double blitz = k.equals("LFl") ? 2 : k.equals("Q") ? 0.3 : k.equals("VQ") ? 0.2 : 0.5;
                double schritt = k.equals("Q") ? 1 : k.equals("VQ") ? 0.5 : blitz + 1;
                if (Double.isNaN(per)) per = k.equals("Q") || k.equals("VQ") ? (grp == null ? schritt : schritt * n + 2 * schritt) : 4;
                for (int i = 0; i < n; i++) { l.add(i * schritt); l.add(blitz); }
            }
        }
        gruppe = g;
        double ende = l.get(l.size() - 2) + l.get(l.size() - 1);
        if (ende > per + 1e-9) throw new IllegalArgumentException("Kennung „" + this.text + "“ passt nicht in " + per + " s");
        periode = per;
        an = new double[l.size()];
        for (int i = 0; i < an.length; i++) an[i] = l.get(i);
    }

    /** Helligkeit zur Zeit t (s), 0 … 1, mit weichem Rand {@link #RAND}. */
    public double hell(double t) {
        double p = t % periode;
        if (p < 0) p += periode;
        double best = 0;
        for (int i = 0; i < an.length; i += 2) {
            double a = an[i], b = an[i] + an[i + 1];
            double v = Math.min(1, Math.min(Math.max(0, (p - a) / RAND + 1), Math.max(0, (b - p) / RAND)));
            best = Math.max(best, Math.min(1, Math.max(0, v)));
        }
        return best;
    }

    /** Zahl der Blitze (Anstiege über 0,5) in einer Periode, gezählt an der Funktion {@link #hell} (Prüfung). */
    public int blitzeJePeriode() {
        int z = 0;
        double vor = hell(-1e-3);
        for (int i = 0; i < 2000; i++) {
            double h = hell(periode * i / 2000.0);
            if (vor < 0.5 && h >= 0.5) z++;
            vor = h;
        }
        return z;
    }

    /** Anteil der Zeit, in der es leuchtet. */
    public double anteil() {
        double s = 0;
        for (int i = 1; i < an.length; i += 2) s += an[i];
        return s / periode;
    }

    /** Farbe als linearer Anteil (Signalfarben nach IALA, gerundet) [G]. */
    public double[] rgb() { return rgb(farbe); }

    /** Farbe zu 'W', 'R', 'G', 'Y' (linear) [G]. */
    public static double[] rgb(char f) {
        return switch (f) {
            case 'R' -> new double[] {1, 0.08, 0.05};
            case 'G' -> new double[] {0.05, 1, 0.45};
            case 'Y' -> new double[] {1, 0.75, 0.1};
            default -> new double[] {1, 0.92, 0.8};
        };
    }
}
