package com.dan.physics.sediment;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Subgrid;

import java.util.Locale;

/**
 * Das Sediment am Bus (Werkbuch HalligHooge Teil A, 12.10, P9b): Schwebstoff, Geschiebe, Schlick und Rippel auf dem
 * Gitter, das hydro rechnet (G3, auch als Fenster), bei festem Boden von hydro – die Bodenänderung liegt am Bus, wirkt
 * aber erst mit P9c zurück.
 * <ul>
 * <li><b>Liest</b> im Intervall von hydro {@code water.eta} (Anfang und Ende: das Volumen der Zelle aus der Tabelle des
 * Subgrids, {@code ground.hyps}), {@code water.q} (Zeitmittel des Flusses je Kante über das Intervall) und in der Mitte
 * des Intervalls {@code water.h}, {@code water.u}, {@code water.v} (Tiefe über der benetzten Fläche und Geschwindigkeit
 * für die Schubspannung); zu Beginn vom Seegang {@code waves.ubot}, {@code waves.tp}, {@code waves.dir} (der Bus rechnet
 * sie auf dieses Gitter um).</li>
 * <li><b>Schreibt</b> am Ende des Intervalls, in float: {@code sed.c} (kg/m³; Sand, Schlick), {@code bed.d50} (m),
 * {@code bed.mud} (Schlickanteil der Mischschicht), {@code bed.ripple} (Höhe und Länge in m; Richtung in Grad von
 * Gitternord im Uhrzeigersinn, in die der formende Strom lief), {@code bed.dz.sediment} (m) und
 * {@code surf.z0.sediment} (m).</li>
 * </ul>
 * <b>Wasser:</b> Zu Beginn jedes Intervalls gilt für die Masse die Tiefe V(η) / Zellfläche; im Intervall folgt sie der
 * Kontinuität mit {@code water.q}. Der Unterschied am Ende ({@link #tiefeMittel}, {@link #tiefeUeber}) misst, wie gut
 * Fluss und Wasserstand von hydro zusammenpassen; was er an Schwebstoff ausmacht, steht in {@link #abgleich} und geht in
 * die Bilanz ({@link #bilanz()}).
 * <p>
 * <b>Boden am Anfang</b> aus dem {@link Kornfeld} des Gebiets (Aufbereitung, Schritt {@code sediment}): d50, Porosität,
 * Schlickanteil (als konsolidierter Schlick in der Mischschicht), Anteil festen Bodens; ohne Kornfeld überall Sand mit
 * 0,2 mm. Am Rand des Gitters kommt Sand mit der Gleichgewichtskonzentration der Randzelle herein, Schlick mit
 * {@link #hintergrund} [G]; mit ihr beginnt auch das Wasser.
 * <p>
 * <b>Seit P9c (Sediment 0.3):</b> Mit {@link #rueck} wirkt der Boden auf hydro zurück (F81 A): hydro verschiebt seine Tabellen
 * um {@code bed.dz}; das Sediment liest dasselbe Feld zu Beginn des Intervalls und rechnet das Wasser der Zelle aus
 * V(η − dz) mit dem Boden, den hydro hatte, als es η schrieb. Im Randstreifen von {@link #rand} Zellen geht, was als
 * {@code bed.dz.sediment} am Bus liegt, linear auf null (am Rand passt hydro an das grobe Gitter außen); innen rechnet das
 * Sediment mit dem vollen Wert. Mit {@link #spirale} liest es {@code water.spiral} (Mitte des Intervalls). Neu am Bus:
 * {@code bed.fluff} (lockere Auflage, kg/m²). {@code surf.z0.sediment} ist auf festem Boden 0 (keine Aussage: hydro
 * behält dort die Rauheit der Packung). Die Bilanz rechnet mit dem, was der Boden wirklich aufgenommen hat
 * ({@code Sediment.wirklich}), also unabhängig von Faktor und Deckel.
 * <p>
 * Kennt nur den Kern (Schichtenvertrag); die Karte hängt als {@link Rechner} daran ({@code gpu.SedimentRechner}).
 */
public final class SedimentModul implements Modul {
    /** Wo die Teilschritte laufen. */
    public interface Rechner extends AutoCloseable {
        /** Einmal vor dem ersten Schritt: der Zustand der Referenz gilt. */
        void einrichten(Sediment s);

        /** Ein Kopplungsintervall; der Eingang steht in {@link Sediment}, danach auch Zustand, Schubspannung und Randfluss. Gibt die Zahl der Teilschritte zurück. */
        int schritt(Sediment s, double dt);

        String geraet();

        @Override default void close() { }
    }

    /** Die Referenz auf dem Prozessor (double, ein Faden). */
    public static final class Prozessor implements Rechner {
        @Override public void einrichten(Sediment s) { }
        @Override public int schritt(Sediment s, double dt) { return s.schritt(dt); }
        @Override public String geraet() { return "Prozessor (ein Faden, double)"; }
    }

    public final Gitter gitter;
    public final Sediment sediment;
    public final Rechner rechner;
    public final double kopplung;
    /** Liest den Seegang ({@code waves.*}); ohne ihn rührt nur der Strom. */
    public final boolean seegang;
    /** Schlick im Wasser am Anfang und im Zufluss am Rand (kg/m³) [G]. */
    public final double hintergrund;
    private final Subgrid.Packung packung;
    private final double flaeche;

    /** Ab dieser Modellzeit (s) rechnet das Sediment; davor bleibt sein Stand (etwa über den Anlauf der Tide). */
    public double ab;
    /** Seit P9c, vor dem Anmelden zu setzen: hydro verschiebt seinen Boden um {@code bed.dz} (das Sediment liest es mit); {@code water.spiral} lesen. */
    public boolean rueck, spirale;
    /** Seit P9c: Breite des Randstreifens in Zellen, in dem die Bodenänderung am Bus auf null geht (0: ohne) [G]. */
    public int rand;
    /** Bestand zu Beginn nach {@code Sediment.wirklich} (kg). */
    public double[] wirkAnfang;
    private double[] dzHydro;
    private Feld fDz, fSp;
    /** Rechenzeit in {@link #schritt} (s), gerechnete Intervalle und Teilschritte. */
    public double sekunden;
    public long intervalle, teilschritte;
    /** Tiefe aus der Kontinuität gegen V(η)/Fläche am Ende der Intervalle: Summe der Beträge (m), Zahl der Zellen, davon über {@link #tiefeGrenze}, größter Betrag (m). */
    public double tiefeSumme, tiefeGroesste;
    public long tiefeZellen, tiefeUeber;
    public double tiefeGrenze = 0.005;
    /** Schwebstoff, den der Abgleich der Tiefe zu Beginn der Intervalle ausmacht (kg): Sand, Schlick. */
    public final double[] abgleich = new double[2];
    /** Bestand am Anfang (kg, wie {@link Sediment#masse()}) und Umsatz (kg): Summe der Beträge, um die sich Boden und Randfluss je Intervall ändern; Sand, Schlick. */
    public double[] anfang;
    public final double[] umsatz = new double[2];
    /** NaN oder negative Konzentration nach einem Intervall (Zellen, über den Lauf). */
    public long schlecht;
    private double[] bodenLetzt, ausLetzt = new double[3];
    private Feld fEta, fQ, fH, fU, fV, fUb, fTp, fDir, fVeg;
    /**
     * Seit P13b (F153 A): {@code veg.cover} lesen. Unter Pflanzen trägt der Boden nur einen Teil der Schubspannung
     * (Aufteilung nach Raupach): Die Geschwindigkeit, aus der das Sediment die Schubspannung am Boden rechnet, geht mit
     * √(1 − {@link #vegAnteil} · cover). So erodiert dort weniger und mehr Schlick setzt sich ab; die Advektion
     * ({@code water.q}) bleibt.
     */
    public boolean vegetation;
    /** Anteil der Schubspannung, den volle Bedeckung den Pflanzen gibt [G]. */
    public double vegAnteil = 0.9;
    private float[] deckung;

    /**
     * @param gitter      das Gitter von hydro in UTM (G3, auch als Fenster)
     * @param packung     die Tabelle des Subgrids dieses Gitters
     * @param korn        der Boden am Anfang oder null
     * @param hintergrund Schlick im Wasser am Anfang und am Rand (kg/m³) [G: 0,02]
     */
    public SedimentModul(Gitter gitter, Subgrid.Packung packung, Kornfeld korn, Rechner rechner, double kopplung, boolean seegang, double hintergrund) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("sediment: Gitter " + gitter.name + " ist nicht in UTM");
        if (packung.zellen() != gitter.zellen()) throw new IllegalArgumentException("sediment: die Packung hat " + packung.zellen() + " Zellen, das Gitter " + gitter.zellen());
        this.gitter = gitter;
        this.packung = packung;
        this.rechner = rechner;
        this.kopplung = kopplung;
        this.seegang = seegang;
        this.hintergrund = hintergrund;
        flaeche = gitter.dx * gitter.dy;
        Sediment s = new Sediment(gitter.nx, gitter.ny, gitter.dx, gitter.dy);
        sediment = s;
        s.tiefe = new double[s.nc];
        s.ug = new double[s.nc];
        s.vg = new double[s.nc];
        s.teileMax = 150;
        s.hRate = 0.2;
        s.randSchlick = hintergrund;
        // Mittlere Höhe des Bodens der Zelle: über der höchsten Stufe wächst das Volumen mit der ganzen Zellfläche
        for (int c = 0; c < s.nc; c++) {
            double oben = packung.basis()[c] + 100;
            s.grund[c] = oben - packung.volumen(c, oben) / packung.zellflaeche();
        }
        if (korn != null) {
            double[] d = korn.auf(gitter, Kornfeld.D50), n = korn.auf(gitter, Kornfeld.POROSITAET), m = korn.auf(gitter, Kornfeld.SCHLICK), f = korn.auf(gitter, Kornfeld.FEST);
            s.porosZ = new double[s.nc];
            s.frei = new double[s.nc];
            for (int c = 0; c < s.nc; c++) {
                s.d50[c] = Math.max(6.3e-5, Math.min(2e-3, d[c]));           // die Formeln gelten für Sand
                s.porosZ[c] = Math.max(0.25, Math.min(0.6, n[c]));
                s.frei[c] = Math.max(0, Math.min(1, 1 - f[c]));
                double pm = Math.max(0, Math.min(0.95, m[c]));
                s.mSchlick[c] = pm / (1 - pm) * s.rhoS * (1 - s.porosZ[c]) * s.deltaAkt;
                s.alter[c] = 3 * s.tKons;                                    // liegt schon lange
            }
        }
    }

    @Override public String name() { return "sediment"; }

    @Override public String version() { return Sediment.VERSION; }

    @Override public void anmelden(Anmeldung a) {
        Gitter g = gitter;
        a.kopplung(kopplung);
        a.liestImIntervall("water.eta", "m NHN", Ort.ZELLE, g).liestImIntervall("water.q", "m²/s", Ort.KANTE, g).liestImIntervall("water.h", "m", Ort.ZELLE, g)
                .liestImIntervall("water.u", "m/s", Ort.ZELLE, g).liestImIntervall("water.v", "m/s", Ort.ZELLE, g);
        if (seegang) a.liest("waves.ubot", "m/s", Ort.ZELLE, g).liest("waves.tp", "s", Ort.ZELLE, g).liest("waves.dir", "°", Ort.ZELLE, g);
        if (rueck) a.liest("bed.dz", "m", Ort.ZELLE, g);
        if (spirale) a.liestImIntervall("water.spiral", "m/s", Ort.ZELLE, g);
        if (vegetation) a.liest("veg.cover", "–", Ort.ZELLE, g);
        a.schreibt("bed.fluff", "kg/m²", Ort.ZELLE, g, Feld.Zahl.FLOAT);
        a.schreibt("sed.c", "kg/m³", Ort.ZELLE, g, Feld.Zahl.FLOAT).schreibt("bed.d50", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT).schreibt("bed.mud", "–", Ort.ZELLE, g, Feld.Zahl.FLOAT)
                .schreibt("bed.ripple", "m, m, °", Ort.ZELLE, g, Feld.Zahl.FLOAT).schreibt("bed.dz.sediment", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT)
                .schreibt("surf.z0.sediment", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT);
    }

    @Override public void beginn(double t0, Bus bus) {
        fEta = bus.lesefeld(this, "water.eta");
        fQ = bus.lesefeld(this, "water.q");
        fH = bus.lesefeld(this, "water.h");
        fU = bus.lesefeld(this, "water.u");
        fV = bus.lesefeld(this, "water.v");
        if (seegang) {
            fUb = bus.lesefeld(this, "waves.ubot");
            fTp = bus.lesefeld(this, "waves.tp");
            fDir = bus.lesefeld(this, "waves.dir");
        }
        if (rueck) fDz = bus.lesefeld(this, "bed.dz");
        if (vegetation) fVeg = bus.lesefeld(this, "veg.cover");
        if (spirale) {
            fSp = bus.lesefeld(this, "water.spiral");
            sediment.spiral = new double[sediment.nc];
        }
        bus.lies(this, "water.eta", t0, fEta);
        beginne(fEta);
        lege(t0, bus);
    }

    /** Der Anfang ohne Bus (auch für die Prüfung): Wasser aus dem Wasserstand, Schlick im Wasser mit dem Hintergrund, Bestand für die Bilanz. */
    public void beginne(Feld eta) {
        Sediment s = sediment;
        for (int c = 0; c < s.nc; c++) {
            s.h[c] = packung.volumen(c, eta.hol(c)) / flaeche;
            s.cm[c] = s.mitSchlick && s.h[c] >= s.hMin ? hintergrund : 0;
        }
        dzHydro = new double[s.nc];                                           // hydro beginnt mit dem Boden der Packung
        rechner.einrichten(s);
        anfang = s.masse();
        wirkAnfang = s.wirklich();
        bodenLetzt = new double[] {wirkAnfang[0], wirkAnfang[1] + wirkAnfang[2]};
    }

    @Override public void schritt(double t0, double t1, Bus bus) {
        if (rueck) bus.lies(this, "bed.dz", t0, fDz);                         // mit diesem Boden rechnet hydro dieses Intervall
        if (t1 <= ab + 1e-9 * Math.max(1, Math.abs(ab))) {                   // rechnet noch nicht: der Stand bleibt
            bodenVonHydro();
            lege(t1, bus);
            return;
        }
        long n0 = System.nanoTime();
        bus.lies(this, "water.eta", t0, fEta);
        bus.lies(this, "water.q", t1, fQ);
        double mitte = 0.5 * (t0 + t1);
        bus.lies(this, "water.h", mitte, fH);
        bus.lies(this, "water.u", mitte, fU);
        bus.lies(this, "water.v", mitte, fV);
        if (seegang) {
            bus.lies(this, "waves.ubot", t0, fUb);
            bus.lies(this, "waves.tp", t0, fTp);
            bus.lies(this, "waves.dir", t0, fDir);
        }
        if (vegetation) {
            bus.lies(this, "veg.cover", t0, fVeg);
            if (deckung == null) deckung = new float[sediment.nc];
            for (int c = 0; c < sediment.nc; c++) { double d = fVeg.hol(c); deckung[c] = Double.isFinite(d) ? (float) Math.max(0, Math.min(1, d)) : 0f; }
        }
        if (spirale) {
            bus.lies(this, "water.spiral", mitte, fSp);
            for (int c = 0; c < sediment.nc; c++) {
                double w = fSp.hol(c);
                sediment.spiral[c] = Double.isFinite(w) ? w : 0;
            }
        }
        rechne(t1 - t0, fEta, fQ, fH, fU, fV, fUb, fTp, fDir);
        bodenVonHydro();
        lege(t1, bus);
        sekunden += (System.nanoTime() - n0) / 1e9;
    }

    /** Merkt sich den Boden, den hydro in diesem Intervall hat ({@code bed.dz} zu Beginn): mit ihm steht η am Ende des Intervalls. */
    private void bodenVonHydro() {
        if (!rueck) return;
        for (int c = 0; c < sediment.nc; c++) {
            double d = fDz.hol(c);
            dzHydro[c] = Double.isFinite(d) ? d : 0;
        }
    }

    /** Gewicht der Zelle im Randstreifen: 0 am Rand des Gitters, 1 ab {@link #rand} Zellen innen. */
    public double randgewicht(int c) {
        if (rand <= 0) return 1;
        int i = c % sediment.nx, j = c / sediment.nx;
        return Math.min(1, Math.min(Math.min(i, sediment.nx - 1 - i), Math.min(j, sediment.ny - 1 - j)) / (double) rand);
    }

    /**
     * Setzt den Boden zu Beginn aus dem Stand eines früheren Laufs (vor {@link #beginn}; P9c): {@code bed.ripple} (Höhe, Länge,
     * Richtung), {@code bed.mud}, {@code bed.dz} (ganz als Sand gebucht) und {@code bed.fluff}; null lässt den Teil, wie er ist.
     */
    public void anfang(float[] rippel, float[] schlick, float[] dz, float[] auflage) {
        Sediment s = sediment;
        for (int c = 0; c < s.nc; c++) {
            if (dz != null && Float.isFinite(dz[c])) { s.dzS[c] = dz[c]; s.dzM[c] = 0; }
            if (schlick != null && Float.isFinite(schlick[c])) s.schlick(c, Math.max(0, Math.min(0.95, schlick[c])));
            if (auflage != null && Float.isFinite(auflage[c])) s.mAuflage[c] = Math.max(0, auflage[c]);
            if (rippel != null && rippel[3 * c] > 0) {
                s.rH[c] = rippel[3 * c];
                s.rL[c] = rippel[3 * c + 1];
                double w = Math.toRadians(90 - rippel[3 * c + 2]);
                s.rX[c] = Math.cos(w);
                s.rY[c] = Math.sin(w);
            }
        }
    }

    /**
     * Ein Kopplungsintervall der Länge tau ohne Bus (auch für die Prüfung), mit den Feldern, wie das Modul sie am Bus liest:
     * {@code eta} zu Beginn, {@code q} als Mittel über das Intervall, {@code h}, {@code u}, {@code v} in seiner Mitte,
     * {@code ubot}, {@code tp}, {@code dir} zu Beginn (null: ohne Seegang).
     */
    public void rechne(double tau, Feld eta, Feld q, Feld h, Feld u, Feld v, Feld ubot, Feld tp, Feld dir) {
        Sediment s = sediment;
        // Wasser: Volumen zu Beginn aus dem Wasserstand; der Unterschied zur Kontinuität des letzten Intervalls
        boolean erstes = intervalle == 0;
        for (int c = 0; c < s.nc; c++) {
            double hv = packung.volumen(c, eta.hol(c) - dzHydro[c]) / flaeche, d = hv - s.h[c];
            if (!erstes && (hv >= s.hMin || s.h[c] >= s.hMin)) {
                double b = Math.abs(d);
                tiefeSumme += b;
                tiefeZellen++;
                if (b > tiefeGrenze) tiefeUeber++;
                if (b > tiefeGroesste) tiefeGroesste = b;
            }
            abgleich[0] += d * s.cs[c] * flaeche;
            abgleich[1] += d * s.cm[c] * flaeche;
            s.h[c] = hv;
            s.tiefe[c] = h.hol(c);
            double teil = deckung != null ? Math.sqrt(Math.max(0, 1 - vegAnteil * deckung[c])) : 1;
            s.ug[c] = u.hol(c) * teil;
            s.vg[c] = v.hol(c) * teil;
        }
        for (int j = 0; j < s.ny; j++) for (int i = 0; i <= s.nx; i++) s.qx[i + (s.nx + 1) * j] = q.hol(q.kanteX(i, j));
        for (int j = 0; j <= s.ny; j++) for (int i = 0; i < s.nx; i++) s.qy[i + s.nx * j] = q.hol(q.kanteY(i, j));
        if (ubot != null)
            for (int c = 0; c < s.nc; c++) {
                s.ubot[c] = Math.max(0, ubot.hol(c));
                s.tp[c] = Math.max(0, tp.hol(c));
                double wohin = Math.toRadians(270 - dir.hol(c));             // waves.dir: woher, von Gitternord im Uhrzeigersinn
                s.wcos[c] = Math.cos(wohin);
                s.wsin[c] = Math.sin(wohin);
            }
        teilschritte += rechner.schritt(s, tau);
        for (int c = 0; c < s.nc; c++) if (!(s.cs[c] >= 0) || !(s.cm[c] >= 0) || !(Math.abs(s.dzS[c]) < 1e6) || !(Math.abs(s.dzM[c]) < 1e6)) schlecht++;
        double[] m = s.wirklich();
        umsatz[0] += Math.abs(m[0] - bodenLetzt[0]) + Math.abs(s.aus[0] - ausLetzt[0]) + Math.abs(s.aus[2] - ausLetzt[2]);
        umsatz[1] += Math.abs(m[1] + m[2] - bodenLetzt[1]) + Math.abs(s.aus[1] - ausLetzt[1]);
        bodenLetzt[0] = m[0];
        bodenLetzt[1] = m[1] + m[2];
        System.arraycopy(s.aus, 0, ausLetzt, 0, 3);
        intervalle++;
    }

    private void lege(double t, Bus bus) {
        Sediment s = sediment;
        Feld c = bus.schreibfeld(this, "sed.c"), d = bus.schreibfeld(this, "bed.d50"), m = bus.schreibfeld(this, "bed.mud"), r = bus.schreibfeld(this, "bed.ripple"),
                z = bus.schreibfeld(this, "bed.dz.sediment"), z0 = bus.schreibfeld(this, "surf.z0.sediment"), fl = bus.schreibfeld(this, "bed.fluff");
        for (int k = 0; k < s.nc; k++) {
            c.f[2 * k] = (float) s.cs[k];
            c.f[2 * k + 1] = (float) s.cm[k];
            d.f[k] = (float) s.d50[k];
            m.f[k] = (float) s.schlickanteil(k);
            r.f[3 * k] = (float) s.rH[k];
            r.f[3 * k + 1] = (float) s.rL[k];
            double grad = s.rH[k] > 0 && (s.rX[k] != 0 || s.rY[k] != 0) ? 90 - Math.toDegrees(Math.atan2(s.rY[k], s.rX[k])) : 0;
            r.f[3 * k + 2] = (float) (grad < 0 ? grad + 360 : grad);
            z.f[k] = (float) (randgewicht(k) * (s.dzS[k] + s.dzM[k]));
            z0.f[k] = s.frei != null && s.frei[k] < 0.5 ? 0f : (float) (s.z0[k] > 0 ? s.z0[k] : s.d50[k] / 12);
            fl.f[k] = (float) s.mAuflage[k];
        }
        for (String n : new String[] {"sed.c", "bed.d50", "bed.mud", "bed.ripple", "bed.dz.sediment", "surf.z0.sediment", "bed.fluff"}) bus.lege(this, n, t);
    }

    // ------------------------------------------------------------------ für Prüfungen

    /** Mittlerer Betrag des Unterschieds der Tiefe (m) zwischen Kontinuität und V(η)/Fläche über alle Intervalle. */
    public double tiefeMittel() { return tiefeZellen > 0 ? tiefeSumme / tiefeZellen : 0; }

    /** Anteil der Zellen, in denen der Unterschied über {@link #tiefeGrenze} liegt. */
    public double tiefeAnteilUeber() { return tiefeZellen > 0 ? (double) tiefeUeber / tiefeZellen : 0; }

    /**
     * Massenbilanz seit dem Anfang, relativ zum Umsatz: {Sand, Schlick}. Rest = Bestand − Anfang + über den Rand − Abgleich
     * der Tiefe; Sand: Wasser, Boden, Schwebstoff und Geschiebe über den Rand; Schlick: Wasser, Boden, Rand.
     */
    public double[] bilanz() {
        double[] m = sediment.masse(), w = sediment.wirklich(), a = sediment.aus;
        double sand = m[0] - anfang[0] + w[0] - wirkAnfang[0] + a[0] + a[2] - abgleich[0];
        double schlick = m[2] - anfang[2] + w[1] - wirkAnfang[1] + w[2] - wirkAnfang[2] + a[1] - abgleich[1];
        return new double[] {Math.abs(sand) / Math.max(1, umsatz[0]), Math.abs(schlick) / Math.max(1, umsatz[1])};
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "sediment %s auf %s, Kopplung %.0f s, %s, %s", Sediment.VERSION, gitter, kopplung, seegang ? "mit Seegang" : "ohne Seegang", rechner.geraet());
    }
}
