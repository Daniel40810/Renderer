package com.dan.lauf;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Flaeche;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Spiegel;
import com.dan.core.zeit.Uhr;
import com.dan.render.air.Himmel;
import com.dan.render.air.Kimmung;
import com.dan.render.frame.Rahmen;
import com.dan.render.reference.Schranke;
import com.dan.render.bau.Hausbild;
import com.dan.render.terrain.Gelaendebild;
import com.dan.render.water.Wasserbild;

import java.awt.Canvas;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Bühne (Werkbuch HalligHooge Teil B, B.3; F70 A): alles, was ein Projekt braucht, um ein Gebiet mit Gelände,
 * Wasser und Himmel über einem Lauf zu zeigen – Gerät, Fläche auf einer AWT-Leinwand, Rahmen, Bildmodule,
 * {@link Bild}, Uhr und die Bildschleife. Aus {@code lauf.demo.BildDemo} herausgezogen; nichts hier ist an ein
 * Gebiet gebunden. Ein Projekt liefert das Fenster, eine Szenendatei ({@link Szenen}) und die Bedienung.
 * <pre>
 * try (Buehne b = Buehne.oeffnen(szenen.gebiet(), leinwand)) {   // die Leinwand muss schon sichtbar sein
 *     b.pegel("Hooge", 470410, 6048090, 300);
 *     b.szenario(szenen.szenario("sturm"));                       // Archiv oder live
 *     b.ansicht(szenen.ansicht("westspitze"));
 *     b.laufen();                                                 // Bildschleife, bis beenden() gerufen wird
 * }
 * </pre>
 * <b>Fäden.</b> {@link #oeffnen}, {@link #laufen} und {@link #close} gehören in einen Faden (nicht den der
 * Oberfläche). Alles andere darf die Oberfläche jederzeit rufen: Wünsche ({@link #szenario}, {@link #ansicht},
 * {@link #zeit}, {@link #zeitraffer}, {@link #rueckwaerts}, {@link #pause}) gelten ab dem nächsten Bild,
 * {@link #zustand} und {@link #pegelreihe} geben den Stand des letzten Bilds.
 * <p>
 * <b>Zeit.</b> Aus dem Archiv läuft die Szenenzeit mit jedem Zeitraffer vor und zurück und springt; an den Enden
 * bleibt sie stehen. Live begrenzt die Physik den Zeitraffer, rückwärts und Springen gibt es nicht.
 * <p>
 * <b>Ohne Fenster</b> ({@link #ohneFenster}) zeichnet die Bühne Standbilder in ein Bild auf der Karte, auf demselben
 * Weg wie {@code BildCheck}: für Prüfungen.
 * <p>
 * <b>Seit W2</b> (Werkbuch HalligHooge 113): {@link Laden} meldet die Schritte beim Öffnen; {@link #einblendung()} gibt
 * die Einblendungen ({@code render.overlay}), die nach dem Messblick über das Bild gelegt werden – im Fenster und in
 * {@link #bildzeiten}, nie im Foto und nie im Standbild (außer mit {@link #standbild(Blick, double, boolean)}).
 */
public final class Buehne implements AutoCloseable {
    /** Der Stand nach einem Bild; ein neues Objekt je Bild, nie geändert. */
    public static final class Zustand {
        /** Das Szenario (null: noch keins). */
        public Szenario szenario;
        public boolean live, pause, rueckwaerts;
        /** Woher die Stände kommen, kurz. */
        public String herkunft = "";
        /** Szenenzeit: s seit dem Start der Quelle und als UTC (null ohne Quelle). */
        public double t;
        public Instant jetzt;
        /** Start der Quelle (UTC) und der Bereich der Stände (s seit Start); live wächst das Ende mit der Physik. */
        public Instant start;
        public double tAnfang, tEnde;
        /** Zeitraffer: gewollt, gehalten, und was die Physik schafft (NaN aus dem Archiv). */
        public double gewollt, gehalten, erreichbar = Double.NaN;
        public double bilderJeS;
        /** Wind am Auge (m/s) und Wasserstand am Pegel (m NHN; NaN: trocken oder kein Pegel). */
        public double wind, pegel = Double.NaN;
        /** Seit W2: Wind am Auge als Vektor (m/s, nach Ost und Nord: wohin er weht). */
        public double windU, windV;
        /** Der Blick dieses Bilds. */
        public Blick blick;
        public long bilder;
        /** Letzter Fehler beim Wechsel des Szenarios oder aus dem Lauf (null: keiner). */
        public String fehler;
        /**
         * Seit W5c: wie lange die Uhr schon auf Stände wartet (s; 0: sie wartet nicht), wie lange sie zuletzt gewartet hat
         * und wie oft seit dem Öffnen des Szenarios.
         */
        public double datenWarten, datenGewartet;
        /** Seit D1 (Werkbuch HalligHooge 162): wie lange die Uhr seit dem Öffnen des Szenarios zusammen auf Stände gewartet hat (s). */
        public double datenGewartetSumme;
        public int datenWarteZahl;

        /** Eine Zeile für die Einblendung: Zeit in der Zone, Pegel, Wind, Zeitraffer, Bilder/s, Herkunft. */
        public String text(ZoneId zone, String pegelName) {
            if (fehler != null) return "Fehler – " + fehler;
            if (jetzt == null) return "Kein Szenario";
            String p = pegelName == null ? "" : " · Pegel " + pegelName + " " + (Double.isNaN(pegel) ? "trocken" : String.format(Locale.GERMAN, "%+.2f m NHN", pegel));
            String r = live ? String.format(Locale.GERMAN, "Zeitraffer %.0f (gewollt %.0f, die Physik schafft %.0f)", gehalten, gewollt, erreichbar)
                    : String.format(Locale.GERMAN, "Zeitraffer %.0f%s", gehalten, pause ? " (angehalten)" : "");
            return String.format(Locale.GERMAN, "%s%s · Wind %.1f m/s · %s · %.1f Bilder/s · %s", ZEIT.withZone(zone).format(jetzt), p, wind, r, bilderJeS, herkunft);
        }
    }

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss zzz", Locale.GERMAN);

    public final File ordner;
    public final Gebiet gebiet;
    public final Gelaende gelaende;
    public final Steuerung steuerung = new Steuerung();
    /** Name des Geräts und Treiber, für die Einblendung. */
    public final String geraet;

    private final List<AutoCloseable> teile = new ArrayList<>();
    private final Geraet g;
    private final Konto konto;
    private final Flaeche flaeche;
    private final Rahmen rahmen;
    private final Himmel himmel;
    /** Seit P10b: die Kimmung, letzter Teil des Bilds; ohne Luft in der Quelle lässt sie das Bild, wie es ist. */
    public final Kimmung kimmung;
    private final Gelaendebild gelaendebild;
    private final Wasserbild wasser;
    /** Seit P12a: die Häuser (null: das Gebiet hat keine, {@code bau/gebaeude.bin}); an oder aus mit {@link #haeuser(boolean)}. */
    private final Hausbild haeuser;
    /** Seit P12c: der Bewuchs (Gehölze aus {@code bau/gehoelz.bin}, Halme am Auge); null ohne Gehölze. */
    private final com.dan.render.bewuchs.Bewuchsbild bewuchs;
    /** Seit P13c: die Brandung an den Böschungen (null: das Gebiet hat keine Kanten, {@code brandung/kanten.txt}). */
    private final com.dan.render.brandung.Brandungsbild brandung;
    private final Stoppuhr stoppuhr;
    /** Ohne Fenster: das Ziel der Standbilder. */
    private com.dan.core.gpu.Bild ausgabe;
    private final int breite, hoehe;
    /** Das feinste Gitter in UTM des Gebiets: für Länge und Breite eines Orts. */
    private final Gitter utm;

    private Szenario szenario;
    private Lauf lauf;
    private Archivquelle archiv;
    private Bild bild;
    private Uhr uhr;
    private double tAnfang, tEnde;
    private volatile Pegelreihe reihe;

    private volatile Szenario neuesSzenario;
    private volatile Ansicht neueAnsicht;
    private volatile double sprung = Double.NaN, raffer = 100;
    private volatile boolean rueckwaerts, pause, ende, wechselt;
    private volatile Zustand zustand = new Zustand();
    private volatile String fehler;

    private String pegelName;
    private double pegelOst = Double.NaN, pegelNord, pegelUmkreis;

    /** Seit P11b: das Foto (null: keins), der Wunsch nach einem und die Schranke des Geländes (einmal je Mitte gebaut). */
    private volatile Foto foto;
    private volatile Fotowunsch fotoWunsch;
    private Schranke schranke;

    /** Seit P11c2: der Messblick (null, bis er zum ersten Mal gebraucht wird), seine Quelle und ob er an ist. */
    private com.dan.render.analysis.Messblick messblick;
    private volatile Messquelle messquelle;
    private volatile boolean messblickAn, schnittAn;

    /**
     * Seit W2: meldet die Schritte beim Öffnen der Bühne, der Reihe nach, mit dem Anteil der Arbeit, der danach getan ist
     * (0 … 1, steigend; der letzte Schritt meldet 1). Gerufen im Faden, der öffnet.
     */
    public interface Laden {
        void schritt(String name, double anteil);

        Laden STILL = (n, a) -> { };
    }

    /** Die Schritte beim Öffnen und ihr Anteil an der Arbeit [G] (aus Lauf W1: Gelände und Bewuchs sind die großen). */
    static final String[] SCHRITTE = {"Gebiet", "Gerät", "Fläche", "Rahmen", "Himmel", "Gelände", "Häuser", "Bewuchs", "Wasser", "Brandung", "Kimmung", "Fertig"};
    static final double[] ANTEILE = {0.02, 0.10, 0.16, 0.20, 0.24, 0.55, 0.62, 0.80, 0.86, 0.92, 1.0};

    /** Seit W2: die Einblendungen (null, bis sie zum ersten Mal gebraucht werden). */
    private com.dan.render.overlay.Einblendung einblendung;

    /** Ein Wunsch nach einem Foto: Titel, Größe (Bildpunkte), Ordner der Ablage, Zeitgrenze (s). */
    private record Fotowunsch(String titel, int breite, int hoehe, File ablage, double zeitgrenze) { }

    private Buehne(File gb, Canvas leinwand, int w, int h, Laden laden) throws Exception {
        if (laden == null) laden = Laden.STILL;
        laden.schritt(SCHRITTE[0], 0);
        ordner = gb;
        gebiet = Gebiet.lies(gb);
        gelaende = Gelaende.ordner(new File(gb, "gelaende"));
        Gitter fein = null;
        for (Gebiet.Eintrag e : gebiet.kette()) if (e.gitter().lage == Gitter.Lage.UTM && (fein == null || e.gitter().dx < fein.dx)) fein = e.gitter();
        utm = fein;
        try {
            laden.schritt(SCHRITTE[1], ANTEILE[0]);
            if (leinwand != null) {
                Geraet.Optionen o = new Geraet.Optionen();
                o.flaeche = true;
                o.strahlen = false;
                g = halte(new Geraet(o));
                laden.schritt(SCHRITTE[2], ANTEILE[1]);
                flaeche = halte(new Flaeche(g, leinwand, true));
                konto = g.konto("Buehne", 3L << 30);
                // die Fläche hat erst eine Größe, wenn das Fenster steht
                for (int i = 0; i < 50 && (flaeche.breite() == 0 || flaeche.hoehe() == 0); i++) {
                    Thread.sleep(100);
                    flaeche.neuBauen();
                    leeresBild();
                }
                breite = Math.max(16, flaeche.breite());
                hoehe = Math.max(16, flaeche.hoehe());
            } else {
                g = halte(new Geraet());
                laden.schritt(SCHRITTE[2], ANTEILE[1]);
                flaeche = null;
                konto = g.konto("Buehne", 3L << 30);
                breite = w;
                hoehe = h;
            }
            geraet = g.name + " (" + g.treiber + ")";
            laden.schritt(SCHRITTE[3], ANTEILE[2]);
            rahmen = halte(new Rahmen(g, konto, breite, hoehe));
            laden.schritt(SCHRITTE[4], ANTEILE[3]);
            himmel = halte(new Himmel(g, konto));
            laden.schritt(SCHRITTE[5], ANTEILE[4]);
            gelaendebild = halte(Gelaendebild.aus(g, konto, gb));
            laden.schritt(SCHRITTE[6], ANTEILE[5]);
            Hausbild hb = Hausbild.aus(g, konto, gb);
            haeuser = hb != null ? halte(hb) : null;
            if (haeuser != null) {
                haeuser.kruemmung = gelaendebild.hoehe.curvatureRadius;
                haeuser.an = !"aus".equals(System.getProperty("lauf.haeuser"));
            }
            // seit P12b: die Gestalt (Karte im Gelände, Körper in render.bau); aus mit -Dlauf.gestalt=aus
            if ("aus".equals(System.getProperty("lauf.gestalt"))) gestalt(false);
            // seit P12c: der Bewuchs (Gehölze, Halme); aus mit -Dlauf.bewuchs=aus
            laden.schritt(SCHRITTE[7], ANTEILE[6]);
            com.dan.render.bewuchs.Bewuchsbild bw = com.dan.render.bewuchs.Bewuchsbild.aus(g, konto, gb);
            bewuchs = bw != null ? halte(bw) : null;
            if (bewuchs != null) {
                bewuchs.kruemmung = gelaendebild.hoehe.curvatureRadius;
                bewuchs.halme(gelaendebild.gelaende, gelaendebild.gestalt(), gelaendebild.material.boden);
            }
            laden.schritt(SCHRITTE[8], ANTEILE[7]);
            wasser = halte(new Wasserbild(g, konto));
            laden.schritt(SCHRITTE[9], ANTEILE[8]);
            // seit P13c: die Brandung (Brecher, Auflauf, Gischt) an den Kanten; aus mit -Dlauf.brandung=aus
            com.dan.render.brandung.Brandungsbild br = com.dan.render.brandung.Brandungsbild.aus(g, konto, gb);
            brandung = br != null ? halte(br) : null;
            if (brandung != null) {
                brandung.kruemmung = gelaendebild.hoehe.curvatureRadius;
                Wasserbild wb = wasser;
                com.dan.core.szene.Brandung.Wasser ww = new com.dan.core.szene.Brandung.Wasser() {
                    @Override public float eta(double ost, double nord) {
                        com.dan.core.szene.Spiegelbild sb = wb.spiegelbild();
                        if (sb == null) return com.dan.core.szene.Brandung.KEIN;
                        float e = sb.etaBildUtm(ost, nord, null);
                        return Float.isNaN(e) || e < -1e29f ? com.dan.core.szene.Brandung.KEIN : e;
                    }
                    @Override public double[] seegang(double ost, double nord) {
                        return wb.seegang && wb.brandung ? com.dan.core.szene.Seegangsoptik.werte(wb.seeNetze(), wb.zeit, ost, nord) : null;
                    }
                };
                brandung.quelle = new com.dan.render.brandung.Brandungsbild.Quelle() {
                    @Override public double zeit() { return wb.zeit; }
                    @Override public com.dan.core.szene.Brandung.Wasser wasser() { return ww; }
                    @Override public double[] wind() { return new double[] {wb.u10, wb.v10}; }
                };
            }
            laden.schritt(SCHRITTE[10], ANTEILE[9]);
            kimmung = halte(new Kimmung(g, konto));
            // seit W2: 48 Abschnitte (die Einblendungen bringen zwei mit)
            stoppuhr = leinwand != null ? halte(new Stoppuhr(g, g.grafik, 48)) : null;
        } catch (Throwable e) {
            schliesseTeile();
            throw e;
        }
        laden.schritt(SCHRITTE[11], 1.0);
    }

    private <T extends AutoCloseable> T halte(T t) {
        teile.add(t);
        return t;
    }

    /** Öffnet die Bühne auf einer Leinwand, die schon in einem sichtbaren Fenster liegt. */
    public static Buehne oeffnen(File gebiet, Canvas leinwand) throws Exception { return oeffnen(gebiet, leinwand, null); }

    /** Wie {@link #oeffnen(File, Canvas)}; seit W2 mit den Schritten an {@code laden} (null: still). */
    public static Buehne oeffnen(File gebiet, Canvas leinwand, Laden laden) throws Exception {
        if (leinwand == null) throw new IllegalArgumentException("Buehne: ohne Leinwand ohneFenster() benutzen");
        return new Buehne(gebiet, leinwand, 0, 0, laden);
    }

    /** Öffnet die Bühne ohne Fenster, für Standbilder ({@link #standbild}). */
    public static Buehne ohneFenster(File gebiet, int breite, int hoehe) throws Exception { return ohneFenster(gebiet, breite, hoehe, null); }

    /** Wie {@link #ohneFenster(File, int, int)}; seit W2 mit den Schritten an {@code laden} (null: still). */
    public static Buehne ohneFenster(File gebiet, int breite, int hoehe, Laden laden) throws Exception {
        Buehne b = new Buehne(gebiet, null, breite, hoehe, laden);
        try {
            b.ausgabe = b.halte(new com.dan.core.gpu.Bild(b.g, b.konto, "Ausgabe", breite, hoehe, com.dan.core.gpu.Bild.RGBA8S));
        } catch (Throwable e) {
            b.schliesseTeile();
            throw e;
        }
        return b;
    }

    /** Der Ort des Pegels (UTM) und der Umkreis, in dem die tiefste Zelle gilt; gilt ab dem nächsten Szenario. */
    public synchronized void pegel(String name, double ost, double nord, double umkreis) {
        pegelName = name;
        pegelOst = ost;
        pegelNord = nord;
        pegelUmkreis = umkreis;
    }

    // ------------------------------------------------------------------ Wünsche von außen

    /** Wechselt das Szenario mit dem nächsten Bild (das Bild steht, solange der Wechsel dauert). */
    public void szenario(Szenario s) {
        wechselt = true;
        neuesSzenario = s;
    }

    /** Steht ein Wechsel des Szenarios an oder läuft er gerade? Solange steht das Bild. */
    public boolean wechselt() { return wechselt; }

    /** Setzt den Blick mit dem nächsten Bild auf eine Ansicht. */
    public void ansicht(Ansicht a) { neueAnsicht = a; }

    /** Springt zur Szenenzeit t (s seit dem Start der Quelle); nur aus dem Archiv. */
    public void zeit(double t) { sprung = t; }

    /** Zeitraffer als Betrag; die Richtung setzt {@link #rueckwaerts}. */
    public void zeitraffer(double r) { raffer = Math.abs(r); }

    /** Die Zeit läuft zurück; nur aus dem Archiv. */
    public void rueckwaerts(boolean r) { rueckwaerts = r; }

    public void pause(boolean p) { pause = p; }

    private volatile boolean ruhen;

    /**
     * Seit W4 (F200 A): Die Bildschleife zeichnet nicht, solange {@code r} gilt (Karte frei für eine Ausgabe); das Bild im
     * Fenster bleibt stehen, die Szenenzeit auch.
     */
    public void ruhen(boolean r) { ruhen = r; }

    public boolean ruht() { return ruhen; }

    /** Beendet {@link #laufen} nach dem laufenden Bild. */
    public void beenden() { ende = true; }

    /** Der Stand des letzten Bilds. */
    public Zustand zustand() { return zustand; }

    /** Die Pegelreihe des Szenarios (null: kein Pegel gesetzt oder noch kein Szenario). Live wächst sie. */
    public Pegelreihe pegelreihe() { return reihe; }

    public int breite() { return flaeche != null ? flaeche.breite() : breite; }

    public int hoehe() { return flaeche != null ? flaeche.hoehe() : hoehe; }

    // ------------------------------------------------------------------ Szenario

    private void quelleWeg() {
        Foto f = foto;
        if (f != null) f.abbrechen();
        Lauf l = lauf;
        Archivquelle a = archiv;
        lauf = null;
        archiv = null;
        bild = null;
        messquelle = null;
        kimmung.ohneLuft();
        reihe = null;
        szenario = null;
        try {
            if (l != null) l.takt.anhalten();
        } catch (RuntimeException e) {
            fehler = "Lauf: " + e.getMessage();
        }
        g.ruhe();
        for (Spiegel s : wasser.netze()) wasser.weg(s.gitter.name);
        try {
            if (l != null) l.close();
            if (a != null) a.close();
        } catch (Exception e) {
            fehler = "Schließen der Quelle: " + e;
        }
    }

    /** Wechselt die Quelle jetzt, in diesem Faden. Nur rufen, wenn die Bildschleife nicht läuft (ohne Fenster). */
    public void oeffne(Szenario s) throws Exception {
        quelleWeg();
        Bild.Quelle q;
        if (!s.live()) {
            archiv = new Archivquelle(s.archiv.toPath());
            q = archiv;
            uhr = new Uhr(archiv.start);
            tGezeigt = Double.NaN;
            wartetSeitNs = 0;
            datenWarteZahl = 0;
            datenGewartetSumme = 0;
            double[] z = archiv.zeiten();
            tAnfang = z[0];
            tEnde = z[z.length - 1];
            uhr.setze(s.beginn != null ? Math.max(tAnfang, Math.min(tEnde, uhr.t(s.beginn))) : tAnfang);
        } else {
            Lauf.Einstellung e = new Lauf.Einstellung();
            e.gebiet = ordner;
            e.start = s.start;
            e.stufen(s.stufen);
            e.karte = true;
            e.fenster3 = s.fenster3;
            e.bild = true;
            if (s.sediment) {                                                // live mit Sediment (P9c): ab dem Ende des Anlaufs der Tide
                e.sediment = Lauf.Einstellung.Sediment.KARTE;
                e.sedimentAb = e.anlauf;
            }
            e.name = "Bühne " + s.name;
            lauf = Lauf.bauen(e);
            reglerAn(lauf);
            q = lauf.bild;
            uhr = new Uhr(s.start);
            tGezeigt = Double.NaN;
            wartetSeitNs = 0;
            tAnfang = tEnde = 0;
        }
        wasser.optik.schwebstoff = Double.isNaN(s.schwebstoff) ? new com.dan.core.szene.Wasseroptik().schwebstoff : s.schwebstoff;
        bild = new Bild(q, gebiet, wasser, gelaendebild);
        messquelle = new Messquelle(q, s.live() ? null : s.archiv);
        schnittAn = false;
        if (messblick != null) {
            messblick.zuruecksetzen();
            messblick.schnitt(null);
        }
        bild.kimmung = kimmung;
        bild.himmel = himmel;                         // seit P10d: Albedo der Luft aus dem Anteil des Wassers
        String pn;
        double po, pno, pu;
        synchronized (this) {
            pn = pegelName;
            po = pegelOst;
            pno = pegelNord;
            pu = pegelUmkreis;
        }
        if (!Double.isNaN(po)) reihe = Pegelreihe.aus(q, uhr.start, gebiet, pn, po, pno, pu);
        raffer = Math.abs(s.zeitraffer);
        rueckwaerts = false;
        pause = false;
        sprung = Double.NaN;
        szenario = s;
        if (lauf != null) lauf.takt.laufen(uhr);
    }

    // ------------------------------------------------------------------ Regler im Live-Lauf (seit Steuerhaus Y2, Werkbuch HalligHooge 153)

    private volatile double[] reglerZiel = {1, 0, 0, 0};

    /**
     * Seit Y2: die Ziele der Regler für den Live-Lauf – Faktor auf den Wind aus ERA5, Drehung (Grad, im Uhrzeigersinn),
     * Aufschlag und Anstieg des Meeresspiegels (m). Die beiden letzten wirken zusammen als Wasserstand am Rand der
     * innersten Stufe ({@code HydroModul.aufschlagZiel}; der Rand von G0 liegt im Atlantik). Antrieb und hydro fahren
     * die Ziele in der Modellzeit an. Sie gelten auch für den nächsten Live-Lauf. true: ein Live-Lauf nimmt sie jetzt.
     */
    public boolean regler(double windFaktor, double windDrehung, double aufschlag, double meeresspiegel) {
        reglerZiel = new double[] {windFaktor, windDrehung, aufschlag, meeresspiegel};
        Lauf l = lauf;
        if (l == null) return false;
        reglerAn(l);
        return true;
    }

    private void reglerAn(Lauf l) {
        double[] z = reglerZiel;
        if (l.antrieb != null) {
            l.antrieb.windFaktorZiel = z[0];
            l.antrieb.windDrehungZiel = z[1];
        }
        l.hydro.aufschlagZiel = z[2] + z[3];
    }

    /** Seit Y2: die wirksamen Werte im Live-Lauf {Windfaktor, Drehung (°), Wasserstand am Rand (m)}; null ohne Live-Lauf. */
    public double[] reglerIst() {
        Lauf l = lauf;
        if (l == null) return null;
        return new double[] {l.antrieb != null ? l.antrieb.windFaktor() : 1, l.antrieb != null ? l.antrieb.windDrehung() : 0, l.hydro.aufschlag()};
    }

    // ------------------------------------------------------------------ Bildschleife

    private void leeresBild() {
        flaeche.bild(null, 0, (c, ziel) -> new Ablauf(g, g.grafik).zeichne("löschen", new com.dan.core.gpu.Bild[] {ziel}, new float[][] {{0, 0, 0, 1}}, null, null, null, x -> { })
                .aufnehmen(c, null));
    }

    /** Die Bildschleife: läuft in diesem Faden, bis {@link #beenden} gerufen wird. */
    public void laufen() throws Exception {
        if (flaeche == null) throw new IllegalStateException("Buehne ohne Fenster: standbild() benutzen");
        gelaendebild.start();
        long letzt = System.nanoTime(), fpsT = letzt, bilder = 0;
        int fpsN = 0;
        double fps = 0;
        try {
            while (!ende) {
                Szenario ns = neuesSzenario;
                if (ns != null) {
                    neuesSzenario = null;
                    fehler = null;
                    try {
                        oeffne(ns);
                    } catch (Throwable e) {
                        quelleWeg();
                        fehler = ns.name + ": " + e;
                    }
                    wechselt = neuesSzenario != null;
                    letzt = System.nanoTime();
                }
                if (ruhen) {
                    // seit W4: eine Ausgabe rechnet auf derselben Karte; das Fenster zeichnet nicht (die Zeit steht)
                    Thread.sleep(50);
                    letzt = System.nanoTime();
                    continue;
                }
                long n = System.nanoTime();
                double dt = Math.min(0.25, (n - letzt) / 1e9);
                letzt = n;
                Ansicht na = neueAnsicht;
                if (na != null) {
                    neueAnsicht = null;
                    steuerung.setze(na, gelaende, utm, uhr != null ? uhr.jetzt() : Instant.now());
                }
                steuerung.bewegen(dt, gelaende);
                Blick bk0 = steuerung.blick();
                Zustand z = new Zustand();
                z.blick = bk0;
                z.fehler = fehler;
                if (bild == null) {
                    // noch kein Szenario (oder der Wechsel schlug fehl): die Fläche lebt weiter, das Bild bleibt schwarz
                    leeresBild();
                    Thread.sleep(30);
                    zustand = z;
                    continue;
                }
                // während eines Fotos steht die Zeit, Sprünge gelten nicht: das Foto rechnet über den Ständen seines Bilds
                Foto fa = foto;
                boolean fotoAktiv = fa != null && !fa.fertig();
                if (fotoAktiv) sprung = Double.NaN;
                double gewollt = pause || fotoAktiv ? 0 : raffer, ts;
                if (lauf != null) {
                    lauf.takt.ziel = gewollt;
                    ts = lauf.takt.fuehre(uhr, dt);
                    tEnde = Math.max(tEnde, lauf.takt.zeit());
                } else {
                    double sp = sprung;
                    if (!Double.isNaN(sp)) {
                        sprung = Double.NaN;
                        uhr.setze(Math.max(tAnfang, Math.min(tEnde, sp)));
                    }
                    uhr.zeitraffer = rueckwaerts ? -gewollt : gewollt;
                    double tVor = uhr.sekunden();
                    ts = uhr.vor(dt);
                    if (ts < tAnfang || ts > tEnde) uhr.setze(ts = Math.max(tAnfang, Math.min(tEnde, ts)));
                    // seit W5c (Werkbuch HalligHooge 131, F221 A): die Stände liest ein eigener Faden voraus; sind die zu
                    // ts nicht da (eine Platte wacht auf), hält die Uhr an und das Bild zeichnet weiter die letzte Zeit
                    Bild.Quelle q = bild.quelle;
                    q.vorlesen(ts, uhr.zeitraffer, VORLESEN_S);
                    if (!Double.isNaN(tGezeigt) && !q.bereit(ts)) {
                        if (wartetSeitNs == 0) wartetSeitNs = n;
                        uhr.setze(tVor);
                        ts = tGezeigt;
                    } else {
                        if (wartetSeitNs != 0) {
                            datenGewartet = (n - wartetSeitNs) / 1e9;
                            datenGewartetSumme += datenGewartet;
                            datenWarteZahl++;
                            wartetSeitNs = 0;
                        }
                        tGezeigt = ts;
                    }
                }
                // seit W3: eine Kamera-Quelle bestimmt den Blick dieses Bilds; die Steuerung folgt ihr
                final Blick bk = kameraBlick(bk0, dt, ts);
                z.blick = bk;
                long gc0 = gcMs();
                com.dan.core.archiv.Archiv.messung();
                long nZu = System.nanoTime();
                bild.zu(ts, bk.ost, bk.nord);
                long nAuf = System.nanoTime();
                zuLesen = com.dan.core.archiv.Archiv.messung();
                bild.blick(bk, (double) breite() / Math.max(1, hoehe()));
                windAnBewuchs();
                Instant jetzt = uhr.bei(ts);
                final double dtf = dt;
                final List<com.dan.core.gpu.Auflage> auf = auflagen(ts, true);
                long nKarte = System.nanoTime();
                flaeche.bild(null, 0, (c, ziel) -> {
                    // Das Bildziel folgt der Swapchain, wie sie für dieses Bild gebaut ist (die Fläche baut beim Holen
                    // neu, wenn sich das Fenster geändert hat); vorher ruht die Karte, die alten Ziele sind frei
                    if (rahmen.ziel().breite != ziel.breite || rahmen.ziel().hoehe != ziel.hoehe) {
                        g.ruhe();
                        rahmen.groesse(ziel.breite, ziel.hoehe);
                    }
                    stoppuhr.runde(c);
                    Ablauf a = new Ablauf(g, g.grafik);
                    rahmen.bild(a, bildteile(), auf, bk, jetzt, ziel, dtf);
                    a.aufnehmen(c, stoppuhr);
                });
                stoppuhr.sammle(false);
                long nFertig = System.nanoTime();
                if (nFertig - n >= LANG * 1e9) gcBild = gcMs() - gc0;
                langesBild(n, nZu, nAuf, nKarte, nFertig, ts);
                bildzeitMerken(nFertig, (nFertig - n) / 1e6f);
                bilder++;
                fpsN++;
                if (n - fpsT > 500_000_000L) {
                    fps = fpsN / ((n - fpsT) / 1e9);
                    fpsN = 0;
                    fpsT = n;
                }
                Pegelreihe pr = reihe;
                if (pr != null && lauf != null) pr.ergaenze(lauf.bild);
                z.szenario = szenario;
                z.live = lauf != null;
                z.pause = pause;
                z.rueckwaerts = rueckwaerts && lauf == null;
                z.herkunft = bild.quelle.herkunft();
                z.t = ts;
                z.jetzt = jetzt;
                z.start = uhr.start;
                z.tAnfang = tAnfang;
                z.tEnde = tEnde;
                z.gewollt = gewollt;
                z.gehalten = uhr.zeitraffer;
                z.erreichbar = lauf != null ? lauf.takt.erreichbar() : Double.NaN;
                z.bilderJeS = fps;
                z.datenWarten = wartetSeitNs == 0 ? 0 : (n - wartetSeitNs) / 1e9;
                z.datenGewartet = datenGewartet;
                z.datenWarteZahl = datenWarteZahl;
                z.datenGewartetSumme = datenGewartetSumme;
                z.wind = Math.hypot(bild.windU, bild.windV);
                z.windU = bild.windU;
                z.windV = bild.windV;
                if (pr != null) {
                    float e = wasser.etaBild((float) (pr.gitter.x(pr.zelle % pr.gitter.nx) - bk.ost), (float) (bk.nord - pr.gitter.y(pr.zelle / pr.gitter.nx)), null);
                    z.pegel = e;
                }
                z.bilder = bilder;
                zustand = z;
                Fotowunsch fw = fotoWunsch;
                if (fw != null && pause) {
                    // die Zeit steht, das Bild zu dieser Zeit ist gezeichnet: die Referenz über denselben Ständen bauen
                    fotoWunsch = null;
                    try {
                        Foto f = baueFoto(bk, jetzt, fw.titel(), fw.breite(), fw.hoehe());
                        f.zeitgrenze = fw.zeitgrenze();
                        foto = f;
                        Thread t = new Thread(() -> {
                            f.rechne();
                            try {
                                if (f.ende() != Foto.Ende.ABGEBROCHEN && f.ende() != Foto.Ende.FEHLER) f.lege(fw.ablage());
                            } catch (Exception e) {
                                fehler = "Foto ablegen: " + e;
                            }
                        }, "Foto");
                        t.setDaemon(true);
                        t.start();
                    } catch (Throwable e) {
                        fehler = "Foto: " + e;
                    }
                }
                if (lauf != null && !lauf.takt.laeuft()) {
                    // ein Fehler in einem Modul hat den Takt angehalten: die Quelle geht weg, die Bühne läuft weiter
                    quelleWeg();
                    if (fehler == null) fehler = "Der Lauf hat angehalten.";
                }
            }
        } finally {
            gelaendebild.stop();
        }
    }

    // ------------------------------------------------------------------ Foto (P11b)

    /**
     * Wünscht ein Foto der Ansicht, wie sie gerade steht (F109 A): Die Zeit hält an; mit dem nächsten Bild baut die Bühne
     * die Referenz über denselben Ständen und rechnet das Foto in einem eigenen Faden; am Ende legt sie es in
     * {@code ablage} ab. Ein laufendes Foto bricht dabei ab. Den Stand gibt {@link #foto()}.
     */
    public void foto(String titel, int breite, int hoehe, File ablage) { foto(titel, breite, hoehe, ablage, Foto.ZEITGRENZE); }

    /** Wie {@link #foto(String, int, int, File)} mit eigener Zeitgrenze (s; seit F114 B in der Anwendung wählbar). */
    public void foto(String titel, int breite, int hoehe, File ablage, double zeitgrenze) {
        Foto f = foto;
        if (f != null && !f.fertig()) f.abbrechen();
        pause = true;
        fotoWunsch = new Fotowunsch(titel, Math.max(16, breite), Math.max(16, hoehe), ablage, zeitgrenze > 0 ? zeitgrenze : Foto.ZEITGRENZE);
    }

    /** Das laufende oder letzte Foto (null: keins). */
    public Foto foto() { return foto; }

    /** Bricht ein laufendes Foto ab (es wird nicht abgelegt). */
    public void fotoAbbrechen() {
        fotoWunsch = null;
        Foto f = foto;
        if (f != null) f.abbrechen();
    }

    /** Ohne Fenster: ein Foto zur Szenenzeit t – erst das Standbild (Belichtung, Kimmung), dann die Referenz. Rechnen: {@link Foto#rechne}. */
    public Foto foto(Blick blick, double t, String titel, int breite, int hoehe) {
        standbild(blick, t);
        return baueFoto(blick.kopie().groesse(this.breite, this.hoehe), uhr.start.plusMillis(Math.round(t * 1000)), titel, breite, hoehe);
    }

    private Foto baueFoto(Blick b, Instant jetzt, String titel, int fb, int fh) {
        // die Schranke deckt rund 262 km um ihre Mitte; neu erst, wenn das Auge mehr als 50 km [G] davon weg ist
        if (schranke == null || Math.hypot(b.ost - schranke.ost0 - Schranke.N * Schranke.BASIS / 2, b.nord - schranke.nord0 - Schranke.N * Schranke.BASIS / 2) > 50_000)
            schranke = new Schranke(gelaende, b.ost, b.nord);
        // seit P12c3: mit Gestalt, Häusern, Körpern und Gehölzen, wie sie im Bild an sind
        com.dan.render.reference.Referenz r = Foto.referenz(gelaendebild, wasser, himmel, kimmung, bild, b, jetzt, schranke, haeuser,
                bewuchs != null && bewuchs.an ? bewuchs : null);
        return new Foto(r, b, fb, fh, jetzt, titel, rahmen);
    }

    // ------------------------------------------------------------------ ohne Fenster

    /**
     * Ein Standbild zur Szenenzeit t (s seit dem Start der Quelle) auf dem Weg von {@code BildCheck}: zwölfmal
     * gezeichnet, jede Messung der Belichtung gilt sofort. Gibt RGBA je Bildpunkt (0 … 1), zeilenweise von oben.
     */
    public float[] standbild(Blick blick, double t) {
        if (ausgabe == null) throw new IllegalStateException("Buehne mit Fenster: laufen() benutzen");
        if (bild == null) throw new IllegalStateException("Buehne: erst oeffne(Szenario)");
        Blick b = blick.kopie().groesse(breite, hoehe);
        Instant zeit = uhr.start.plusMillis(Math.round(t * 1000));
        // ein Standbild hat keine laufende Zeit: Die Streifen des Sandfegens (P10c) stehen bei 0, sonst wäre es nicht wiederholbar
        double flugZeit = gelaendebild.flugZeit;
        gelaendebild.flugZeit = 0;
        double windZeit = bewuchs != null ? bewuchs.windZeit : Double.NaN;
        if (bewuchs != null) bewuchs.windZeit = 0;
        try {
            if (messblickAn && messblick != null) {
                // ein Standbild hat keine laufende Zeit: die Partikel neu und über eine Spur vorlaufen lassen (im Feld zur Zeit t)
                messblick.zuruecksetzen();
                messblick.vorlauf(messblick.spurDauer, 4 * (com.dan.render.analysis.Messblick.K - 1));
            }
            for (int i = 0; i < 12; i++) {
                rahmen.belichtung.sofort();
                bild.zu(t, b.ost, b.nord);
                bild.blick(b, (double) breite / Math.max(1, hoehe));
                windAnBewuchs();
                List<com.dan.core.gpu.Auflage> auf = auflagen(t, mitEinblendung);
                Ablauf a = new Ablauf(g, g.grafik);
                rahmen.bild(a, bildteile(), auf, b, zeit, ausgabe, 0.5);
                g.grafik.einmal(c -> a.aufnehmen(c, null));
                if (i == 0 && messblickAn && auf.contains(messblick)) messblick.linienAbwarten();       // die Isolinien rechnen in einem eigenen Faden
            }
        } finally {
            gelaendebild.flugZeit = flugZeit;
            if (bewuchs != null) bewuchs.windZeit = windZeit;
        }
        return ausgabe.lies();
    }

    // ------------------------------------------------------------------ Messblick (P11c2)

    /** Der Messblick (beim ersten Aufruf angelegt); seine Ebenen stellt der Aufrufer, an und aus {@link #messblick(boolean)}. */
    public synchronized com.dan.render.analysis.Messblick messblick() {
        if (messblick == null) messblick = halte(new com.dan.render.analysis.Messblick(g));
        return messblick;
    }

    /**
     * Seit P11c3: der Ort im Bild (fx, fy in 0 … 1 von links oben) auf der ersten Fläche längs des Sehstrahls – Gelände
     * oder, im Gitter des Messblicks, Wasser: {Ost, Nord, Höhe}; null, wenn der Strahl in 50 km [G] nichts trifft.
     */
    public double[] ort(double fx, double fy) {
        Buehne.Zustand z = zustand;
        if (z.blick == null) return null;
        return ort(z.blick, fx, fy);
    }

    /** Seit W2: wie {@link #ort(double, double)} mit einem gegebenen Blick (ohne Fenster, für den Maßstab und Prüfungen). */
    public double[] ort(Blick blick, double fx, double fy) {
        Blick b = blick.kopie().groesse(breite(), hoehe());
        double[] d = b.strahl(fx * b.breite, fy * b.hoehe_px);
        double ost = b.ost, nord = b.nord, h = b.hoehe;
        // d relativ zum Auge: x nach Ost, y nach oben, z nach Süd
        double sAlt = 0;
        for (double sd = 0.5; sd < 50_000; sd += Math.max(0.5, 0.004 * sd)) {
            if (flaeche(ost + d[0] * sd, nord - d[2] * sd, sd) >= h + d[1] * sd) {
                double lo = sAlt, hi = sd;
                for (int i = 0; i < 40; i++) {
                    double m = 0.5 * (lo + hi);
                    if (flaeche(ost + d[0] * m, nord - d[2] * m, m) >= h + d[1] * m) hi = m;
                    else lo = m;
                }
                return new double[] {ost + d[0] * hi, nord - d[2] * hi, h + d[1] * hi};
            }
            sAlt = sd;
        }
        return null;
    }

    /** Höhe der ersten Fläche am Ort: Gelände oder Wasser (aus dem Messblick), was höher ist. */
    private double flaeche(double ost, double nord, double abstand) {
        double g = gelaende.hoehe(ost, nord, Math.max(1, 0.002 * abstand));
        Messquelle mq = messquelle;
        double w = mq == null ? Double.NaN : mq.wasser(ost, nord);
        if (Double.isNaN(g)) g = Double.NEGATIVE_INFINITY;
        return Double.isNaN(w) ? g : Math.max(g, w);
    }

    /** Seit P11c3: Höhe des Geländes am Ort (m NHN; Schritt 1 m), für das Profil eines Schnitts. */
    public double gelaendeHoehe(double ost, double nord) { return gelaende.hoehe(ost, nord, 1); }

    /** Seit X5: Abstände (m) und Richtungen, in denen {@link #tonWerte} nach Wasser sucht [G]. */
    private static final double[] TON_RINGE = {0, 12, 25, 50, 100, 200, 400};
    private static final int TON_RICHTUNGEN = 12;

    /**
     * Seit X5 (Werkbuch HalligHooge 151): Werte für den Ton am Ort des Auges, schnell genug für jeden Takt der Oberfläche
     * und jedes Bild eines Films: {Wasserstand am Ort (m NHN; NaN: trocken), Boden (m NHN), Abstand zum nächsten
     * Wasser (m; bis 400, sonst +∞), Hs dort (m; NaN ohne Seegang), Höhe der Sonne (Grad), Richtung zum Wasser (Grad,
     * von Nord; NaN: keins)}. Wasser heißt: mehr als 5 cm über dem Boden. Aus jedem Faden; Fehler geben NaN.
     */
    public double[] tonWerte(double ost, double nord, Instant zeit) {
        double[] r = {Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN};
        try {
            r[1] = gelaende.hoehe(ost, nord, 1);
            r[0] = wasserAm(ost, nord);
            if (!Double.isNaN(r[0])) {
                r[2] = 0;
                r[5] = Double.NaN;
            } else {
                suche:
                for (int k = 1; k < TON_RINGE.length; k++)
                    for (int j = 0; j < TON_RICHTUNGEN; j++) {
                        double a = 2 * Math.PI * j / TON_RICHTUNGEN, o = ost + TON_RINGE[k] * Math.sin(a), n = nord + TON_RINGE[k] * Math.cos(a);
                        if (!Double.isNaN(wasserAm(o, n))) {
                            r[2] = TON_RINGE[k];
                            r[5] = Math.toDegrees(a);
                            break suche;
                        }
                    }
            }
            if (wasser != null && wasser.seegang && wasser.brandung && !Double.isInfinite(r[2])) {
                double a = Double.isNaN(r[5]) ? 0 : Math.toRadians(r[5]);
                double[] w = com.dan.core.szene.Seegangsoptik.werte(wasser.seeNetze(), wasser.zeit, ost + r[2] * Math.sin(a), nord + r[2] * Math.cos(a));
                if (w != null) r[3] = w[0];
            }
            if (zeit != null && utm != null) {
                double[] ll = utm.grad(ost, nord);
                r[4] = com.dan.core.astro.Gestirne.bei(zeit).sonne(ll[0], ll[1]).hoeheGrad();
            }
        } catch (RuntimeException x) {
            // ohne Werte: der Ton bleibt, wie er war
        }
        return r;
    }

    /** Wasserstand am Ort (m NHN), wenn dort mehr als 5 cm Wasser über dem Boden stehen; sonst NaN. */
    private double wasserAm(double ost, double nord) {
        Messquelle mq = messquelle;
        double w = Double.NaN;
        if (mq != null) w = mq.wasser(ost, nord);
        else if (wasser != null && wasser.spiegelbild() != null) {
            float e = wasser.spiegelbild().etaBildUtm(ost, nord, null);
            w = Float.isNaN(e) || e < -1e29f ? Double.NaN : e;
        }
        if (Double.isNaN(w)) return Double.NaN;
        double g = gelaende.hoehe(ost, nord, 1);
        return Double.isNaN(g) || w - g > 0.05 ? w : Double.NaN;
    }

    /**
     * Seit P11c3: ein Schnitt von a nach b (UTM) auf dem Gitter des Messblicks; er wird im Bild weiß gezeichnet (auch bei
     * ausgeschaltetem Messblick). Null, wenn das Szenario kein Gitter für den Messblick hat.
     */
    public Schnitt schnitt(double[] a, double[] b) {
        Messquelle mq = messquelle;
        if (mq == null || mq.gitter == null) return null;
        com.dan.render.analysis.Querschnitt s = com.dan.render.analysis.Querschnitt.von(mq.gitter, a[0], a[1], b[0], b[1]);
        int n = (int) Math.max(2, Math.ceil(s.laenge / 5)) + 1;              // je 5 m ein Punkt [G]
        float[] v = new float[8 * (n - 1)];
        double[] alt = null;
        for (int k = 0; k < n; k++) {
            double f = (double) k / (n - 1), o = s.ostA + f * (s.ostB - s.ostA), no = s.nordA + f * (s.nordB - s.nordA);
            double hh = flaeche(o, no, 1) + 0.5;
            double[] p = {o - mq.gitter.x0, no - mq.gitter.y0, Double.isInfinite(hh) ? 0 : hh};
            if (alt != null) {
                int q = 8 * (k - 1);
                v[q] = (float) alt[0];
                v[q + 1] = (float) alt[1];
                v[q + 2] = (float) alt[2];
                v[q + 4] = (float) p[0];
                v[q + 5] = (float) p[1];
                v[q + 6] = (float) p[2];
            }
            alt = p;
        }
        messblick().schnitt(v);
        schnittAn = true;
        return new Schnitt(s);
    }

    /** Seit P11c3: das Profil des Schnitts zur Zeit des letzten Bilds (null ohne Stand). */
    public Schnitt.Profil schnittProfil(Schnitt s) {
        Messquelle mq = messquelle;
        return mq == null ? null : Schnitt.Profil.aus(mq.profil(s.q, this::gelaendeHoehe));
    }

    /** Seit P11c3: die Reihe Q(t) durch den Schnitt über alle Stände ({Zeiten, Q in m³/s}); liest das Archiv, kann Sekunden dauern. */
    public double[][] schnittReihe(Schnitt s) {
        Messquelle mq = messquelle;
        return mq == null ? null : mq.durchfluss(s.q);
    }

    /** Seit P11c3: die Ebenen des Messblicks (Art der Linien: 0 Wasserstand, 1 Tiefe, 2 Trübung); an oder aus nach der Wahl. */
    public void messblickEbenen(boolean partikel, boolean lic, boolean linien, int art) {
        boolean an = partikel || lic || linien;
        if (an) {
            com.dan.render.analysis.Messblick m = messblick();
            m.partikel = partikel;
            m.lic = lic;
            m.linien = linien;
            m.art = com.dan.render.analysis.Messblick.Linienart.values()[Math.max(0, Math.min(2, art))];
        }
        messblick(an);
    }

    /** Seit P11c3: Stand der Validierungsschicht – null ohne sie, sonst „n Fehler“ und die erste Meldung (für Prüfungen). */
    public String validierung() {
        if (!g.validierung) return null;
        return g.validierungsFehler + " Fehler (" + g.schicht + ")" + (g.validierungsFehler > 0 && !g.meldungen.isEmpty() ? ": " + g.meldungen.get(0) : "");
    }

    /** Seit P11c3: Zahl der Fehler der Validierungsschicht (−1 ohne sie). */
    public int validierungsFehler() { return g.validierung ? g.validierungsFehler : -1; }

    /** Seit P11c3: ein Hinweis des Messblicks zur Zeit t (null: keiner), etwa „schwingt ein“ (F118 A). */
    public String messHinweis(double t) {
        Messquelle mq = messquelle;
        return mq == null ? null : mq.hinweis(t);
    }

    /** Seit P11c3: hat der Messblick im laufenden Szenario ein Gitter und Strömung? */
    public String messGitter() {
        Messquelle mq = messquelle;
        return mq == null || mq.gitter == null ? null : mq.gitter + (mq.stroemung ? ", Strömung aus dem Nachlauf" : ", ohne Strömung");
    }

    /** Seit P11c3: nimmt den Schnitt aus dem Bild. */
    public void schnittWeg() {
        schnittAn = false;
        if (messblick != null) messblick.schnitt(null);
    }

    /**
     * Seit P12a: die Teile des Bilds in ihrer Folge. Die Häuser stehen vor dem Gelände: Sie legen beim Zeichnen ihren
     * Schatten ab, den Gelände und Wasser lesen (render.bau).
     */
    private List<com.dan.core.gpu.Bildteil> bildteile() {
        // seit P12c: der Bewuchs nach den Häusern (zeichnet sich in ihre Karte des Schattens) und vor dem Gelände
        List<com.dan.core.gpu.Bildteil> l = new ArrayList<>();
        if (haeuser != null) l.add(haeuser);
        if (bewuchs != null) l.add(bewuchs);
        // seit P13c: die Brandung nach dem Wasser (dessen Tiefe verdeckt die Brecher unter der Fläche), vor dem Himmel
        l.add(gelaendebild);
        l.add(wasser);
        if (brandung != null) l.add(brandung);
        l.addAll(List.of(himmel, kimmung));
        return l;
    }

    /** Seit P12c3: die Tiefe des letzten Standbilds (umgekehrtes Z: nah / Abstand längs der Blickachse; 0 Himmel). */
    public float[] tiefe() { return rahmen.ziel().tiefe.lies(); }

    /** Seit P12a: die Belichtung des Rahmens (für Prüfungen mit fester Belichtung). */
    public com.dan.render.frame.Belichtung belichtung() { return rahmen.belichtung; }

    /** Seit P12a: Hat das Gebiet Häuser? */
    public boolean hatHaeuser() { return haeuser != null; }

    /** Seit P12a: Häuser an oder aus; aus ist das Bild wie ohne Häuser (Gelände und Wasser ohne ihren Schatten). */
    public void haeuser(boolean an) { if (haeuser != null) haeuser.an = an; }

    public boolean haeuserAn() { return haeuser != null && haeuser.an; }

    /** Seit P12c: der Wind aus dem Bild an den Bewuchs (Gehölze und Halme biegen sich). */
    private void windAnBewuchs() {
        if (bewuchs != null && bild != null) {
            bewuchs.windU = bild.windU;
            bewuchs.windV = bild.windV;
        }
    }

    /** Seit P12c: Hat das Gebiet Bewuchs ({@code bau/gehoelz.bin})? */
    public boolean hatBewuchs() { return bewuchs != null; }

    /** Seit P12c: Bewuchs an oder aus (Gehölze und Halme); aus ist das Bild wie in P12b. */
    public void bewuchs(boolean an) { if (bewuchs != null) bewuchs.an = an; }

    public boolean bewuchsAn() { return bewuchs != null && bewuchs.an; }

    /** Seit P13b: das Bildmodul des Geländes (für Prüfungen). */
    public Gelaendebild gelaendebild() { return gelaendebild; }

    /** Seit P13b: Hat das Gebiet Zonen der Salzwiese ({@code vegetation/zonen})? */
    /** Seit P13c: Hat das Gebiet Kanten der Brandung? */
    public boolean hatBrandung() { return brandung != null; }

    /**
     * Seit P13c: Brandung an oder aus – im Wasser die langen Wellen mit Höhe, Weißwasser und Schaumköpfe, an den Kanten
     * Brecher, Auflauf und Gischt. Aus: das Bild wie bis P13b.
     */
    public void brandung(boolean an) {
        wasser.brandung = an;
        if (brandung != null) brandung.an = an;
    }

    public boolean brandungAn() { return wasser.brandung; }

    /**
     * Seit P13d (A36): Streulicht des Nachthimmels an oder aus – das Licht des Nachthimmels, das die Luft auf dem Weg
     * zur Kimm schluckt, kommt als gleichmäßige Himmelshelligkeit zurück. Aus: das Bild wie bis P13c (bitgleich); bei Tag
     * (Sonne über −3°) ohne Wirkung.
     */
    public void nachtStreu(boolean an) { himmel.nachtStreu = an ? com.dan.render.air.Himmel.NACHT_STREU : 0; }

    public boolean nachtStreuAn() { return himmel.nachtStreu > 0; }

    /** Seit P13c: der Bildteil der Brandung (null ohne Kanten), für Prüfungen. */
    public com.dan.render.brandung.Brandungsbild brandungsbild() { return brandung; }

    /** Seit P13c: Gerät und Konto der Bühne (für Proben auf der Karte). */
    public Geraet geraet() { return g; }

    public Konto konto() { return konto; }

    /** Seit P13c: das Wasser (für Prüfungen der Brandung im Wasser). */
    public Wasserbild wasserbild() { return wasser; }

    public boolean hatSalzwiese() { return gelaendebild.hatZonen; }

    /** Seit P13b (F152 A): Zonen der Salzwiese im Bild an oder aus (Muster des Geländes und Halme); aus ist das Bild wie in P12c. */
    public void salzwiese(boolean an) {
        gelaendebild.zonenAn = an;
        if (bewuchs != null) bewuchs.zonenAn = an;
    }

    public boolean salzwieseAn() { return gelaendebild.hatZonen && gelaendebild.zonenAn; }

    /** Seit P12c: das Bildmodul des Bewuchses (für Prüfungen; null ohne). */
    public com.dan.render.bewuchs.Bewuchsbild bewuchsbild() { return bewuchs; }

    /** Seit P12b: Hat das Gebiet eine Gestalt (Karte {@code gestalt/} oder Körper {@code bau/koerper.bin})? */
    public boolean hatGestalt() { return gelaendebild.gestalt() != null || haeuser != null && haeuser.hatKoerper(); }

    /**
     * Seit P12b: Gestalt an oder aus – die Karte im Gelände (Wasser in Gräben und Fethingen, Wege, Deckwerk, Gras) und
     * die Körper (Lahnungen, Buhnen, Anleger). Aus ist das Bild wie in P12a.
     */
    public void gestalt(boolean an) {
        gelaendebild.gestaltAn = an;
        if (haeuser != null) haeuser.koerperAn = an;
    }

    public boolean gestaltAn() { return gelaendebild.gestaltAn && (gelaendebild.gestalt() != null || haeuser != null && haeuser.koerperAn && haeuser.hatKoerper()); }

    /** Seit P12a: das Bildmodul der Häuser (für Prüfungen; null ohne Häuser). */
    public Hausbild hausbild() { return haeuser; }

    /** Messblick an oder aus; aus ist das Bild bitgleich zu dem ohne Messblick. */
    public void messblick(boolean an) {
        if (an) messblick();
        messblickAn = an;
    }

    public boolean messblickAn() { return messblickAn; }

    /** Die Quelle des Messblicks im laufenden Szenario (null: keins); sagt, ob es Strömung und Trübung gibt. */
    public Messquelle messquelle() { return messquelle; }

    /**
     * Die Auflagen des nächsten Bilds zur Szenenzeit t: der Messblick, wenn er an ist und Felder hat; seit W2 danach die
     * Einblendungen, wenn {@code mitEinblendung} und etwas zu zeigen ist (ohne beides bleibt das Bild bitgleich).
     */
    private List<com.dan.core.gpu.Auflage> auflagen(double t, boolean mitEinblendung) {
        Messquelle mq = messquelle;
        com.dan.render.overlay.Einblendung eb = einblendung;
        boolean mitE = mitEinblendung && eb != null && !eb.leer();
        boolean mitM = (messblickAn || schnittAn) && messblick != null && mq != null && mq.zu(t, messblick);
        if (!mitM && !mitE) return List.of();
        List<com.dan.core.gpu.Auflage> l = new ArrayList<>(2);
        if (mitM) l.add(messblick);
        if (mitE) l.add(eb);
        return l;
    }

    // ------------------------------------------------------------------ Filmbild (W4)

    private double filmZeit, filmOffen;
    private long filmBilder;
    /** Seit W4: Liegen die Einblendungen (und der Messblick) auf den Filmbildern? Vorgabe ja (F189 A: im Video wählbar). */
    public volatile boolean filmMitEinblendung = true;

    /**
     * Seit W4 (Werkbuch HalligHooge 121, F200–F205 A): ein Bild wie in der Bildschleife, aber ohne Fenster und mit festem
     * Takt {@code dt} (echte Sekunden des Films). Wie im Fenster: Szenario wechseln, Ansicht, Uhr mit Zeitraffer und Sprung,
     * Kamera, Stände, Auflagen, Rahmen. Wind der Halme und Flugsand laufen auf der Filmzeit, nicht auf der Uhr des Rechners;
     * das Gelände lädt in diesem Faden. Zwei gleiche Folgen geben darum dieselben Bilder (Prüfstein 1).
     * <p>
     * {@code zeichnen = false} rückt nur Uhr und Kamera vor (der Verschluss ist zu); das nächste gezeichnete Bild bekommt
     * die ganze Zeit seit dem letzten als Takt des Rahmens. Nur aus dem Archiv (F205 A): live wirft.
     *
     * @return der Zustand dieses Bilds; die Bildpunkte gibt {@link #filmBytes()}
     */
    public Zustand filmbild(double dt, boolean zeichnen) {
        if (ausgabe == null) throw new IllegalStateException("Buehne mit Fenster: laufen() benutzen");
        Szenario ns = neuesSzenario;
        if (ns != null) {
            neuesSzenario = null;
            fehler = null;
            try {
                oeffne(ns);
            } catch (Throwable e) {
                quelleWeg();
                fehler = ns.name + ": " + e;
            }
            wechselt = neuesSzenario != null;
        }
        if (bild == null) throw new IllegalStateException("Buehne: kein Szenario" + (fehler != null ? " (" + fehler + ")" : ""));
        if (lauf != null) throw new IllegalStateException("Buehne: Filmbilder nur aus dem Archiv, nicht live (" + szenario.name + ")");
        Ansicht na = neueAnsicht;
        if (na != null) {
            neueAnsicht = null;
            steuerung.setze(na, gelaende, utm, uhr.jetzt());
        }
        Blick bk0 = steuerung.blick().kopie().groesse(breite, hoehe);
        double sp = sprung;
        if (!Double.isNaN(sp)) {
            sprung = Double.NaN;
            uhr.setze(Math.max(tAnfang, Math.min(tEnde, sp)));
        }
        double gewollt = pause ? 0 : raffer;
        uhr.zeitraffer = rueckwaerts ? -gewollt : gewollt;
        double ts = uhr.vor(dt);
        if (ts < tAnfang || ts > tEnde) uhr.setze(ts = Math.max(tAnfang, Math.min(tEnde, ts)));
        filmZeit += dt;
        filmOffen += dt;
        Blick bk = kameraBlick(bk0, dt, ts);
        bk.groesse(breite, hoehe);
        Instant jetzt = uhr.bei(ts);
        Zustand z = new Zustand();
        z.blick = bk;
        z.fehler = fehler;
        if (zeichnen) {
            bild.zu(ts, bk.ost, bk.nord);
            bild.blick(bk, (double) breite / Math.max(1, hoehe));
            windAnBewuchs();
            gelaendebild.flugZeit = filmZeit;
            if (bewuchs != null) bewuchs.windZeit = filmZeit;
            List<com.dan.core.gpu.Auflage> auf = auflagen(ts, filmMitEinblendung);
            Ablauf a = new Ablauf(g, g.grafik);
            rahmen.bild(a, bildteile(), auf, bk, jetzt, ausgabe, Math.max(1e-4, filmOffen));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            filmOffen = 0;
            filmBilder++;
        }
        z.szenario = szenario;
        z.live = false;
        z.pause = pause;
        z.rueckwaerts = rueckwaerts;
        z.herkunft = bild.quelle.herkunft();
        z.t = ts;
        z.jetzt = jetzt;
        z.start = uhr.start;
        z.tAnfang = tAnfang;
        z.tEnde = tEnde;
        z.gewollt = gewollt;
        z.gehalten = uhr.zeitraffer;
        z.bilderJeS = dt > 0 ? 1 / dt : 0;
        z.wind = Math.hypot(bild.windU, bild.windV);
        z.windU = bild.windU;
        z.windV = bild.windV;
        Pegelreihe pr = reihe;
        if (pr != null) z.pegel = wasser.etaBild((float) (pr.gitter.x(pr.zelle % pr.gitter.nx) - bk.ost), (float) (bk.nord - pr.gitter.y(pr.zelle / pr.gitter.nx)), null);
        z.bilder = filmBilder;
        zustand = z;
        return z;
    }

    /** Seit W4: Filmzeit (s) seit dem Öffnen der Bühne, Summe der {@code dt} von {@link #filmbild}. */
    public double filmZeit() { return filmZeit; }

    /** Seit W4: die Bildpunkte des letzten gezeichneten Bilds, RGBA8 sRGB, zeilenweise von oben (wartet). */
    public byte[] filmBytes() {
        if (ausgabe == null) throw new IllegalStateException("Buehne mit Fenster");
        java.nio.ByteBuffer r = ausgabe.roh();
        byte[] b = new byte[r.remaining()];
        r.get(b);
        return b;
    }

    /** Seit W4: belegter Speicher auf der Karte jetzt und höchstens (Bytes), aus dem Konto. */
    public long[] speicher() { return new long[] {konto.belegt(), konto.spitze()}; }

    // ------------------------------------------------------------------ Analyse (W5)

    /**
     * Seit W5 (Werkbuch HalligHooge 126, F209 A): eine Sonde an einem Ort über alle Stände der Quelle des offenen
     * Szenarios; seit W5b aus dem Archiv über eine eigene Quelle, so wartet die Bildschleife nicht.
     */
    public Sonde sonde(String name, double ost, double nord) {
        Bild bi = bild;
        Szenario s = szenario;
        if (bi == null || uhr == null) throw new IllegalStateException("Buehne: kein Szenario");
        // seit W5b (F216 A): aus dem Archiv über eine eigene Quelle; die laufende (Sperre, gehaltene Sätze) gehört dem Bild
        if (s != null && s.archiv != null) {
            try {
                return Sonde.ausArchiv(s, gebiet, name, ost, nord);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Sonde: " + e.getMessage(), e);
            }
        }
        return Sonde.aus(bi.quelle, uhr.start, gebiet, name, ost, nord);
    }

    private final float[] bildMs = new float[4096];
    private final long[] bildNs = new long[4096];
    private int bildK;

    /** Seit W5: merkt die Dauer eines Bilds der Bildschleife (ms) für {@link #bildzeitVerlauf} (öffentlich für Prüfungen). */
    public void bildzeitMerken(long jetztNs, float ms) {
        synchronized (bildMs) {
            int k = bildK++ & 4095;
            bildMs[k] = ms;
            bildNs[k] = jetztNs;
        }
    }

    /** Seit W5: die Dauern der Bilder der letzten {@code sekunden} (ms, älteste zuerst). */
    public float[] bildzeitVerlauf(double sekunden) {
        synchronized (bildMs) {
            long grenze = System.nanoTime() - (long) (sekunden * 1e9);
            int n = Math.min(bildK, 4096), m = 0;
            float[] r = new float[n];
            for (int i = bildK - n; i < bildK; i++) {
                int k = i & 4095;
                if (bildNs[k] >= grenze) r[m++] = bildMs[k];
            }
            return java.util.Arrays.copyOf(r, m);
        }
    }

    /** Seit W5: das gleitende Mittel jedes Abschnitts der Stoppuhr im Fenster (ms), teuerste zuerst; leer ohne Fenster. */
    public java.util.LinkedHashMap<String, Double> fensterAbschnitte() {
        java.util.LinkedHashMap<String, Double> r = new java.util.LinkedHashMap<>();
        if (stoppuhr == null) return r;
        List<String> ab = stoppuhr.abschnitte();
        ab.sort((x, y) -> Double.compare(stoppuhr.ms(y), stoppuhr.ms(x)));
        for (String a : ab) r.put(a, stoppuhr.ms(a));
        return r;
    }

    // ------------------------------------------------------------------ lange Bilder (W3b)

    /** Seit W3b (Werkbuch HalligHooge 118, A47): ab dieser Dauer (s) meldet die Bildschleife ein Bild als lang. */
    public static final double LANG = 0.5;
    private volatile String langesBild;
    private volatile double langesBildS;
    private int gpuNach = -1;
    private String langText;

    /**
     * Ein Bild, das länger als {@link #LANG} dauerte, mit seinen Teilen: vorher (Szenario, Steuerung, Uhr, Kamera), zu (die
     * Stände des Bilds, aufgeteilt nach {@link Bild#ZU_TEILE}), Auflagen und Karte (Aufnehmen und Abschicken, Warten auf
     * die Fläche); vier Bilder später dazu die längsten Abschnitte der Stoppuhr. Steht auch in der Ausgabe. null: keins.
     */
    public String langesBild() { return langesBild; }

    /** Dauer (s) des längsten Bilds der Bildschleife bisher. */
    public double laengstesBild() { return langesBildS; }

    private volatile com.dan.core.archiv.Archiv.Messung zuLesen;
    /** Seit W5c: so viele Sekunden Echtzeit liest der Vorleser voraus (beim Zeitraffer des Bilds). */
    public static final double VORLESEN_S = 60;
    private double tGezeigt = Double.NaN, datenGewartet, datenGewartetSumme;
    private long wartetSeitNs;
    private int datenWarteZahl;
    private long gcBild;
    private static final List<java.lang.management.GarbageCollectorMXBean> GC = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();

    /** Seit W5b: Zeit der anhaltenden Speicherbereinigungen seit Start (ms). */
    static long gcMs() {
        long s = 0;
        // ohne „G1 Concurrent GC“: dessen Zeit läuft neben den Fäden und hält kein Bild an
        for (java.lang.management.GarbageCollectorMXBean b : GC) if (!b.getName().contains("Concurrent")) s += Math.max(0, b.getCollectionTime());
        return s;
    }

    private void langesBild(long n, long nZu, long nAuf, long nKarte, long nEnde, double ts) {
        if (gpuNach >= 0 && --gpuNach < 0) {
            List<String> ab = stoppuhr.abschnitte();
            ab.sort((x, y) -> Double.compare(stoppuhr.letzte(y), stoppuhr.letzte(x)));
            StringBuilder sb = new StringBuilder(langText).append("; Karte danach: ");
            for (int i = 0; i < Math.min(4, ab.size()); i++)
                sb.append(i > 0 ? ", " : "").append(ab.get(i)).append(String.format(java.util.Locale.GERMAN, " %.2f ms", stoppuhr.letzte(ab.get(i))));
            langesBild = sb.toString();
            System.out.println("Bühne:   " + langesBild);
        }
        double ganz = (nEnde - n) / 1e9;
        langesBildS = Math.max(langesBildS, ganz);
        if (ganz < LANG) return;
        langText = String.format(java.util.Locale.GERMAN, "langes Bild %.2f s bei %s: vorher %.3f s, zu %.3f s (%s), Auflagen %.3f s, Karte %.3f s",
                ganz, uhr != null ? uhr.bei(ts) : "?", (nZu - n) / 1e9, (nAuf - nZu) / 1e9, Bild.teileText(bild.zuTeile), (nKarte - nAuf) / 1e9, (nEnde - nKarte) / 1e9);
        // seit W5b (A47, F220 A): woran zu hing – Lesen aus den Dateien, Warten auf die Sperren, Speicherbereinigung
        com.dan.core.archiv.Archiv.Messung m = zuLesen;
        if (m != null && (nAuf - nZu) >= 0.1e9) langText += "; in zu: " + m;
        langText += String.format(java.util.Locale.GERMAN, "; Speicherbereinigung im Bild %.2f s", gcBild / 1e3);
        langesBild = langText;
        System.out.println("Bühne: " + langText);
        gpuNach = Stoppuhr.RUNDEN;
    }

    /** Seit W3b: die Teile des längsten {@code zu} seit dem letzten Aufruf als Text (für Prüfungen ohne Fenster); setzt zurück. */
    public String zuMessung() {
        if (bild == null) return "kein Bild";
        double[] g = bild.zuGroesst.clone();
        bild.zuGroesstWeg();
        return String.format(java.util.Locale.GERMAN, "%.3f s: %s", g[6], Bild.teileText(g));
    }

    /** Seit W3b: Zeit des Abschnitts der Stoppuhr im Fenster im letzten gelesenen Bild (ms); NaN ohne Fenster oder Abschnitt. */
    public double fensterAbschnitt(String name) {
        if (stoppuhr == null || !stoppuhr.abschnitte().contains(name)) return Double.NaN;
        return stoppuhr.letzte(name);
    }

    // ------------------------------------------------------------------ Kamera (W3)

    private volatile Kamera kamera;

    /**
     * Seit W3 (Werkbuch HalligHooge 116, F192 A): eine Quelle für den Blick, die die Bildschleife je Bild fragt; null:
     * Maus und Tasten wie bisher. Ohne Quelle ist das Bild unverändert.
     */
    public void kamera(Kamera k) { kamera = k; }

    public Kamera kamera() { return kamera; }

    private Blick kameraBlick(Blick jetzt, double dt, double ts) {
        Kamera k = kamera;
        if (k == null) return jetzt;
        Blick b;
        try {
            b = k.weiter(dt, ts, jetzt.kopie());
        } catch (RuntimeException e) {
            kamera = null;
            fehler = "Kamera: " + e;
            return jetzt;
        }
        if (b == null) return jetzt;
        steuerung.setze(b.ost, b.nord, b.hoehe, b.richtung, b.neigung, b.bildwinkel);
        Blick r = b.kopie();
        r.breite = jetzt.breite;
        r.hoehe_px = jetzt.hoehe_px;
        return r;
    }

    // ------------------------------------------------------------------ Einblendungen (W2)

    /**
     * Seit W2: die Einblendungen (beim ersten Aufruf angelegt, mit der Krümmung des Geländes). Kacheln dazu und weg darf
     * jeder Faden; gezeichnet wird im Fenster und in {@link #bildzeiten}, nie im Foto oder Standbild.
     */
    public synchronized com.dan.render.overlay.Einblendung einblendung() {
        if (einblendung == null) {
            einblendung = halte(new com.dan.render.overlay.Einblendung(g, konto));
            einblendung.kruemmung = gelaendebild.hoehe.curvatureRadius;
        }
        return einblendung;
    }

    /**
     * Seit W2, für Prüfungen: ein Standbild wie {@link #standbild(Blick, double)}, mit {@code mitEinblendung} samt den
     * Einblendungen.
     */
    public float[] standbild(Blick blick, double t, boolean mitEinblendung) {
        this.mitEinblendung = mitEinblendung;
        try {
            return standbild(blick, t);
        } finally {
            this.mitEinblendung = false;
        }
    }

    private volatile boolean mitEinblendung;

    /**
     * Seit W2: der Wasserstand des letzten Bilds auf einem Raster nx × ny über dem Rechteck (UTM; Mitten der Zellen,
     * Zeilen von Süd nach Nord): η (m NHN), NaN, wo trocken oder außerhalb der Netze. Für die Minikarte (F188 A).
     */
    public float[] wasserRaster(double ost0, double nord0, double ost1, double nord1, int nx, int ny) {
        float[] r = new float[nx * ny];
        com.dan.core.szene.Spiegelbild sb = wasser.spiegelbild();
        if (sb == null) {
            java.util.Arrays.fill(r, Float.NaN);
            return r;
        }
        double dx = (ost1 - ost0) / nx, dy = (nord1 - nord0) / ny;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                float e = sb.etaBildUtm(ost0 + (i + 0.5) * dx, nord0 + (j + 0.5) * dy, null);
                r[i + nx * j] = e < -1e29f ? Float.NaN : e;
            }
        return r;
    }

    /**
     * Seit W2: Anteil der Zellen mit Wasser (Tiefe über 5 cm, wie im Bild) im Gitter der Nässe im Rechteck (UTM);
     * NaN ohne Szenario oder Zelle. Bezug für die Minikarte (Prüfstein 6).
     */
    public double nassAnteil(double ost0, double nord0, double ost1, double nord1) {
        Bild bi = bild;
        return bi == null ? Double.NaN : bi.nassAnteil(ost0, nord0, ost1, nord1);
    }

    /** Seit W2: das Rechteck des Gitters der Nässe (Ost, Nord, Ost, Nord; null ohne Szenario). */
    public double[] nassGebiet() {
        Bild bi = bild;
        Gitter gi = bi == null ? null : bi.nassGitter();
        return gi == null ? null : new double[] {gi.x0, gi.y0, gi.x0 + gi.nx * gi.dx, gi.y0 + gi.ny * gi.dy};
    }

    /** Seit W2, für die Legende: Geschwindigkeit am Ende der Farbleiter des Messblicks (m/s; NaN ohne Messblick). */
    public double messLeiter() {
        com.dan.render.analysis.Messblick m = messblick;
        return m == null ? Double.NaN : m.vLeiter;
    }

    /** Seit W2, für die Legende: Art der Isolinien (0 Wasserstand, 1 Tiefe, 2 Trübung; −1 ohne Messblick) und ihre Stufen. */
    public int messArt() {
        com.dan.render.analysis.Messblick m = messblick;
        return m == null ? -1 : m.art.ordinal();
    }

    /** Die Stufen der Isolinien (Kopie; null ohne). */
    public double[] messStufen() {
        com.dan.render.analysis.Messblick m = messblick;
        double[] s = m == null ? null : m.stufen;
        return s == null ? null : s.clone();
    }

    /** Zeiten von n Bildern: je Bild die Uhr des Prozessors (abgeschickt und abgewartet) und die Summe der Abschnitte des Messblicks auf der Karte (ms), je sortiert; dazu die Mittel je Abschnitt des Messblicks und (seit P12a) der Häuser. */
    public record Bildzeiten(double[] wand, double[] messblick, double[] karte, java.util.Map<String, Double> abschnitte) { }

    /**
     * Ohne Fenster, zur Prüfung der Kosten: zeichnet n Bilder zur Zeit t, jedes einzeln abgeschickt und abgewartet, mit
     * Zeitstempeln der Karte je Durchgang. {@code karte} ist die Summe aller Durchgänge je Bild: so lange braucht die Karte
     * für ein Bild, wenn der Prozessor sie nicht warten lässt (in der Anwendung sind zwei Bilder zugleich unterwegs).
     */
    public Bildzeiten bildzeiten(Blick blick, double t, int n) { return bildzeiten(blick, t, n, false); }

    /** Wie {@link #bildzeiten(Blick, double, int)}; mit {@code alle} (seit P13d) das Mittel jedes Abschnitts in {@code abschnitte}. */
    public Bildzeiten bildzeiten(Blick blick, double t, int n, boolean alle) {
        if (ausgabe == null) throw new IllegalStateException("Buehne mit Fenster: laufen() benutzen");
        Blick b = blick.kopie().groesse(breite, hoehe);
        Instant zeit = uhr.start.plusMillis(Math.round(t * 1000));
        double[] ms = new double[n], mb = new double[n], ka = new double[n];
        java.util.Map<String, Double> summe = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++)
            try (Stoppuhr su = new Stoppuhr(g, g.grafik, 64)) {         // je Bild eine eigene: nur dessen Abschnitte zählen
                bild.zu(t, b.ost, b.nord);
                bild.blick(b, (double) breite / Math.max(1, hoehe));
                windAnBewuchs();
                List<com.dan.core.gpu.Auflage> auf = auflagen(t, true);
                long n0 = System.nanoTime();
                Ablauf a = new Ablauf(g, g.grafik);
                rahmen.bild(a, bildteile(), auf, b, zeit, ausgabe, 1.0 / 60);
                g.grafik.einmal(c -> {
                    su.runde(c);
                    a.aufnehmen(c, su);
                });
                ms[i] = (System.nanoTime() - n0) / 1e6;
                su.sammle(true);
                for (String name : su.abschnitte()) ka[i] += su.letzte(name);
                for (String name : su.abschnitte())
                    if (name.startsWith("Messblick")) {
                        double x = su.letzte(name);
                        mb[i] += x;
                        summe.merge(name, x / n, Double::sum);
                    } else if (alle || name.startsWith("Häuser") || name.startsWith("Gelände") || name.startsWith("Bewuchs"))   // seit P12a, P12b, P12c; alle seit P13d
                        summe.merge(name, su.letzte(name) / n, Double::sum);
            }
        java.util.Arrays.sort(ms);
        java.util.Arrays.sort(mb);
        java.util.Arrays.sort(ka);
        return new Bildzeiten(ms, mb, ka, summe);
    }

    /** Die Stände im Bild (null: kein Szenario); seit P10b für die Prüfung der Kimmung. */
    public Bild bild() { return bild; }

    /** Wasserstand im Bild an einem Ort (m NHN; NaN: trocken), mit dem Blick b als Bezug. */
    public float wasserstand(Blick b, double ost, double nord) { return wasser.etaBild((float) (ost - b.ost), (float) (b.nord - nord), null); }

    /** Das feinste Gitter in UTM des Gebiets: für Länge und Breite eines Orts ({@link Ansicht#setze}). */
    public Gitter utm() { return utm; }

    /** Die Uhr der Quelle (null: kein Szenario); die Bühne führt sie. */
    public Uhr uhr() { return uhr; }

    // ------------------------------------------------------------------ Schluss

    private void schliesseTeile() {
        for (int i = teile.size() - 1; i >= 0; i--) {
            try {
                teile.get(i).close();
            } catch (Exception e) {
                System.err.println("Buehne: " + e);
            }
        }
        teile.clear();
    }

    @Override public void close() {
        ende = true;
        Foto f = foto;
        if (f != null) f.abbrechen();
        if (g != null) {
            quelleWeg();
            g.ruhe();
        }
        schliesseTeile();
    }
}
