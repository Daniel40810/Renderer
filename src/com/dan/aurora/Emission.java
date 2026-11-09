package com.dan.aurora;

/**
 * Die vier Lichter des Polarlichts aus der Ionisationsrate und der Zusammensetzung der Luft:
 * <ul>
 * <li><b>427,8 nm</b> (N₂⁺ 1NG, blau-violett): sofort, je N₂-Ionisation, unten am Vorhang am stärksten.</li>
 * <li><b>557,7 nm</b> (O(¹S), grün): Lebensdauer 0,7 s, Anregung wächst mit dem Anteil atomaren Sauerstoffs, unterhalb
 * 95 km von O₂ gelöscht.</li>
 * <li><b>630,0 nm</b> (O(¹D), rot): Lebensdauer 110 s, unterhalb 200 km durch Stöße mit N₂ gelöscht, darum nur oben;
 * aus direkter Anregung und aus dem Zerfall von O(¹S).</li>
 * <li><b>670 nm</b> (N₂ 1PG, rot, als eine Linie für die sichtbaren Banden 600–700 nm): sofort, je N₂-Anregung; mit dem
 * Blau zusammen der purpurne untere Saum heller Vorhänge.</li>
 * </ul>
 * Ausbeuten je Ionenpaar nach Rees und Luckey (1974) und Rees (1989), Löschraten nach Streit u. a. (1976) und Rees (1989),
 * Übergangsraten nach Wiese, Fuhr und Deters (1996). Die Zahlen sind so gewählt, dass 557,7/427,8 bei 3 bis 7 und
 * 630,0/557,7 bei harten Spektren um 0,2, bei weichen über 1 liegt, wie beobachtet.
 */
public final class Emission {
    public static final int BLAU = 0, GRUEN = 1, ROT = 2, PURPUR = 3, LINIEN = 4;
    /** Wellenlängen in nm. */
    public static final double[] WELLENLAENGE = {427.8, 557.7, 630.0, 670.0};
    public static final String[] NAME = {"427,8 nm N₂⁺", "557,7 nm O(¹S)", "630,0 nm O(¹D)", "670 nm N₂ 1PG"};

    /** Photonen je Ionenpaar: 427,8 je N₂-Ionisation; sichtbares 1PG je N₂-Ionisation; O(¹S) Grund und mit dem O-Anteil; O(¹D) direkt je O-Anteil. */
    public double ausbeuteBlau = 0.021, ausbeutePurpur = 0.025, ausbeuteGruen0 = 0.050, ausbeuteGruenO = 0.12, ausbeuteRot = 1.2;
    /** O(¹S): Übergangsrate 1/s (0,74 s) und Löschung durch O₂ in cm³/s. */
    public double aGruen = 1.35, kGruenO2 = 3.0e-13;
    /** O(¹D): Übergangsrate der Linie 630,0 und des ganzen Zustands (630,0 + 636,4) in 1/s; Löschung durch N₂, O₂, O in cm³/s. */
    public double aRot = 5.63e-3, aRotGesamt = 7.45e-3, kRotN2 = 2.0e-11, kRotO2 = 2.9e-11, kRotO = 2.5e-12;
    /** Relative Ionisationsquerschnitte von N₂, O₂ und O (nach der Zahl der Elektronen). */
    public double wN2 = 1.0, wO2 = 1.14, wO = 0.57;

    /** Volumenemission in Photonen je cm³ und s, [Linie][Höhe] auf {@link Niederschlag#HOEHEN}, aus der Ionisationsrate q. */
    public double[][] profile(double[] q) {
        int n = Niederschlag.HOEHEN.length;
        double[][] p = new double[LINIEN][n];
        for (int i = 0; i < n; i++) {
            double h = Niederschlag.HOEHEN[i];
            double n2 = Atmosphaere.n2(h), o2 = Atmosphaere.o2(h), o = Atmosphaere.o(h), t = Atmosphaere.temperatur(h);
            double fN2 = wN2 * n2 / (wN2 * n2 + wO2 * o2 + wO * o), xO = o / (n2 + o2 + o);
            double qi = q[i];
            p[BLAU][i] = ausbeuteBlau * fN2 * qi;
            p[PURPUR][i] = ausbeutePurpur * fN2 * qi;
            double gruen = (ausbeuteGruen0 + ausbeuteGruenO * xO) * qi * aGruen / (aGruen + kGruenO2 * o2);
            p[GRUEN][i] = gruen;
            double kN2 = kRotN2 * Math.exp(107.8 / t), kO2 = kRotO2 * Math.exp(67.5 / t);
            double anregung = ausbeuteRot * xO * qi + gruen;
            p[ROT][i] = anregung * aRot / (aRotGesamt + kN2 * n2 + kO2 * o2 + kRotO * o);
        }
        return p;
    }

    /** Senkrechte Säulenrate je Linie in kR (1 R = 10⁶ Photonen je cm² und s in der Säule). */
    public static double[] saeuleKr(double[][] profile) {
        double[] s = new double[LINIEN];
        for (int l = 0; l < LINIEN; l++) s[l] = Niederschlag.saeule(profile[l]) / 1e9;
        return s;
    }

    /** Internationale Helligkeitsklasse nach der Säule in 557,7 nm: I = 1 kR (Milchstraße), II = 10, III = 100 (Mondwolken), IV = 1000 (Vollmondlicht). */
    public static String ibc(double kr557) {
        if (kr557 < 0.3) return "unter IBC I";
        if (kr557 < 3) return "IBC I";
        if (kr557 < 30) return "IBC II";
        if (kr557 < 300) return "IBC III";
        return "IBC IV";
    }
}
