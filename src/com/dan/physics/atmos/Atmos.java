package com.dan.physics.atmos;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Boden, Wärme und bodennahe Luft (Werkbuch HalligHooge Teil A, 7.5 und 12.11, P10a): die Referenz in doppelter
 * Genauigkeit auf einem Gitter aus nx × ny Zellen; die Profile der Luft stehen auf einem groben Raster aus Säulen zu
 * je {@link #teiler} × {@link #teiler} Zellen. Eine Fassung auf der Grafikkarte gibt es nicht: Ein Kopplungsintervall
 * kostet auf dem Prozessor Zehntelsekunden (Werkbuch 12.11, Befund 2.4).
 * <p>
 * <b>Eingang</b> (setzt der Aufrufer; am Bus mit P10b): je Zelle die Wassertiefe {@link #h} und die Rauheit
 * {@link #z0}; je Säule das Wetter – Wind in 10 m, Temperatur und Feuchte in 2 m, Druck, Sonnen- und Gegenstrahlung
 * am Boden, Temperatur des Meerwassers.
 * <p>
 * <b>Energiebilanz der Oberfläche</b> je Zelle und Schritt, mit der Temperatur T_s der Oberfläche als Unbekannter:
 * (1 − α) S + ε (L − σ T_s⁴) − H − LE − G − C_w (T_s − T_w) / Δt = 0. Fühlbare Wärme H = ρ c_p (T_s − θ_a) / r_a und
 * latente Wärme LE = ρ L_v (q_satt(T_s) − q_a) / (r_a + r_s) mit dem Widerstand der Luft r_a nach Monin und Obukhov
 * ({@link Schicht}); r_s ist der Widerstand der Oberfläche gegen die Verdunstung. Gelöst nach Newton, im Wechsel mit
 * der Schichtung der Luft ({@link #durchgaenge}).
 * <p>
 * <b>Boden:</b> Wärmeleitung in {@link #SCHICHTEN} Schichten bis 0,5 m ({@link #dicke}), implizit; unten kein Fluss.
 * Der Fluss in den Boden G ist dabei genau linear in T_s und geht so in die Bilanz ein. Stoffwerte von
 * wassergesättigtem Sand [G], überall gleich.
 * <p>
 * <b>Wasser:</b> Ab {@link #hNass} Tiefe trägt die Zelle Wasser. Flaches Wasser (unter {@link #hTief}) ist eine
 * durchmischte Schicht mit eigener Temperatur: Es erwärmt sich mit der Bilanz, zulaufendes Wasser bringt die
 * Temperatur des Meers mit, ablaufendes nimmt die der Schicht mit. Tieferes Wasser hat die Temperatur des Meers [G];
 * was das an Wärme heißt, steht als Austausch mit dem Meer in der Bilanz.
 * <p>
 * <b>Nässe</b> ({@code surf.wet}): der Wassergehalt der obersten Bodenschicht (1 cm) als Anteil der Sättigung. Unter
 * Wasser 1; an der Luft nimmt die Verdunstung ihn mit, Tau gibt ihn zurück. Der Widerstand der Oberfläche steigt, wenn
 * die Schicht unter 15 Vol.-% Wasser fällt (van de Griend und Owe 1994: r_s = 10 s/m darüber, 10 exp(0,3563 (15 − θ))
 * darunter, θ in Vol.-%; die Schwelle ist an der Zusammenfassung der Arbeit nachgelesen, die Zahlen 10 und 0,3563
 * stammen aus dem Gedächtnis [G]). Mehr als da ist, verdunstet nicht.
 * <p>
 * <b>Entwässerung</b> (seit 0.2, P10b, F92 A; {@link #mitEntwaesserung}): Unter trockengefallenem Sand sinkt das
 * Grundwasser ({@link #grundwasser}) mit {@link #sinken} [G], höchstens bis zum Wasserstand nebenan ({@link #ueber});
 * steigt der, steigt es sofort mit. Die oberste Schicht
 * strebt zur Sättigung, die im Gleichgewicht über diesem Grundwasser steht – Haltekurve nach van Genuchten (1980),
 * S_e = [1 + (α h)^n]^−(1 − 1/n), mit n und dem Restwasser von Sand nach Carsel und Parrish (1988) und α für Feinsand
 * [G] ({@link #haltekurve}). Wie schnell, gibt die Leitfähigkeit nach Mualem und van Genuchten,
 * K = K_s S_e^½ [1 − (1 − S_e^(1/m))^m]² ({@link #leitung}): Nasser Sand gleicht sich in Minuten aus, trockener
 * bekommt kaum Nachschub – dort trocknet die Verdunstung die Oberfläche ganz.
 * <p>
 * <b>Profile</b> je Säule ({@link #profile}): Schichtung aus dem Wind und den Flüssen im Mittel der Säule, Temperatur
 * und Feuchte nach Businger und Dyer von den Werten in 2 m aus nach unten und oben, an der Oberfläche die mittlere
 * Temperatur der Säule; Druck hydrostatisch, Brechzahl nach Ciddor ({@link Profil}).
 * <p>
 * <b>Bilanz:</b> {@link #eStrahlung}, {@link #eFuehlbar}, {@link #eLatent}, {@link #eZufluss}, {@link #eMeer} laufen
 * auf (J/m², Summe über die Zellen); {@link #waerme()} gibt den Bestand. Kennt nur java.* (Schichtenvertrag,
 * Werkbuch 4).
 * <p>
 * <b>Regen</b> (seit 0.3, P10c, F97 A; {@link #regen}): Was in einem Schritt fällt, füllt zu dessen Beginn die oberste
 * Schicht, höchstens bis zur Sättigung; der Rest versickert und zählt nicht weiter [G]. Die Wärme des Regens und
 * Pfützen fehlen. Ohne Regen (0) rechnet der Schritt bitgleich wie 0.2.
 * <p>
 * <b>Grenzen:</b> ohne {@link #mitEntwaesserung} trocknet nur die Verdunstung; kein Eis; ein Satz Bodenwerte
 * (Feinsand) für alle Zellen; Stoffwerte des Bodens
 * hängen nicht von der Nässe ab (trockener Sand dämmt in Wahrheit besser); die Albedo des Wassers ist fest; das Profil
 * über 10–20 m hinaus ist die Fortsetzung der bodennahen Schicht, keine eigene Rechnung der Grenzschicht.
 */
public final class Atmos {
    public static final String VERSION = "0.3";
    public static final int SCHICHTEN = 5;

    public final int nx, ny, nc;
    /** Zellen je Säule in jeder Richtung; Säulen in x und y und insgesamt. */
    public final int teiler, sx, sy, ns;

    // ------------------------------------------------------------------ gesetzte Größen
    /** Dicke der Bodenschichten von oben (m) [G]; die oberste trägt die Nässe. */
    public final double[] dicke = {0.01, 0.03, 0.06, 0.12, 0.28};
    /** Wärmeleitfähigkeit (W/(m K)) und Wärmekapazität (J/(m³ K)) des Bodens: wassergesättigter Sand [G]. */
    public double leit = 2.2, kapaz = 2.95e6;
    /** Porenanteil der obersten Schicht. */
    public double poros = 0.40;
    /** Albedo von nassem und trockenem Sand und von Wasser, Emissionsgrad [G]. */
    public double albedoNass = 0.20, albedoTrocken = 0.35, albedoWasser = 0.08, emission = 0.97;
    /** Ab dieser Tiefe (m) trägt die Zelle Wasser; ab {@link #hTief} hat es die Temperatur des Meers [G]. */
    public double hNass = 0.001, hTief = 0.30;
    /** Messhöhen von Wind und von Temperatur und Feuchte (m). */
    public double zWind = 10, zTemp = 2;
    /** Widerstand der Oberfläche: kleinster Wert (s/m), Schwelle (Volumenanteil Wasser), Anstieg je Volumenanteil. */
    public double rsMin = 10, rsSchwelle = 0.15, rsAnstieg = 35.63;
    /** Wellenlänge der Brechzahl (µm) und Kohlendioxid (µmol/mol) [G]. */
    public double lambda = 0.55, co2 = 430;
    /** Wechsel zwischen Schichtung und Bilanz je Schritt und Durchläufe der Schichtung je Wechsel [G]. */
    public int durchgaenge = 2, laeufe = 4;
    public boolean mitTurbulenz = true, mitVerdunstung = true, mitBoden = true, parallel = true;
    /** Seit 0.2: Entwässerung und Nachschub aus dem Grundwasser (F92 A); ohne sie wie 0.1. */
    public boolean mitEntwaesserung;
    /** Haltekurve: α (1/m; Feinsand [G]), n und Restsättigung θ_r/θ_s (Sand nach Carsel und Parrish 1988: n 2,68; 0,045/0,43). */
    public double vgAlpha = 3.5, vgN = 2.68, sRest = 0.045 / 0.43;
    /** Gesättigte Leitfähigkeit (m/s; Sand nach Carsel und Parrish, 712,8 cm je Tag) und Sinken des Grundwassers nach dem Trockenfallen (m/s; 2 cm je Stunde [G]). */
    public double kSatt = 8.25e-5, sinken = 0.02 / 3600;
    /** Werte der Schicht (Grenzen, Charnock), für alle Zellen. */
    public final Schicht vorgabe = new Schicht();

    // ------------------------------------------------------------------ Eingang
    /** Wassertiefe je Zelle (m). */
    public final double[] h;
    /** Rauheit je Zelle über Land (m); über Wasser rechnet die Schicht nach Charnock. */
    public final double[] z0;
    /** Seit 0.2: Höhe der Zelle über dem Wasserstand nebenan (m); so tief kann das Grundwasser höchstens sinken. Vorgabe: unbegrenzt. */
    public final double[] ueber;
    /** Wetter je Säule: Wind in 10 m (m/s), Temperatur in 2 m (K), spezifische Feuchte in 2 m (kg/kg), Druck (Pa). */
    public final double[] wind, t2, q2, druck;
    /** Wetter je Säule: Sonnenstrahlung und Gegenstrahlung am Boden (W/m², nach unten), Temperatur des Meers (K). */
    public final double[] sonne, gegen, tMeer;
    /** Seit 0.3: Regen je Säule (kg/(m² s)); Vorgabe 0. */
    public final double[] regen;

    // ------------------------------------------------------------------ Zustand
    /** Temperatur der Bodenschichten (K), Schicht k der Zelle c bei {@code SCHICHTEN * c + k}. */
    public final double[] tb;
    /** Temperatur der Wasserschicht (K), Nässe ({@code surf.wet}), Temperatur der Oberfläche ({@code surf.T}, K). */
    public final double[] tw, wet, ts;
    /** Wassertiefe des vorigen Schritts (m) und 1/L der Zelle (1/m). */
    public final double[] hVor, invL;
    /** Seit 0.2: wie lange die Zelle trocken liegt (s); unter Wasser 0. */
    public final double[] trocken;
    /** Seit 0.2: Tiefe des Grundwassers unter der Oberfläche (m); unter Wasser 0. */
    public final double[] grundwasser;

    // ------------------------------------------------------------------ Ausgabe
    /** Je Zelle (W/m²): Strahlungsbilanz, fühlbare und latente Wärme (nach oben positiv), Fluss in den Boden. */
    public final double[] rn, hf, le, gb;
    /** Je Säule das Profil der Luft; {@link Profil#r} ist {@code air.n}. */
    public final Profil[] profile;
    /** Je Säule: Temperatur der Oberfläche im Mittel (K), 1/L (1/m), u* (m/s). */
    public final double[] tsSaeule, invLSaeule, usSaeule;
    /** Aufgelaufen (J/m², Summe über die Zellen): Strahlung, fühlbar, latent, Zufluss mit dem Wasser, Austausch mit dem Meer. */
    public double eStrahlung, eFuehlbar, eLatent, eZufluss, eMeer;
    /** Schritte, in denen die Verdunstung am Vorrat endete (Zellen mal Schritte). */
    public long begrenzt;

    private final double[][] zeile;                  // Summen je Zeile: 0 Strahlung, 1 fühlbar, 2 latent, 3 Zufluss, 4 Meer, 5 begrenzt
    private final double[] q0;                       // Feuchte an der Oberfläche je Zelle (kg/kg)

    public Atmos(int nx, int ny, int teiler) {
        this.nx = nx;
        this.ny = ny;
        this.nc = nx * ny;
        this.teiler = teiler;
        this.sx = (nx + teiler - 1) / teiler;
        this.sy = (ny + teiler - 1) / teiler;
        this.ns = sx * sy;
        h = new double[nc];
        z0 = new double[nc];
        Arrays.fill(z0, 1e-3);
        ueber = new double[nc];
        Arrays.fill(ueber, Double.MAX_VALUE);
        trocken = new double[nc];
        grundwasser = new double[nc];
        wind = new double[ns]; t2 = new double[ns]; q2 = new double[ns]; druck = new double[ns];
        sonne = new double[ns]; gegen = new double[ns]; tMeer = new double[ns]; regen = new double[ns];
        tb = new double[SCHICHTEN * nc];
        tw = new double[nc]; wet = new double[nc]; ts = new double[nc]; hVor = new double[nc]; invL = new double[nc];
        rn = new double[nc]; hf = new double[nc]; le = new double[nc]; gb = new double[nc]; q0 = new double[nc];
        profile = new Profil[ns];
        for (int s = 0; s < ns; s++) profile[s] = new Profil();
        tsSaeule = new double[ns]; invLSaeule = new double[ns]; usSaeule = new double[ns];
        zeile = new double[ny][6];
    }

    /** Die Säule der Zelle (i, j). */
    public int saeule(int i, int j) { return i / teiler + sx * (j / teiler); }

    /** Dasselbe Wetter in allen Säulen. */
    public void wetter(double wind, double t2, double q2, double druck, double sonne, double gegen, double tMeer) {
        Arrays.fill(this.wind, wind); Arrays.fill(this.t2, t2); Arrays.fill(this.q2, q2); Arrays.fill(this.druck, druck);
        Arrays.fill(this.sonne, sonne); Arrays.fill(this.gegen, gegen); Arrays.fill(this.tMeer, tMeer);
    }

    /** Anfang: Boden, Wasser und Oberfläche mit der Temperatur T (K), der Boden nass, die Bilanz auf null. */
    public void anfang(double T) {
        Arrays.fill(tb, T); Arrays.fill(tw, T); Arrays.fill(ts, T); Arrays.fill(wet, 1); Arrays.fill(invL, 0); Arrays.fill(trocken, 0); Arrays.fill(grundwasser, 0);
        System.arraycopy(h, 0, hVor, 0, nc);
        eStrahlung = eFuehlbar = eLatent = eZufluss = eMeer = 0;
        begrenzt = 0;
    }

    /** Widerstand der Oberfläche gegen die Verdunstung (s/m) bei der Nässe w (0–1). */
    public double widerstand(double w) {
        double theta = w * poros;
        return theta >= rsSchwelle ? rsMin : rsMin * Math.exp(rsAnstieg * (rsSchwelle - theta));
    }

    /** Wirksame Sättigung S_e (0–1) im Gleichgewicht in der Höhe {@code h} (m) über dem Grundwasser: van Genuchten (1980) mit m = 1 − 1/n. */
    public static double haltekurve(double h, double alpha, double n) { return h <= 0 ? 1 : Math.pow(1 + Math.pow(alpha * h, n), -(1 - 1 / n)); }

    /** Leitfähigkeit relativ zur gesättigten bei der wirksamen Sättigung S_e: Mualem und van Genuchten. */
    public static double leitung(double se, double n) {
        if (se <= 0) return 0;
        if (se >= 1) return 1;
        double m = 1 - 1 / n, x = 1 - Math.pow(1 - Math.pow(se, 1 / m), m);
        return Math.sqrt(se) * x * x;
    }

    /** Sättigung der obersten Schicht (0–1, mit Restwasser) im Gleichgewicht über Grundwasser in der Tiefe {@code tiefe} (m) unter der Oberfläche. */
    public double gleichgewicht(double tiefe) { return sRest + (1 - sRest) * haltekurve(tiefe + 0.5 * dicke[0], vgAlpha, vgN); }

    /** Wasser, das die oberste Schicht gesättigt hält (kg/m²). */
    public double vorrat() { return poros * dicke[0] * Luft.RHO_W; }

    /** Bestand an Wärme (J/m², Summe über die Zellen): Boden und Wasserschicht, gegen 0 K. */
    public double waerme() {
        double e = 0;
        for (int c = 0; c < nc; c++) {
            for (int k = 0; k < SCHICHTEN; k++) e += kapaz * dicke[k] * tb[SCHICHTEN * c + k];
            if (hVor[c] >= hNass) e += Luft.RHO_W * Luft.C_W * hVor[c] * tw[c];
        }
        return e;
    }

    // ------------------------------------------------------------------ Schritt

    /** Ein Schritt der Länge dt (s) mit dem Eingang, wie er steht; danach die Profile der Säulen. */
    public void schritt(double dt) {
        IntStream z = IntStream.range(0, ny);
        if (parallel) z = z.parallel();
        z.forEach(j -> zeile(j, dt));
        for (int j = 0; j < ny; j++) {
            double[] s = zeile[j];
            eStrahlung += s[0]; eFuehlbar += s[1]; eLatent += s[2]; eZufluss += s[3]; eMeer += s[4];
            begrenzt += (long) s[5];
        }
        profile();
    }

    private void zeile(int j, double dt) {
        Schicht sch = new Schicht();
        sch.zetaMax = vorgabe.zetaMax; sch.windMin = vorgabe.windMin; sch.charnock = vorgabe.charnock; sch.z0Verhaeltnis = vorgabe.z0Verhaeltnis;
        double[] a = new double[SCHICHTEN], b = new double[SCHICHTEN], nen = new double[SCHICHTEN], sum = zeile[j];
        Arrays.fill(sum, 0);
        final double rcw = Luft.RHO_W * Luft.C_W, ks = 2 * leit / dicke[0], wmax = vorrat();
        // Boden: T_k' = a_k + b_k T_(k−1)', von unten nach oben aufgelöst; b und der Nenner hängen nur an den Stoffwerten
        double[] ck = new double[SCHICHTEN], kOben = new double[SCHICHTEN];
        for (int k = 0; k < SCHICHTEN; k++) {
            ck[k] = kapaz * dicke[k] / dt;
            kOben[k] = k == 0 ? ks : 2 * leit / (dicke[k - 1] + dicke[k]);
        }
        for (int k = SCHICHTEN - 1; k >= 0; k--) {
            nen[k] = ck[k] + kOben[k] + (k == SCHICHTEN - 1 ? 0 : kOben[k + 1] * (1 - b[k + 1]));
            b[k] = kOben[k] / nen[k];
        }
        for (int i = 0; i < nx; i++) {
            int c = i + nx * j, s = saeule(i, j), o = SCHICHTEN * c;
            double hNeu = h[c], hAlt = hVor[c] >= hNass ? hVor[c] : 0, tMeerS = tMeer[s], p = druck[s], ta = t2[s], qa = q2[s];
            boolean wasser = hNeu >= hNass, fest = wasser && hNeu >= hTief;
            double cwdt = 0, twS = 0, zu = 0;
            if (wasser) {
                if (!fest) {
                    if (hNeu > hAlt) {
                        twS = (hAlt * tw[c] + (hNeu - hAlt) * tMeerS) / hNeu;
                        zu = rcw * (hNeu - hAlt) * tMeerS;
                    } else {
                        twS = tw[c];
                        zu = -rcw * (hAlt - hNeu) * tw[c];
                    }
                    cwdt = rcw * hNeu / dt;
                }
                wet[c] = 1;
            } else if (hAlt > 0) {
                zu = -rcw * hAlt * tw[c];                    // trockengefallen: das Wasser geht mit seiner Temperatur
                wet[c] = 1;
            }
            // der Fluss in den Boden ist genau g1 T_s − g0
            double g1 = 0, g0 = 0;
            if (mitBoden) {
                for (int k = SCHICHTEN - 1; k >= 0; k--) a[k] = (ck[k] * tb[o + k] + (k == SCHICHTEN - 1 ? 0 : kOben[k + 1] * a[k + 1])) / nen[k];
                g1 = ks * (1 - b[0]);
                g0 = ks * a[0];
            }
            double w = wasser ? wet[c] : Math.min(1, wet[c] + regen[s] * dt / wmax);      // Regen füllt die oberste Schicht (seit 0.3)
            double rs = wasser ? 0 : widerstand(w), thA = ta + Luft.GAMMA * zTemp, rho = Luft.dichte(ta, qa, p), lv = Luft.verdampfung(ta);
            double alb = wasser ? albedoWasser : albedoNass + (albedoTrocken - albedoNass) * (1 - w);
            double sw = (1 - alb) * sonne[s], lw = emission * gegen[s], es = emission * Luft.SIGMA;
            double t = fest ? tMeerS : wasser ? twS : ts[c];
            double rcH = 0, rlE = 0, ra = 0, leFest = Double.NaN;
            boolean grenze = false;
            for (int dg = 0; dg < (mitTurbulenz ? durchgaenge : 1); dg++) {
                if (mitTurbulenz) {
                    // Feuchte an der Oberfläche, wie die Luft sie sieht (hinter dem Widerstand der Oberfläche)
                    double raS = ra > 0 ? ra : 50;
                    double qOb = mitVerdunstung ? qa + (Luft.feuchteSatt(t, p) - qa) * raS / (raS + rs) : qa;
                    sch.loese(wind[s], zWind, thA, zTemp, qa, t, qOb, z0[c], wasser, invL[c], laeufe, 0);
                    invL[c] = sch.invL;
                    ra = sch.ra;
                    rcH = rho * Luft.CP / ra;
                    rlE = mitVerdunstung ? rho * lv / (ra + rs) : 0;
                }
                if (fest) continue;
                // Newton: F(T) = sw + lw − εσT⁴ − rcH (T − θ_a) − rlE (q_satt(T) − q_a) − (g1 T − g0) − cwdt (T − T_w)
                for (int it = 0; it < 50; it++) {
                    double t3 = t * t * t, f = sw + lw - es * t3 * t - rcH * (t - thA) - (g1 * t - g0) - cwdt * (t - twS), df = -4 * es * t3 - rcH - g1 - cwdt;
                    if (grenze) f -= leFest;
                    else if (rlE > 0) {
                        f -= rlE * (Luft.feuchteSatt(t, p) - qa);
                        df -= rlE * Luft.feuchteSattNachT(t, p);
                    }
                    double dT = -f / df;
                    t += dT;
                    if (Math.abs(dT) <= 1e-11) break;
                }
                // mehr als der Vorrat verdunstet nicht (Land)
                if (!wasser && !grenze && rlE > 0) {
                    double verd = rlE / lv * (Luft.feuchteSatt(t, p) - qa);
                    if (verd * dt > w * wmax) {
                        grenze = true;
                        leFest = lv * w * wmax / dt;
                        dg--;                                   // die Bilanz mit der festen Verdunstung noch einmal
                        sum[5]++;
                    }
                }
            }
            // Flüsse zum Ergebnis
            double t3 = t * t * t, rnet = sw + lw - es * t3 * t, hF = rcH * (t - thA);
            double lE = grenze ? leFest : rlE > 0 ? rlE * (Luft.feuchteSatt(t, p) - qa) : 0, g = g1 * t - g0;
            if (mitBoden) {
                double oben = t;
                for (int k = 0; k < SCHICHTEN; k++) {
                    oben = a[k] + b[k] * oben;
                    tb[o + k] = oben;
                }
            }
            if (wasser) {
                if (fest) {
                    // Austausch mit dem Meer: was die Bilanz der Zelle schließt
                    sum[4] += rcw * (hNeu * tMeerS - hAlt * tw[c]) + (g - (rnet - hF - lE)) * dt;
                    tw[c] = tMeerS;
                } else {
                    tw[c] = t;
                }
                trocken[c] = 0;
                grundwasser[c] = 0;
            } else {
                double verd = lE / lv;                           // kg/(m² s), Tau negativ
                double wn = Math.max(0, Math.min(1, w - verd * dt / wmax));
                trocken[c] += dt;
                if (mitEntwaesserung) {
                    // das Grundwasser sinkt, höchstens bis zum Wasserstand nebenan, und steigt mit ihm
                    double gw = Math.max(0, Math.min(ueber[c], grundwasser[c] + sinken * dt));
                    grundwasser[c] = gw;
                    // Ausgleich mit dem Gleichgewicht darüber; die Rate aus der Leitfähigkeit der nasseren Seite (implizit)
                    double ziel = gleichgewicht(gw), nass = Math.max(wn, ziel);
                    double r = Luft.RHO_W * kSatt * leitung((nass - sRest) / (1 - sRest), vgN) / wmax * dt;
                    wn = (wn + r * ziel) / (1 + r);
                }
                wet[c] = wn;
            }
            ts[c] = t;
            rn[c] = rnet; hf[c] = hF; le[c] = lE; gb[c] = g;
            q0[c] = mitTurbulenz ? qa + lE / lv * ra / rho : qa;
            hVor[c] = hNeu;
            sum[0] += rnet * dt; sum[1] += hF * dt; sum[2] += lE * dt; sum[3] += zu;
        }
    }

    // ------------------------------------------------------------------ Profile

    /** Rechnet die Profile aller Säulen aus dem Stand der Zellen; {@link #schritt} ruft es selbst. */
    public void profile() {
        IntStream z = IntStream.range(0, ns);
        if (parallel) z = z.parallel();
        z.forEach(this::profil);
    }

    private void profil(int s) {
        int i0 = s % sx * teiler, j0 = s / sx * teiler, i1 = Math.min(nx, i0 + teiler), j1 = Math.min(ny, j0 + teiler);
        double tsM = 0, hfM = 0, leM = 0, qM = 0, lnz = 0;
        int n = 0, nass = 0;
        for (int j = j0; j < j1; j++)
            for (int i = i0; i < i1; i++) {
                int c = i + nx * j;
                tsM += ts[c]; hfM += hf[c]; leM += le[c]; qM += q0[c];
                if (h[c] >= hNass) nass++;
                else lnz += Math.log(z0[c]);
                n++;
            }
        tsM /= n; hfM /= n; leM /= n; qM /= n;
        boolean wasser = 2 * nass >= n;
        double z0S = nass < n ? Math.exp(lnz / (n - nass)) : 1e-4, ta = t2[s], qa = q2[s], p = druck[s];
        double thA = ta + Luft.GAMMA * zTemp, rho = Luft.dichte(ta, qa, p), lv = Luft.verdampfung(ta);
        Schicht sch = new Schicht();
        sch.zetaMax = vorgabe.zetaMax; sch.windMin = vorgabe.windMin; sch.charnock = vorgabe.charnock; sch.z0Verhaeltnis = vorgabe.z0Verhaeltnis;
        if (mitTurbulenz) sch.loeseAusFluss(wind[s], zWind, thA, qa, hfM, leM / lv, rho, z0S, wasser, 60, 1e-12);
        Profil pr = profile[s];
        pr.t[0] = tsM;
        pr.q[0] = qM;
        boolean labil = tsM > thA;
        for (int k = 1; k < Profil.STUFEN; k++) {
            double zk = Profil.hoehe(k), th = thA, q = qa;
            if (mitTurbulenz) {
                th += sch.thetaZwischen(zTemp, zk);
                q += sch.feuchteZwischen(zTemp, zk);
                // nicht über die Oberfläche hinaus (unter der Rauheit für Wärme gilt die Form nicht)
                th = labil ? Math.min(th, tsM) : Math.max(th, tsM);
                q = qM > qa ? Math.min(q, qM) : Math.max(q, qM);
            }
            pr.t[k] = th - Luft.GAMMA * zk;
            pr.q[k] = Math.max(0, q);
        }
        pr.schliesse(p, lambda, co2);
        tsSaeule[s] = tsM;
        invLSaeule[s] = mitTurbulenz ? sch.invL : 0;
        usSaeule[s] = mitTurbulenz ? sch.ustern : 0;
    }
}
