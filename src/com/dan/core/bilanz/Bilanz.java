package com.dan.core.bilanz;

import java.util.Locale;

/**
 * Eine Bilanz (Werkbuch HalligHooge Teil A, 5.8): der Bestand einer erhaltenen Größe – Volumen je Gitter,
 * Sedimentmasse je Fraktion, Wellenenergie – und ihre Posten (Zufluss, Abfluss, Regen, Siel; Eintrag, Verlust). Nach
 * jedem Kopplungsschritt wird gebucht: Der Bestand muss sich um die Summe der Posten geändert haben.
 * <p>
 * Liegt der aufgelaufene Rest über dem Grenzwert, ist das ein Fehler ({@link Bilanzfehler}), keine Warnung. Der
 * Grenzwert ist relativ zum Bezug: dem größten Bestand oder dem aufgelaufenen Umsatz (Summe der Beträge aller
 * Posten), je nachdem, was größer ist. Bei der Tide ist das der Tidehub als Volumen.
 * <p>
 * Posten sind über den Schritt aufsummierte Mengen in der Einheit des Bestands, mit Vorzeichen: was hereinkommt
 * positiv, was hinausgeht negativ.
 */
public final class Bilanz {
    public final String name, einheit;
    public final String[] posten;
    /** Größter erlaubter Rest relativ zum Bezug. */
    public double grenze;
    /** Kleinster Bezug: Darunter gilt der Rest absolut gegen grenze · {@code mindestbezug}. */
    public double mindestbezug = 1e-300;
    private double zeit = Double.NaN, bestand, rest, groessterRest, umsatz, groessterBestand;
    private final double[] summen;
    private int buchungen;

    /**
     * @param grenze größter erlaubter Rest relativ zum Bezug, etwa 1·10⁻⁵ für das Volumen je Tide auf der Karte
     * @param posten Namen der Posten, etwa „Zufluss“, „Abfluss“, „Regen“, „Siel“
     */
    public Bilanz(String name, String einheit, double grenze, String... posten) {
        this.name = name;
        this.einheit = einheit;
        this.grenze = grenze;
        this.posten = posten.clone();
        summen = new double[posten.length];
    }

    /** Anfangsbestand zur Zeit t. */
    public void beginn(double t, double bestand) {
        zeit = t;
        this.bestand = bestand;
        groessterBestand = Math.abs(bestand);
        rest = groessterRest = umsatz = 0;
        java.util.Arrays.fill(summen, 0);
        buchungen = 0;
    }

    /**
     * Bucht einen Schritt bis zur Zeit t: neuer Bestand und die Posten des Schritts. Gibt den Rest dieses Schritts
     * zurück (Bestand − alter Bestand − Summe der Posten).
     *
     * @throws Bilanzfehler wenn der aufgelaufene Rest über dem Grenzwert liegt
     */
    public double buche(double t, double bestandNeu, double... werte) {
        if (Double.isNaN(zeit)) throw new IllegalStateException("Bilanz " + name + ": beginn fehlt");
        if (werte.length != posten.length) throw new IllegalArgumentException("Bilanz " + name + ": " + werte.length + " Werte für " + posten.length + " Posten");
        double s = 0;
        for (int i = 0; i < werte.length; i++) {
            s += werte[i];
            summen[i] += werte[i];
            umsatz += Math.abs(werte[i]);
        }
        double r = bestandNeu - bestand - s;
        rest += r;
        groessterRest = Math.max(groessterRest, Math.abs(rest));
        bestand = bestandNeu;
        groessterBestand = Math.max(groessterBestand, Math.abs(bestandNeu));
        zeit = t;
        buchungen++;
        if (Double.isNaN(rest) || Math.abs(rest) > grenze * bezug())
            throw new Bilanzfehler(String.format(Locale.ROOT, "Bilanz %s zur Zeit %.1f s: Rest %.6g %s, erlaubt %.6g (%.1e des Bezugs %.6g)", name, t, rest, einheit,
                    grenze * bezug(), grenze, bezug()));
        return r;
    }

    /** Woran der Rest gemessen wird: größter Bestand oder aufgelaufener Umsatz. */
    public double bezug() { return Math.max(mindestbezug, Math.max(groessterBestand, umsatz)); }

    /** Aufgelaufener Rest. */
    public double rest() { return rest; }

    /** Größter Betrag des aufgelaufenen Rests relativ zum heutigen Bezug. */
    public double relativ() { return groessterRest / bezug(); }

    public double bestand() { return bestand; }
    public double summe(int posten) { return summen[posten]; }
    public int buchungen() { return buchungen; }

    /** Kopfzeile für {@code bilanzen.csv}. */
    public String kopf() { return "bilanz;zeit_s;bestand_" + einheit + ";" + String.join(";", posten) + ";rest;rest_relativ"; }

    /** Der Stand als Zeile für {@code bilanzen.csv}: Bestand, aufgelaufene Posten, aufgelaufener Rest. */
    public String zeile() {
        StringBuilder b = new StringBuilder(name).append(';').append(zeit).append(';').append(bestand);
        for (double s : summen) b.append(';').append(s);
        return b.append(';').append(rest).append(';').append(rest / bezug()).toString();
    }
}
