package com.dan.lauf;

import com.dan.core.raum.Blick;
import com.dan.core.szene.Seekrank;

/**
 * Die Kamera an Bord eines Schwimmers (Seegang Werkbuch, S7c, Datei 89, SF110 A): Das Auge sitzt fest am Rumpf, wie er in
 * diesem Bild gezeichnet wird ({@link Flotte#bildLagen}); die Kimm rollt und stampft mit.
 * <ul>
 * <li><b>Ruhiger Blick</b> (SF11): Der Kopf gleicht das Rollen zu {@link #AUSGLEICH_ROLLEN} und das Stampfen zu
 * {@link #AUSGLEICH_STAMPFEN} aus, mit dem Verzug {@link #VERZUG} s (Tiefpass erster Ordnung); das Tauchen nicht [G].
 * Ohne Verzug bliebe die Kimm bei 30 % des Rollens; mit ihm bleibt mehr: |1 − 0,7 / (1 + iωτ)| (bei T = 5,3 s rund 41 %).</li>
 * <li><b>Seekrank</b>: die senkrechte Beschleunigung am Auge aus der Lage dreier Bilder, gewichtet nach ISO 2631-1
 * ({@link Seekrank}).</li>
 * </ul>
 * Richtung und Neigung der Ansicht gelten gegen den Bug und die Waagerechte des Schiffs.
 */
public final class Bordkamera implements Kamera {
    public static final String VERSION = "0.2";
    public static final double AUSGLEICH_ROLLEN = 0.7, AUSGLEICH_STAMPFEN = 0.5, VERZUG = 0.3, UEBER_DECK = 1.6;

    private final Flotte flotte;
    public final int nr;
    /** Auge im Körper (m) und Blick gegen den Bug (Grad), Bildwinkel. */
    public final double[] auge;
    public double richtung, neigung, bildwinkel = 60;
    /** Seit dem letzten Bild: Krängung und Trimm des Rumpfs, Rollen und Neigung der Kimm im Bild (Grad); Auge (UTM, m NHN). */
    public double kraengung, trimm, rollenBild, neigungBild;
    public final double[] augeWelt = new double[3];
    public final Seekrank seekrank = new Seekrank(1.0 / 30);
    private double rollF = Double.NaN, trimmF = Double.NaN, z1 = Double.NaN, z2 = Double.NaN, dtAlt = Double.NaN;
    /** Seit dem letzten Bild: senkrechte Beschleunigung am Auge (m/s²; NaN in den ersten zwei Bildern). */
    public double az = Double.NaN;
    /** Seit S8a (Seegang Werkbuch 104, SF111 A): die Scheibe vor der Kamera; Gischt, die das Auge erreicht, setzt Tropfen. */
    public final Linsenglas linse;
    private double tLinse = Double.NaN;

    /** An Bord des Schwimmers nr der Flotte; auge null: die Vorgabe der Art. */
    public Bordkamera(Flotte flotte, int nr, double[] auge) {
        this.flotte = flotte;
        this.nr = nr;
        this.auge = auge != null ? auge.clone() : vorgabe(flotte.schwimmer.get(nr));
        this.linse = new Linsenglas(1885 + 31 * nr);
    }

    /**
     * Auge an Bord nach der Art [G], über dem Deck mittschiffs (wie {@code Schiffbau}): seit S7d2 (Datei 90, SF118 A) beim
     * Kutter auf dem Dach des Ruderhauses (Dach 2,2 m über Deck), der Blick geht über die Netze; bei der Fähre auf dem
     * Oberdeck vor dem Fahrgastraum (Oberdeck 5,2 m über dem Autodeck, Fahrgastraum bis x = 6,5 m, Brücke darüber bis 7 m;
     * seit S7e2, Befund L5e3: im Netz nach Foto lag das alte Auge bei x = 0,5 m im Fahrgastraum). In S7c lag das Auge der Fähre im
     * Block des Oberdecks und das des Kutters hinter den Netzen (Befund L5d 5). {@code BordkameraCheck} prüft, dass es frei ist.
     */
    public static double[] vorgabe(Flotte.Schwimmer s) { return vorgabe(s.angabe.art, s.koerper); }

    /** Wie oben für eine Art (wie in {@code Hooge.szenen}) und ihren Körper. */
    public static double[] vorgabe(String art, com.dan.physics.auftrieb.Koerper k) {
        double deck = k.form instanceof com.dan.physics.auftrieb.Form.Rumpf r ? r.deck(0) : Flotte.deck(k);
        return switch (art) {
            case "krabbenkutter" -> new double[] {-2.0, 0, deck + 2.2 + UEBER_DECK};
            case "faehre" -> new double[] {7.5, 0, deck + 5.2 + UEBER_DECK};   // seit S7e2: vor dem Fahrgastraum unter der Nock (Netz nach Foto, S7e1)
            default -> new double[] {0, 0, deck + UEBER_DECK};
        };
    }

    /** Der Weltpunkt des Auges aus den Lagen eines Bilds (je Körper 16 double, Spalten) – genau wie {@link #weiter}. */
    public static double[] augeAus(double[] lagen, int nr, double[] auge) {
        int o = 16 * nr;
        double x = auge[0], y = auge[1], z = auge[2];
        return new double[] {lagen[o + 12] + x * lagen[o] + y * lagen[o + 4] + z * lagen[o + 8],
                lagen[o + 13] + x * lagen[o + 1] + y * lagen[o + 5] + z * lagen[o + 9],
                lagen[o + 14] + x * lagen[o + 2] + y * lagen[o + 6] + z * lagen[o + 10]};
    }

    @Override public Blick weiter(double dt, double szenenzeit, Blick jetzt) {
        double[] l = flotte.bildLagen();
        if (l == null || 16 * nr + 15 >= l.length || Double.isNaN(l[16 * nr + 12])) return null;
        int o = 16 * nr;
        double[] p = augeAus(l, nr, auge);
        augeWelt[0] = p[0]; augeWelt[1] = p[1]; augeWelt[2] = p[2];
        // Kurs, Krängung, Trimm aus den Achsen (Spalten: x vorn, y Backbord, z oben im Körper)
        double kurs = (Math.toDegrees(Math.atan2(l[o], l[o + 1])) + 360) % 360;
        trimm = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, l[o + 2]))));
        kraengung = Math.toDegrees(Math.atan2(l[o + 6], l[o + 10]));
        // der Kopf gleicht nach (Tiefpass mit dem Verzug)
        double a = dt > 0 ? 1 - Math.exp(-dt / VERZUG) : 0;
        if (Double.isNaN(rollF)) { rollF = kraengung; trimmF = trimm; }
        else { rollF += a * (kraengung - rollF); trimmF += a * (trimm - trimmF); }
        rollenBild = kraengung - AUSGLEICH_ROLLEN * rollF;
        neigungBild = neigung + trimm - AUSGLEICH_STAMPFEN * trimmF;
        // senkrechte Beschleunigung am Auge aus drei Bildern
        if (!Double.isNaN(z2) && dt > 0 && dtAlt > 0) {
            az = 2 * ((p[2] - z1) / dt - (z1 - z2) / dtAlt) / (dt + dtAlt);
            seekrank.schritt(az);
        } else az = Double.NaN;
        z2 = z1;
        z1 = p[2];
        dtAlt = dt;
        // seit S8a: die Scheibe vor der Kamera, in der Zeit der Szene (höchstens 0,25 s je Bild)
        double dtS = Double.isNaN(tLinse) ? 0 : szenenzeit - tLinse;
        linse.schritt(dtS > 0 && dtS < 1 ? Math.min(0.25, dtS) : 0, szenenzeit, p, kraengung, flotte.gischt);
        tLinse = szenenzeit;
        Blick b = jetzt.kopie();
        b.ost = p[0];
        b.nord = p[1];
        b.hoehe = p[2];
        b.richtung = (kurs + richtung + 360) % 360;
        b.neigung = neigungBild;
        b.rollen = rollenBild;
        b.bildwinkel = bildwinkel;
        return b;
    }

    /** Rest des Rollens im Bild, den das Modell bei der Kreisfrequenz ω erwartet: |1 − A / (1 + iωτ)|. */
    public static double restRollen(double omega) {
        double wt = omega * VERZUG, d = 1 + wt * wt;
        double re = 1 - AUSGLEICH_ROLLEN / d, im = AUSGLEICH_ROLLEN * wt / d;
        return Math.hypot(re, im);
    }
}
