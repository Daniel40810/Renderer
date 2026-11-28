package com.dan.physics.auftrieb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ein schwimmender Körper mit sechs Freiheiten (Seegang Werkbuch, S7a; Plan Datei 86, 3.2; SF104 A, SF105 A, SF112 A).
 * Nach dem Vorbild von `vessel.Boat` (LituyaBay1958 Phase 15, SF3 A), aber mit dem Druck nach Froude–Krylov, Zusatzmasse
 * und Dämpfung je Freiheit, angeströmten Flächen statt Haut und einem Integrator, der die Energie hält.
 * <ul>
 * <li><b>Druck:</b> Jede Probe sieht in ihrer Tiefe z den Spiegel pegel + ψ(z) ({@link Wasser#welle}) und trägt mit dem
 * Anteil darunter: ρ g V f senkrecht und −ρ g V f ∇ψ waagerecht. Für lange Wellen ist das der Auftrieb unter der Fläche wie
 * in `vessel`; kurze Wellen klingen zur Tiefe ab und heben einen tiefen Rumpf weniger.</li>
 * <li><b>Zusatzmasse</b> je Freiheit (Längs, Quer, Hoch im Kurs; Rollen, Stampfen, Gieren im Körper) [G], abgeschätzt aus
 * Streifen ({@link #zusatzAusStreifen}); sie wirkt auf die Beschleunigung gegen das Wasser (Morison).</li>
 * <li><b>Strahlungsdämpfung</b> als Anteil der kritischen für Tauchen, Rollen und Stampfen [G], gegen die Bewegung der
 * Fläche; Rollen dazu quadratisch (Schlingerkiele, vereinfacht nach Ikeda [G]).</li>
 * <li><b>Widerstand</b> gegen Strom und Welle auf die angeströmten Flächen der Proben mit c_D längs, quer und senkrecht
 * [G]; <b>Wellenwiderstand</b> beim Fahren wie in `vessel` [G]; <b>Wind</b> auf die Flächen über Wasser [G].</li>
 * <li><b>Grund:</b> Proben unter dem Gelände drückt eine Feder mit Dämpfer zurück, mit Reibung nach Coulomb [G].</li>
 * <li><b>Festmacher</b> (Kette, Leine, Fender) und <b>Antrieb</b> (Schraube, Ruder, Autopilot wie `vessel`).</li>
 * </ul>
 * <b>Integration:</b> Leapfrog (Stoß, Drift, Stoß) in Teilschritten von höchstens {@link #schrittMax}; die Drehung als
 * Quaternion, der Drehimpuls im Körper mit der freien Drehung in Teilen um die Hauptachsen (Dullweber, Leimkuhler und
 * McLachlan 1997). Ohne Dämpfung hält das die Energie ohne Drift.
 * <p>
 * Welt: x Ost, y Nord, z oben. Körper: x vorn, y Backbord, z oben ab der Unterkante. Kurs in Grad von Nord im
 * Uhrzeigersinn; Krängung positiv = Steuerbord tief; Trimm positiv = Bug hoch.
 */
public final class Koerper {
    public static final String VERSION = "0.4";
    public static final double G = 9.81;

    public final String name;
    public final Form form;
    public final Proben proben;
    /** Handrechnung auf dem Konstruktionstiefgang (aus der Form, fein). */
    public final Hydrostatik hydro;
    /** Masse (kg), Schwerpunkt im Körper (m). */
    public final double masse;
    public final double[] schwerpunkt;
    /** Trägheitsmomente um x, y, z durch den Schwerpunkt (kg m²). */
    public final double[] traegheit = new double[3];
    /** Zusatzmasse längs, quer, hoch (kg) und um x, y, z (kg m²). */
    public final double[] zusatz = new double[6];
    /** Strahlungsdämpfung als Anteil der kritischen: Tauchen, Rollen, Stampfen [G]. */
    public double zetaTauchen = 0.18, zetaRollen = 0.05, zetaStampfen = 0.15;
    /** Quadratische Rolldämpfung: Moment −κ (I + A₄₄) |ω| ω (Schlingerkiele, vereinfacht nach Ikeda) [G]. */
    public double rollenQuadrat = 0.4;
    /** Widerstandsbeiwerte auf die angeströmten Flächen: längs, quer, senkrecht [G]. */
    public double cdLaengs = 0.08, cdQuer = 1.0, cdHoch = 1.0;
    /** Wind: Flächen über Wasser von vorn und von der Seite (m²), Höhe und Längslage des Druckpunkts (Körper), c_D [G]. */
    public double windVorn, windSeite, windHoehe, windX, cdWind = 1.0;
    /**
     * Seit S7e2 (Seegang Werkbuch 101, SF151 A): ein Antrieb in der Welt am Schwerpunkt (N, nach Ost und Nord) und ein Kurs,
     * auf den sich der Körper dreht (Grad; NaN: keiner), mit der Eigenzeit {@link #kursZeit} (s), gedämpft mit 0,7 [G] – für
     * die Eiderenten, die paddelnd ihren Ort halten und mit dem Kopf voran schwimmen.
     */
    public double antriebOst, antriebNord, kursSoll = Double.NaN, kursZeit = 2.0;
    /** Wellenwiderstand bei Froude-Zahl 0,4 als Anteil des Gewichts (Verdränger) [G]. */
    public double wellenwiderstand = 0.02;
    /** Grund: Einsinken unter dem eigenen Gewicht (m) und Reibung [G]. */
    public double grundEinsinken = 0.005, grundReibung = 0.5;
    /** Antrieb: größter Schub (N), Hebel −1 … 1, Ruder −1 … 1 (Steuerbord positiv), Ruderfläche (m²), Schraube (Körper). */
    public double schubMax, hebel, ruder, ruderFlaeche;
    /**
     * Seit S7d7 (Seegang Werkbuch 95, SF138 A): der Widerstand des Rumpfs nach dem Querstromprinzip – längs ½ ρ c_D A |u_x| u_x,
     * quer zur Längsachse ½ ρ c_D A |u_⊥| u_⊥ mit der Querströmung u_⊥ = (u_y, u_z), dazu der Quertrieb ½ ρ c_Q A |u_x| u_y
     * (linear im Schrägwinkel, wie ein Flügel kleiner Streckung) – statt einer Kraft längs der ganzen Strömung mit der
     * angeströmten Fläche aller Seiten. Sonst kostet jedes Schrägfahren Widerstand mit der ganzen Seitenfläche (Befund L5d6 1).
     */
    public boolean querstrom;
    /** Seit S7d7: Beiwert des Quertriebs je Seitenfläche [G] (Clarke: π T/L (1 + 0,4 C_B B/T)); nur mit {@link #querstrom}. */
    public double cQuertrieb;
    /** Seit S7d7: Ruder – Anstieg des Auftriebs je rad, größter Beiwert, Widerstand bei 0 [G]; bis S7d6 2π, unbegrenzt, 0. */
    public double ruderAnstieg = 2 * Math.PI, ruderClMax = Double.POSITIVE_INFINITY, ruderCd0 = 0, ruderStreckung = Double.POSITIVE_INFINITY;
    public double[] schraube = {0, 0, 0};
    /** Seit S7e2 (Seegang Werkbuch 101, SF150 A): Gleiten nach Savitsky für einen Knickspant, sonst null. */
    public Gleiten gleiten;
    /** Längster Teilschritt (s). */
    public double schrittMax = 0.004;
    /** Schalter für die Prüfung: Dämpfung, Widerstand, Zusatzmasse, Wind, Wellenwiderstand. */
    public boolean mitDaempfung = true, mitWiderstand = true, mitZusatz = true, mitWind = true, mitWellenwiderstand = true;
    /**
     * Seit S7b-2 (Seegang Werkbuch 88): die Welle an einer Probe gilt so lange (s), solange die Probe nicht mehr als
     * 2 cm steigt oder sinkt [G]; 0: in jedem Teilschritt neu (Vorgabe, wie S7a). Spart Rechenzeit, wenn das Wasser von der
     * Karte kommt ({@link Bandwasser}); die Welle ändert sich in 12 ms um weniger als 1 cm.
     */
    public double wasserTakt = 0;
    private double[] cT, cZ, cBuf;

    private final List<Festmacher> festmacher = new ArrayList<>();

    // Zustand: Schwerpunkt (Welt), Quaternion Körper → Welt {w, x, y, z}, Geschwindigkeit (Welt), Drehgeschwindigkeit (Körper)
    final double[] o = new double[3], q = {1, 0, 0, 0}, R = new double[9], v = new double[3], w = new double[3];
    private final double[] a = new double[3], al = new double[3], fLetzt = new double[3], tbLetzt = new double[3];
    private boolean beschleunigt;
    double zeit;
    // Werte des letzten Schritts
    double eingetaucht, grundTiefe, auftrieb;
    boolean aufGrund;
    private double wMittel, uMittel, vMittel, wAlt, uAlt, vAlt;
    private boolean ersterSchritt = true;
    // Kennwerte der Dämpfung
    final double steifTauchen, steifRollen, steifStampfen, volDesign;
    private final double kGrund;
    private final double[] buf = new double[6], st = new double[2], tf = new double[3], tp = new double[3];

    /**
     * Ein Körper aus Form, Proben der Kante d, Masse (kg; ≤ 0: die Verdrängung auf dem Tiefgang der Hydrostatik) und
     * Schwerpunkthöhe kg über der Unterkante; der Schwerpunkt liegt längs über dem Formschwerpunkt. Trägheitsradien um x, y, z
     * (m). Die Hydrostatik h ist die Handrechnung auf dem Konstruktionstiefgang.
     */
    public Koerper(Form form, double d, Hydrostatik h, double masse, double kg, double rx, double ry, double rz) {
        this(form, new Proben(form, d), h, masse, kg, rx, ry, rz);
    }

    /** Wie oben mit fertigen Proben. */
    public Koerper(Form form, Proben proben, Hydrostatik h, double masse, double kg, double rx, double ry, double rz) {
        this.name = form.name();
        this.form = form;
        this.proben = proben;
        this.hydro = h;
        this.masse = masse > 0 ? masse : 1025 * h.volumen;
        this.schwerpunkt = new double[] {h.lcb, 0, kg};
        traegheit[0] = this.masse * rx * rx;
        traegheit[1] = this.masse * ry * ry;
        traegheit[2] = this.masse * rz * rz;
        volDesign = this.masse / 1025;
        steifTauchen = 1025 * G * h.awl;
        steifRollen = this.masse * G * Math.max(1e-3, h.gm(kg));
        steifStampfen = this.masse * G * Math.max(1e-3, h.gmL(kg));
        int unten = 0;
        double zMin = 1e9;
        for (int p = 0; p < proben.n; p++) zMin = Math.min(zMin, proben.z[p] - proben.unten[p]);
        for (int p = 0; p < proben.n; p++) if (proben.z[p] - proben.unten[p] < zMin + 0.5 * proben.d) unten++;
        kGrund = this.masse * G / Math.max(1, unten) / grundEinsinken;
        setzeDrehung(1, 0, 0, 0);
        kartenRaster(0);
    }

    // ------------------------------------------------------------------ Zusatzmasse

    /**
     * Zusatzmasse aus Streifen [G] auf dem Tiefgang T: Tauchen Σ ρ π b² / 8 · C(B/T) dx nach Lewis (C aus dem Verhältnis
     * Breite zu Tiefgang, 1 beim Halbkreis, flach größer), Quer Σ ρ π T² / 2 dx (Platte), Längs 5 % der Masse, Rollen
     * 15 % des Trägheitsmoments, Stampfen und Gieren die Streifen mal x²; die Streifen mal L / (L + B) für die Enden.
     * Im flachen Wasser wächst sie; das fehlt [G].
     */
    public Koerper zusatzAusStreifen(Form.Rumpf r) {
        double t = r.tiefgang, rho = 1025, a33 = 0, a22 = 0, a55 = 0, a66 = 0, dx = r.laenge / 200;
        for (double x = -r.laenge / 2 + dx / 2; x < r.laenge / 2; x += dx) {
            double b = 2 * r.halbeBreite(x, Math.min(t, r.deck(x)) - 1e-6);
            double tt = Math.max(0, t - r.boden(x));
            if (b <= 0 || tt <= 0) continue;
            double c = 1 + 0.25 * Math.max(0, Math.log(b / (2 * tt)));          // flach: mehr (grob nach Lewis) [G]
            double s33 = rho * Math.PI * b * b / 8 * c * dx, s22 = rho * Math.PI * tt * tt / 2 * dx;
            a33 += s33; a22 += s22; a55 += s33 * x * x; a66 += s22 * x * x;
        }
        double j = r.laenge / (r.laenge + r.breite);                          // Enden: dreidimensional weniger [G]
        zusatz[0] = 0.05 * masse;
        zusatz[1] = a22 * j;
        zusatz[2] = a33 * j;
        zusatz[3] = 0.15 * traegheit[0];
        zusatz[4] = a55 * j;
        zusatz[5] = a66 * j;
        return this;
    }

    /** Zusatzmasse als Anteile von Masse und Trägheit [G] (Tonnen, Treibgut, Enten). */
    public Koerper zusatzAnteile(double laengs, double quer, double hoch, double rollen, double stampfen, double gieren) {
        zusatz[0] = laengs * masse; zusatz[1] = quer * masse; zusatz[2] = hoch * masse;
        zusatz[3] = rollen * traegheit[0]; zusatz[4] = stampfen * traegheit[1]; zusatz[5] = gieren * traegheit[2];
        return this;
    }

    // ------------------------------------------------------------------ Lage

    void setzeDrehung(double qa, double qb, double qc, double qd) {
        double l = Math.sqrt(qa * qa + qb * qb + qc * qc + qd * qd);
        qa /= l; qb /= l; qc /= l; qd /= l;
        q[0] = qa; q[1] = qb; q[2] = qc; q[3] = qd;
        R[0] = 1 - 2 * (qc * qc + qd * qd); R[1] = 2 * (qb * qc - qa * qd); R[2] = 2 * (qb * qd + qa * qc);
        R[3] = 2 * (qb * qc + qa * qd); R[4] = 1 - 2 * (qb * qb + qd * qd); R[5] = 2 * (qc * qd - qa * qb);
        R[6] = 2 * (qb * qd - qa * qc); R[7] = 2 * (qc * qd + qa * qb); R[8] = 1 - 2 * (qb * qb + qc * qc);
    }

    static double[] qmul(double[] p, double[] r) {
        return new double[] {p[0] * r[0] - p[1] * r[1] - p[2] * r[2] - p[3] * r[3], p[0] * r[1] + p[1] * r[0] + p[2] * r[3] - p[3] * r[2],
                p[0] * r[2] - p[1] * r[3] + p[2] * r[0] + p[3] * r[1], p[0] * r[3] + p[1] * r[2] - p[2] * r[1] + p[3] * r[0]};
    }

    /** Setzt den Körper ruhend: Schwerpunkt (x, y, z) in der Welt, Kurs, Krängung, Trimm (Grad). */
    public void setzen(double x, double y, double z, double kurs, double kraengung, double trimm) {
        double psi = Math.toRadians(90 - kurs) / 2, th = Math.toRadians(-trimm) / 2, ph = Math.toRadians(kraengung) / 2;
        double[] qz = {Math.cos(psi), 0, 0, Math.sin(psi)}, qy = {Math.cos(th), 0, Math.sin(th), 0}, qx = {Math.cos(ph), Math.sin(ph), 0, 0};
        double[] qq = qmul(qmul(qz, qy), qx);
        setzeDrehung(qq[0], qq[1], qq[2], qq[3]);
        o[0] = x; o[1] = y; o[2] = z;
        Arrays.fill(v, 0);
        Arrays.fill(w, 0);
        beschleunigt = false;
        ersterSchritt = true;
    }

    /**
     * Setzt den Körper aufrecht mit Kurs auf (x, y) und sucht die Höhe, in der er im ruhigen Wasser zur Zeit t trägt
     * (Halbierung; Krängung und Trimm wie gegeben).
     */
    public void einschwimmen(Wasser wa, double x, double y, double kurs, double kraengung, double trimm, double t) {
        double pg = wa.pegel(x, y, t), lo = pg - 50, hi = pg + 50;
        for (int it = 0; it < 80; it++) {
            double m = 0.5 * (lo + hi);
            setzen(x, y, m, kurs, kraengung, trimm);
            if (auftriebStill(wa, t) > masse * G) lo = m; else hi = m;
        }
        setzen(x, y, 0.5 * (lo + hi), kurs, kraengung, trimm);
        zeit = t;
    }

    /** Auftrieb im Wasser ohne Welle (N), bei der jetzigen Lage. */
    public double auftriebStill(Wasser wa, double t) {
        double f = 0;
        for (int p = 0; p < proben.n; p++) {
            double[] pw = probeWelt(p);
            f += wa.dichte() * G * proben.vol[p] * proben.traegt[p] * Proben.anteil(wa.pegel(pw[0], pw[1], t) - (pw[2] - proben.unten[p]), proben.hoehe[p]);
        }
        return f;
    }

    /** Punkt des Körpers (x, y, z im Körper) in der Welt. */
    public double[] punktWelt(double x, double y, double z) {
        double lx = x - schwerpunkt[0], ly = y - schwerpunkt[1], lz = z - schwerpunkt[2];
        return new double[] {R[0] * lx + R[1] * ly + R[2] * lz + o[0], R[3] * lx + R[4] * ly + R[5] * lz + o[1], R[6] * lx + R[7] * ly + R[8] * lz + o[2]};
    }

    double[] probeWelt(int p) { return punktWelt(proben.x[p], proben.y[p], proben.z[p]); }

    /**
     * Seit S7b-1 (Seegang Werkbuch 88): wo die Achsen der Säulen ({@link Proben#saeule}) die Ebene in der Höhe pegel
     * schneiden, in der Welt (UTM, m): nach ost[off + c], nord[off + c]. Dort braucht der Körper das Wasser von der Karte.
     */
    public void saeulenWelt(double pegel, double[] ost, double[] nord, int off) {
        double dz = R[8];
        for (int c = 0; c < proben.saeulen; c++) {
            double lx = proben.saeuleX[c] - schwerpunkt[0], ly = proben.saeuleY[c] - schwerpunkt[1];
            double px = R[0] * lx + R[1] * ly + o[0], py = R[3] * lx + R[4] * ly + o[1], pz = R[6] * lx + R[7] * ly + o[2];
            double s = Math.abs(dz) > 0.2 ? (pegel - pz) / dz : 0;
            ost[off + c] = px + s * R[2];
            nord[off + c] = py + s * R[5];
        }
    }

    /**
     * Seit S7b-4 (Seegang Werkbuch 89): Punkte der Karte. Je Säule die Nummer ihres Punkts ({@link #kartenpunkt}); ein
     * Punkt fasst die Säulen in Zellen von {@link #kartenRaster} × {@link #kartenRaster} (m, im Körper) zusammen und liegt
     * in der Mitte ihrer Stellen ({@link #kartenX}, {@link #kartenY}, im Körper). Ein Raster bis {@link Proben#SAEULE}: je
     * Säule ein Punkt (wie S7b, Vorgabe). Die Karte rechnet nur an den Punkten; {@link Bandwasser#zuordnung} dreht die
     * Summen eines Punkts je Band auf die Stelle der Säule (wie über die Zeit des Verzugs).
     */
    public int[] kartenpunkt;
    public double[] kartenX, kartenY;
    public int kartenpunkte;
    public double kartenRaster;
    /** Seit S7d6: das Raster quer (m); gleich {@link #kartenRaster}, wenn nur eins gesetzt ist. */
    public double kartenRasterQuer;

    /** Setzt das Raster der Punkte der Karte (m) [G]; siehe {@link #kartenpunkt}. */
    public Koerper kartenRaster(double raster) { return kartenRaster(raster, raster); }

    /**
     * Seit S7d6 (Seegang Werkbuch 94, SF132 A): das Raster längs (x) und quer (y) getrennt (m); bis {@link Proben#SAEULE} in beiden: je
     * Säule ein Punkt. Quer fein hält das Rollen, längs grob spart Punkte.
     */
    public Koerper kartenRaster(double laengs, double quer) {
        double raster = Math.max(laengs, quer);
        int n = proben.saeulen;
        kartenRaster = raster;
        kartenRasterQuer = quer;
        kartenpunkt = new int[n];
        if (!(raster > Proben.SAEULE + 1e-9)) {
            for (int c = 0; c < n; c++) kartenpunkt[c] = c;
            kartenpunkte = n;
            kartenX = proben.saeuleX.clone();
            kartenY = proben.saeuleY.clone();
            return this;
        }
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY;
        for (int c = 0; c < n; c++) { x0 = Math.min(x0, proben.saeuleX[c]); y0 = Math.min(y0, proben.saeuleY[c]); }
        // Zellen ab der Mitte des Körpers in y (symmetrisch zur Mittschiffsebene), in x ab dem hintersten Punkt
        double ym = 0;
        for (int c = 0; c < n; c++) ym += proben.saeuleY[c] / n;
        java.util.Map<Long, Integer> nr = new java.util.LinkedHashMap<>();
        for (int c = 0; c < n; c++) {
            long ix = (long) Math.floor((proben.saeuleX[c] - x0) / Math.max(laengs, 1e-6) + 1e-9);
            long iy = (long) Math.floor((proben.saeuleY[c] - ym) / Math.max(quer, 1e-6) + 0.5);
            long key = ix << 32 | (iy & 0xffffffffL);
            Integer m = nr.get(key);
            if (m == null) { m = nr.size(); nr.put(key, m); }
            kartenpunkt[c] = m;
        }
        kartenpunkte = nr.size();
        kartenX = new double[kartenpunkte];
        kartenY = new double[kartenpunkte];
        int[] z = new int[kartenpunkte];
        for (int c = 0; c < n; c++) {
            int m = kartenpunkt[c];
            kartenX[m] += proben.saeuleX[c];
            kartenY[m] += proben.saeuleY[c];
            z[m]++;
        }
        for (int m = 0; m < kartenpunkte; m++) { kartenX[m] /= z[m]; kartenY[m] /= z[m]; }
        return this;
    }

    /** Wie {@link #saeulenWelt}, für die Punkte der Karte ({@link #kartenpunkt}). */
    public void kartenWelt(double pegel, double[] ost, double[] nord, int off) {
        double dz = R[8];
        for (int m = 0; m < kartenpunkte; m++) {
            double lx = kartenX[m] - schwerpunkt[0], ly = kartenY[m] - schwerpunkt[1];
            double px = R[0] * lx + R[1] * ly + o[0], py = R[3] * lx + R[4] * ly + o[1], pz = R[6] * lx + R[7] * ly + o[2];
            double s = Math.abs(dz) > 0.2 ? (pegel - pz) / dz : 0;
            ost[off + m] = px + s * R[2];
            nord[off + m] = py + s * R[5];
        }
    }

    /** Seit S7b-4: setzt die Geschwindigkeit des Schwerpunkts (Welt, m/s), etwa für eine Fähre, die in Fahrt erscheint. */
    public void geschwindigkeit(double vx, double vy, double vz) { v[0] = vx; v[1] = vy; v[2] = vz; }

    /** Richtung (im Körper) in der Welt. */
    public double[] richtungWelt(double x, double y, double z) { return new double[] {R[0] * x + R[1] * y + R[2] * z, R[3] * x + R[4] * y + R[5] * z, R[6] * x + R[7] * y + R[8] * z}; }

    /** Geschwindigkeit eines Weltpunkts, der fest am Körper sitzt. */
    public double[] geschwindigkeitAn(double[] pw) {
        double rx = pw[0] - o[0], ry = pw[1] - o[1], rz = pw[2] - o[2];
        double wx = R[0] * w[0] + R[1] * w[1] + R[2] * w[2], wy = R[3] * w[0] + R[4] * w[1] + R[5] * w[2], wz = R[6] * w[0] + R[7] * w[1] + R[8] * w[2];
        return new double[] {v[0] + wy * rz - wz * ry, v[1] + wz * rx - wx * rz, v[2] + wx * ry - wy * rx};
    }

    public void festmachen(Festmacher f) { festmacher.add(f); }

    public List<Festmacher> festmacher() { return festmacher; }

    // ------------------------------------------------------------------ Schritt

    /** Rechnet von der jetzigen Zeit dt (s) weiter, in Teilschritten. */
    public void schritt(Wasser wa, double dt) {
        int m = Math.max(1, (int) Math.ceil(dt / schrittMax - 1e-9));
        double h = dt / m;
        for (int i = 0; i < m; i++) teilschritt(wa, h);
    }

    private void teilschritt(Wasser wa, double dt) {
        if (!beschleunigt) {
            kraefte(wa, zeit, dt, fLetzt, tbLetzt);
            beschleunigt = true;
        }
        // Drehimpuls im Körper Π = I ω; Stoß mit dem Moment, freie Drehung in Teilen um die Hauptachsen (Dullweber,
        // Leimkuhler und McLachlan 1997): symplektisch, ohne Drift der Energie
        double[] ie = traegheitWirksam();
        double[] pi = {ie[0] * w[0], ie[1] * w[1], ie[2] * w[2]};
        beschleunigung(fLetzt, tbLetzt);
        for (int i = 0; i < 3; i++) { v[i] += 0.5 * dt * a[i]; pi[i] += 0.5 * dt * tbLetzt[i]; }
        for (int i = 0; i < 3; i++) o[i] += dt * v[i];
        frei(pi, ie, 0, 0.5 * dt);
        frei(pi, ie, 1, 0.5 * dt);
        frei(pi, ie, 2, dt);
        frei(pi, ie, 1, 0.5 * dt);
        frei(pi, ie, 0, 0.5 * dt);
        for (int i = 0; i < 3; i++) w[i] = pi[i] / ie[i];
        zeit += dt;
        // Kräfte am neuen Ort, Stoß
        kraefte(wa, zeit, dt, fLetzt, tbLetzt);
        beschleunigung(fLetzt, tbLetzt);
        ie = traegheitWirksam();
        for (int i = 0; i < 3; i++) { v[i] += 0.5 * dt * a[i]; pi[i] += 0.5 * dt * tbLetzt[i]; w[i] = pi[i] / ie[i]; }
    }

    /** Wirksame Trägheit um x, y, z (mit der Zusatzmasse nach der Eintauchung). */
    private double[] traegheitWirksam() {
        double s = zusatzAnteil();
        return new double[] {traegheit[0] + s * zusatz[3], traegheit[1] + s * zusatz[4], traegheit[2] + s * zusatz[5]};
    }

    /** Freie Drehung um die Körperachse ax über h: Lage um θ = Π_ax / I_ax · h, Π im Körper um −θ. */
    private void frei(double[] pi, double[] ie, int ax, double h) {
        double th = pi[ax] / ie[ax] * h;
        if (th == 0) return;
        double c = Math.cos(th), s = Math.sin(th), ch = Math.cos(th / 2), sh = Math.sin(th / 2);
        double[] dq = {ch, ax == 0 ? sh : 0, ax == 1 ? sh : 0, ax == 2 ? sh : 0};
        double[] nq = qmul(q, dq);
        setzeDrehung(nq[0], nq[1], nq[2], nq[3]);
        int i = (ax + 1) % 3, j = (ax + 2) % 3;
        double pi1 = c * pi[i] + s * pi[j], pj1 = -s * pi[i] + c * pi[j];
        pi[i] = pi1;
        pi[j] = pj1;
    }

    /** Anteil der Zusatzmasse nach der Eintauchung (trocken keine) [G]. */
    private double zusatzAnteil() { return mitZusatz ? Math.min(1, eingetaucht / Math.max(1e-9, volDesign)) : 0; }

    private void beschleunigung(double[] f, double[] tb) {
        double s = zusatzAnteil();
        // Kurs: Längs- und Querachse waagerecht
        double cx = R[0], cy = R[3], cl = Math.hypot(cx, cy);
        if (cl < 1e-9) { cx = 1; cy = 0; } else { cx /= cl; cy /= cl; }
        double fl = f[0] * cx + f[1] * cy, fq = -f[0] * cy + f[1] * cx;
        double al0 = fl / (masse + s * zusatz[0]), aq = fq / (masse + s * zusatz[1]);
        a[0] = al0 * cx - aq * cy;
        a[1] = al0 * cy + aq * cx;
        a[2] = f[2] / (masse + s * zusatz[2]);
        double ix = traegheit[0] + s * zusatz[3], iy = traegheit[1] + s * zusatz[4], iz = traegheit[2] + s * zusatz[5];
        // Euler: I ω̇ = τ − ω × I ω (nur Auskunft; integriert wird über den Drehimpuls)
        double lx = ix * w[0], ly = iy * w[1], lz = iz * w[2];
        al[0] = (tb[0] - (w[1] * lz - w[2] * ly)) / ix;
        al[1] = (tb[1] - (w[2] * lx - w[0] * lz)) / iy;
        al[2] = (tb[2] - (w[0] * ly - w[1] * lx)) / iz;
    }

    /** Kraft (Welt) und Moment um den Schwerpunkt (Körper) zur Zeit t. */
    void kraefte(Wasser wa, double t, double dt, double[] fOut, double[] tbOut) {
        Proben P = proben;
        double rho = wa.dichte();
        double fx = 0, fy = 0, fz = -masse * G, tx = 0, ty = 0, tz = 0;
        double sub = 0, auf = 0, gt = 0;
        double fbx = 0, fby = 0, fbz = 0, tbxW = 0, tbyW = 0, tbzW = 0;                // seit S7e2: der Auftrieb allein (fürs Gleiten)
        // seit S7e2: im Gleiten trägt Savitsky den Boden; der Widerstand senkrecht (Querströmung auf den Boden) mit 1 − w (des letzten Schritts)
        double chGleiten = gleiten != null ? cdHoch * (1 - gleiten.anteil) : cdHoch;
        boolean grundJa = false;
        // Ebene der senkrechten Wassergeschwindigkeit über die eingetauchten Proben: w ≈ w0 + α x + β y (Körper)
        double s0 = 0, sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0, sw = 0, swx = 0, swy = 0, su = 0, sv = 0;
        double wx = R[0] * w[0] + R[1] * w[1] + R[2] * w[2], wy = R[3] * w[0] + R[4] * w[1] + R[5] * w[2], wz = R[6] * w[0] + R[7] * w[1] + R[8] * w[2];
        for (int p = 0; p < P.n; p++) {
            double bx = P.x[p] - schwerpunkt[0], by = P.y[p] - schwerpunkt[1], bz = P.z[p] - schwerpunkt[2];
            double rx = R[0] * bx + R[1] * by + R[2] * bz, ry = R[3] * bx + R[4] * by + R[5] * bz, rz = R[6] * bx + R[7] * by + R[8] * bz;
            double px = o[0] + rx, py = o[1] + ry, pz = o[2] + rz;
            double ux = v[0] + wy * rz - wz * ry, uy = v[1] + wz * rx - wx * rz, uz = v[2] + wx * ry - wy * rx;
            double pg = wa.pegel(px, py, t), zq = Math.min(0, pz - pg);
            if (wasserTakt > 0) {
                if (cT == null || cT.length != P.n) {
                    cT = new double[P.n]; cZ = new double[P.n]; cBuf = new double[6 * P.n];
                    Arrays.fill(cT, Double.NaN);
                }
                if (Math.abs(t - cT[p]) < wasserTakt && Math.abs(zq - cZ[p]) < 0.02) System.arraycopy(cBuf, 6 * p, buf, 0, 6);
                else {
                    wa.welle(P.saeule[p], px, py, zq, t, buf);
                    cT[p] = t;
                    cZ[p] = zq;
                    System.arraycopy(buf, 0, cBuf, 6 * p, 6);
                }
            } else wa.welle(P.saeule[p], px, py, zq, t, buf);
            double unterkante = pz - P.unten[p];
            double f = Proben.anteil(pg + buf[0] - unterkante, P.hoehe[p]);
            double gx = 0, gy = 0, gz = 0;
            if (f > 0) {
                double vol = P.vol[p] * P.traegt[p] * f;
                sub += vol;
                double b = rho * G * vol;
                auf += b;
                gz += b;
                gx -= b * buf[1];
                gy -= b * buf[2];
                if (gleiten != null) {
                    fbx -= b * buf[1]; fby -= b * buf[2]; fbz += b;
                    tbxW += ry * b + rz * b * buf[2]; tbyW -= rz * b * buf[1] + rx * b; tbzW += -rx * b * buf[2] + ry * b * buf[1];
                }
                double wgt = P.vol[p] * f;
                s0 += wgt; sx += wgt * bx; sy += wgt * by; sxx += wgt * bx * bx; syy += wgt * by * by; sxy += wgt * bx * by;
                sw += wgt * buf[5]; swx += wgt * buf[5] * bx; swy += wgt * buf[5] * by; su += wgt * buf[3]; sv += wgt * buf[4];
                if (mitWiderstand) {
                    wa.strom(px, py, t, st);
                    double dwx = st[0] + buf[3] - ux, dwy = st[1] + buf[4] - uy, dwz = buf[5] - uz;
                    widerstand(p, dwx, dwy, dwz, rho, f, cdLaengs, cdQuer, chGleiten, tf);
                    gx += tf[0]; gy += tf[1]; gz += tf[2];
                }
            }
            // Grund
            double gr = wa.grund(P.saeule[p], px, py), pen = gr - unterkante;
            if (pen > 0) {
                grundJa = true;
                gt = Math.max(gt, pen);
                double mp = masse / P.n, c = 2 * Math.sqrt(kGrund * mp);
                double nf = Math.max(0, kGrund * pen * (P.vol[p] / (P.d * P.d * P.d)) - c * uz);
                gz += nf;
                double th = Math.hypot(ux, uy);
                if (th > 1e-6) {
                    double fr = Math.min(grundReibung * nf, mp * th / dt) / th;
                    gx -= fr * ux; gy -= fr * uy;
                }
            }
            fx += gx; fy += gy; fz += gz;
            tx += ry * gz - rz * gy; ty += rz * gx - rx * gz; tz += rx * gy - ry * gx;
        }
        eingetaucht = sub;
        auftrieb = auf;
        aufGrund = grundJa;
        grundTiefe = gt;
        // Ebene der Fläche: Drehgeschwindigkeit des Wassers um x und y des Körpers
        double omx = 0, omy = 0, wbar = 0;
        if (s0 > 0) {
            double mx = sx / s0, my = sy / s0;
            double cxx = sxx / s0 - mx * mx, cyy = syy / s0 - my * my, cxy = sxy / s0 - mx * my;
            double mw = sw / s0, cwx = swx / s0 - mw * mx, cwy = swy / s0 - mw * my;
            double det = cxx * cyy - cxy * cxy;
            double al1 = 0, be1 = 0;
            if (Math.abs(det) > 1e-12) { al1 = (cwx * cyy - cwy * cxy) / det; be1 = (cwy * cxx - cwx * cxy) / det; }
            wbar = mw;
            omx = be1;          // Backbord steigt: Rollen um +x
            omy = -al1;         // Bug steigt: Drehung um −y
            uMittel = su / s0; vMittel = sv / s0;
        } else { uMittel = 0; vMittel = 0; }
        wMittel = wbar;
        double s = zusatzAnteil();
        // Zusatzmasse gegen die Beschleunigung des Wassers (Morison)
        if (s > 0 && !ersterSchritt && dt > 0) {
            double aw = (wMittel - wAlt) / dt, au = (uMittel - uAlt) / dt, av = (vMittel - vAlt) / dt;
            double cx = R[0], cy = R[3], cl = Math.hypot(cx, cy);
            if (cl > 1e-9) { cx /= cl; cy /= cl; }
            double aLaengs = au * cx + av * cy, aQuer = -au * cy + av * cx;
            double fl = s * zusatz[0] * aLaengs, fq = s * zusatz[1] * aQuer;
            fx += fl * cx - fq * cy; fy += fl * cy + fq * cx; fz += s * zusatz[2] * aw;
        }
        wAlt = wMittel; uAlt = uMittel; vAlt = vMittel;
        ersterSchritt = false;
        // Moment in den Körper
        double tbx = R[0] * tx + R[3] * ty + R[6] * tz, tby = R[1] * tx + R[4] * ty + R[7] * tz, tbz = R[2] * tx + R[5] * ty + R[8] * tz;
        // seit S7e2 (SF150 A): Gleiten – mit dem Anteil w den Auftrieb der Proben zurück (Kraft und Moment) und dafür w · L nach
        // Savitsky senkrecht zum Kiel, längs im Druckpunkt, quer dort, wo der Auftrieb der Proben sitzt: so trägt der Boden den
        // geneigten Rumpf auf der tieferen Seite wie die Verdrängung (dynamische Querstabilität [G])
        if (gleiten != null && sub > 0 && gleiten.kraft(this, wa, t, tf, tp, buf, st)) {
            double wg = gleiten.anteil;
            double mbx = R[0] * tbxW + R[3] * tbyW + R[6] * tbzW;
            double mby = R[1] * tbxW + R[4] * tbyW + R[7] * tbzW, mbz = R[2] * tbxW + R[5] * tbyW + R[8] * tbzW;
            fx += tf[0] - wg * fbx; fy += tf[1] - wg * fby; fz += tf[2] - wg * fbz;
            tbx -= wg * mbx; tby -= wg * mby; tbz -= wg * mbz;
            double fh = Math.hypot(R[0], R[3]);
            if (fbz > 0 && fh > 1e-9) {
                double qx = -R[3] / fh, qy = R[0] / fh, bxW = o[0] - tbyW / fbz, byW = o[1] + tbxW / fbz;
                double sq = (bxW - tp[0]) * qx + (byW - tp[1]) * qy;
                tp[0] += sq * qx; tp[1] += sq * qy;
            }
            double[] pg2 = {tp[0], tp[1], tp[2]};
            kraftMoment(pg2, tf[0], tf[1], tf[2], tp);
            tbx += tp[0]; tby += tp[1]; tbz += tp[2];
        }
        // Dämpfung je Freiheit, gegen die Fläche
        if (mitDaempfung && sub > 0) {
            double ant = Math.min(1, sub / volDesign);
            double cT = 2 * zetaTauchen * Math.sqrt(steifTauchen * (masse + s * zusatz[2])) * ant;
            fz -= cT * (v[2] - wbar);
            double ix = traegheit[0] + s * zusatz[3], iy = traegheit[1] + s * zusatz[4];
            double cR = 2 * zetaRollen * Math.sqrt(steifRollen * ix) * ant, cS = 2 * zetaStampfen * Math.sqrt(steifStampfen * iy) * ant;
            double dr = w[0] - omx, ds = w[1] - omy;
            tbx -= cR * dr + rollenQuadrat * ix * Math.abs(dr) * dr * ant;
            tby -= cS * ds;
        }
        // Wind auf die Flächen über Wasser
        if (mitWind && (windVorn > 0 || windSeite > 0)) {
            double[] pw = punktWelt(windX, 0, windHoehe);
            wa.wind(pw[0], pw[1], t, st);
            double[] up = geschwindigkeitAn(pw);
            double rxw = st[0] - up[0], ryw = st[1] - up[1], sp = Math.hypot(rxw, ryw);
            if (sp > 1e-6) {
                double cx = R[0], cy = R[3], cl = Math.hypot(cx, cy);
                if (cl > 1e-9) { cx /= cl; cy /= cl; }
                double ca = Math.abs(rxw * cx + ryw * cy) / sp, sa = Math.abs(-rxw * cy + ryw * cx) / sp;
                double k = 0.5 * wa.luftdichte() * cdWind * (windVorn * ca + windSeite * sa) * sp;
                double gx = k * rxw, gy = k * ryw;
                fx += gx; fy += gy;
                kraftMoment(pw, gx, gy, 0, tp);
                tbx += tp[0]; tby += tp[1]; tbz += tp[2];
            }
        }
        // seit S7e2: Antrieb in der Welt und Drehen auf den Kurs
        if (antriebOst != 0 || antriebNord != 0) { fx += antriebOst; fy += antriebNord; }
        if (!Double.isNaN(kursSoll) && sub > 0) {
            double iz = traegheit[2] + zusatz[5], om = 2 * Math.PI / kursZeit;
            double dk = Math.toRadians(((kursSoll - kurs()) % 360 + 540) % 360 - 180), wzW = R[6] * w[0] + R[7] * w[1] + R[8] * w[2];
            double tzW = -iz * (om * om * dk + 2 * 0.7 * om * wzW);         // Kurs im Uhrzeigersinn: um die Hochachse rechts herum
            tbx += R[6] * tzW; tby += R[7] * tzW; tbz += R[8] * tzW;
        }
        // Wellenwiderstand längs
        if (mitWellenwiderstand && sub > 0 && form instanceof Form.Rumpf r) {
            wa.strom(o[0], o[1], t, st);
            double cx = R[0], cy = R[3], cl = Math.hypot(cx, cy);
            if (cl > 1e-9) { cx /= cl; cy /= cl; }
            double ua = (v[0] - st[0]) * cx + (v[1] - st[1]) * cy;
            double fn = Math.abs(ua) / Math.sqrt(G * r.laenge * 0.92);
            double rw = masse * G * wellenwiderstand * Math.pow(Math.min(fn, gleiten != null ? 0.4 : fn) / 0.4, 6) * Math.min(1, sub / volDesign);
            if (gleiten != null) rw *= Gleiten.wellenAnteil(fn);              // seit S7e2: der Gleiter trägt sich, Savitsky übernimmt
            rw = Math.min(rw, 0.5 * masse * Math.abs(ua) / Math.max(dt, 1e-6));
            fx -= Math.signum(ua) * rw * cx; fy -= Math.signum(ua) * rw * cy;
        }
        // Schraube und Ruder
        if (schubMax > 0 && (hebel != 0 || ruder != 0)) {
            double[] spw = punktWelt(schraube[0], schraube[1], schraube[2]);
            double pg = wa.pegel(spw[0], spw[1], t);
            if (spw[2] < pg) {
                double[] fw = richtungWelt(1, 0, 0), bw = richtungWelt(0, 1, 0);
                double[] up = geschwindigkeitAn(spw);
                double uf = up[0] * fw[0] + up[1] * fw[1] + up[2] * fw[2];
                double schub = schubMax * Math.max(-1, Math.min(1, hebel));
                double strahl = Math.max(0, schub) / (rho * 0.5);
                // Ruder nach Steuerbord: Kraft am Heck nach Backbord (+y), der Bug dreht nach Steuerbord
                // seit S7d7: Auftrieb des Ruders begrenzt (ruderClMax) und mit Widerstand (ruderCd0, induziert über ruderStreckung)
                double al = Math.toRadians(35) * Math.max(-1, Math.min(1, ruder)), q = 0.5 * rho * ruderFlaeche * (uf * uf + strahl * 0.02);
                double cl = Math.max(-ruderClMax, Math.min(ruderClMax, ruderAnstieg * al));
                double cd = ruderCd0 + (Double.isInfinite(ruderStreckung) ? 0 : cl * cl / (Math.PI * ruderStreckung * 0.8));
                double seite = q * cl, zurueck = q * cd * (uf >= 0 ? 1 : -1);
                double gx = (schub - zurueck) * fw[0] + seite * bw[0], gy = (schub - zurueck) * fw[1] + seite * bw[1], gz = (schub - zurueck) * fw[2] + seite * bw[2];
                fx += gx; fy += gy; fz += gz;
                kraftMoment(spw, gx, gy, gz, tp);
                tbx += tp[0]; tby += tp[1]; tbz += tp[2];
            }
        }
        // Festmacher
        double[] ff = new double[3], pp = new double[3];
        for (Festmacher fm : festmacher)
            if (fm.kraft(this, wa, t, dt, ff, pp)) {
                fx += ff[0]; fy += ff[1]; fz += ff[2];
                kraftMoment(pp, ff[0], ff[1], ff[2], tp);
                tbx += tp[0]; tby += tp[1]; tbz += tp[2];
            }
        fOut[0] = fx; fOut[1] = fy; fOut[2] = fz;
        tbOut[0] = tbx; tbOut[1] = tby; tbOut[2] = tbz;
    }

    /** Moment (Körper) einer Kraft (Welt) am Weltpunkt pw um den Schwerpunkt. */
    private void kraftMoment(double[] pw, double gx, double gy, double gz, double[] out) {
        double rx = pw[0] - o[0], ry = pw[1] - o[1], rz = pw[2] - o[2];
        double mx = ry * gz - rz * gy, my = rz * gx - rx * gz, mz = rx * gy - ry * gx;
        out[0] = R[0] * mx + R[3] * my + R[6] * mz; out[1] = R[1] * mx + R[4] * my + R[7] * mz; out[2] = R[2] * mx + R[5] * my + R[8] * mz;
    }

    /**
     * Widerstand der Probe p gegen das Wasser, das mit (dx, dy, dz) (Welt) relativ zur Probe strömt: je Richtung des Körpers
     * die angeströmte freie Fläche mal c_D, ½ ρ |u| u · Σ c_D A |û·n|, mal dem Anteil unter Wasser f. Kraft in die Welt.
     */
    private void widerstand(int p, double dx, double dy, double dz, double rho, double f, double cl, double cq, double ch, double[] out) {
        double sp = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (sp < 1e-9) { out[0] = out[1] = out[2] = 0; return; }
        double bx = R[0] * dx + R[3] * dy + R[6] * dz, by = R[1] * dx + R[4] * dy + R[7] * dz, bz = R[2] * dx + R[5] * dy + R[8] * dz;
        double[] fr = proben.frei;
        int i = 6 * p;
        // Wasser strömt nach +x: es trifft die Flächen mit Normale −x (Index 0)
        double ax = bx > 0 ? fr[i] : fr[i + 1], ay = by > 0 ? fr[i + 2] : fr[i + 3], az = bz > 0 ? fr[i + 4] : fr[i + 5];
        if (querstrom) {                                                     // seit S7d7: je Achse, quer mit Quertrieb
            double mp = masse / proben.n, kMax = 0.5 * mp / Math.max(schrittMax, 1e-6), h = 0.5 * rho * f;
            double uq = Math.sqrt(by * by + bz * bz);                           // Querströmung (quer und senkrecht zur Längsachse)
            double kx = Math.min(kMax, h * cl * ax * Math.abs(bx)), ky = Math.min(kMax, h * ay * (cq * uq + cQuertrieb * Math.abs(bx))),
                    kz = Math.min(kMax, h * ch * az * uq);
            double fxk = kx * bx, fyk = ky * by, fzk = kz * bz;
            out[0] = R[0] * fxk + R[1] * fyk + R[2] * fzk; out[1] = R[3] * fxk + R[4] * fyk + R[5] * fzk; out[2] = R[6] * fxk + R[7] * fyk + R[8] * fzk;
            return;
        }
        double proj = (cl * ax * Math.abs(bx) + cq * ay * Math.abs(by) + ch * az * Math.abs(bz)) / sp;
        double k = 0.5 * rho * proj * sp * f;
        double mp = masse / proben.n;
        k = Math.min(k, 0.5 * mp / Math.max(schrittMax, 1e-6));             // stabil beim Teilschritt (wie vessel)
        out[0] = k * dx; out[1] = k * dy; out[2] = k * dz;
    }

    // ------------------------------------------------------------------ Abfragen

    public double zeit() { return zeit; }

    public void zeit(double t) { zeit = t; }

    /** Schwerpunkt in der Welt. */
    public double[] lage() { return o.clone(); }

    /** Quaternion Körper → Welt {w, x, y, z}. */
    public double[] drehung() { return q.clone(); }

    public double[] geschwindigkeit() { return v.clone(); }

    /** Drehgeschwindigkeit im Körper (rad/s). */
    public double[] drehgeschwindigkeit() { return w.clone(); }

    public double kurs() { return (Math.toDegrees(Math.atan2(R[0], R[3])) + 360) % 360; }

    public double kraengung() { return Math.toDegrees(Math.atan2(R[7], R[8])); }

    public double trimm() { return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, R[6])))); }

    /** Wie weit die Hochachse des Körpers nach oben zeigt (1 aufrecht, &lt; 0 gekentert). */
    public double aufrecht() { return R[8]; }

    /** Tiefgang mittschiffs: Wasserlinie über der Unterkante bei x = 0, y = 0 im ruhigen Wasser (m). */
    public double tiefgang(Wasser wa) { double[] k = punktWelt(0, 0, 0); return wa.pegel(k[0], k[1], zeit) - k[2]; }

    /** Eingetauchtes Volumen beim letzten Schritt (m³, tragend). */
    public double eingetaucht() { return eingetaucht; }

    /** Auftrieb beim letzten Schritt (N). */
    public double auftrieb() { return auftrieb; }

    public boolean aufGrund() { return aufGrund; }

    /** Tiefstes Einsinken einer Probe in den Grund beim letzten Schritt (m). */
    public double grundTiefe() { return grundTiefe; }

    /** Seit S7b-3: Modellmatrix (Spalten, double) vom Körper in die Welt nach m[off … off + 15]. */
    public void modell(double[] m, int off) {
        double[] t = punktWelt(0, 0, 0);
        m[off] = R[0]; m[off + 1] = R[3]; m[off + 2] = R[6]; m[off + 3] = 0;
        m[off + 4] = R[1]; m[off + 5] = R[4]; m[off + 6] = R[7]; m[off + 7] = 0;
        m[off + 8] = R[2]; m[off + 9] = R[5]; m[off + 10] = R[8]; m[off + 11] = 0;
        m[off + 12] = t[0]; m[off + 13] = t[1]; m[off + 14] = t[2]; m[off + 15] = 1;
    }

    /** Modellmatrix (Spalten, float) vom Körper in die Welt. */
    public float[] modell() {
        double[] t = punktWelt(0, 0, 0);
        return new float[] {(float) R[0], (float) R[3], (float) R[6], 0, (float) R[1], (float) R[4], (float) R[7], 0, (float) R[2], (float) R[5], (float) R[8], 0,
                (float) t[0], (float) t[1], (float) t[2], 1};
    }

    /**
     * Energie im ruhigen Wasser auf dem Pegel (J, für die Prüfung): Bewegung mit der Zusatzmasse, Lage im Schwerefeld und
     * das Potential des Auftriebs je Probe, U = ρ g V (s² / 2 h für s &lt; h, sonst s − h / 2), s Tiefe der Unterkante.
     */
    public double energie(Wasser wa) {
        double s = zusatzAnteil();
        double cx = R[0], cy = R[3], cl = Math.hypot(cx, cy);
        if (cl > 1e-9) { cx /= cl; cy /= cl; }
        double vl = v[0] * cx + v[1] * cy, vq = -v[0] * cy + v[1] * cx;
        double e = 0.5 * ((masse + s * zusatz[0]) * vl * vl + (masse + s * zusatz[1]) * vq * vq + (masse + s * zusatz[2]) * v[2] * v[2]);
        e += 0.5 * ((traegheit[0] + s * zusatz[3]) * w[0] * w[0] + (traegheit[1] + s * zusatz[4]) * w[1] * w[1] + (traegheit[2] + s * zusatz[5]) * w[2] * w[2]);
        e += masse * G * o[2];
        for (int p = 0; p < proben.n; p++) {
            double[] pw = probeWelt(p);
            double sd = Math.max(0, wa.pegel(pw[0], pw[1], zeit) - (pw[2] - proben.unten[p])), hz = proben.hoehe[p];
            e += wa.dichte() * G * proben.vol[p] * proben.traegt[p] * (sd < hz ? sd * sd / (2 * hz) : sd - hz / 2);
        }
        return e;
    }

    /** Autopilot: Ruder auf den Kurs zum Punkt (x, y), voll bei 30° Abweichung; gibt den Abstand zurück (m). */
    public double steuern(double x, double y) {
        double soll = (Math.toDegrees(Math.atan2(x - o[0], y - o[1])) + 360) % 360;
        double d = ((soll - kurs()) + 540) % 360 - 180;
        ruder = Math.max(-1, Math.min(1, d / 30));
        return Math.hypot(x - o[0], y - o[1]);
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s: %.1f t, KG %.2f m, %d Proben zu %.2f m", name, masse / 1000, schwerpunkt[2], proben.n, proben.d);
    }
}
