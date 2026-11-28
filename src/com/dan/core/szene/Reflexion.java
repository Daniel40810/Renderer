package com.dan.core.szene;

/**
 * Die Phase der Reflexion (Seegang Werkbuch, S5b-6; Plan Datei 45, B69; seit S5b-6b Datei 56/57). Das Nahfeld gibt an
 * Flächen zu trockenen Zellen K_r² der ausfließenden Energie in die gespiegelte Richtung zurück (an Ost-West-Flächen
 * nach {@link #spiegelX}, an Nord-Süd-Flächen nach {@link #spiegelY}). Die Synthese gab jeder Stützstelle eine eigene
 * Hülle und eine eigene Phase; die reflektierte Welle war damit eine andere Wellengruppe als die einlaufende, vor einer
 * Wand stand keine Welle. Hier, in drei Schritten:
 * <ol>
 * <li><b>Partner</b> ({@link #partner}): je Stützstelle die einlaufende, die am meisten in sie hinein reflektiert wird
 * – über die Flächen nasser Zellen zu trockenen, gewichtet mit ihrer Energie und dem Anteil ihrer Richtung auf die
 * Fläche zu. Partner wird nur, wer selbst mehr in die Wände läuft als die reflektierte; so gibt es keine Kreise. Seit
 * S5b-6b zählen auch die Wände des feinen Gitters (Energie davor aus der groben Stufe): Im Landunter stehen die Warften
 * oft nur im feinen Gitter über Wasser.</li>
 * <li><b>Gemeinsame Hülle</b> ({@link #teilen}): die reflektierte Stützstelle liest die Hülle ihres Partners
 * ({@link Profilpuffer#teilen}); dazu ihre Richtung genau gespiegelt ({@link #richtungen}), sonst liefe der
 * Unterschied der Phasen längs der Wand.</li>
 * <li><b>Bauch an der Wand</b> ({@link #wand}, seit S5b-6b; vorher eine Konstante je Paar): je gerades Wandstück der
 * Versatz von Φ und T der reflektierten Stützstelle, der dort Φ_r = Φ_e und T_r = T_e macht; der Versatz läuft mit der
 * reflektierten Welle längs ihrer Strahlen vom Stück weg (konstant längs des Strahls, quer dazu geglättet über
 * {@link #QUER} Wellenlängen); seit S5b-6b2 je Fläche ihr eigener Versatz (an der echten Küste ändert er sich längs
 * des Stücks). Eine Konstante für alle Wände trifft Wände in verschiedener Lage nicht (Unterschied
 * 2 k_x Δx; Datei 56, U1); ein Versatz, der längs des Strahls abklingt, ändert die Wellenzahl (Datei 57).</li>
 * </ol>
 * Gewicht einer Wandfläche seit S5b-6b √(E_e E_r) · cos (Winkel der einlaufenden zur Normalen): Es zählt, wo das
 * Nahfeld wirklich zurückwirft (an flachen Rändern ist E_r fast null; Datei 56, U2).
 * <p>
 * Strahlen, die an keinem geraden Stück beginnen (Treppe, kein Wandstück, Gitterrand), tragen die eine Konstante je
 * Paar über alle seine Wandflächen, wie in S5b-6 [G]. Im feinen Feld, das seinen Rand vom groben nimmt, wird der
 * Versatz über die äußeren {@link #RAMPE_ZELLEN} Zellen eingeführt [G] (bis S5b-6b: bis zur Mitte).
 * <p>
 * <b>Gerades Stück</b> [G]: eine Folge von Flächen derselben Achse und Seite in einer Spalte (Zeile), lückenlos, aus
 * mindestens {@link #STUECK_ZELLEN} Zellen und mindestens {@link #STUECK_L} Wellenlängen lang. Kürzere Folgen
 * (Treppe einer schrägen Küste) bleiben frei: Dort ist die Spiegelung an der Achse des Gitters nicht die wahre
 * Reflexion; zwänge man die Phase, würde |∇Φ_r| längs der Treppe zu null.
 * <p>
 * Die Partner gelten für eine gebaute Fläche fest, damit nichts mit dem Wasserstand springt [G].
 */
public final class Reflexion {
    public static final String VERSION = "0.3";

    /** Gerades Stück: mindestens so viele Zellen und so viele Wellenlängen [G]. */
    public static final int STUECK_ZELLEN = 3;
    public static final double STUECK_L = 0.5;
    /** Breite der Glättung quer zu den Strahlen (σ der Gauß-Glocke), Anteil der Wellenlänge [G]. */
    public static double QUER = 0.5;
    /** Paare mit weniger Wandgewicht als dieser Anteil des stärksten Paars der Stufe bleiben frei [G]. */
    public static final double PAAR_MIN = 1e-4;

    public final int nb, nd;
    /** Je Stützstelle bq = b · nd + q: der Partner (bq der einlaufenden, −1: keiner) und die Achse (0: spiegelX, 1: spiegelY). */
    public final int[] partner, achse;
    /** Je Stützstelle: Fluss in die Wände (Gewicht, nur zum Ordnen und für Berichte). */
    public final double[] fluss;
    /** Reihenfolge der Stützstellen, die stärksten zuerst (Partner vor reflektierter). */
    final int[] ordnung;
    public int paare;
    /** Seit S5b-6b: Flächen nass zu trocken im groben und im feinen Gitter, die {@link #partner} gezählt hat. */
    public int flaechenGrob, flaechenFein;

    public static int spiegelX(int q, int nd) { return Math.floorMod(nd / 2 - q, nd); }

    public static int spiegelY(int q, int nd) { return Math.floorMod(-q, nd); }

    private Reflexion(int nb, int nd) {
        this.nb = nb; this.nd = nd;
        partner = new int[nb * nd];
        achse = new int[nb * nd];
        fluss = new double[nb * nd];
        ordnung = new int[nb * nd];
        java.util.Arrays.fill(partner, -1);
    }

    /** Keine Paare (zum Ausschalten). */
    public static Reflexion keine(int nb, int nd) {
        Reflexion r = new Reflexion(nb, nd);
        for (int i = 0; i < nb * nd; i++) r.ordnung[i] = i;
        return r;
    }

    /** Wie {@link #partner(Oberflaeche.Stufe, Oberflaeche.Stufe)}, nur mit den Wänden der Stufe. */
    public static Reflexion partner(Oberflaeche.Stufe st) {
        return partner(st, null);
    }

    /**
     * Die Partner aus den Wänden der Stufe {@code grob} und, seit S5b-6b, des Gitters {@code fein} (nur dessen Tiefe
     * zählt; null: ohne): je Fläche einer nassen Zelle c zu einer trockenen (Tiefe unter {@link Phasenfeld#H_MIN}) und
     * je Stützstelle mit Laufrichtung auf die Fläche zu das Gewicht E_bq · cos(Winkel zur Normalen) · dx. E im feinen
     * Gitter aus der groben Stufe am Ort (bilinear über die nassen Zellen). Dieselben Richtungen wie das Nahfeld
     * (q · 2π / nd).
     */
    public static Reflexion partner(Oberflaeche.Stufe st, Oberflaeche.Stufe fein) {
        int nb = st.nb, nd = st.nd, n = nb * nd;
        Reflexion r = new Reflexion(nb, nd);
        double[][] f = new double[2][n];                                 // Fluss je Achse
        double dth = 2 * Math.PI / nd;
        double[] cs = new double[nd], sn = new double[nd];
        for (int q = 0; q < nd; q++) { cs[q] = Math.cos(q * dth); sn[q] = Math.sin(q * dth); }
        double[] ez = new double[n];
        for (int stufe = 0; stufe < (fein != null ? 2 : 1); stufe++) {
            Oberflaeche.Stufe g = stufe == 0 ? st : fein;
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    int c = j * g.nx + i;
                    if (g.h[c] < Phasenfeld.H_MIN) continue;
                    boolean geladen = false;
                    for (int s = 0; s < 4; s++) {
                        int a = s / 2, sg = s % 2 == 0 ? -1 : 1, ii = i + (a == 0 ? sg : 0), jj = j + (a == 1 ? sg : 0);
                        if (ii < 0 || jj < 0 || ii >= g.nx || jj >= g.ny || g.h[jj * g.nx + ii] >= Phasenfeld.H_MIN) continue;
                        if (stufe == 0) r.flaechenGrob++; else r.flaechenFein++;
                        if (!geladen) {
                            if (stufe == 0) System.arraycopy(st.e, c * n, ez, 0, n);
                            else energieAm(st, g.x0 + (i + 0.5) * g.dx, g.y0 + (j + 0.5) * g.dx, ez);
                            geladen = true;
                        }
                        for (int b = 0; b < nb; b++)
                            for (int q = 0; q < nd; q++) {
                                double comp = sg * (a == 0 ? cs[q] : sn[q]);
                                if (comp <= 1e-9) continue;
                                f[a][b * nd + q] += ez[b * nd + q] * comp * g.dx;
                            }
                    }
                }
        }
        double fmax = 0;
        for (int bq = 0; bq < n; bq++) { r.fluss[bq] = f[0][bq] + f[1][bq]; fmax = Math.max(fmax, r.fluss[bq]); }
        Integer[] o = new Integer[n];
        for (int i = 0; i < n; i++) o[i] = i;
        java.util.Arrays.sort(o, (u, v) -> u.equals(v) ? 0 : r.fluss[u] != r.fluss[v] ? Double.compare(r.fluss[v], r.fluss[u]) : Integer.compare(u, v));
        for (int i = 0; i < n; i++) r.ordnung[i] = o[i];
        if (!(fmax > 0)) return r;
        for (int b = 0; b < nb; b++)
            for (int qs = 0; qs < nd; qs++) {
                int bs = b * nd + qs, qx = b * nd + spiegelX(qs, nd), qy = b * nd + spiegelY(qs, nd);
                double ex = f[0][qx], ey = f[1][qy];                     // was über x- bzw. y-Flächen in qs reflektiert wird
                int a = ex >= ey ? 0 : 1, bp = a == 0 ? qx : qy;
                double ein = Math.max(ex, ey);
                if (!(ein > 1e-9 * fmax) || bp == bs || !(r.fluss[bp] > r.fluss[bs])) continue;
                r.partner[bs] = bp;
                r.achse[bs] = a;
                r.paare++;
            }
        return r;
    }

    /** Varianz je Stützstelle der Stufe am Ort (x, y), bilinear über die nassen Zellen (keine: 0). */
    static void energieAm(Oberflaeche.Stufe st, double x, double y, double[] out) {
        int n = st.nb * st.nd;
        java.util.Arrays.fill(out, 0);
        double fx = (x - st.x0) / st.dx - 0.5, fy = (y - st.y0) / st.dx - 0.5;
        fx = Math.max(0, Math.min(st.nx - 1.000001, fx));
        fy = Math.max(0, Math.min(st.ny - 1.000001, fy));
        int i = (int) fx, j = (int) fy;
        double ax = fx - i, ay = fy - j, sw = 0;
        for (int k = 0; k < 4; k++) {
            int ii = Math.min(st.nx - 1, i + (k & 1)), jj = Math.min(st.ny - 1, j + (k >> 1)), c = jj * st.nx + ii;
            if (st.h[c] < Phasenfeld.H_MIN) continue;
            double w = ((k & 1) == 1 ? ax : 1 - ax) * ((k >> 1) == 1 ? ay : 1 - ay);
            if (!(w > 0)) continue;
            for (int m = 0; m < n; m++) out[m] += w * st.e[c * n + m];
            sw += w;
        }
        if (sw > 0) for (int m = 0; m < n; m++) out[m] /= sw;
    }

    /** Die Richtungen der reflektierten Stützstellen genau gespiegelt zu denen ihres Partners (in {@code ri}, Länge nb · nd). */
    public void richtungen(double[] ri) {
        double dth = 2 * Math.PI / nd;
        for (int bq : ordnung) {
            int p = partner[bq];
            if (p < 0) continue;
            int q = bq % nd, qp = p % nd;
            ri[bq] = q * dth - (ri[p] - qp * dth);                       // der Versatz gespiegelt (Spiegeln ist eine Involution)
        }
    }

    /** Die reflektierten Stützstellen lesen die Hülle ihres Partners (über Ketten bis zur Wurzel). */
    public void teilen(Profilpuffer pp) {
        for (int bq : ordnung) if (partner[bq] >= 0) pp.teilen(partner[bq], bq);
    }

    /**
     * Was {@link #wand} an einer Stufe gefunden und erreicht hat. Kohärenz über alle Wandflächen der Paare, Gewicht
     * w = √(E_e E_r) · cos: <b>vorher</b> |Σ w e^{iρ}| / Σ w je Paar (das Beste, was eine Konstante je Paar erreicht),
     * <b>nachher</b> Σ w cos ρ / Σ w (1: an allen Flächen im Bauch), ρ der Abstand vom Bauch an der Fläche. Summen über
     * die Paare.
     */
    public static final class Bilanz {
        public int paareMitWand, paareGeloest, flaechen, stuecke;
        public double vorher = Double.NaN, nachher = Double.NaN, anteilStuecke = Double.NaN, nachherStuecke = Double.NaN;

        public String text() {
            if (paareMitWand == 0) return "keine Wand mit Reflexion";
            return String.format(java.util.Locale.GERMANY, "%d Paare an %d Flächen, %d gerade Stücke (%.0f %% des Gewichts); Kohärenz vorher %.2f, nachher %.2f (in den Stücken %.2f)",
                    paareMitWand, flaechen, stuecke, 100 * anteilStuecke, vorher, nachher, nachherStuecke);
        }
    }

    /** Zuletzt in {@link #wand}: grob und fein (zur Auskunft). */
    public Bilanz bilanzGrob, bilanzFein;

    /**
     * Seit S5b-6b: setzt den Bauch an den Wänden der Stufe {@code st} im Phasenfeld {@code p} (siehe Klasse). Paare in
     * Stufen der Kette (Partner zuerst), je Stufe parallel. {@code fein}: für den Bericht ({@link #bilanzFein}).
     */
    public Bilanz wand(Oberflaeche.Stufe st, Phasenfeld p, boolean fein) {
        int n = nb * nd;
        final double[] rampe = fein ? rampe(p) : null;
        int[] tiefe = new int[n];
        int tmax = 0;
        for (int bq : ordnung) if (partner[bq] >= 0) { tiefe[bq] = tiefe[partner[bq]] + 1; tmax = Math.max(tmax, tiefe[bq]); }
        Paar[] ps = new Paar[n];
        double wmax = 0;
        for (int bq = 0; bq < n; bq++) if (partner[bq] >= 0) { ps[bq] = flaechen(st, p, bq); wmax = Math.max(wmax, ps[bq].summe); }
        Bilanz bi = new Bilanz();
        final double grenze = PAAR_MIN * wmax;
        int[] schritte = new int[1];                                     // nur als Sperre
        for (int t = 1; t <= tmax; t++) {
            final int tt = t;
            java.util.stream.IntStream.range(0, n).parallel().filter(bq -> ps[bq] != null && tiefe[bq] == tt && ps[bq].summe > grenze).forEach(bq -> {
                Paar pa = tt == 1 ? ps[bq] : flaechen(st, p, bq);        // in einer Kette ist der Partner eben neu gelöst
                tragen(st, p, bq, pa, rampe);
                synchronized (schritte) { bi.paareGeloest++; }
            });
        }
        double sv = 0, sn = 0, sw = 0, sst = 0, sns = 0;
        for (int bq = 0; bq < n; bq++) {
            if (ps[bq] == null || !(ps[bq].summe > grenze)) continue;
            Paar nach = flaechen(st, p, bq);
            bi.paareMitWand++;
            bi.flaechen += nach.zahl;
            bi.stuecke += nach.stuecke;
            sv += ps[bq].koh;                                             // |Σ w e^{iρ}| vorher
            for (int k = 0; k < nach.zahl; k++) { double x = nach.w[k] * Math.cos(nach.rho[k]); sn += x; if (nach.stueck[k] >= 0) sns += x; }
            sw += nach.summe;
            sst += nach.imStueck;
        }
        if (sw > 0) { bi.vorher = sv / sw; bi.nachher = sn / sw; bi.anteilStuecke = sst / sw; }
        if (sst > 0) bi.nachherStuecke = sns / sst;
        if (fein) bilanzFein = bi; else bilanzGrob = bi;
        return bi;
    }

    /** Die Wandflächen eines Paars in einer Stufe mit Gewicht, Abstand vom Bauch und Stück. */
    static final class Paar {
        int zahl, stuecke;
        int[] zelle = new int[16];
        double[] w = new double[16], rho = new double[16], tau = new double[16];
        int[] stueck = new int[16];                                       // −1: in keinem geraden Stück
        double[] abgewickelt;                                             // seit S5b-6b2: ρ längs des Stücks stetig
        /** Seit S5b-6b2 (Spiegelbild): Seite der trockenen Zelle (±1), Schritt der Phase und der Laufzeit von der Zellmitte zur Fläche, mittlere Wellenzahl, Zellen bis zum nächsten Ende des Stücks. */
        int[] seite = new int[16], ende = new int[16];
        double[] halbP = new double[16], halbT = new double[16], kq = new double[16];
        double summe, imStueck, koh;
        double[] mitte;                                                   // je Stück: arg Σ w e^{iρ}

        void dazu(int c, double ww, double r, double t) {
            if (zahl == zelle.length) {
                int m = 2 * zahl;
                zelle = java.util.Arrays.copyOf(zelle, m); w = java.util.Arrays.copyOf(w, m); rho = java.util.Arrays.copyOf(rho, m);
                tau = java.util.Arrays.copyOf(tau, m); stueck = java.util.Arrays.copyOf(stueck, m);
                seite = java.util.Arrays.copyOf(seite, m); ende = java.util.Arrays.copyOf(ende, m);
                halbP = java.util.Arrays.copyOf(halbP, m); halbT = java.util.Arrays.copyOf(halbT, m); kq = java.util.Arrays.copyOf(kq, m);
            }
            zelle[zahl] = c; w[zahl] = ww; rho[zahl] = r; tau[zahl] = t; stueck[zahl] = -1;
            zahl++;
            summe += ww;
        }
    }

    /**
     * Die Flächen des Paars bq in der Stufe: je nasse Zelle c mit trockenem Nachbarn auf der Achse des Paars, auf den
     * die einlaufende Stützstelle zuläuft (Richtung des Nahfelds). Bedingung für den Bauch an der Fläche, ausgedrückt in
     * der Mitte der nassen Zelle: Φ_r(c) = Φ_e(c) + ½ (k_e + k_r) s_e Δx, T_r(c) = T_e(c) + ½ (1/c_e + 1/c_r) s_e Δx,
     * s_e = ± cos θ_e bzw. ± sin θ_e (Phase der einlaufenden zur Fläche), k und c wie {@link Phasenfeld#gradient}.
     * ρ = wrap(Φ_e + … − Φ_r), τ = T_e + … − T_r.
     */
    Paar flaechen(Oberflaeche.Stufe st, Phasenfeld p, int bq) {
        int pq = partner[bq], a = achse[bq], b = bq / nd, qe = pq % nd, qr = bq % nd;
        double dth = 2 * Math.PI / nd;
        double the = p.richtung[pq];
        double[] oe = new double[6], or = new double[6];
        Paar pa = new Paar();
        int[] linie = new int[16], lauf = new int[16];
        for (int j = 0; j < st.ny; j++)
            for (int i = 0; i < st.nx; i++) {
                int c = j * st.nx + i;
                if (st.h[c] < Phasenfeld.H_MIN) continue;
                for (int sg = -1; sg <= 1; sg += 2) {
                    int ii = i + (a == 0 ? sg : 0), jj = j + (a == 1 ? sg : 0);
                    if (ii < 0 || jj < 0 || ii >= st.nx || jj >= st.ny || st.h[jj * st.nx + ii] >= Phasenfeld.H_MIN) continue;
                    double comp = sg * (a == 0 ? Math.cos(qe * dth) : Math.sin(qe * dth));
                    if (comp <= 1e-9) continue;
                    double ee = st.e[(c * nb + b) * nd + qe], er = st.e[(c * nb + b) * nd + qr];
                    double w = Math.sqrt(Math.max(0, ee) * Math.max(0, er)) * comp;
                    if (!(w > 0)) continue;
                    double se = sg * (a == 0 ? Math.cos(the) : Math.sin(the));
                    double[] ge = p.gradient(pq, c), gr = p.gradient(bq, c);
                    double x = p.x(i), y = p.y(j);
                    p.wert(pq, x, y, oe);
                    p.wert(bq, x, y, or);
                    double rho = wrap(oe[0] + 0.5 * (ge[0] + gr[0]) * se * st.dx - or[0]);
                    double tau = oe[1] + 0.5 * (ge[1] + gr[1]) * se * st.dx - or[1];
                    if (pa.zahl == linie.length) { linie = java.util.Arrays.copyOf(linie, 2 * pa.zahl); lauf = java.util.Arrays.copyOf(lauf, 2 * pa.zahl); }
                    linie[pa.zahl] = (sg + 1) / 2 * (a == 0 ? st.nx : st.ny) + (a == 0 ? i : j);   // Seite und Spalte (Zeile)
                    lauf[pa.zahl] = a == 0 ? j : i;
                    pa.dazu(c, w, rho, tau);
                    int f = pa.zahl - 1;
                    pa.seite[f] = sg;
                    pa.halbP[f] = 0.5 * (ge[0] + gr[0]) * se * st.dx * 0.5;     // Zellmitte → Fläche: halbe Zelle
                    pa.halbT[f] = 0.5 * (ge[1] + gr[1]) * se * st.dx * 0.5;
                    pa.kq[f] = 0.5 * (ge[0] + gr[0]);
                }
            }
        double sr = 0, si = 0;
        for (int k = 0; k < pa.zahl; k++) { sr += pa.w[k] * Math.cos(pa.rho[k]); si += pa.w[k] * Math.sin(pa.rho[k]); }
        pa.koh = Math.hypot(sr, si);
        // gerade Stücke: gleiche Linie, lückenlos längs des Laufs
        Integer[] o = new Integer[pa.zahl];
        for (int k = 0; k < pa.zahl; k++) o[k] = k;
        final int[] li = linie, la = lauf;
        java.util.Arrays.sort(o, (u, v) -> li[u] != li[v] ? Integer.compare(li[u], li[v]) : Integer.compare(la[u], la[v]));
        java.util.List<double[]> mitten = new java.util.ArrayList<>();
        pa.abgewickelt = new double[pa.zahl];
        int k0 = 0;
        while (k0 < pa.zahl) {
            int k1 = k0 + 1;
            while (k1 < pa.zahl && li[o[k1]] == li[o[k0]] && la[o[k1]] == la[o[k1 - 1]] + 1) k1++;
            int m = k1 - k0;
            double ks = 0;
            for (int k = k0; k < k1; k++) ks += p.gradient(bq, pa.zelle[o[k]])[0];
            double lw = 2 * Math.PI / (ks / m);
            if (m >= STUECK_ZELLEN && m * st.dx >= STUECK_L * lw) {
                double ar = 0, ai = 0, sw = 0, su = 0;
                for (int k = k0; k < k1; k++) {
                    int f = o[k];
                    pa.stueck[f] = pa.stuecke; ar += pa.w[f] * Math.cos(pa.rho[f]); ai += pa.w[f] * Math.sin(pa.rho[f]); pa.imStueck += pa.w[f];
                    pa.abgewickelt[f] = k == k0 ? pa.rho[f] : pa.abgewickelt[o[k - 1]] + wrap(pa.rho[f] - pa.rho[o[k - 1]]);
                    pa.ende[f] = Math.min(k - k0, k1 - 1 - k);
                    su += pa.w[f] * pa.abgewickelt[f]; sw += pa.w[f];
                }
                double mi = Math.atan2(ai, ar), n = Math.rint((mi - su / sw) / (2 * Math.PI));
                for (int k = k0; k < k1; k++) pa.abgewickelt[o[k]] += 2 * Math.PI * n;   // Mittel des Stücks nahe seinem Mittel auf dem Kreis
                mitten.add(new double[] {mi});
                pa.stuecke++;
            }
            k0 = k1;
        }
        pa.mitte = new double[pa.stuecke];
        for (int s = 0; s < pa.stuecke; s++) pa.mitte[s] = mitten.get(s)[0];
        return pa;
    }

    /**
     * Trägt den Versatz der geraden Stücke des Paars bq längs der Strahlen der reflektierten Stützstelle ins Feld: je
     * Zelle {@code STRAHLEN} parallele Strahlen rückwärts (gegen die Laufrichtung der reflektierten), quer versetzt um
     * u = −2σ … 2σ mit Gewicht exp(−u²/2σ²), σ = {@link #QUER} L. Ein Strahl, der durch eine Fläche eines geraden Stücks
     * in die trockene Zelle tritt, trägt den Versatz dieser Fläche; jeder andere, der eine trockene Zelle oder den Rand
     * erreicht, die eine Konstante über alle Wandflächen des Paars (wie S5b-6); Strahlen, die außerhalb oder im
     * Trockenen beginnen, zählen nicht. Versatz von δΦ je Fläche (seit S5b-6b2; vorher ein Mittel je Stück, Datei 58,
     * C2): ihr Abstand vom Bauch ρ, längs des Stücks stetig abgewickelt, das Stück im Mittel auf die Konstante gewickelt
     * (Übergänge springen höchstens π); von δT: ihr τ. Trockene Zellen aus den nassen Nachbarn. Ohne gerades Stück: nur
     * die Konstante.
     */
    void tragen(Oberflaeche.Stufe st, Phasenfeld p, int bq, Paar pa, double[] rampe) {
        int nc = p.nc, nx = st.nx, ny = st.ny;
        int ns = pa.stuecke;
        double[] uP = new double[ns], wP = new double[ns];
        double gr = 0, gi = 0, gt = 0, gw = 0;
        int[] flaecheAn = new int[nc];
        java.util.Arrays.fill(flaecheAn, -1);
        boolean stueck = false;
        for (int k = 0; k < pa.zahl; k++) {
            gr += pa.w[k] * Math.cos(pa.rho[k]); gi += pa.w[k] * Math.sin(pa.rho[k]);   // das Beste mit einer Konstante (S5b-6)
            gt += pa.w[k] * pa.tau[k]; gw += pa.w[k];
            int s = pa.stueck[k];
            if (s < 0) continue;
            flaecheAn[pa.zelle[k]] = k;
            uP[s] += pa.w[k] * pa.abgewickelt[k];
            wP[s] += pa.w[k];
            stueck = true;
        }
        if (!(gw > 0)) return;
        double mG = Math.atan2(gi, gr), tG = gt / gw;
        if (!stueck) {                                                    // nur die Konstante
            for (int c = 0; c < nc; c++) {
                double g = rampe != null ? rampe[c] : 1;
                p.dPhi[bq * nc + c] += g * mG;
                p.dT[bq * nc + c] += g * tG;
            }
            return;
        }
        // seit S5b-6b2: je Fläche ihr eigener Abstand vom Bauch, längs des Stücks stetig, das Stück auf die Konstante gewickelt
        double[] oF = new double[pa.zahl];
        for (int k = 0; k < pa.zahl; k++) {
            int s = pa.stueck[k];
            if (s < 0) continue;
            oF[k] = pa.abgewickelt[k] + 2 * Math.PI * Math.rint((mG - uP[s] / wP[s]) / (2 * Math.PI));
        }
        int a = achse[bq];
        double th = p.richtung[bq], bx = -Math.cos(th), by = -Math.sin(th), qx = -by, qy = bx;
        double ks = 0, kw = 0;
        for (int k = 0; k < pa.zahl; k++) if (pa.stueck[k] >= 0) { ks += pa.w[k] * p.gradient(bq, pa.zelle[k])[0]; kw += pa.w[k]; }
        double sigma = QUER * 2 * Math.PI / (ks / kw);
        double[] dp = new double[nc], dt = new double[nc];
        boolean[] nass = new boolean[nc];
        for (int c = 0; c < nc; c++) nass[c] = st.h[c] >= Phasenfeld.H_MIN;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (!nass[c]) continue;
                double sw = 0, sp = 0, stt = 0;
                for (int m = -STRAHLEN / 2; m <= STRAHLEN / 2; m++) {
                    double u = m * 4.0 * sigma / (STRAHLEN - 1), g = Math.exp(-0.5 * u * u / (sigma * sigma));
                    double x = (i + 0.5) + u * qx / st.dx, y = (j + 0.5) + u * qy / st.dx;   // in Zellen
                    int f = strahl(st, nass, flaecheAn, a, x, y, bx, by);
                    if (f == -2) continue;                                 // beginnt außerhalb oder im Trockenen
                    sw += g;
                    sp += g * (f >= 0 ? oF[f] : mG);
                    stt += g * (f >= 0 ? pa.tau[f] : tG);
                }
                if (sw > 0) { dp[c] = sp / sw; dt[c] = stt / sw; }
                else { dp[c] = mG; dt[c] = tG; }
            }
        Phasenfeld.fuellen(nx, ny, nass, dp);
        Phasenfeld.fuellen(nx, ny, nass, dt);
        for (int c = 0; c < nc; c++) {
            double g = rampe != null ? rampe[c] : 1;
            p.dPhi[bq * nc + c] += g * dp[c];
            p.dT[bq * nc + c] += g * dt[c];
        }
        spiegeln(st, p, bq, pa, nass, rampe);
    }

    /** Seit S5b-6b2: das Spiegelbild gilt ganz bis {@link #SPIEGEL_VOLL} Wellenlängen vor der Fläche, ist ab {@link #SPIEGEL_AUS} aus [G]. */
    public static final double SPIEGEL_VOLL = 1, SPIEGEL_AUS = 2;

    /**
     * Seit S5b-6b2 (Datei 59): vor den Flächen gerader Stücke die reflektierte als Spiegelbild der einlaufenden,
     * Φ_r(x) = 2 Φ_e(F) − Φ_e(x) und T_r(x) = 2 T_e(F) − T_e(x), F der Fußpunkt auf der Fläche in derselben Zeile
     * (Spalte). Für die ebene Welle ist das bei jedem Winkel die gespiegelte Welle; dann ist Φ_e − Φ_r =
     * 2 (Φ_e(x) − Φ_e(F)), der Bauch liegt an der Fläche und das Muster läuft mit dem Abstand nicht weg (die eigenen
     * Felder von einlaufender und reflektierter sind nicht spiegelgleich, Datei 59). Gewicht 1 bis {@link #SPIEGEL_VOLL}
     * L vor der Fläche, glatt auf 0 bis {@link #SPIEGEL_AUS} L, an den Enden des Stücks halb, im feinen Feld mal Rampe;
     * dazwischen bleibt das Feld der Strahlen (Unterschied gewickelt).
     */
    void spiegeln(Oberflaeche.Stufe st, Phasenfeld p, int bq, Paar pa, boolean[] nass, double[] rampe) {
        int nc = p.nc, nx = st.nx, ny = st.ny, a = achse[bq], pq = partner[bq];
        double[] bet = new double[nc], zp = new double[nc], zt = new double[nc];
        double[] oe = new double[6], or = new double[6], of = new double[6];
        for (int k = 0; k < pa.zahl; k++) {
            if (pa.stueck[k] < 0) continue;
            int cf = pa.zelle[k], i0 = cf % nx, j0 = cf / nx, sg = pa.seite[k];
            double lw = 2 * Math.PI / pa.kq[k], enden = Math.min(1, 0.5 * (pa.ende[k] + 1));
            p.wert(pq, p.x(i0), p.y(j0), of);
            double fP = of[0] + pa.halbP[k], fT = of[1] + pa.halbT[k];   // Φ_e und T_e an der Fläche
            double roh0 = 0, z0 = 0;
            for (int n = -1; ; n++) {                                     // n = −1: die trockene Zelle hinter der Fläche (für den Gradienten bis zur Wand)
                int i = i0 - (a == 0 ? sg * n : 0), j = j0 - (a == 1 ? sg * n : 0);
                if (i < 0 || j < 0 || i >= nx || j >= ny) { if (n < 0) continue; break; }
                int c = j * nx + i;
                if (n >= 0 && !nass[c]) break;
                double d = Math.max(0.5, n + 0.5) * st.dx / lw;
                if (d >= SPIEGEL_AUS) break;
                double u = d <= SPIEGEL_VOLL ? 1 : 1 - (d - SPIEGEL_VOLL) / (SPIEGEL_AUS - SPIEGEL_VOLL), be = u * u * (3 - 2 * u) * enden;
                if (rampe != null) be *= rampe[c];
                p.wert(pq, p.x(i), p.y(j), oe);
                p.wert(bq, p.x(i), p.y(j), or);
                double roh = 2 * fP - oe[0] - or[0];                       // Unterschied zum Feld der Strahlen
                double z = n <= 0 ? wrap(roh) : z0 + wrap(roh - roh0);     // längs des Wegs stetig (an der Fläche nahe 0)
                roh0 = roh; z0 = z;
                if (!(be > bet[c])) continue;                              // überlappt: das stärkere
                bet[c] = be;
                zp[c] = z;
                zt[c] = 2 * fT - oe[1] - or[1];
            }
        }
        for (int c = 0; c < nc; c++) {
            if (!(bet[c] > 0)) continue;
            p.dPhi[bq * nc + c] += bet[c] * zp[c];
            p.dT[bq * nc + c] += bet[c] * zt[c];
        }
    }

    /** Seit S5b-6b2: Breite der Rampe am Rand des feinen Felds (Zellen; zweimal die Überblendung von 6 der Stufe) [G]. */
    public static final int RAMPE_ZELLEN = 12;

    /** Parallele Strahlen je Zelle (ungerade) [G]. */
    static final int STRAHLEN = 9;

    /**
     * Ein Strahl von (x, y) (in Zellen) in Richtung (bx, by) durch das Gitter (Amanatides–Woo): die Fläche eines geraden
     * Stücks (Index im Paar), durch die er zuerst ins Trockene tritt (Achse a), −1 an jeder anderen Fläche oder am Rand,
     * −2 wenn er außerhalb oder im Trockenen beginnt.
     */
    static int strahl(Oberflaeche.Stufe st, boolean[] nass, int[] flaecheAn, int a, double x, double y, double bx, double by) {
        int nx = st.nx, ny = st.ny;
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        if (i < 0 || j < 0 || i >= nx || j >= ny || !nass[j * nx + i]) return -2;
        int sx = bx > 0 ? 1 : -1, sy = by > 0 ? 1 : -1;
        double tdx = Math.abs(bx) > 1e-12 ? 1 / Math.abs(bx) : Double.POSITIVE_INFINITY, tdy = Math.abs(by) > 1e-12 ? 1 / Math.abs(by) : Double.POSITIVE_INFINITY;
        double tx = Math.abs(bx) > 1e-12 ? ((sx > 0 ? i + 1 - x : x - i) * tdx) : Double.POSITIVE_INFINITY;
        double ty = Math.abs(by) > 1e-12 ? ((sy > 0 ? j + 1 - y : y - j) * tdy) : Double.POSITIVE_INFINITY;
        for (int z = 0; z < 4 * (nx + ny); z++) {
            int ni = i, nj = j, ax;
            if (tx < ty) { ni += sx; tx += tdx; ax = 0; } else { nj += sy; ty += tdy; ax = 1; }
            if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) return -1;
            if (!nass[nj * nx + ni]) {
                int c = j * nx + i;
                return ax == a && flaecheAn[c] >= 0 ? flaecheAn[c] : -1;
            }
            i = ni; j = nj;
        }
        return -1;
    }

    /**
     * Gewicht je Zelle: 0 auf dem Rand des Gitters, 1 ab {@link #RAMPE_ZELLEN} Zellen vom Rand, glatt dazwischen [G].
     * Seit S5b-6b2; vorher bis zur halben kleineren Seite, dann wirkte der Versatz an den meisten Wänden nur zum Teil
     * (Datei 58, C1).
     */
    static double[] rampe(Phasenfeld p) {
        double[] r = new double[p.nc];
        for (int j = 0; j < p.ny; j++)
            for (int i = 0; i < p.nx; i++) {
                double d = Math.min(Math.min(i, p.nx - 1 - i), Math.min(j, p.ny - 1 - j)), u = Math.min(1, d / RAMPE_ZELLEN);
                r[j * p.nx + i] = u * u * (3 - 2 * u);
            }
        return r;
    }

    static double wrap(double x) { return Math.IEEEremainder(x, 2 * Math.PI); }

    /** Für Berichte: Paare und ihre Achsen, Flächen. */
    public String bericht() {
        int ax = 0, ay = 0;
        for (int bq = 0; bq < nb * nd; bq++) if (partner[bq] >= 0) { if (achse[bq] == 0) ax++; else ay++; }
        return String.format(java.util.Locale.GERMANY, "%d Paare (%d an Ost-West-, %d an Nord-Süd-Flächen) von %d Stützstellen; Wandflächen grob %d, fein %d",
                paare, ax, ay, nb * nd, flaechenGrob, flaechenFein);
    }
}
