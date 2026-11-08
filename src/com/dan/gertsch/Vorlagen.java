package com.dan.gertsch;

/**
 * Die sechs Vorlagen als Zahlen: Kennwerte der Bilder in {@code templates/}, gemessen mit {@link Messung} (HIGH, 6 Stufen, seed 1).
 * Erzeugt von {@code GertschDemo --quelltext} am 2026-11-08; nicht von Hand ändern, sondern neu messen.
 * <p>
 * Die Bilder selbst gehören nicht ins Repository: Es sind Werke von Franz Gertsch (1930–2022), urheberrechtlich
 * geschützt. Hier stehen nur die gemessenen Kennwerte.
 */
public final class Vorlagen {
    private Vorlagen() { }

    public static final String[] NAMEN = {"herbst", "winter", "graeser", "sommer", "holzschnitt", "blaetter"};
    public static final String[] TITEL = {"Herbst", "Winter", "Gräser", "Sommer", "Holzschnitt", "Blätter"};
    public static final String[] DATEIEN = {"franz-gertsch1.jpg", "franz-gertsch2.png", "franz-gertsch3.jpg", "franz-gertsch4.jpg", "franz-gertsch5.jpg", "franz-gertsch6.jpg"};
    public static final String[] BESCHREIBUNG = {
            "Unterholz im Herbst: Ocker und Orange, wenig Grün, feine Flecken, Zweige senkrecht",
            "Wald im Schnee: dunkle Zweige auf Weiß, große helle Flächen",
            "Gräser vor Schwarz: breite Halme mit weichem Verlauf und harter Kante",
            "Wald im Sommer: Laub als Flecken, Grün auf fast Schwarz, warme Lichtflecken",
            "Holzschnitt: eine Farbe, das Licht aus der Dichte gestochener Punkte",
            "Große Blätter im Gras: gezähnte Ränder mit hellem Saum, orange Punkte im Dunkel"};

    /** Vorlage nach Kurzname (etwa „sommer“), groß oder klein geschrieben. */
    public static Vorlage nachName(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "herbst" -> herbst();
            case "winter" -> winter();
            case "graeser" -> graeser();
            case "sommer" -> sommer();
            case "holzschnitt" -> holzschnitt();
            case "blaetter" -> blaetter();
            default -> throw new IllegalArgumentException("keine Vorlage " + name);
        };
    }

    /** Alle Vorlagen in der Reihenfolge von {@link #NAMEN}, jede eine frische Kopie. */
    public static Vorlage[] alle() {
        Vorlage[] v = new Vorlage[NAMEN.length];
        for (int i = 0; i < v.length; i++) v[i] = nachName(NAMEN[i]);
        return v;
    }

    /** Herbst (franz-gertsch1.jpg): Unterholz im Herbst: Ocker und Orange, wenig Grün, feine Flecken, Zweige senkrecht. */
    public static Vorlage herbst() {
        Vorlage v = new Vorlage();
        v.name = "herbst"; v.titel = "Herbst"; v.quelle = "franz-gertsch1.jpg";
        v.breite = 2000; v.hoehe = 1328; v.arbeitsbreite = 1000; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{20.34, 3.23, 8.13}, {34.77, 6.27, 18.20}, {43.35, 15.23, 31.54}, {56.14, 5.34, 22.49}, {61.89, 18.34, 39.56}, {74.42, 7.97, 29.92}},
                new double[]{0.1444, 0.1944, 0.1550, 0.1844, 0.1516, 0.1701});
        v.helligkeit = new double[]{0.0052, 0.0632, 0.1205, 0.1480, 0.1702, 0.1804, 0.1668, 0.1133, 0.0322, 0.0001};
        v.l5 = 18.08; v.l50 = 49.58; v.l95 = 77.90; v.grund = 0.0685;
        v.farbton = 69.75; v.buntheit = 26.99; v.akzent = 0.0002; v.akzentLab = new double[]{41.65, -12.98, 12.43};
        v.durchmesser = new double[]{0.00200, 0.00400, 0.00600, 0.00800, 0.01000, 0.01200, 0.01400, 0.01800, 0.02400, 0.03000, 0.03600, 0.04600, 0.05600, 0.07000};
        v.hell = new double[]{0.1728, 0.3072, 0.4022, 0.5013, 0.5527, 0.6026, 0.6391, 0.7067, 0.7698, 0.8193, 0.8604, 0.9155, 0.9552, 1.0000};
        v.dunkel = new double[]{0.1570, 0.2928, 0.4001, 0.5156, 0.5771, 0.6343, 0.6764, 0.7530, 0.8210, 0.8722, 0.9095, 0.9467, 0.9765, 1.0000};
        v.hellD50 = 0.00797; v.dunkelD50 = 0.00773; v.fleckHell = 0.9866; v.fleckDunkel = 1.0000;
        v.richtung = new double[]{0.0468, 0.0550, 0.0705, 0.0762, 0.0911, 0.1180, 0.1643, 0.1383, 0.0852, 0.0601, 0.0492, 0.0454};
        v.senkrecht = 0.5118; v.kohaerenz = 0.5056; v.hauptrichtung = 94.03;
        return v;
    }

    /** Winter (franz-gertsch2.png): Wald im Schnee: dunkle Zweige auf Weiß, große helle Flächen. */
    public static Vorlage winter() {
        Vorlage v = new Vorlage();
        v.name = "winter"; v.titel = "Winter"; v.quelle = "franz-gertsch2.png";
        v.breite = 474; v.hoehe = 321; v.arbeitsbreite = 474; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{11.87, 2.50, 1.70}, {26.50, 2.48, 2.14}, {41.35, 2.37, 2.23}, {55.50, 2.31, 2.15}, {69.42, 2.17, 1.81}, {83.93, 1.79, 0.58}},
                new double[]{0.1481, 0.1425, 0.1594, 0.1594, 0.1454, 0.2453});
        v.helligkeit = new double[]{0.0492, 0.1067, 0.0971, 0.1029, 0.1117, 0.1139, 0.1095, 0.1030, 0.1993, 0.0067};
        v.l5 = 10.08; v.l50 = 52.84; v.l95 = 86.92; v.grund = 0.1559;
        v.farbton = 36.77; v.buntheit = 3.13; v.akzent = 0.0000; v.akzentLab = new double[]{0.00, 0.00, 0.00};
        v.durchmesser = new double[]{0.00422, 0.00844, 0.01266, 0.01688, 0.02110, 0.02532, 0.02954, 0.03797, 0.05063, 0.06329, 0.07173};
        v.hell = new double[]{0.2244, 0.3709, 0.4807, 0.6047, 0.6707, 0.7333, 0.7745, 0.8402, 0.9055, 0.9621, 1.0000};
        v.dunkel = new double[]{0.2238, 0.3660, 0.4772, 0.5954, 0.6634, 0.7320, 0.7817, 0.8639, 0.9279, 0.9768, 1.0000};
        v.hellD50 = 0.01331; v.dunkelD50 = 0.01347; v.fleckHell = 0.7733; v.fleckDunkel = 0.9894;
        v.richtung = new double[]{0.0589, 0.0497, 0.0530, 0.0436, 0.0402, 0.1013, 0.1595, 0.1640, 0.1138, 0.0841, 0.0705, 0.0613};
        v.senkrecht = 0.4650; v.kohaerenz = 0.5965; v.hauptrichtung = 113.61;
        return v;
    }

    /** Gräser (franz-gertsch3.jpg): Gräser vor Schwarz: breite Halme mit weichem Verlauf und harter Kante. */
    public static Vorlage graeser() {
        Vorlage v = new Vorlage();
        v.name = "graeser"; v.titel = "Gräser"; v.quelle = "franz-gertsch3.jpg";
        v.breite = 2000; v.hoehe = 1412; v.arbeitsbreite = 1000; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{7.86, -0.41, 0.63}, {31.60, -8.57, 8.48}, {33.62, -2.98, 29.69}, {52.93, -13.22, 26.52}, {55.33, 4.99, 53.31}, {78.71, -6.39, 15.50}},
                new double[]{0.3964, 0.1370, 0.0650, 0.1070, 0.0515, 0.2430});
        v.helligkeit = new double[]{0.3189, 0.0840, 0.0775, 0.0832, 0.0901, 0.0679, 0.0476, 0.1185, 0.1122, 0.0000};
        v.l5 = 5.06; v.l50 = 32.25; v.l95 = 82.89; v.grund = 0.4029;
        v.farbton = 108.43; v.buntheit = 14.57; v.akzent = 0.0061; v.akzentLab = new double[]{43.96, -12.86, -7.47};
        v.durchmesser = new double[]{0.00200, 0.00400, 0.00600, 0.00800, 0.01000, 0.01200, 0.01400, 0.01800, 0.02400, 0.03000, 0.03600, 0.04600, 0.05600, 0.07000};
        v.hell = new double[]{0.0087, 0.0185, 0.0331, 0.0609, 0.0893, 0.1288, 0.1644, 0.2354, 0.3186, 0.4034, 0.4845, 0.6186, 0.7571, 1.0000};
        v.dunkel = new double[]{0.0103, 0.0223, 0.0378, 0.0616, 0.0809, 0.1039, 0.1276, 0.1850, 0.2757, 0.3734, 0.4839, 0.6574, 0.8118, 1.0000};
        v.hellD50 = 0.03716; v.dunkelD50 = 0.03693; v.fleckHell = 0.7567; v.fleckDunkel = 0.4308;
        v.richtung = new double[]{0.0372, 0.0630, 0.0656, 0.0719, 0.0672, 0.1072, 0.1524, 0.1702, 0.1147, 0.0776, 0.0359, 0.0369};
        v.senkrecht = 0.4971; v.kohaerenz = 0.9539; v.hauptrichtung = 101.75;
        return v;
    }

    /** Sommer (franz-gertsch4.jpg): Wald im Sommer: Laub als Flecken, Grün auf fast Schwarz, warme Lichtflecken. */
    public static Vorlage sommer() {
        Vorlage v = new Vorlage();
        v.name = "sommer"; v.titel = "Sommer"; v.quelle = "franz-gertsch4.jpg";
        v.breite = 2000; v.hoehe = 1328; v.arbeitsbreite = 1000; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{12.09, -0.98, 0.95}, {22.81, -4.10, 4.01}, {35.29, -7.04, 7.03}, {49.40, -7.15, 10.19}, {64.80, -7.46, 10.22}, {79.88, -4.03, 17.75}},
                new double[]{0.2354, 0.2203, 0.1826, 0.1549, 0.1235, 0.0833});
        v.helligkeit = new double[]{0.0695, 0.2226, 0.1798, 0.1376, 0.1134, 0.0968, 0.0811, 0.0581, 0.0361, 0.0051};
        v.l5 = 8.95; v.l50 = 31.86; v.l95 = 78.12; v.grund = 0.2921;
        v.farbton = 125.49; v.buntheit = 9.49; v.akzent = 0.0052; v.akzentLab = new double[]{43.30, 13.15, 22.55};
        v.durchmesser = new double[]{0.00200, 0.00400, 0.00600, 0.00800, 0.01000, 0.01200, 0.01400, 0.01800, 0.02400, 0.03000, 0.03600, 0.04600, 0.05600, 0.07000};
        v.hell = new double[]{0.1797, 0.3518, 0.4739, 0.5914, 0.6505, 0.7040, 0.7408, 0.8047, 0.8568, 0.8932, 0.9228, 0.9554, 0.9767, 1.0000};
        v.dunkel = new double[]{0.0829, 0.1770, 0.2663, 0.3744, 0.4401, 0.5091, 0.5609, 0.6607, 0.7603, 0.8331, 0.8871, 0.9376, 0.9705, 1.0000};
        v.hellD50 = 0.00644; v.dunkelD50 = 0.01174; v.fleckHell = 0.9647; v.fleckDunkel = 1.0000;
        v.richtung = new double[]{0.1306, 0.1106, 0.0832, 0.0581, 0.0475, 0.0639, 0.0741, 0.0585, 0.0681, 0.0797, 0.0979, 0.1276};
        v.senkrecht = 0.2441; v.kohaerenz = 0.4961; v.hauptrichtung = 179.03;
        return v;
    }

    /** Holzschnitt (franz-gertsch5.jpg): Holzschnitt: eine Farbe, das Licht aus der Dichte gestochener Punkte. */
    public static Vorlage holzschnitt() {
        Vorlage v = new Vorlage();
        v.name = "holzschnitt"; v.titel = "Holzschnitt"; v.quelle = "franz-gertsch5.jpg";
        v.breite = 1438; v.hoehe = 1000; v.arbeitsbreite = 1000; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{39.91, -15.75, -13.06}, {44.99, -16.27, -13.96}, {45.45, -16.09, -10.30}, {50.64, -15.45, -10.76}, {57.07, -12.78, -7.96}, {66.95, -8.41, -4.09}},
                new double[]{0.2281, 0.2537, 0.1811, 0.2017, 0.1030, 0.0323});
        v.helligkeit = new double[]{0.0000, 0.0000, 0.0001, 0.0988, 0.6507, 0.2018, 0.0422, 0.0052, 0.0008, 0.0003};
        v.l5 = 38.60; v.l50 = 45.84; v.l95 = 59.83; v.grund = 0.0000;
        v.farbton = 216.87; v.buntheit = 19.30; v.akzent = 0.0000; v.akzentLab = new double[]{0.00, 0.00, 0.00};
        v.durchmesser = new double[]{0.00200, 0.00400, 0.00600, 0.00800, 0.01000, 0.01200, 0.01400, 0.01800, 0.02400, 0.03000, 0.03600, 0.04600, 0.05600, 0.07000};
        v.hell = new double[]{0.2178, 0.3327, 0.4127, 0.4969, 0.5398, 0.5797, 0.6094, 0.6671, 0.7276, 0.7761, 0.8215, 0.8800, 0.9313, 1.0000};
        v.dunkel = new double[]{0.1012, 0.1545, 0.1981, 0.2570, 0.2949, 0.3368, 0.3722, 0.4476, 0.5331, 0.6170, 0.6904, 0.7895, 0.8854, 1.0000};
        v.hellD50 = 0.00815; v.dunkelD50 = 0.02168; v.fleckHell = 1.0000; v.fleckDunkel = 1.0000;
        v.richtung = new double[]{0.0673, 0.0642, 0.0753, 0.0776, 0.0810, 0.1731, 0.1315, 0.0550, 0.0615, 0.0675, 0.0784, 0.0676};
        v.senkrecht = 0.4406; v.kohaerenz = 0.6639; v.hauptrichtung = 83.88;
        return v;
    }

    /** Blätter (franz-gertsch6.jpg): Große Blätter im Gras: gezähnte Ränder mit hellem Saum, orange Punkte im Dunkel. */
    public static Vorlage blaetter() {
        Vorlage v = new Vorlage();
        v.name = "blaetter"; v.titel = "Blätter"; v.quelle = "franz-gertsch6.jpg";
        v.breite = 1465; v.hoehe = 1000; v.arbeitsbreite = 1000; v.qualitaet = "HIGH"; v.stufen = 6; v.seed = 1L;
        v.palette = new Palette(new double[][]{{9.95, -0.70, 3.32}, {24.87, -8.50, 10.31}, {40.92, -17.04, 17.19}, {43.71, 14.08, 34.73}, {56.42, -16.19, 25.96}, {71.16, -7.77, 24.77}},
                new double[]{0.2885, 0.1921, 0.1818, 0.0481, 0.1731, 0.1164});
        v.helligkeit = new double[]{0.1519, 0.1723, 0.1328, 0.1260, 0.1246, 0.1246, 0.1071, 0.0505, 0.0097, 0.0005};
        v.l5 = 4.82; v.l50 = 33.37; v.l95 = 71.37; v.grund = 0.3242;
        v.farbton = 117.78; v.buntheit = 19.13; v.akzent = 0.0167; v.akzentLab = new double[]{30.92, 22.31, 27.55};
        v.durchmesser = new double[]{0.00200, 0.00400, 0.00600, 0.00800, 0.01000, 0.01200, 0.01400, 0.01800, 0.02400, 0.03000, 0.03600, 0.04600, 0.05600, 0.07000};
        v.hell = new double[]{0.1088, 0.2291, 0.3263, 0.4308, 0.4856, 0.5376, 0.5750, 0.6442, 0.7159, 0.7753, 0.8280, 0.8865, 0.9375, 1.0000};
        v.dunkel = new double[]{0.0432, 0.0911, 0.1433, 0.2169, 0.2676, 0.3235, 0.3706, 0.4685, 0.5807, 0.6795, 0.7669, 0.8551, 0.9254, 1.0000};
        v.hellD50 = 0.01055; v.dunkelD50 = 0.01969; v.fleckHell = 0.7224; v.fleckDunkel = 0.9523;
        v.richtung = new double[]{0.0653, 0.0617, 0.0650, 0.0726, 0.0790, 0.1124, 0.1064, 0.1028, 0.1026, 0.0820, 0.0784, 0.0718};
        v.senkrecht = 0.4006; v.kohaerenz = 0.6932; v.hauptrichtung = 106.29;
        return v;
    }

    /** Holzschnitt (franz-gertsch5.jpg) als Druck, gemessen mit {@link Druck#messen} in voller Auflösung: Papier, Farbe, gestochene Punkte, Ton, Wolken und Korn. */
    public static Druck druck() {
        Druck d = new Druck();
        d.name = "holzschnitt"; d.titel = "Holzschnitt"; d.quelle = "franz-gertsch5.jpg"; d.breite = 1438; d.hoehe = 1000;
        d.papier = new double[]{79.44, -6.90, -3.22}; d.farbe = new double[]{44.05, -16.46, -13.36};
        d.punkt = 0.001121; d.abstand = 0.002782; d.ordnung = 1.0058; d.punkte = 8103;
        d.ton = new double[]{0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0012, 0.0026, 0.0046, 0.0078, 0.0137, 0.0224, 0.0328, 0.0443, 0.0566, 0.0699, 0.0849, 0.1019, 0.1235, 0.1553, 0.2140, 0.6982};
        d.tonMittel = 0.0554; d.wolken = 3.487; d.korn = 0.938;
        d.wolkenX = new double[]{0.882, 0.683, 0.436, 0.350, 0.032}; d.wolkenY = new double[]{0.898, 0.803, 0.813, 0.826, 0.589};
        return d;
    }
}
