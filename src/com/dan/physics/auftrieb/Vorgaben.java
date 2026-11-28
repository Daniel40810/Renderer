package com.dan.physics.auftrieb;

/**
 * Die Schwimmer von Hooge (Seegang Werkbuch, S7a; SF106 A, SF107 A; Vorlagen-Fotos in HalligHooge\docs\img). Maße der
 * Schiffe aus öffentlichen Angaben, alles Übrige gestaltet [G]:
 * <ul>
 * <li><b>Fähre „Hilligenlei“</b> (W.D.R., Schlüttsiel–Hooge–Langeneß): 38,3 × 10,0 m, AIS-Tiefgang 1,6 m; Prahm mit
 * flachem Boden und aufgeholten Enden [G].</li>
 * <li><b>„Adler-Express“</b> (Nordstrand–Hooge): 42 × 8 m, Einrumpf, schnell, Tiefgang 1,2 m [G].</li>
 * <li><b>„Hauke Haien“</b> (Fahrgastschiff, Baujahr 1960): 33,6 × 6,0 m, AIS-Tiefgang 1,4 m; Rundspant mit Kiel [G].</li>
 * <li><b>Krabbenkutter</b> (Vorbild WRE 6 „Claudia“: 15,65 × 4,70 m, Tiefgang 1,60 m): 16,0 × 4,8 m, Rundspant, Kiel,
 * Ruderhaus achtern [G]. Er ist das „kleine Boot“ aus SF10 und trägt die Bordkamera.</li>
 * <li><b>Tonnen</b> [G]: Spierentonne (Spiere 5 m, Ø 0,5 m, 3 m unter Wasser), Spitztonne (Ø 1,6 m, 1 m Tiefgang, KG 0,4 m),
 * Leuchttonne (Schwimmer Ø 2,4 m, 5,1 t, 1,1 m Tiefgang wie `buoyancy.Buoy`, KG 0,5 m). Das Auge für die Kette am Fuß.</li>
 * <li><b>Treibgut</b> [G]: Balken (Holz 600 kg/m³), Fischkiste (mit Luft, wirksam 250 kg/m³), Stamm (650 kg/m³).</li>
 * <li><b>Eiderente</b> [G]: Ellipsoid 0,60 × 0,26 × 0,22 m, 2,2 kg.</li>
 * </ul>
 * Schwerpunkthöhe der Schiffe aus einer gestalteten Querstabilität GM [G]; Trägheitsradien 0,38 B (Rollen), 0,25 L
 * (Stampfen), 0,26 L (Gieren) [G].
 */
public final class Vorgaben {
    private Vorgaben() { }

    /** Ein Schiff aus Rumpf, Probenkante d, GM [G]; Masse aus der Verdrängung auf dem Tiefgang. */
    static Koerper schiff(Form.Rumpf r, double d, double gm) {
        Hydrostatik h = new Hydrostatik(r, r.tiefgang);
        double kg = h.kb + h.it / h.volumen - gm;
        Koerper k = new Koerper(r, d, h, 0, kg, 0.38 * r.breite, 0.25 * r.laenge, 0.26 * r.laenge);
        k.zusatzAusStreifen(r);
        steuern(k, r);
        return k;
    }

    /** Fähre „Hilligenlei“. */
    public static Koerper faehre() {
        Form.Rumpf r = new Form.Rumpf("Fähre Hilligenlei", 38.3, 10.0, 2.6, 1.6);
        r.mitteVorn = 0.55; r.mitteAchtern = 0.6; r.bugVoll = 2.5; r.heckVoll = 3; r.bugBreite = 0.55; r.spiegel = 0.75;
        r.aufkimmung = 0; r.kimmradius = 0.5;
        r.aufholVorn = 0.9; r.aufholVornAb = 0.75; r.aufholAchtern = 0.6; r.aufholAchternAb = 0.8;
        r.sprungVorn = 0.4; r.sprungAchtern = 0.2;
        Koerper k = schiff(r, 0.75, 2.5);
        k.zetaTauchen = 0.18; k.zetaRollen = 0.08; k.zetaStampfen = 0.15; k.rollenQuadrat = 0.2;
        k.windVorn = 50; k.windSeite = 150; k.windHoehe = 4.1; k.windX = -1;
        k.schubMax = 60e3; k.ruderFlaeche = 3; k.schraube = new double[] {-0.45 * r.laenge, 0, 0.5};
        return k;
    }

    /** „Adler-Express“: schnelles Fahrgastschiff, Knickspant. */
    public static Koerper adlerExpress() {
        Form.Rumpf r = new Form.Rumpf("Adler-Express", 42.0, 8.0, 3.0, 1.2);
        r.mitteVorn = 0.1; r.mitteAchtern = 0.4; r.bugVoll = 1.4; r.heckVoll = 4; r.spiegel = 0.85;
        r.aufkimmung = 12; r.kimmradius = 0.3;
        r.aufholVorn = 0.6; r.aufholVornAb = 0.4;
        r.sprungVorn = 0.6; r.sprungAchtern = 0.1;
        // GM seit S7e2 3,5 statt 1,3 m [G]: mit 1,3 m kenterte sie im Container bei 8 m/s schon mit Ruder 0,3 (die Krängung in der
        // Drehung schaukelt sich mit der engeren Drehung auf). Schätzung der Gewichte (Rumpf, Maschine, Aufbau, Ausrüstung, 350
        // Gäste, Vorräte) gibt KG rund 2,8–3,3 m über Kiel, also GM rund 3,3–3,8 m; so steif sind breite, flache Schiffe
        Koerper k = schiff(r, 0.75, 3.5);
        k.zetaTauchen = 0.18; k.zetaRollen = 0.05; k.zetaStampfen = 0.15; k.rollenQuadrat = 0.3;
        k.windVorn = 36; k.windSeite = 170; k.windHoehe = 4.2; k.windX = 0;
        k.schubMax = 150e3; k.ruderFlaeche = 2; k.schraube = new double[] {-0.46 * r.laenge, 0, 0.4};
        k.gleiten = Gleiten.fuer(r);                                               // seit S7e2 (SF150 A)
        return k;
    }

    /** „Hauke Haien“: Fahrgastschiff, Rundspant mit Kiel. */
    public static Koerper haukeHaien() {
        Form.Rumpf r = new Form.Rumpf("Hauke Haien", 33.6, 6.0, 2.4, 1.4);
        r.mitteVorn = 0.2; r.mitteAchtern = 0.3; r.bugVoll = 1.6; r.heckVoll = 2.2; r.spiegel = 0.5;
        r.aufkimmung = 5; r.kimmradius = 1.0; r.kielHoehe = 0.25; r.kielBreite = 0.3;
        r.aufholVorn = 0.8; r.aufholVornAb = 0.55;
        r.sprungVorn = 0.7; r.sprungAchtern = 0.2;
        r.aufbau(-12, 8, 2.4, 0, 2.3, 0.3);
        Koerper k = schiff(r, 0.6, 0.7);
        k.zetaTauchen = 0.18; k.zetaRollen = 0.04; k.zetaStampfen = 0.15; k.rollenQuadrat = 0.5;
        k.windVorn = 30; k.windSeite = 120; k.windHoehe = 3.6; k.windX = -1;
        k.schubMax = 35e3; k.ruderFlaeche = 1.6; k.schraube = new double[] {-0.45 * r.laenge, 0, 0.6};
        return k;
    }

    /** Krabbenkutter (das kleine Boot, Träger der Bordkamera). */
    public static Koerper krabbenkutter() {
        Form.Rumpf r = new Form.Rumpf("Krabbenkutter", 16.0, 4.8, 2.2, 1.6);
        r.mitteVorn = 0.15; r.mitteAchtern = 0.25; r.bugVoll = 1.5; r.heckVoll = 2.0; r.spiegel = 0.55;
        r.aufkimmung = 10; r.kimmradius = 0.9; r.kielHoehe = 0.35; r.kielBreite = 0.3;
        r.aufholVorn = 0.7; r.aufholVornAb = 0.5;
        r.sprungVorn = 0.6; r.sprungAchtern = 0.15;
        r.aufbau(-3.5, -0.5, 1.4, 0, 2.2, 0.3);
        Koerper k = schiff(r, 0.4, 0.55);
        k.zetaTauchen = 0.18; k.zetaRollen = 0.04; k.zetaStampfen = 0.15; k.rollenQuadrat = 0.6;
        k.windVorn = 12; k.windSeite = 35; k.windHoehe = 3.2; k.windX = -1;
        k.schubMax = 25e3; k.ruderFlaeche = 1.2; k.schraube = new double[] {-7.2, 0, 0.6};
        return k;
    }

    /**
     * Seit S7d7 (Seegang Werkbuch 95, SF138 A, Befund L5d6 1): Rumpf nach dem Querstromprinzip mit dem Quertrieb nach Clarke,
     * c_Q = π T/L (1 + 0,4 C_B B/T), und ein Ruder mit 2,8 je rad, höchstens 2,0, Widerstand 0,013 und Streckung 1,5 [G].
     * Geeicht im Container (Werkbuch 95, Abschnitt 6): bei Ruder 0,2 höchstens 10 % weniger Fahrt, Drehkreis bei vollem Ruder
     * 3–5 Längen [G]; Fähre 5 % und 4,8 L, Kutter 1 % und 4,7 L.
     */
    static void steuern(Koerper k, Form.Rumpf r) {
        double cb = k.masse / 1025 / (r.laenge * r.breite * r.tiefgang);
        k.querstrom = true;
        k.cQuertrieb = Math.PI * r.tiefgang / r.laenge * (1 + 0.4 * cb * r.breite / r.tiefgang);
        k.ruderAnstieg = 2.8;
        k.ruderClMax = 2.0;
        k.ruderCd0 = 0.013;
        k.ruderStreckung = 1.5;
    }

    /** Tiefgang, auf dem ein Körper der Masse m schwimmt (Halbierung auf der Hydrostatik, grob, dann fein ± 3 cm). */
    static double tiefgangFuer(Form f, double m) {
        double[] k = f.kasten();
        double lo = k[4], hi = k[5];
        for (int it = 0; it < 30; it++) {
            double t = 0.5 * (lo + hi);
            if (1025 * new Hydrostatik(f, t, 120, 60, 60).volumen > m) hi = t; else lo = t;
        }
        double t0 = 0.5 * (lo + hi);
        lo = Math.max(k[4], t0 - 0.03);
        hi = Math.min(k[5], t0 + 0.03);
        for (int it = 0; it < 14; it++) {
            double t = 0.5 * (lo + hi);
            if (1025 * new Hydrostatik(f, t, 200, 100, 120).volumen > m) hi = t; else lo = t;
        }
        return 0.5 * (lo + hi);
    }

    /** Ein Körper fester Masse m auf seinem Tiefgang; Trägheitsradien, Zusatzmasse als Anteile [G]. */
    static Koerper fest(Form f, double d, double m, double kg, double rx, double ry, double rz) { return fest(f, d, 8, m, kg, rx, ry, rz); }

    /** Wie oben mit s × s × s Stellen je Zelle (dünne Körper). */
    static Koerper fest(Form f, double d, int s, double m, double kg, double rx, double ry, double rz) {
        Hydrostatik h = new Hydrostatik(f, tiefgangFuer(f, m), 200, 100, 120);
        Koerper k = new Koerper(f, new Proben(f, d, s), h, m, kg, rx, ry, rz);
        k.mitWellenwiderstand = false;
        return k;
    }

    /** Spierentonne [G]: Spiere 5 m, Ø 0,5 m, 3 m unter Wasser, Ballast im Fuß (KG 1,2 m). */
    public static Koerper spierentonne() { return spierentonne(0.1); }

    /** Seit S7b-2: wie {@link #spierentonne()} mit Proben der Kante d (in der Szene gröber [G]). */
    public static Koerper spierentonne(double d) {
        Form f = new Form.Drehkoerper("Spierentonne", new double[] {0, 4.6, 5.0}, new double[] {0.25, 0.25, 0.1});
        double m = 1025 * Math.PI * 0.25 * 0.25 * 3.0;
        Koerper k = fest(f, d, 16, m, 1.2, 1.3, 1.3, 0.18);
        k.zusatzAnteile(0.05, 1.0, 0.1, 0.1, 0.1, 0.05);
        k.zetaTauchen = 0.05; k.zetaRollen = 0.05; k.zetaStampfen = 0.05; k.rollenQuadrat = 0.2;
        k.cdLaengs = 1.0;
        k.windVorn = 0.9; k.windSeite = 0.9; k.windHoehe = 4.0; k.windX = 0;
        return k;
    }

    /** Spitztonne [G]: Zylinder Ø 1,6 m bis 1,3 m, Kegel bis 2,6 m; 1,0 m Tiefgang (KG 0,4 m). */
    public static Koerper spitztonne() { return spitztonne(0.2); }

    /** Seit S7b-2: wie {@link #spitztonne()} mit Proben der Kante d. */
    public static Koerper spitztonne(double d) {
        Form f = new Form.Drehkoerper("Spitztonne", new double[] {0, 1.3, 2.6}, new double[] {0.8, 0.8, 0.05});
        double m = 1025 * Math.PI * 0.64 * 1.0;
        Koerper k = fest(f, d, m, 0.4, 0.7, 0.7, 0.55);
        k.zusatzAnteile(0.3, 0.3, 0.5, 0.2, 0.2, 0.05);
        k.zetaTauchen = 0.1; k.zetaRollen = 0.08; k.zetaStampfen = 0.08; k.rollenQuadrat = 0.2;
        k.cdLaengs = 1.0;
        k.windVorn = 1.5; k.windSeite = 1.5; k.windHoehe = 1.8; k.windX = 0;
        return k;
    }

    /** Leuchttonne [G]: Schwimmer Ø 2,4 m, 5,1 t, 1,1 m Tiefgang wie `buoyancy.Buoy`; Gerüst und Laterne nur im Wind. */
    public static Koerper leuchttonne() { return leuchttonne(0.3); }

    /** Seit S7b-2: wie {@link #leuchttonne()} mit Proben der Kante d. */
    public static Koerper leuchttonne(double d) {
        Form f = new Form.Drehkoerper("Leuchttonne", new double[] {0, 0.3, 1.6}, new double[] {0.9, 1.2, 1.2});
        Koerper k = fest(f, d, 5100, 0.5, 1.0, 1.0, 0.85);
        k.zusatzAnteile(0.3, 0.3, 0.5, 0.2, 0.2, 0.05);
        k.zetaTauchen = 0.1; k.zetaRollen = 0.08; k.zetaStampfen = 0.08; k.rollenQuadrat = 0.2;
        k.cdLaengs = 1.0;
        k.windVorn = 3.0; k.windSeite = 3.0; k.windHoehe = 3.5; k.windX = 0;
        return k;
    }

    /**
     * Seit S7e1 (Seegang Werkbuch 99, SF149 A): eine Pricke, ein Birkenstamm 5 m lang, 0,12 m dick [G], mit dem Besen oben.
     * Sie steht fest im Grund ({@code Flotte}: ohne Physik); der Körper trägt nur Form, Lage und Netz.
     */
    public static Koerper pricke() {
        Form f = new Form.Quader("Pricke", 0.12, 0.12, 5.0);
        double m = 650 * 0.12 * 0.12 * 5.0;
        // grobe Proben und Hydrostatik: der Körper schwimmt nie (26 Pricken bauen so in Millisekunden statt Sekunden)
        Koerper k = new Koerper(f, new Proben(f, 0.12, 2), new Hydrostatik(f, 0.5, 4, 4, 8), m, 2.5, 1.4, 1.4, 0.05);
        k.mitWellenwiderstand = false;
        return k;
    }

    /** Balken 4 × 0,3 × 0,3 m, Holz 600 kg/m³ [G]. */
    public static Koerper balken() { return balken(0.1); }

    /** Seit S7e2: wie {@link #balken()} mit Proben der Kante d (in der Szene gröber [G]). */
    public static Koerper balken(double d) {
        Form f = new Form.Quader("Balken", 4.0, 0.3, 0.3);
        double m = 600 * 4.0 * 0.3 * 0.3;
        Koerper k = fest(f, d, m, 0.15, Math.sqrt(0.18 / 12), Math.sqrt(16.09 / 12), Math.sqrt(16.09 / 12));
        k.zusatzAnteile(0.05, 1.0, 1.0, 0.2, 0.5, 0.5);
        k.cdLaengs = 0.3;
        return k;
    }

    /** Fischkiste 0,8 × 0,5 × 0,35 m mit Luft, wirksam 250 kg/m³ [G]. */
    public static Koerper fischkiste() { return fischkiste(0.1); }

    /** Seit S7e2: wie {@link #fischkiste()} mit Proben der Kante d. */
    public static Koerper fischkiste(double d) {
        Form f = new Form.Quader("Fischkiste", 0.8, 0.5, 0.35);
        double m = 250 * 0.8 * 0.5 * 0.35;
        Koerper k = fest(f, d, m, 0.12, Math.sqrt((0.25 + 0.1225) / 12), Math.sqrt((0.64 + 0.1225) / 12), Math.sqrt((0.64 + 0.25) / 12));
        k.zusatzAnteile(0.3, 0.5, 1.0, 0.2, 0.2, 0.2);
        k.cdLaengs = 1.0;
        return k;
    }

    /** Stamm Ø 0,4 m, 3 m lang, 650 kg/m³ [G]. */
    public static Koerper stamm() { return stamm(0.1); }

    /** Seit S7e2: wie {@link #stamm()} mit Proben der Kante d. */
    public static Koerper stamm(double d) {
        Form f = new Form.Zylinder("Stamm", 0.2, 3.0);
        double m = 650 * Math.PI * 0.04 * 3.0;
        Koerper k = fest(f, d, m, 0.2, Math.sqrt(0.5 * 0.04), Math.sqrt((3 * 0.04 + 9) / 12), Math.sqrt((3 * 0.04 + 9) / 12));
        k.zusatzAnteile(0.05, 1.0, 1.0, 0.1, 0.5, 0.5);
        k.cdLaengs = 0.3;
        return k;
    }

    /** Eiderente: Ellipsoid 0,60 × 0,26 × 0,22 m, 2,2 kg [G]. */
    public static Koerper eiderente() { return eiderente(0.05); }

    /** Seit S7e2: wie {@link #eiderente()} mit Proben der Kante d. */
    public static Koerper eiderente(double d) {
        Form f = new Form.Ellipsoid("Eiderente", 0.30, 0.13, 0.11);
        Koerper k = fest(f, d, 2.2, 0.09, 0.07, 0.13, 0.13);
        k.zusatzAnteile(0.2, 0.5, 0.5, 0.2, 0.2, 0.2);
        k.zetaTauchen = 0.2; k.zetaRollen = 0.2; k.zetaStampfen = 0.2;
        k.cdLaengs = 0.3;
        k.windVorn = 0.03; k.windSeite = 0.06; k.windHoehe = 0.18; k.windX = 0;
        return k;
    }

    /**
     * Seit S8d (Seegang Werkbuch 104, SF160 A): der Kopf eines Schwimmers als Träger des Schwimmerblicks [G] – eine Kugel von
     * 0,11 m Halbmesser, zur Hälfte im Wasser (2,86 kg), die der Fläche dicht folgt (Eigenperiode im Tauchen rund 0,7 s); der
     * Körper unter Wasser ist nicht gerechnet. Mitte auf der Wasserlinie (z = 0,11 m im Körper).
     */
    public static Koerper kopf(double d) {
        double r = 0.11;
        Form f = new Form.Ellipsoid("Kopf", r, r, r);
        Koerper k = fest(f, d, 1025 * 2.0 / 3 * Math.PI * r * r * r, r, 0.07, 0.07, 0.07);
        k.zusatzAnteile(0.5, 0.5, 0.5, 0.2, 0.2, 0.2);
        k.zetaTauchen = 0.3; k.zetaRollen = 0.3; k.zetaStampfen = 0.3;
        k.cdLaengs = 0.5;
        k.windVorn = 0.02; k.windSeite = 0.02; k.windHoehe = 0.1; k.windX = 0;
        k.schrittMax = SCHRITT_KLEIN;
        return k;
    }

    // ------------------------------------------------------------------ seit S7e2: Treibgut (SF151 A)

    /** Seit S7e2: Kante der Proben für Treibgut und Enten in der Szene (m) [G] (R20: wenige Proben). */
    public static final double TREIB_D = 0.15, ENTE_D = 0.08;
    /**
     * Seit S7e2 (Befund L5e2: der Faden der Flotte lag mit 31 kleinen Körpern bei 2,55 ms je Teilschritt): Teilschritt für
     * Treibgut und Enten in der Szene (s) [G]. Ihre kürzeste Eigenzeit (Tauchen der Ente 0,40 s) ist 24-mal länger; der Schritt
     * nach Dullweber bleibt stabil bis 2/ω = 0,13 s.
     */
    public static final double SCHRITT_KLEIN = 1.0 / 60;

    private static final java.util.Map<String, double[]> TREIB_FLAECHEN = new java.util.concurrent.ConcurrentHashMap<>();

    /** Ruhelage im Rollen (Grad) einer Vorgabe des Treibguts: der Balken liegt auf der Kante (Datei 87, 6), sonst 0. */
    public static double ruheKraengung(String name) { return name.startsWith("Balken") ? 45 : 0; }

    /**
     * Seit S7e2 (Seegang Werkbuch 101, SF151 A): Treibgut für die Szene – Proben der Kante d, die Flächen gegen den Wind aus
     * der Ruhelage (nicht als fester Anteil): {@link Koerper#windVorn} und {@link Koerper#windSeite} sind die Flächen über der
     * ruhigen Wasserlinie, von vorn und von der Seite gesehen ({@link #flaechen}); der Wind greift im Schwerpunkt an [G].
     */
    public static Koerper treibgut(String art, double d) {
        Koerper k = switch (art) {
            case "balken" -> balken(d);
            case "fischkiste" -> fischkiste(d);
            case "stamm" -> stamm(d);
            default -> throw new IllegalArgumentException("kein Treibgut: " + art);
        };
        k.schrittMax = SCHRITT_KLEIN;
        double[] a = TREIB_FLAECHEN.computeIfAbsent(art + " " + d, s -> {
            k.einschwimmen(Wasser.ruhig(0), 0, 0, 0, ruheKraengung(k.form.name()), 0, 0);
            return flaechen(k, 0.01, 0);
        });
        k.windVorn = a[0];
        k.windSeite = a[1];
        k.windX = k.schwerpunkt[0];
        k.windHoehe = k.schwerpunkt[2];
        return k;
    }

    /**
     * Seit S7e2: Flächen des Körpers in seiner Lage, über und unter dem ruhigen Pegel, senkrecht projiziert (m²): {von vorn
     * über Wasser, von der Seite über Wasser, von vorn unter Wasser, von der Seite unter Wasser}; „vorn“ längs der waagrechten
     * Richtung der Längsachse. Je Zelle zelle × zelle der Projektion ein Strahl durch ihre Mitte, abgetastet in Schritten
     * zelle / 2: die Zelle zählt, wenn ein Punkt des Strahls im Körper liegt.
     */
    public static double[] flaechen(Koerper k, double zelle, double pegel) {
        double[] ka = k.form.kasten();
        double hx = k.R[0], hy = k.R[3], hl = Math.hypot(hx, hy);
        hx /= hl; hy /= hl;
        double[] a = new double[4];
        for (int seite = 0; seite < 2; seite++) {
            // seite 0: Strahlen längs h (von vorn gesehen), Zellen über (q, z); seite 1: Strahlen längs q, Zellen über (h, z)
            double sx = seite == 0 ? hx : -hy, sy = seite == 0 ? hy : hx, ux = seite == 0 ? -hy : hx, uy = seite == 0 ? hx : hy;
            // Ausdehnung längs Strahl, quer und hoch aus den Ecken des Kastens (um den Schwerpunkt)
            double t0 = 1e9, t1 = -1e9, u0 = 1e9, u1 = -1e9, z0 = 1e9, z1 = -1e9;
            for (int c = 0; c < 8; c++) {
                double[] p = k.punktWelt(ka[c & 1], ka[2 + ((c >> 1) & 1)], ka[4 + ((c >> 2) & 1)]);
                double dx = p[0] - k.o[0], dy = p[1] - k.o[1], t = dx * sx + dy * sy, u = dx * ux + dy * uy;
                t0 = Math.min(t0, t); t1 = Math.max(t1, t); u0 = Math.min(u0, u); u1 = Math.max(u1, u); z0 = Math.min(z0, p[2]); z1 = Math.max(z1, p[2]);
            }
            int nt = (int) Math.ceil((t1 - t0) / (0.5 * zelle));
            for (double u = Math.floor(u0 / zelle) * zelle + 0.5 * zelle; u < u1; u += zelle)
                for (double z = Math.floor(z0 / zelle) * zelle + 0.5 * zelle; z < z1; z += zelle) {
                    boolean drin = false;
                    for (int it = 0; it <= nt && !drin; it++) {
                        double t = t0 + it * 0.5 * zelle;
                        double px = u * ux + t * sx, py = u * uy + t * sy, pz = z - k.o[2];
                        double fx = k.R[0] * px + k.R[3] * py + k.R[6] * pz + k.schwerpunkt[0];
                        double fy = k.R[1] * px + k.R[4] * py + k.R[7] * pz + k.schwerpunkt[1];
                        double fz = k.R[2] * px + k.R[5] * py + k.R[8] * pz + k.schwerpunkt[2];
                        drin = k.form.innen(fx, fy, fz);
                    }
                    if (drin) a[seite + (z > pegel ? 0 : 2)] += zelle * zelle;
                }
        }
        return a;
    }
}
