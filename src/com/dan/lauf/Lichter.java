package com.dan.lauf;

import com.dan.core.szene.Kennung;
import com.dan.physics.auftrieb.Koerper;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Lichter der Schwimmer (Seegang Werkbuch, S7e1, Datei 99; SF148 A): je Schwimmer eine Liste fester Lichter im Körper,
 * nach dem Zustand an oder aus, für {@code render.schwimmer.Lichterbild}.
 * <ul>
 * <li><b>In Fahrt</b> (der Lotse kommt oder fährt ab; KVR Regel 23): Topplicht weiß, 225° nach vorn; Seitenlichter
 * grün an Steuerbord und rot an Backbord, je 112,5° von voraus bis 22,5° achterlicher als querab; Hecklicht weiß, 135°
 * nach achtern.</li>
 * <li><b>Vor Anker</b> (Regel 30): ein weißes Rundumlicht.</li>
 * <li><b>Am Anleger fest:</b> Decksbeleuchtung, zwei warmweiße Lampen [G].</li>
 * <li><b>Tonnen</b> mit Kennung: das Feuer auf der Laterne, im Takt der Kennung, jede Tonne mit eigenem Versatz [G].</li>
 * </ul>
 * Lichtstärken nach KVR Anlage I, Abschnitt 8 (I = 3,43·10⁶ · T · D² · K^−D, T = 2·10⁻⁷ lx, K = 0,8): 2 sm 4,3 cd, 3 sm
 * 12 cd, 5 sm 52 cd; für Schiffe von 12 bis 50 m: Topplicht 5 sm, Seiten-, Heck- und Ankerlicht 2 sm. Tonnenfeuer 3 sm [G].
 * Decksbeleuchtung 200 cd je Lampe [G].
 */
final class Lichter {
    /** Wann ein Licht brennt. */
    static final int FAHRT = 1, ANKER = 2, FEST = 4, IMMER = 8;
    public static final double CD_2SM = 4.3, CD_3SM = 12, CD_5SM = 52, CD_DECK = 200;
    static final double[] WARM = {1, 0.78, 0.52};

    /** Ein Licht im Körper (x vorn, y Backbord, z oben ab der Unterkante), Sektor in Grad (Mitte von voraus nach Backbord positiv). */
    record Licht(double x, double y, double z, double cd, double[] rgb, double mitte, double halb, Kennung kennung, double versatz, int wann) {}

    private Lichter() { }

    /** Die Lichter eines Schwimmers nach seiner Angabe [G, Orte nach den Netzen in Schiffbau]. */
    static List<Licht> fuer(Schwimmerangabe a, Koerper k) {
        List<Licht> l = new ArrayList<>();
        double[] w = Kennung.rgb('W'), r = Kennung.rgb('R'), g = Kennung.rgb('G');
        double d = Flotte.deck(k);
        double[] ka = k.form.kasten();
        double L2 = ka[1], hb = ka[3];
        switch (a.art) {
            case "faehre" -> {
                // Topp an der Spitze des Masts, Seitenlichter an den Nocken der Brücke, Hecklicht am Spiegel (Netz S7e1)
                l.add(new Licht(1.0, 0, d + 12.8, CD_5SM, w, 0, 112.5, null, 0, FAHRT));
                l.add(new Licht(5.0, -(hb - 0.1), d + 7.6, CD_2SM, g, -56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(5.0, hb - 0.1, d + 7.6, CD_2SM, r, 56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(-L2 - 0.2, 0, d + 1.6, CD_2SM, w, 180, 67.5, null, 0, FAHRT));
                // Decksbeleuchtung unter der Decke des Autodecks, wenn sie fest liegt [G]
                l.add(new Licht(6.0, 0, d + 4.3, CD_DECK, WARM, 0, 180, null, 0, FEST));
                l.add(new Licht(-6.0, 0, d + 4.3, CD_DECK, WARM, 0, 180, null, 0, FEST));
            }
            case "adlerExpress", "haukeHaien" -> {
                l.add(new Licht(0.15 * 2 * L2, 0, d + 8.5, CD_5SM, w, 0, 112.5, null, 0, FAHRT));
                l.add(new Licht(0.2 * L2, -(hb + 0.05), d + 3.0, CD_2SM, g, -56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(0.2 * L2, hb + 0.05, d + 3.0, CD_2SM, r, 56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(-L2 - 0.2, 0, d + 1.5, CD_2SM, w, 180, 67.5, null, 0, FAHRT));
                l.add(new Licht(0.15 * 2 * L2, 0, d + 8.5, CD_2SM, w, 0, 180, null, 0, ANKER));
            }
            case "krabbenkutter" -> {
                // Ankerlicht an der Spitze des Masts (Netz: Mast bei x 3,5 bis Deck + 9 m)
                l.add(new Licht(3.5, 0, d + 9.25, CD_2SM, w, 0, 180, null, 0, ANKER));
                l.add(new Licht(3.5, 0, d + 9.25, CD_5SM, w, 0, 112.5, null, 0, FAHRT));
                l.add(new Licht(0, -(hb + 0.05), d + 2.4, CD_2SM, g, -56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(0, hb + 0.05, d + 2.4, CD_2SM, r, 56.25, 56.25, null, 0, FAHRT));
                l.add(new Licht(-L2 - 0.1, 0, d + 1.2, CD_2SM, w, 180, 67.5, null, 0, FAHRT));
            }
            case "leuchttonne" -> {
                if (a.kennung != null) {
                    Kennung ke = new Kennung(a.kennung);
                    // Laterne: Gerüst 1,8 m über dem Schwimmer, Kappe 0,3 m (Schiffbau, Leuchttonne)
                    double top = ka[5] + 2.25;
                    double versatz = ((a.name.hashCode() & 0x7fffffff) % 1000) / 1000.0 * ke.periode;
                    l.add(new Licht(0, 0, top, CD_3SM, ke.rgb(), 0, 180, ke, versatz, IMMER));
                }
            }
            default -> { }
        }
        return l;
    }
}
