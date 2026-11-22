package com.dan.physics.hydro.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.hydro.Hydro;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Ein Gitter von hydro auf der Karte (Werkbuch HalligHooge Teil A, 12.5, Plan P4c): dieselben Formeln wie
 * {@link Hydro}, in float, mit den Kernels {@code fluss}, {@code faktor}, {@code begrenzen}, {@code volumen},
 * {@code querschnitt} und {@code impuls} je Schritt und {@code zeitschritt} für Δt.
 * <ul>
 * <li>Die Einrichtung (Packung, Ränder, Antrieb, Coriolis, Bauwerke) kommt aus einem {@code Hydro}; {@link #hoch()}
 * bringt sie und den Stand auf die Karte, {@link #herunter()} den Stand zurück (η, V, u, Fluss, Zeit, Bilanz).</li>
 * <li>{@link #schritte(int, double)} rechnet n Schritte mit festem Δt in einem Befehlspuffer. Die Ränder (Wasserstand
 * oder Durchfluss, Boden der Geisterzelle) wertet der Prozessor vorher zu jedem halben Schritt aus.</li>
 * <li>u liegt zweimal auf der Karte und wechselt je Schritt; der Satz der Kernels wechselt mit.</li>
 * </ul>
 */
public final class HydroKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/physics/hydro/gpu/";
    private static final int GRUPPE = 64, ROT = 256, PUSH = 8;
    /** Bauwerksnummer einer gesperrten Kante (wie {@code GESPERRT} in hydro.glsl). */
    static final int GESPERRT = -2;

    public final Hydro h;
    final Geraet g;
    private final Konto konto;
    final int nx, ny, zellen, kanten, nRand;
    private final int maxSchritte;
    final Puffer werte, tab, basis, eta, vol, uA, uB, fluss, ha, q, faktor, clip, rand, bilanz, fcor, antrieb, bauNr, red;
    private Puffer bauDaten, bauProfil;
    final Kernel kFluss, kFaktor, kBegrenzen, kVolumen, kQuerschnitt, kImpuls, kZeit;
    /** Welcher u-Puffer der jetzige ist (0: uA, 1: uB). */
    int paritaet;
    /** Zeit der Karte (s); beim Herunterladen nach {@code h.zeit}. */
    public double zeit;
    private double zuflussAnfang, abflussAnfang, abgeschnittenAnfang;
    private int bauwerke = -1;
    private boolean eigenesGeraet;
    /**
     * Antrieb in jedem Abschicken hochladen (wie bis P5b; die Prüffälle ändern ihn zwischen zwei Schritten). Mit false
     * lädt ihn nur {@link #antriebHoch()} (seit P5c: {@code KarteRechnung} einmal je Kopplungsintervall).
     */
    public boolean antriebJeSchritt = true;
    /** Summe der Flüsse je Kante (drei floats: Summe, Ausgleich, Umsatz Σ |Fluss| · Δt) für water.q, seit P5c; null: wird nicht gesammelt. */
    private Puffer qSumme;
    private Kernel kSammeln;

    /**
     * Die Felder im geteilten, vom Prozessor sichtbaren Speicher statt im Speicher der Karte (für die Messung in
     * {@code HydroCheck}, Fall 14; gilt für Karten, die danach angelegt werden). Auf der 680M liegen beide im selben
     * Hauptspeicher (Werkbuch 12.3).
     */
    public static volatile boolean speicherGeteilt;

    /** Ein Puffer für ein Feld: im Speicher der Karte oder, mit {@link #speicherGeteilt}, im geteilten. */
    private static Puffer feld(Geraet g, Konto konto, String name, long bytes) {
        return speicherGeteilt ? Puffer.geteilt(g, konto, name, bytes) : Puffer.speicher(g, konto, name, bytes);
    }

    /** Ein Gitter auf der Karte; {@code maxSchritte} Schritte je Abschicken (Größe des Randpuffers). */
    public HydroKarte(Geraet g, Konto konto, Hydro h, int maxSchritte) {
        this.g = g;
        this.konto = konto;
        this.h = h;
        this.maxSchritte = Math.max(1, maxSchritte);
        nx = h.nx;
        ny = h.ny;
        zellen = h.zellen;
        kanten = h.kanten;
        nRand = 2 * nx + 2 * ny;
        short[] t = h.packung.tabelle();
        werte = Puffer.speicher(g, konto, "hydro Werte", 96);
        tab = feld(g, konto, "hydro Tabelle", (2L * t.length + 3) / 4 * 4);
        basis = feld(g, konto, "hydro Basis", 4L * h.packung.basis().length);
        eta = feld(g, konto, "hydro eta", 4L * zellen);
        vol = feld(g, konto, "hydro V", 16L * zellen);               // je Zelle Volumen und Bezugsvolumen in zwei floats (seit P5)
        uA = feld(g, konto, "hydro u A", 4L * kanten);
        uB = feld(g, konto, "hydro u B", 4L * kanten);
        fluss = feld(g, konto, "hydro Fluss", 4L * kanten);
        ha = feld(g, konto, "hydro hA", 4L * kanten);
        q = feld(g, konto, "hydro q", 4L * kanten);
        faktor = feld(g, konto, "hydro Faktor", 4L * zellen);
        clip = feld(g, konto, "hydro abgeschnitten", 4L * zellen);
        rand = Puffer.geteilt(g, konto, "hydro Rand", 8L * nRand * (2L * this.maxSchritte + 1));
        bilanz = Puffer.speicher(g, konto, "hydro Bilanz", 16L * nRand);
        fcor = Puffer.speicher(g, konto, "hydro Zeilen", 4L * (9L * ny + 3));
        antrieb = feld(g, konto, "hydro Antrieb", 24L * zellen);
        bauNr = feld(g, konto, "hydro Bauwerk je Kante", 4L * kanten);
        red = Puffer.speicher(g, konto, "hydro Zeitschritt", 4L * (Math.max(zellen, kanten) / ROT + 2));
        tab.schreibe(t);
        basis.schreibe(h.basis());
        Kernel.Bindung[] b = new Kernel.Bindung[20];
        java.util.Arrays.fill(b, Kernel.Bindung.PUFFER);
        kFluss = Kernel.lade(g, PFAD + "fluss.comp", PUSH, null, b);
        kFaktor = Kernel.lade(g, PFAD + "faktor.comp", PUSH, null, b);
        kBegrenzen = Kernel.lade(g, PFAD + "begrenzen.comp", PUSH, null, b);
        kVolumen = Kernel.lade(g, PFAD + "volumen.comp", PUSH, null, b);
        kQuerschnitt = Kernel.lade(g, PFAD + "querschnitt.comp", PUSH, null, b);
        kImpuls = Kernel.lade(g, PFAD + "impuls.comp", PUSH, null, b);
        kZeit = Kernel.lade(g, PFAD + "zeitschritt.comp", PUSH, null, b);
        hoch();
    }

    /** Ein Gitter mit eigenem Gerät (für Prüfungen); schließt das Gerät mit. */
    public static HydroKarte mitGeraet(Hydro h, int maxSchritte) {
        Geraet g = new Geraet();
        HydroKarte k = new HydroKarte(g, g.konto("hydro", 8L << 30), h, maxSchritte);
        k.eigenesGeraet = true;
        return k;
    }

    // ------------------------------------------------------------------ Abgleich

    /** Bringt Einrichtung und Stand des {@code Hydro} auf die Karte; Bilanz und Abgeschnittenes zählen von hier. */
    public void hoch() {
        ByteBuffer w = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
        int randArt = 0;
        for (int s = 0; s < 4; s++) randArt |= h.rand[s].ordinal() << (2 * s);
        int an = (h.windX != null || h.windY != null ? 1 : 0) | (h.druck != null ? 2 : 0) | (h.kraftX != null || h.kraftY != null ? 4 : 0)
                | (h.gleich != null ? 8 : 0);
        w.putInt(nx).putInt(ny).putInt(zellen).putInt(h.kantenX).putInt(kanten).putInt(h.stufen).putInt(nRand).putInt(randArt)
                .putInt(an).putInt(h.smagorinsky > 0 ? 1 : 0).putInt(0).putInt(0);
        w.putFloat((float) h.dx).putFloat((float) h.dy).putFloat((float) h.zellflaeche).putFloat((float) h.g).putFloat((float) h.rhoW).putFloat((float) h.rhoL)
                .putFloat((float) h.smagorinsky).putFloat((float) h.hWind).putFloat((float) h.hTrocken).putFloat((float) h.muPoleni).putFloat((float) randAbstand(h)).putFloat(0);
        w.flip();
        werte.schreibe(w, 0);
        eta.schreibe(f(h.eta));
        vol.schreibe(volumenZwei(h));
        paritaet = 0;
        uA.schreibe(f(h.u));
        uB.nullen();
        fluss.nullen();
        ha.nullen();
        q.nullen();
        clip.nullen();
        bilanz.nullen();
        fcor.schreibe(zeilen(h));
        antriebHoch();
        bauwerkeHoch();
        zeit = h.zeit;
        zuflussAnfang = h.zufluss;
        abflussAnfang = h.abfluss;
        abgeschnittenAnfang = h.abgeschnitten;
    }

    /**
     * Boden oder Rauheit des {@code Hydro} haben sich geändert (seit P9c, {@code Hydro.boden}, {@code Hydro.rauheit}):
     * Basis, η und das Bezugsvolumen neu auf die Karte. Der Stand muss vorher heruntergeholt sein ({@link #herunter()});
     * Volumen, u, Flüsse und Bilanz bleiben.
     */
    public void bodenHoch() {
        basis.schreibe(h.basis());
        eta.schreibe(f(h.eta));
        vol.schreibe(volumenZwei(h));
    }

    /** Abstand des Werts am Rand für alle Seiten mit Wasserstand (auf der Karte einer für alle). */
    private static double randAbstand(Hydro h) {
        double a = Double.NaN;
        for (int s = 0; s < 4; s++) {
            if (h.rand[s] != Hydro.Rand.WASSERSTAND) continue;
            if (!Double.isNaN(a) && a != h.randAbstand[s]) throw new IllegalArgumentException("HydroKarte: verschiedene Randabstände " + a + " und " + h.randAbstand[s]);
            a = h.randAbstand[s];
        }
        return Double.isNaN(a) ? 1 : a;
    }

    /**
     * Volumen und Bezugsvolumen als Hauptteil und Rest in float (hydro.glsl, Puffer {@code Vol}): Das Bezugsvolumen ist
     * V(η_b) mit η_b = max(0, oberste Stufe), die oberste Stufe in float gerechnet wie auf der Karte.
     */
    static float[] volumenZwei(Hydro h) {
        int n = h.zellen, S = h.stufen;
        float[] r = new float[4 * n];
        float[] basis = h.basis();
        short[] tab = h.packung.tabelle();
        for (int c = 0; c < n; c++) {
            double v = h.vol[c];
            r[c] = (float) v;
            r[n + c] = (float) (v - r[c]);
            float oben = basis[c] + Float.float16ToFloat(tab[c * 3 * S + S - 1]);
            double vb = h.volumen(c, Math.max(0f, oben));
            r[2 * n + c] = (float) vb;
            r[3 * n + c] = (float) (vb - r[2 * n + c]);
        }
        return r;
    }

    /** Geometrie je Zeile wie in hydro.glsl (Puffer {@code Zeilen}): f, Fläche, Breite, Höhe, tan φ / R; je Kantenzeile quer zu y Länge, Abstand, tan φ / R. */
    static float[] zeilen(Hydro h) {
        int ny = h.ny;
        float[] z = new float[9 * ny + 3];
        for (int j = 0; j < ny; j++) {
            z[j] = (float) h.f[j];
            z[ny + j] = (float) h.zeilenFlaeche[j];
            z[2 * ny + j] = (float) h.zeilenBreite[j];
            z[3 * ny + j] = (float) h.zeilenHoehe[j];
            z[4 * ny + j] = (float) h.zeilenTan[j];
            z[8 * ny + 3 + j] = (float) (h.zeilenFlaeche[j] - z[ny + j]);
        }
        for (int j = 0; j <= ny; j++) {
            z[5 * ny + j] = (float) h.kantenLaengeY[j];
            z[6 * ny + 1 + j] = (float) h.kantenAbstandY[j];
            z[7 * ny + 2 + j] = (float) h.kantenTanY[j];
        }
        return z;
    }

    /** Antrieb (Wind, Luftdruck, Kraft aus dem Seegang, Gleichgewichtstide) auf die Karte, soweit vorhanden. */
    public void antriebHoch() {
        if (h.windX == null && h.windY == null && h.druck == null && h.kraftX == null && h.kraftY == null && h.gleich == null) return;
        float[] a = new float[6 * zellen];
        double[][] q = {h.windX, h.windY, h.druck, h.kraftX, h.kraftY, h.gleich};
        for (int k = 0; k < 6; k++) if (q[k] != null) for (int c = 0; c < zellen; c++) a[k * zellen + c] = (float) q[k][c];
        antrieb.schreibe(a);
    }

    private void bauwerkeHoch() {
        int n = h.bauwerke();
        int[] nr = h.bauwerkNummern();
        if (nr == null) {
            nr = new int[kanten];
            java.util.Arrays.fill(nr, -1);
        } else nr = nr.clone();
        for (int e = 0; e < kanten; e++) if (h.istGesperrt(e)) nr[e] = GESPERRT;
        bauNr.schreibe(nr);
        int stellen = 0;
        for (int k = 0; k < n; k++) stellen += h.bauProfil(k).length;
        float[] d = new float[8 * Math.max(1, n)], p = new float[Math.max(1, stellen)];
        int o = 0;
        for (int k = 0; k < n; k++) {
            float[] pr = h.bauProfil(k);
            double[] sw = h.sielWerte(k);
            boolean siel = h.istSiel(k);
            double frei = siel ? sw[0] : Double.POSITIVE_INFINITY;
            for (int s = 0; s < pr.length; s++) {
                p[o + s] = Float.isNaN(pr[s]) ? 1e30f : pr[s];
                if (pr[s] < frei) frei = pr[s];
            }
            d[8 * k] = siel ? (float) sw[0] : 0;
            d[8 * k + 1] = siel ? (float) sw[1] : 0;
            d[8 * k + 2] = siel ? (float) sw[2] : 0;
            d[8 * k + 3] = siel ? (float) sw[3] : 0;
            d[8 * k + 4] = pr.length;
            d[8 * k + 5] = o;
            d[8 * k + 6] = (float) Math.min(frei, 1e30);
            o += pr.length;
        }
        if (bauDaten == null || bauDaten.groesse < 4L * d.length) {
            if (bauDaten != null) bauDaten.close();
            bauDaten = Puffer.speicher(g, konto, "hydro Bauwerke", 4L * d.length);
        }
        if (bauProfil == null || bauProfil.groesse < 4L * p.length) {
            if (bauProfil != null) bauProfil.close();
            bauProfil = Puffer.speicher(g, konto, "hydro Profil", 4L * p.length);
        }
        bauDaten.schreibe(d);
        bauProfil.schreibe(p);
        bauwerke = n;
    }

    /** Holt den Stand in das {@code Hydro}: η, V, u, Fluss des letzten Schritts, Zeit, Bilanz, Abgeschnittenes. */
    public void herunter() {
        d(eta.floats(zellen), h.eta);
        float[] v2 = vol.floats(2 * zellen);
        for (int c = 0; c < zellen; c++) h.vol[c] = (double) v2[c] + (double) v2[zellen + c];
        d((paritaet == 0 ? uA : uB).floats(kanten), h.u);
        h.setzeFluss(fluss.floats(kanten));
        h.zeit = zeit;
        float[] b = bilanz.floats(4 * nRand);
        double zu = 0, ab = 0;
        for (int r = 0; r < nRand; r++) {
            zu += (double) b[4 * r] - b[4 * r + 1];
            ab += (double) b[4 * r + 2] - b[4 * r + 3];
        }
        h.zufluss = zuflussAnfang + zu;
        h.abfluss = abflussAnfang + ab;
        double s = 0;
        for (float x : clip.floats(zellen)) s += x;
        h.abgeschnitten = abgeschnittenAnfang + s;
        if (qSumme != null && h.qSumme != null) {
            float[] q3 = qSumme.floats(3 * kanten);
            for (int e = 0; e < kanten; e++) {
                h.qSumme[e] = (double) q3[3 * e] - q3[3 * e + 1];
                if (h.qBrutto != null) h.qBrutto[e] = q3[3 * e + 2];
            }
        }
    }

    /** η einer Zelle auf der Karte (ein Wert, ohne das Hydro zu ändern). */
    public float etaZelle(int c) {
        ByteBuffer b = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        eta.lies(b, 4L * c);
        return b.getFloat(0);
    }

    /** η auf der Karte, ohne das Hydro zu ändern. */
    public float[] etaKarte() { return eta.floats(zellen); }

    /** u auf der Karte, ohne das Hydro zu ändern. */
    public float[] uKarte() { return u().floats(kanten); }

    /** Der jetzige u-Puffer. */
    public Puffer u() { return paritaet == 0 ? uA : uB; }

    // ------------------------------------------------------------------ Lauf

    Object[] belegung(int p) {
        return new Object[] {werte, tab, basis, eta, vol, p == 0 ? uA : uB, p == 0 ? uB : uA, fluss, ha, q, faktor, clip, rand, bilanz, fcor, antrieb, bauNr,
                bauDaten, bauProfil, red};
    }

    Mittel[] alle() {
        return new Mittel[] {werte, tab, basis, eta, vol, uA, uB, fluss, ha, q, faktor, clip, rand, bilanz, fcor, antrieb, bauNr, bauDaten, bauProfil, red};
    }

    // ------------------------------------------------------------------ Summe der Flüsse (water.q, seit P5c)

    /** Sammelt ab jetzt in jeder Masse Fluss · Δt je Kante (Kernel {@code sammeln}); {@link #herunter()} bringt die Summe nach {@code h.qSumme}. */
    public void qSammeln() {
        if (qSumme != null) return;
        qSumme = feld(g, konto, "hydro Summe der Flüsse", 12L * kanten);
        qSumme.nullen();
        kSammeln = Kernel.lade(g, PFAD + "sammeln.comp", PUSH, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
    }

    /** Setzt die Summe der Flüsse auf null. */
    public void summenNull() { if (qSumme != null) qSumme.nullen(); }

    /** Stand für die einseitige Kopplung (η und Volumen von der Karte), ohne das Hydro zu ändern. */
    public com.dan.physics.hydro.Einseitig.Stand standKarte() {
        float[] e = eta.floats(zellen), v = vol.floats(2 * zellen), w = new float[zellen];
        for (int c = 0; c < zellen; c++) w[c] = (float) ((double) v[c] + v[zellen + c]);
        return com.dan.physics.hydro.Einseitig.Stand.aus(e, w);
    }

    /** Füllt den Randpuffer für n Schritte zu Δt ab {@link #zeit}: je halbem Schritt Wert und Boden der Geisterzelle. */
    public void randFuellen(int n, double dt) {
        float[] r = new float[2 * nRand * (2 * n + 1)];
        int[] kante = randKanten();
        for (int m = 0; m <= 2 * n; m++) {
            double t = zeit + 0.5 * m * dt;
            for (int k = 0; k < nRand; k++) {
                int e = kante[k];
                r[2 * (m * nRand + k)] = (float) h.randWert(e, t);
                r[2 * (m * nRand + k) + 1] = (float) h.randBodenWert(e, t);
            }
        }
        rand.schreibe(r);
    }

    /** Die Randkanten in der Reihenfolge des Randpuffers: West (j), Ost (j), Süd (i), Nord (i). */
    public int[] randKanten() {
        int[] k = new int[nRand];
        for (int j = 0; j < ny; j++) {
            k[j] = h.kanteX(0, j);
            k[ny + j] = h.kanteX(nx, j);
        }
        for (int i = 0; i < nx; i++) {
            k[2 * ny + i] = h.kanteY(i, 0);
            k[2 * ny + nx + i] = h.kanteY(i, ny);
        }
        return k;
    }

    /** Nimmt die sechs Durchgänge eines Schritts in den Ablauf auf (Randpuffer ab halbem Schritt m) und wechselt u. */
    void schrittAufnehmen(Ablauf ab, double dt, int m) {
        masseAufnehmen(ab, dt, m);
        impulsAufnehmen(ab, dt, m);
    }

    /** Masse über dt (Ränder ab halbem Schritt m): fluss, faktor, begrenzen, volumen. */
    void masseAufnehmen(Ablauf ab, double dt, int m) {
        if (bauwerke != h.bauwerke()) bauwerkeHoch();
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        push.putFloat(0, (float) dt).putInt(4, m);
        int p = paritaet, gk = Kernel.gruppen(kanten, GRUPPE), gz = Kernel.gruppen(zellen, GRUPPE);
        Mittel[] a = alle(), keine = new Mittel[0];
        ab.rechne("fluss", kFluss, kFluss.satz(belegung(p)), push, gk, 1, 1, keine, a);
        ab.rechne("faktor", kFaktor, kFaktor.satz(belegung(p)), push, gz, 1, 1, keine, a);
        ab.rechne("begrenzen", kBegrenzen, kBegrenzen.satz(belegung(p)), push, gk, 1, 1, keine, a);
        if (nachBegrenzen != null) nachBegrenzen.accept(ab, dt);
        ab.rechne("volumen", kVolumen, kVolumen.satz(belegung(p)), push, gz, 1, 1, keine, a);
        if (qSumme != null) {
            ByteBuffer ps = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
            ps.putFloat(0, (float) dt).putInt(4, kanten);
            ab.rechne("sammeln", kSammeln, kSammeln.satz(fluss, qSumme), ps, gk, 1, 1, keine, new Mittel[] {fluss, qSumme});
        }
    }

    /** Für {@link KetteKarte}: in jeder Masse nach dem Begrenzen und vor dem Volumen aufgenommen (Ablauf, Δt); seit P4d. */
    java.util.function.ObjDoubleConsumer<Ablauf> nachBegrenzen;

    /** Impuls über dt (Ränder zum halben Schritt m + 2): querschnitt, impuls; wechselt u. */
    void impulsAufnehmen(Ablauf ab, double dt, int m) {
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        push.putFloat(0, (float) dt).putInt(4, m);
        int p = paritaet, gk = Kernel.gruppen(kanten, GRUPPE);
        Mittel[] a = alle(), keine = new Mittel[0];
        ab.rechne("querschnitt", kQuerschnitt, kQuerschnitt.satz(belegung(p)), push, gk, 1, 1, keine, a);
        ab.rechne("impuls", kImpuls, kImpuls.satz(belegung(p)), push, gk, 1, 1, keine, a);
        paritaet ^= 1;
    }

    /** n Schritte mit festem Δt auf der Karte (höchstens {@code maxSchritte}); wartet, bis sie fertig sind. */
    public void schritte(int n, double dt) {
        if (n > maxSchritte) throw new IllegalArgumentException("HydroKarte: " + n + " Schritte, Randpuffer für " + maxSchritte);
        randFuellen(n, dt);
        if (antriebJeSchritt) antriebHoch();
        Ablauf ab = new Ablauf(g);
        for (int k = 0; k < n; k++) schrittAufnehmen(ab, dt, 2 * k);
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        zeit += n * dt;
    }

    /** Die beiden Grenzen des Zeitschritts aus dem Stand auf der Karte, schon mal cfl: {Welle, Speicher} (s). */
    public double[] dtTeile() {
        ByteBuffer push = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        double[] r = new double[2];
        for (int art = 0; art < 2; art++) {
            int gr = Kernel.gruppen(zellen, ROT);
            push.putFloat(0, 0).putInt(4, art);
            Ablauf ab = new Ablauf(g).rechne("zeitschritt", kZeit, kZeit.satz(belegung(paritaet)), push, gr, 1, 1, new Mittel[0], alle());
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            float[] t = red.floats(gr);
            double x = art == 0 ? Double.POSITIVE_INFINITY : 0;
            for (float v : t) x = art == 0 ? Math.min(x, v) : Math.max(x, v);
            if (art == 0) r[0] = h.cfl * (x >= 1e38 ? h.abstandMin : x);
            else r[1] = x > 0 ? h.cfl * 2 / Math.sqrt(x) : Double.POSITIVE_INFINITY;
        }
        return r;
    }

    /** Größter Schritt (s) nach beiden Grenzen, wie {@link Hydro#dtCfl()}. */
    public double dtCfl() {
        double[] t = dtTeile();
        return Math.min(t[0], t[1]);
    }

    /** Läuft bis {@code bis}: je Abschnitt von {@code abschnitt} Schritten ein Δt nach CFL (höchstens dtMax). Gibt die Schritte. */
    public int lauf(double bis, double dtMax, int abschnitt) {
        int n = 0;
        while (zeit < bis - 1e-9) {
            double dt = Math.min(dtMax, dtCfl());
            int k = (int) Math.max(1, Math.min(Math.min(abschnitt, maxSchritte), Math.floor((bis - zeit) / dt)));
            if (zeit + k * dt > bis - 1e-9) {
                k = 1;
                dt = Math.min(dt, bis - zeit);
            }
            schritte(k, dt);
            n += k;
        }
        return n;
    }

    // ------------------------------------------------------------------ Hilfen

    private static float[] f(double[] a) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) r[i] = (float) a[i];
        return r;
    }

    private static void d(float[] a, double[] r) {
        for (int i = 0; i < a.length; i++) r[i] = a[i];
    }

    @Override public void close() {
        g.ruhe();
        for (Kernel k : new Kernel[] {kFluss, kFaktor, kBegrenzen, kVolumen, kQuerschnitt, kImpuls, kZeit}) k.close();
        if (kSammeln != null) kSammeln.close();
        if (qSumme != null) qSumme.close();
        for (Puffer p : new Puffer[] {werte, tab, basis, eta, vol, uA, uB, fluss, ha, q, faktor, clip, rand, bilanz, fcor, antrieb, bauNr, red, bauDaten, bauProfil})
            if (p != null) p.close();
        if (eigenesGeraet) g.close();
    }
}
