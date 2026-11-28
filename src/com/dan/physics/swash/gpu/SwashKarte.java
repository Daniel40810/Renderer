package com.dan.physics.swash.gpu;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.physics.swash.Swash;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Der Löser für Auflauf und Rücklauf auf der Karte (Seegang Werkbuch, S6b) in float, mit denselben Formeln und in
 * derselben Reihenfolge wie {@link Swash}: je Stufe nach Heun die Randzellen ({@code swash_rand.comp}) und die ganze Stufe
 * in einer Abgabe ({@code swash_stufe}, seit S6b-1b): Steigungen, Flüsse nach HLL mit der Rekonstruktion nach Audusse,
 * Kürzung des Abflusses und die Stufe (in Stufe 2 dazu Reibung, Versickern und Spuren), je Kachel von 16 × 8 Zellen im
 * geteilten Speicher; trockene Kacheln, deren Nachbarn trocken sind, werden übersprungen. Der einzelne Weg mit fünf
 * Abgaben je Stufe ({@code swash_steigung}, {@code _fluss}, {@code _abfluss}, {@code _schritt}) bleibt zum Vergleich
 * ({@link #verschmolzen}); beide rechnen mit den Formeln aus {@code swash_gemein.glsl} und sind bitgleich.
 * Der Grund steht auf der Karte relativ zum Ruhewasser ({@link Swash#bezug}); so bleibt ein Film von 10 µm über einem
 * Grund von einigen Metern in float auflösbar.
 * <p>
 * Den Zeitschritt wählt der Aufrufer: in Prüfungen den der Referenz, im Lauf aus {@link #tempo} (größte
 * Wellengeschwindigkeit je Zeile, einige Bilder später gelesen). Der Antrieb liegt je Platz (Seite, k, Lage) in
 * {@link #slots} Ständen auf der Karte; eine Stufe nimmt {@code mix(Stand a, Stand b, w)}. In Prüfungen füllt
 * {@link #antrieb(int, double)} einen Stand aus den Antrieben der Referenz, im Lauf schreibt ein Kernel der Synthese
 * in {@link #antriebPuffer()} (Platz {@link #platz}).
 * <p>
 * Kennt {@code com.dan.core.gpu} und das eigene Modul (Schichtenvertrag: {@code gpu/} des Moduls).
 */
public final class SwashKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/physics/swash/gpu/";
    static final int PUSH = 32, G = Swash.G, KX = 16;
    /**
     * Seit S6e (Seegang Werkbuch 85): Höhe der Kachel des verschmolzenen Wegs, 16 ({@code swash_stufe.comp}) oder 8 wie bis
     * S6d ({@code swash_stufe8.comp}); {@code -Dswash.kachel=8}. Gilt für Karten, die danach gebaut werden.
     */
    public static volatile int kachelY = Integer.getInteger("swash.kachel", 16) == 8 ? 8 : 16;
    static final float FELS = 1e30f, NIE = -1e30f;

    /** Die Referenz, nach der die Karte gebaut ist ({@link #wechsle}: ein neues Rechteck derselben Größe). */
    public Swash s;
    private final Geraet g;
    private final boolean eigenesGeraet;
    private final Puffer werte, zelle, u, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, saum, bild;
    private final Kernel kRand, kSteig, kFluss, kAbfluss, kSchritt, kTempo, kStufe, kSaum, kBild;
    private final long sRand, sSteig, sFluss, sAbfluss, sSchritt, sTempo, sRandB, sStufe1, sStufe2, sSaum, sBild;
    /** Bilanz je Zelle aus dem letzten {@link #runter} (Zufluss über die Seiten, m³, seit {@link #hoch}). */
    private float[] bilanzLetzt;
    /**
     * Seit S6b-1b: eine Stufe in einer Abgabe ({@code swash_stufe.comp}, Kacheln 16 × 16 seit S6e, bis S6d 16 × 8, im geteilten Speicher, trockene
     * Kacheln übersprungen; der Zustand im Wechsel zwischen {@code u} und {@code u0}). false: der einzelne Weg mit fünf
     * Abgaben je Stufe (zum Vergleich). Vor {@link #hoch} setzen.
     */
    public boolean verschmolzen = true;
    private final int kx, ky, KY;
    private long stufen;
    /** Zahl der Stände des Antriebs und Plätze je Stand (alle Seiten mit Antrieb). */
    public final int slots, platzJeSlot;
    private final int[] aOff = {-1, -1, -1, -1};
    private final float[] antriebWerte;
    private final int mMax, zellen;
    private double zeit, basisZufluss, basisVersickert, basisAbgeschnitten;
    private float[] spurLetzt;
    private long schritte;
    /**
     * Seit S6c-2b (Seegang Werkbuch 83): ist das Bild seit dem letzten {@link #hoch} gefüllt? Dann schreibt Stufe 2 jede
     * Zelle, die sich ändert, und {@link #bild} rechnet nichts mehr; Kontur des Spülsaums (m Wasser) dazu.
     */
    private boolean bildVoll;
    private double bildKontur = 0.01;
    /**
     * Seit S6c-2b: schreibt der nächste {@link #schritt} das Bild der Zellen? Die Anwendung setzt es nur für den letzten
     * Schritt eines Bilds ({@code Auflauf}); ohne Angabe jeder Schritt.
     */
    public boolean bildImSchritt = true;

    /** Ein Löser auf der Karte nach dem Gitter, den Feldern und den Seiten der Referenz {@code s}; {@code slots} Stände des Antriebs. */
    public SwashKarte(Geraet g, Konto konto, Swash s, int slots, boolean eigenesGeraet) {
        this.g = g;
        this.s = s;
        this.eigenesGeraet = eigenesGeraet;
        this.slots = Math.max(1, slots);
        mMax = Math.max(s.nx, s.ny);
        zellen = s.px * (s.ny + 2 * G);
        int p = 0;
        for (int seite = 0; seite < 4; seite++) {
            if (s.rand(seite) != Swash.Rand.ANTRIEB || s.antrieb(seite) == null) continue;
            aOff[seite] = p;
            p += ((seite == Swash.W || seite == Swash.E ? s.ny : s.nx) + 2 * G) * G;
        }
        platzJeSlot = Math.max(1, p);
        antriebWerte = new float[4 * this.slots * platzJeSlot];
        werte = Puffer.speicher(g, konto, "swash Werte", 128);
        zelle = Puffer.speicher(g, konto, "swash Zelle", 16L * zellen);
        u = Puffer.speicher(g, konto, "swash U", 16L * zellen);
        u0 = Puffer.speicher(g, konto, "swash U0", 16L * zellen);
        steig = Puffer.speicher(g, konto, "swash Kanten", 48L * zellen);           // seit Swash 0.3: Kanten in x und y, Steigungen der Geschwindigkeit
        fx = Puffer.speicher(g, konto, "swash Fluss x", 16L * (s.nx + 1) * s.ny);
        fy = Puffer.speicher(g, konto, "swash Fluss y", 16L * s.nx * (s.ny + 1));
        theta = Puffer.speicher(g, konto, "swash Theta", 4L * zellen);
        spur = Puffer.speicher(g, konto, "swash Spur", 16L * zellen);
        bilanz = Puffer.speicher(g, konto, "swash Bilanz", 16L * zellen);
        antrieb = Puffer.speicher(g, konto, "swash Antrieb", 16L * this.slots * platzJeSlot);
        tempo = Puffer.geteilt(g, konto, "swash Tempo", 4L * s.ny);       // seit S6b-2 geteilt: der Lauf liest ohne zu warten
        saum = Puffer.geteilt(g, konto, "swash Saum", 4L * s.ny);         // seit S6b-3: Saum je Zeile für Prüfungen
        bild = Puffer.speicher(g, konto, "swash Bild", bildBytes(s));      // seit S6c-2: das Bild des Rechtecks für Gelände und Wasser
        KY = kachelY;
        kx = (s.nx + KX - 1) / KX;
        ky = (s.ny + KY - 1) / KY;
        kachel = Puffer.speicher(g, konto, "swash Kacheln", 8L * kx * ky);
        Kernel.Bindung[] b = new Kernel.Bindung[14];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kRand = Kernel.lade(g, PFAD + "swash_rand.comp", PUSH, null, b);
        kSteig = Kernel.lade(g, PFAD + "swash_steigung.comp", PUSH, null, b);
        kFluss = Kernel.lade(g, PFAD + "swash_fluss.comp", PUSH, null, b);
        kAbfluss = Kernel.lade(g, PFAD + "swash_abfluss.comp", PUSH, null, b);
        kSchritt = Kernel.lade(g, PFAD + "swash_schritt.comp", PUSH, null, b);
        kTempo = Kernel.lade(g, PFAD + "swash_tempo.comp", PUSH, null, b);
        Kernel.Bindung[] b15 = new Kernel.Bindung[15];
        Arrays.fill(b15, Kernel.Bindung.PUFFER);
        kStufe = Kernel.lade(g, PFAD + (KY == 8 ? "swash_stufe8.comp" : "swash_stufe.comp"), PUSH, null, b15);   // seit S6c-2b mit dem Bild (Bindung 14); seit S6e 16 × 16
        Object[] alle = {werte, zelle, u, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u0};
        sRand = kRand.satz(alle); sSteig = kSteig.satz(alle); sFluss = kFluss.satz(alle);
        sAbfluss = kAbfluss.satz(alle); sSchritt = kSchritt.satz(alle); sTempo = kTempo.satz(alle);
        // verschmolzen: Stufe 1 liest u (= U0) und schreibt u0, Stufe 2 liest u0 und U0 = u und schreibt u
        sRandB = kRand.satz(werte, zelle, u0, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u0);
        sStufe1 = kStufe.satz(werte, zelle, u, u, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u0, bild);
        sStufe2 = kStufe.satz(werte, zelle, u0, u, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u, bild);
        kSaum = Kernel.lade(g, PFAD + "swash_saum.comp", PUSH, null, b15);
        sSaum = kSaum.satz(werte, zelle, u, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u0, saum);
        kBild = Kernel.lade(g, PFAD + "swash_bild.comp", PUSH, null, b15);
        sBild = kBild.satz(werte, zelle, u, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, u0, bild);
        schreibeWerte();
    }

    /**
     * Seit S6b-2: ein neues Rechteck (Lage, Grund, Felder) mit derselben Größe und denselben Seiten; danach {@link #hoch}.
     * Die Kernel und Puffer bleiben.
     */
    public void wechsle(Swash neu) {
        if (neu.nx != s.nx || neu.ny != s.ny || neu.dx != s.dx) throw new IllegalArgumentException("SwashKarte.wechsle: andere Größe");
        for (int seite = 0; seite < 4; seite++)
            if ((neu.rand(seite) == Swash.Rand.ANTRIEB && neu.antrieb(seite) != null) != aOff[seite] >= 0 || neu.rand(seite) != s.rand(seite))
                throw new IllegalArgumentException("SwashKarte.wechsle: andere Seiten");
        s = neu;
        schreibeWerte();
    }

    /** Mit eigenem Gerät (für Prüfungen); schließt es mit. */
    public static SwashKarte mitGeraet(Swash s, int slots) {
        Geraet g = new Geraet();
        return new SwashKarte(g, g.konto("swash", 2L << 30), s, slots, true);
    }

    public Geraet geraet() { return g; }

    /** Seit S6e: Höhe der Kachel dieser Karte (16 oder 8). */
    public int kachelHoehe() { return KY; }

    /** Der Puffer des Antriebs (vec4 je Platz und Stand: Stand relativ zum Ruhewasser, Geschwindigkeit ins Gitter, längs, –). */
    public Puffer antriebPuffer() { return antrieb; }

    /** Platz im Stand für eine Randzelle (Seite, k von −G bis m + G − 1, Lage l = 1 … G); −1 ohne Antrieb an der Seite. */
    public int platz(int seite, int k, int l) { return aOff[seite] < 0 ? -1 : aOff[seite] + (k + G) * G + (l - 1); }

    private void schreibeWerte() {
        ByteBuffer p = ByteBuffer.allocateDirect(128).order(ByteOrder.nativeOrder());
        p.putInt(s.nx).putInt(s.ny).putInt(s.px);
        for (int seite = 0; seite < 4; seite++) p.putInt(switch (s.rand(seite)) { case WAND -> 0; case OFFEN -> 1; case ANTRIEB -> aOff[seite] >= 0 ? 2 : 0; });
        for (int seite = 0; seite < 4; seite++) p.putInt(Math.max(0, aOff[seite]));
        p.putInt(platzJeSlot).putInt(mMax).putInt(s.teilgitter ? 1 : 0).putInt(0).putInt(0);   // I_TEIL seit Swash 0.3
        p.putFloat((float) s.dx).putFloat((float) s.schwere).putFloat((float) s.hTrocken).putFloat((float) s.hGeschw).putFloat((float) s.vMax)
                .putFloat((float) s.hSpitze).putFloat((float) s.psi).putFloat((float) s.dTheta);
        while (p.position() < 128) p.putFloat(0);
        p.flip();
        werte.schreibe(p, 0);
    }

    /** Grund der Randzellen nach der Regel der Referenz (Wand und offen: gespiegelte Zelle; Antrieb: erste Zelle innen; Ecken nach S und N). */
    private double[] grundMitRand() {
        double[] bb = s.b.clone();
        for (int pass = 0; pass < 2; pass++)
            for (int seite = pass == 0 ? 0 : 2; seite < (pass == 0 ? 2 : 4); seite++) {
                boolean xs = seite == Swash.W || seite == Swash.E;
                int m = xs ? s.ny : s.nx;
                for (int k = xs ? 0 : -G; k < (xs ? m : m + G); k++)
                    for (int l = 1; l <= G; l++) {
                        int gi, gj, ii, jj, ai, aj;
                        if (seite == Swash.W) { gi = -l; gj = k; ii = l - 1; jj = k; ai = 0; aj = k; }
                        else if (seite == Swash.E) { gi = s.nx - 1 + l; gj = k; ii = s.nx - l; jj = k; ai = s.nx - 1; aj = k; }
                        else if (seite == Swash.S) { gi = k; gj = -l; ii = k; jj = l - 1; ai = k; aj = 0; }
                        else { gi = k; gj = s.ny - 1 + l; ii = k; jj = s.ny - l; ai = k; aj = s.ny - 1; }
                        ii = Math.max(0, Math.min(s.nx - 1, ii)); jj = Math.max(0, Math.min(s.ny - 1, jj));
                        ai = Math.max(0, Math.min(s.nx - 1, ai)); aj = Math.max(0, Math.min(s.ny - 1, aj));
                        boolean an = s.rand(seite) == Swash.Rand.ANTRIEB && s.antrieb(seite) != null;
                        bb[s.id(gi, gj)] = an ? s.b[s.id(ai, aj)] : s.b[s.id(ii, jj)];
                    }
            }
        return bb;
    }

    /** Bringt Grund, Felder, Zustand und Spuren der Referenz auf die Karte; die Bilanz zählt von hier an weiter. */
    public void hoch() {
        double[] bb = grundMitRand();
        float[] z = new float[4 * zellen], uu = new float[4 * zellen], sp = new float[4 * zellen], th = new float[zellen];
        for (int c = 0; c < zellen; c++) {
            boolean fels = Double.isNaN(bb[c]);
            z[4 * c] = fels ? FELS : (float) (bb[c] - s.bezug);
            z[4 * c + 1] = (float) s.n[c];
            z[4 * c + 2] = (float) s.kf[c];
            z[4 * c + 3] = s.gw[c] == Double.NEGATIVE_INFINITY ? NIE : (float) (s.gw[c] - s.bezug);
            uu[4 * c] = fels ? 0 : (float) s.h[c];
            uu[4 * c + 1] = fels ? 0 : (float) s.hu[c];
            uu[4 * c + 2] = fels ? 0 : (float) s.hv[c];
            sp[4 * c] = (float) s.sick[c];
            sp[4 * c + 1] = Double.isNaN(s.etaMax[c]) ? NIE : (float) (s.etaMax[c] - s.bezug);
            sp[4 * c + 2] = s.nassZeit[c] == Double.NEGATIVE_INFINITY ? NIE : (float) s.nassZeit[c];
            th[c] = 1;
        }
        zelle.schreibe(z);
        u.schreibe(uu);
        u0.schreibe(uu);
        int[] km = new int[2 * kx * ky];                                    // Bit 1 nass, 2 Sickerwasser, 4 beide Puffer gleich
        for (int j = 0; j < s.ny; j++)
            for (int i = 0; i < s.nx; i++) {
                int c = s.id(i, j), k = (j / KY) * kx + i / KX;
                km[k] |= 4;
                if (!Double.isNaN(bb[c]) && s.h[c] > s.hTrocken) km[k] |= 1;
                if (s.sick[c] > 0) km[k] |= 2;
            }
        System.arraycopy(km, 0, km, kx * ky, kx * ky);
        kachel.schreibe(km);
        stufen = 0;
        bildVoll = false;                                                    // seit S6c-2b: das nächste bild füllt
        spur.schreibe(sp);
        theta.schreibe(th);
        bilanz.nullen();
        bilanzLetzt = null;
        spurLetzt = null;
        steig.nullen();
        // seit S6b-3 (F1, Datei 71): das Tempo dieses Rechtecks gleich in den Puffer; vorher las das erste Bild nach dem
        // Setzen einen leeren Puffer oder das Tempo des vorigen Rechtecks (L4d: ein Bild mit zu wenig Schritten)
        float[] tz = new float[s.ny];
        for (int j = 0; j < s.ny; j++)
            for (int i = 0; i < s.nx; i++) {
                int c = s.id(i, j);
                double hh = s.h[c];
                if (Double.isNaN(bb[c]) || hh <= s.hTrocken) continue;
                double[] v = s.geschwindigkeit(i, j);
                tz[j] = (float) Math.max(tz[j], Math.max(Math.abs(v[0]), Math.abs(v[1])) + Math.sqrt(s.schwere * hh));
            }
        tempo.schreibe(tz);
        zeit = s.zeit();
        schritte = s.schritte();
        basisZufluss = s.zufluss();
        basisVersickert = s.versickert();
        basisAbgeschnitten = s.abgeschnitten();
    }

    /** Holt Zustand, Spuren und Bilanz von der Karte in die Referenz (wartet). */
    public void runter() {
        float[] uu = u.floats(4 * zellen), sp = spur.floats(4 * zellen), bl = bilanz.floats(4 * zellen);
        double zu = 0, ve = 0, cut = 0;
        for (int c = 0; c < zellen; c++) {
            s.h[c] = uu[4 * c];
            s.hu[c] = uu[4 * c + 1];
            s.hv[c] = uu[4 * c + 2];
            s.sick[c] = sp[4 * c];
            s.etaMax[c] = sp[4 * c + 1] <= -1e29f ? Double.NaN : sp[4 * c + 1] + s.bezug;
            s.nassZeit[c] = sp[4 * c + 2] <= -1e29f ? Double.NEGATIVE_INFINITY : sp[4 * c + 2];
            ve += sp[4 * c + 3];
            zu += bl[4 * c];
            cut += bl[4 * c + 1];
        }
        bilanzLetzt = bl;
        spurLetzt = sp;
        s.uebernehmen(zeit, schritte, basisZufluss + zu, basisVersickert + ve * s.dx * s.dx, basisAbgeschnitten + cut);
    }

    /** Füllt Stand {@code slot} des Antriebs zur Zeit t aus den Antrieben der Referenz (für Prüfungen; danach {@link #antriebHoch}). */
    public void antrieb(int slot, double t) {
        double[] w = new double[3];
        for (int seite = 0; seite < 4; seite++) {
            if (aOff[seite] < 0) continue;
            boolean xs = seite == Swash.W || seite == Swash.E;
            int m = xs ? s.ny : s.nx;
            Swash.Antrieb a = s.antrieb(seite);
            for (int k = -G; k < m + G; k++)
                for (int l = 1; l <= G; l++) {
                    int gi, gj;
                    if (seite == Swash.W) { gi = -l; gj = k; }
                    else if (seite == Swash.E) { gi = s.nx - 1 + l; gj = k; }
                    else if (seite == Swash.S) { gi = k; gj = -l; }
                    else { gi = k; gj = s.ny - 1 + l; }
                    a.welle(t, s.x(gi), s.y(gj), w);
                    int o = 4 * (slot * platzJeSlot + platz(seite, k, l));
                    antriebWerte[o] = (float) (w[0] - s.bezug);
                    antriebWerte[o + 1] = (float) w[1];
                    antriebWerte[o + 2] = (float) w[2];
                }
        }
    }

    /** Bringt alle Stände des Antriebs aus {@link #antrieb(int, double)} auf die Karte. */
    public void antriebHoch() { antrieb.schreibe(antriebWerte); }

    private ByteBuffer push(double dt, double t, double w, int stufe, int a, int b) {
        ByteBuffer p = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        p.putFloat((float) dt).putFloat((float) t).putFloat((float) w).putInt(stufe).putInt(a).putInt(b).putInt(0).putInt(0).flip();
        return p;
    }

    /** Ein Schritt dt in den Ablauf; Stufe 1 nimmt den Antrieb aus Stand slot1, Stufe 2 aus slot2. */
    public void schritt(Ablauf ab, double dt, int slot1, int slot2) { schritt(ab, dt, slot1, slot1, 0, slot2, slot2, 0); }

    /** Ein Schritt dt in den Ablauf; Stufe 1 nimmt mix(a1, b1, w1), Stufe 2 mix(a2, b2, w2) aus den Ständen des Antriebs. */
    public void schritt(Ablauf ab, double dt, int a1, int b1, double w1, int a2, int b2, double w2) {
        double tEnde = zeit + dt;
        if (verschmolzen) {
            for (int stufe = 1; stufe <= 2; stufe++) {
                ByteBuffer p = stufe == 1 ? push(dt, tEnde, w1, 1, a1, b1) : push(dt, tEnde, w2, 2, a2, b2);
                p.putInt(24, (int) (stufen++ % 2));
                // seit S6c-2b: die Zellen des Bilds im letzten Schritt eines Bilds mitschreiben
                boolean mitBild = bildVoll && bildImSchritt;
                p.putInt(28, mitBild ? 1 : 0);
                Puffer ein = stufe == 1 ? u : u0, aus = stufe == 1 ? u0 : u;
                ab.rechne("swash_rand", kRand, stufe == 1 ? sRand : sRandB, p, Kernel.gruppen(4 * (mMax + 2 * G) * G, 64), 1, 1, new Mittel[] {werte, zelle, antrieb, ein}, new Mittel[] {ein});
                ab.rechne("swash_stufe", kStufe, stufe == 1 ? sStufe1 : sStufe2, p, kx, ky, 1,
                        stufe == 1 ? (mitBild ? new Mittel[] {werte, zelle, u, spur, bilanz, kachel, bild} : new Mittel[] {werte, zelle, u, spur, bilanz, kachel}) : mitBild ? new Mittel[] {werte, zelle, u0, u, spur, bilanz, kachel, bild} : new Mittel[] {werte, zelle, u0, u, spur, bilanz, kachel},
                        mitBild ? new Mittel[] {aus, spur, bilanz, kachel, bild} : new Mittel[] {aus, spur, bilanz, kachel});
            }
            zeit = tEnde;
            schritte++;
            return;
        }
        for (int stufe = 1; stufe <= 2; stufe++) {
            ByteBuffer p = stufe == 1 ? push(dt, tEnde, w1, 1, a1, b1) : push(dt, tEnde, w2, 2, a2, b2);
            ab.rechne("swash_rand", kRand, sRand, p, Kernel.gruppen(4 * (mMax + 2 * G) * G, 64), 1, 1, new Mittel[] {werte, zelle, antrieb, u}, new Mittel[] {u});
            ab.rechne("swash_steigung", kSteig, sSteig, p, Kernel.gruppen(s.nx + 2, 16), Kernel.gruppen(s.ny + 2, 16), 1, new Mittel[] {werte, zelle, u}, new Mittel[] {steig});
            ab.rechne("swash_fluss", kFluss, sFluss, p, Kernel.gruppen(s.nx + 1, 16), Kernel.gruppen(s.ny + 1, 16), 1, new Mittel[] {werte, zelle, u, steig}, new Mittel[] {fx, fy});
            ab.rechne("swash_abfluss", kAbfluss, sAbfluss, p, Kernel.gruppen(s.nx, 16), Kernel.gruppen(s.ny, 16), 1, new Mittel[] {werte, zelle, u, fx, fy}, new Mittel[] {theta});
            ab.rechne("swash_schritt", kSchritt, sSchritt, p, Kernel.gruppen(s.nx, 16), Kernel.gruppen(s.ny, 16), 1,
                    new Mittel[] {werte, zelle, fx, fy, theta, steig, u, u0, spur, bilanz}, new Mittel[] {u, u0, spur, bilanz});
        }
        zeit = tEnde;
        schritte++;
    }

    /** Die größte Wellengeschwindigkeit je Zeile in den Ablauf ({@link #tempoLesen} danach). */
    public void tempo(Ablauf ab) {
        ab.rechne("swash_tempo", kTempo, sTempo, push(0, 0, 0, 0, 0, 0), s.ny, 1, 1, new Mittel[] {werte, u}, new Mittel[] {tempo});
    }

    /**
     * Seit S6b-3 (SF71 A): der Saum je Zeile nach dem Stand in {@code u} (nach den Schritten eines Bilds): der höchste Grund
     * unter einer Zelle mit mindestens 1 cm Wasser, relativ zum Ruhewasser; −1e30 ohne. Für Prüfungen, {@link #saumLesen}.
     */
    public void saum(Ablauf ab) { saum(ab, 0.01); }

    /** Seit Seegang Werkbuch 76 (SF82 A): wie {@link #saum(Ablauf)}, mit der Kontur (m Wasser) als Wasserlinie. */
    public void saum(Ablauf ab, double kontur) {
        ab.rechne("swash_saum", kSaum, sSaum, push(kontur, 0, 0, 0, 0, 0), s.ny, 1, 1, new Mittel[] {werte, zelle, u}, new Mittel[] {saum});
    }

    /**
     * Seit S6c-2 (Seegang Werkbuch 82, SF93 A): das Bild des Rechtecks nach dem Stand in {@code u} und den Spuren, für
     * Gelände und Wasser ({@code swash_bild.comp}): je Zelle (Index j · nx + i) vec4 (h, Stand relativ zum Ruhewasser oder
     * ≥ 1e29 für Fels, zuletzt nass in s der Karte oder −1e30 (seit S6c-2b absolut; vorher Sekunden seit nass), versickert
     * oberhalb der Front in m), danach je Zeile vec4 (Spülsaum: seit S6c-2b Index der landwärtigsten Zelle + 1 in den Bits von
     * x, 0 ohne; {@link #saumAusBild}; die Zelle, deren höchster Stand mindestens {@code kontur} über dem Grund lag).
     */
    public void bild(Ablauf ab, double kontur) {
        // seit S6c-2b (Seegang Werkbuch 83): nur nach dem Setzen (oder mit anderer Kontur) füllen; danach hält die Stufe die
        // Zellen nach (swash_stufe, bildZelle, im letzten Schritt) und hier wandert nur der Spülsaum. L4o: das Füllen je Bild
        // kostete 0,165 ms; L4p mit dem Saum aus allen Spuren jedes 4. Bild 0,038 ms; L4q mit Bits aus der Stufe 0,009 ms,
        // aber die Stufe +0,05 ms
        if (bildVoll && kontur == bildKontur && verschmolzen) { bildZeilen(ab); return; }   // der einzelne Weg schreibt das Bild nicht mit
        bildKontur = kontur;
        ab.rechne("swash_bild", kBild, sBild, push(kontur, zeit, 0, 0, 0, 0), s.ny, 1, 1, new Mittel[] {werte, zelle, u, spur}, new Mittel[] {bild});
        bildVoll = true;
    }

    /**
     * Seit S6c-2b: nur den Spülsaum je Zeile weiterschieben (ab der vorigen Grenze, solange der höchste Stand die Kontur
     * erreichte; der höchste Stand steigt nur). {@link #bild} ruft es je Bild.
     */
    public void bildZeilen(Ablauf ab) {
        ab.rechne("swash_bild", kBild, sBild, push(bildKontur, zeit, 0, 1, 0, 0), Kernel.gruppen(s.ny, 64), 1, 1, new Mittel[] {werte, zelle, spur, bild}, new Mittel[] {bild});
    }

    /** Seit S6c-2b: das Bild ganz neu füllen beim nächsten {@link #bild} (Prüfungen: Vergleich mit dem Weg je Bild). */
    public void bildNeu() { bildVoll = false; }

    /** Seit S6c-2: der Puffer des Bilds ({@link #bild}). */
    public Puffer bildPuffer() { return bild; }

    /** Seit S6c-2: Bytes des Bilds für ein Gitter wie {@code s}. */
    public static long bildBytes(Swash s) { return 16L * s.nx * s.ny + 16L * s.ny; }

    /** Seit S6c-2b: Spülsaum der Zeile aus dem Wert im Bild (m quer bis zur Mitte der Zelle; NaN ohne). */
    public static double saumAusBild(float wert, double dx) {
        int k = Float.floatToRawIntBits(wert);
        return k <= 0 ? Double.NaN : (k - 0.5) * dx;
    }

    /** Seit S6c-2: das Bild von der Karte (wartet; Prüfungen). */
    public float[] bildLesen() { return bild.floats(4 * s.nx * s.ny + 4 * s.ny); }

    /**
     * Seit S6c-2: dasselbe Bild auf dem Prozessor aus der Referenz (nach {@link #runter}), in float wie {@code swash_bild.comp};
     * {@code zeit} wie {@link #zeit}. Für die Prüfung „Bild gegen Löser“.
     */
    public static float[] bildReferenz(Swash s, double zeit, double kontur) {
        float[] r = new float[4 * s.nx * s.ny + 4 * s.ny];
        float t = (float) zeit, k = (float) kontur, dx = (float) s.dx;
        for (int j = 0; j < s.ny; j++) {
            int weit = -1;
            boolean frei = true;                                                // seit S6c-2b: zusammenhängend von der See
            for (int i = 0; i < s.nx; i++) {
                int c = s.id(i, j), o = 4 * (j * s.nx + i);
                boolean fe = Double.isNaN(s.b[c]);
                float b = fe ? FELS : (float) (s.b[c] - s.bezug);
                float h = fe ? 0f : (float) s.h[c];
                float em = Double.isNaN(s.etaMax[c]) ? NIE : (float) (s.etaMax[c] - s.bezug);
                float nz = s.nassZeit[c] == Double.NEGATIVE_INFINITY ? NIE : (float) s.nassZeit[c];
                r[o] = h;
                r[o + 1] = fe ? 1e29f : h + b;
                r[o + 2] = nz <= -1e29f ? -1e30f : nz;                                 // seit S6c-2b absolut
                r[o + 3] = (float) s.sick[c];
                if (frei && !fe && em > -1e29f && em - b >= k) weit = i;
                else frei = false;
            }
            r[4 * s.nx * s.ny + 4 * j] = Float.intBitsToFloat(weit + 1);              // seit S6c-2b: Index + 1 (0 ohne)
        }
        return r;
    }

    /** Der Saum je Zeile (m über dem Ruhewasser; NaN ohne Zelle mit 1 cm Wasser) aus dem letzten {@link #saum}. */
    public double[] saumLesen() {
        float[] f = saum.floats(s.ny);
        double[] d = new double[s.ny];
        for (int j = 0; j < s.ny; j++) d[j] = f[j] <= -1e29f ? Double.NaN : f[j];
        return d;
    }

    /**
     * Seit S6b-3 (SF70 A): Zufluss über eine Seite je Zeile (W, E) bzw. Spalte (S, N) seit {@link #hoch}, m³ (negativ:
     * hinaus), aus dem letzten {@link #runter}. In den Eckzellen steht der Fluss beider Seiten.
     */
    public double[] randZufluss(int seite) {
        boolean xs = seite == Swash.W || seite == Swash.E;
        int m = xs ? s.ny : s.nx;
        double[] r = new double[m];
        if (bilanzLetzt == null) return r;
        for (int k = 0; k < m; k++) {
            int c = seite == Swash.W ? s.id(0, k) : seite == Swash.E ? s.id(s.nx - 1, k) : seite == Swash.S ? s.id(k, 0) : s.id(k, s.ny - 1);
            r[k] = bilanzLetzt[4 * c];
        }
        return r;
    }

    /** Seit Datei 73: versickertes Wasser der Zelle (i, j) seit {@link #hoch} (m³), aus dem letzten {@link #runter}. */
    public double versickert(int i, int j) { return spurLetzt == null ? 0 : spurLetzt[4 * s.id(i, j) + 3] * s.dx * s.dx; }

    /** Die größte Wellengeschwindigkeit im Gitter aus dem letzten {@link #tempo} (geteilter Puffer: liest, was zuletzt fertig wurde, ohne zu warten). */
    public double tempoLesen() {
        float[] t = tempo.floats(s.ny);
        double m = 0;
        for (float v : t) m = Math.max(m, v);
        return m;
    }

    /** Seit S6b-2b: die größte Wellengeschwindigkeit je Zeile (m/s) aus dem zuletzt fertigen {@link #tempo} (Prüfungen). */
    public float[] tempoZeilen() { return tempo.floats(s.ny); }

    /**
     * Seit S6b-3b (Datei 73): Zeile j des Zustands (h, hu, hv je Zelle i = 0 … nx − 1, Richtung der Referenz) aus
     * {@code u}, ohne den ganzen Zustand zu holen (Diagnose; die Schritte müssen fertig sein).
     */
    public float[] zeileLesen(int j) {
        java.nio.ByteBuffer bb = org.lwjgl.system.MemoryUtil.memAlloc(16 * s.nx).order(java.nio.ByteOrder.nativeOrder());
        try {
            u.lies(bb, 16L * s.id(0, j));
            float[] r = new float[3 * s.nx];
            for (int i = 0; i < s.nx; i++) { r[3 * i] = bb.getFloat(16 * i); r[3 * i + 1] = bb.getFloat(16 * i + 4); r[3 * i + 2] = bb.getFloat(16 * i + 8); }
            return r;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(bb);
        }
    }

    /** Zeit der Karte (s) und Schritte seit dem Füllen der Referenz. */
    public double zeit() { return zeit; }

    public long schritte() { return schritte; }

    @Override public void close() {
        g.ruhe();
        for (Kernel k : new Kernel[] {kRand, kSteig, kFluss, kAbfluss, kSchritt, kTempo, kStufe, kSaum, kBild}) k.close();
        for (Puffer p : new Puffer[] {werte, zelle, u, u0, steig, fx, fy, theta, spur, bilanz, antrieb, tempo, kachel, saum, bild}) p.close();
        if (eigenesGeraet) g.close();
    }
}
