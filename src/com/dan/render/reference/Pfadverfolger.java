package com.dan.render.reference;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Ein Pfadverfolger für einen Faden (P7b, Werkbuch HalligHooge Teil A, 8.5 und 12.8): Pfade vom Auge zurück durch Luft,
 * Wasser und an Boden und Wasseroberfläche, je Pfad mehrere Wellenlängen ({@link Wellen}).
 * <ul>
 * <li><b>Sonne und Mond</b> nur über das Ereignis zur Lichtquelle (die Scheiben sind klein gegen jede Keule der Szene);
 * gefunden durch Streuung zählen sie nicht, außer im direkten Blick des Auges. So wird nichts doppelt gezählt.</li>
 * <li><b>Unter Wasser</b> kommt das Licht durch die raue Oberfläche: eine Mikronormale wird gezogen, der gebrochene Strahl
 * der Sonne gesucht, die BTDF nach Walter am Treffer der Oberfläche ausgewertet und durch die Dichte der Richtung
 * geteilt (zweistufig: Punkt auf der Scheibe, dann Mikronormale). Unverzerrt für jede Rauheit.</li>
 * <li><b>Nachthimmel:</b> gleichmäßige Strahldichte über der Luft; zählt bei jedem Pfad, der die Luft verlässt.</li>
 * <li><b>Ofentest:</b> {@link #ofen}: ohne Lichtquellen, die Umgebung über der Luft leuchtet mit 1 je Bahn, nichts
 * absorbiert.</li>
 * </ul>
 */
public final class Pfadverfolger {
    final Szene szene;
    public final Wellen wl;
    final Medien med;
    final List<Licht> lichter;
    /** Die Facetten der Fläche: {@link #basis} aus dem Wind (Cox und Munk); seit P8c am Treffer mit dem Rest des Seegangs. */
    Facette facette;
    final Facette basis;
    private Facette letzte;
    final double eta;
    final SplittableRandom r;
    /** Ofentest; nur Einfachstreuung in der Luft (Prüfung); größte Zahl der Stöße. */
    public boolean ofen, einfach;
    public int maxTiefe = 100_000;
    /**
     * Suchweite unter Wasser (m, seit P7c): ein Strahl im Wasser sucht die nächste Fläche nur so weit. Kommt keine, fliegt
     * er bis dorthin wie durch Wasser; dass er weiter frei fliegt, hat bei b ≈ 19/m die Wahrscheinlichkeit e^−95 bei
     * 5 m. Unendlich: ohne Grenze (die Prüfszenen).
     */
    public double wasserWeite = Double.POSITIVE_INFINITY;
    /**
     * Seit P7c: in der Luft das Ereignis zur Lichtquelle an jedem vorläufigen Stoß des Trackings (Gewicht σ_s / σ̄), nicht
     * nur an der Streuung; der Erwartungswert ist derselbe, die Streuung der Schätzung viel kleiner (der Himmel).
     */
    public boolean jederStoss = true;
    /** Ohne Wassersäule (Zerlegung, F55): ein Pfad, der von oben ins Wasser gebrochen wird, endet. */
    public boolean ohneSaeule;
    /**
     * Zerlegung des Wassers (P7h, F65 A): mit {@link #teile} zählt jeder Beitrag zusätzlich in {@link #LT}, nach dem Weg
     * des Pfads vom Auge bis dorthin: 0 nie im Wasser (Fläche: Spiegelung, Luft davor, Gelände); 1 im Wasser gewesen, den
     * Grund nicht berührt, Licht der Quelle durch die Fläche (Säule aus Sonne und Mond); 2 ebenso, aber Licht, das der
     * Pfad nach dem Austritt aus dem Wasser findet (Säule aus dem Himmel); 3 nach einer Berührung des Grunds unter Wasser
     * (Grund). Die Summe der vier ist {@link #L}, Pfad für Pfad.
     */
    public boolean teile;
    public final double[][] LT;
    private boolean getaucht, grund, direkt;
    /** Je Ereignis eine Lichtquelle, gezogen nach ihrer Stärke ({@link #licht}); Kehrwert ihrer Wahrscheinlichkeit. */
    private double nachWahl = 1;
    /**
     * Anteil der Richtungen, die nach einer Streuung oder am Boden aus einer Keule um die stärkste Lichtquelle gezogen
     * werden (seit P7c): der Kranz um die Sonne (Mie) beleuchtet Boden und Luft stark und ist mit Lambert oder der
     * Phasenfunktion allein selten getroffen. Gewichtet mit der Mischdichte (unverzerrt). 0: aus.
     */
    public double keule = 0.25;
    /** Breite der Keule als g von Henyey-Greenstein [G]. */
    public static final double KEULE_G = 0.8;
    /** Ort und Richtung des laufenden Flugs (für die Ereignisse an den Stößen). */
    private double[] pFlug, dFlug;
    /** Die Umgebung über der Luft (Ofentest) oder der Nachthimmel. */
    private final Szene.Treffer tr = new Szene.Treffer(), tr2 = new Szene.Treffer();
    private final double[] w, L, T, alb, f, le;
    static final double EPS = 1e-4;

    static final double[] OBEN = {0, 1, 0};

    public Pfadverfolger(Szene szene, Wellen wl, List<Licht> lichter, double sigma2, double eta, long seed) {
        this.szene = szene;
        this.wl = wl;
        this.med = new Medien(wl, szene.augeHoehe());
        this.lichter = lichter;
        this.basis = new Facette(sigma2, eta);
        this.facette = basis;
        this.eta = eta;
        this.r = new SplittableRandom(seed);
        w = new double[wl.n];
        L = new double[wl.n];
        T = new double[wl.n];
        alb = new double[wl.n];
        f = new double[wl.n];
        le = new double[wl.n];
        LT = new double[4][wl.n];
    }

    /** Beitrag c der Bahn k; quelle: Licht der Quelle durch die Wasserfläche (für {@link #teile}). */
    private void plus(int k, double c, boolean quelle) {
        L[k] += c;
        if (teile) LT[!getaucht ? 0 : grund ? 3 : quelle ? 1 : 2][k] += c;
    }

    /** Strahldichte je Bahn längs des Strahls vom Auge (p0, d0) für die gesetzten Wellenlängen; Ergebnis in {@link #L}. */
    public double[] verfolge(double[] p0, double[] d0) {
        int n = wl.n;
        Arrays.fill(w, 0, n, 1);
        Arrays.fill(L, 0, n, 0);
        getaucht = false;
        grund = false;
        if (teile) for (double[] z : LT) Arrays.fill(z, 0, n, 0);
        double[] p = p0.clone(), d = d0.clone();
        boolean imWasser = false;
        if (!Double.isNaN(med.truebung)) med.truebung(Double.NaN);
        // Ereignis im Wasser seit dem Eintritt (Streuung oder Grund): dann deckt neeWasser das Licht durch die Fläche ab,
        // und das Ereignis an der Fläche von unten zählte es doppelt
        boolean ereignis = false;
        for (int tiefe = 0; tiefe < maxTiefe; tiefe++) {
            boolean hit = szene.schneide(p, d, imWasser ? wasserWeite : Double.POSITIVE_INFINITY, tr);
            int e;
            if (imWasser) {
                if (!hit && wasserWeite == Double.POSITIVE_INFINITY) break;
                e = med.fliegeWasser(hit ? tr.t : wasserWeite, w, r);
                if (!hit && e == Medien.ENDE) break;
            } else {
                pFlug = p;
                dFlug = d;
                e = med.fliegeLuft(p, d, hit ? tr.t : Double.POSITIVE_INFINITY, w, r, jederStoss && !ofen ? stossNee : null);
            }
            if (e == Medien.ABSORBIERT) break;
            if (e == Medien.STREUUNG) {
                double[] x = Vek.plus(p, med.tEreignis, d);
                if (imWasser) {
                    if (!ofen) neeWasser(x, d, null, null);
                    ereignis = true;
                } else {
                    if (!ofen && !jederStoss) neeLuftStreuung(x, d);
                }
                if (einfach) break;
                if (imWasser) d = Phasen.umAchse(d, Phasen.ffZiehe(r.nextDouble()), r);
                else d = streueLuft(d);
                p = x;
            } else if (!hit) {
                // verlässt die Luft
                for (int k = 0; k < n; k++) plus(k, w[k] * (ofen ? 1 : wl.nacht[k]), false);
                if (tiefe == 0 && !ofen)
                    for (Licht li : lichter)
                        if (li.trifft(d)) {
                            double s = li.strahldichte(d);
                            for (int k = 0; k < n; k++) plus(k, w[k] * (li.sonne ? wl.sonne[k] : wl.mond[k]) * s, false);
                        }
                break;
            } else {
                double[] x = Vek.plus(p, tr.t, d);
                double[] nn = tr.n.clone();
                int art = tr.art;
                // seit P12c3: das Wasser der Gestalt auf dem Boden – aus der Luft mit seinem Anteil der Spiegel (F134 A: unter
                // der Flut ist der Grund das Gelände)
                if (art == Szene.BODEN && tr.spiegel > 0 && !imWasser && (tr.spiegel >= 1 || r.nextDouble() < tr.spiegel)) {
                    art = Szene.SPIEGEL;
                    tr.f0 = Gestaltfeld.F0;
                    tr.alpha2 = Gestaltfeld.ALPHA2;
                    System.arraycopy(Gestaltfeld.KOERPER, 0, tr.albedo, 0, 3);
                }
                // seit P12c3: leuchtende Flächen (Fenster nachts)
                if (tr.leuchtet && !ofen) {
                    wl.leuchten(tr.leucht, le);
                    for (int k = 0; k < n; k++) plus(k, w[k] * le[k], false);
                }
                if (art == Szene.BLATT) {
                    double[] neu = blatt(x, d, nn);
                    if (neu == null) break;
                    d = neu;
                } else if (art == Szene.SPIEGEL) {
                    double[] neu = spiegel(x, d, nn);
                    if (neu == null) break;
                    d = neu;
                } else if (art == Szene.BODEN) {
                    if (Vek.dot(nn, d) > 0) nn = Vek.neg(nn);
                    if (ofen) Arrays.fill(alb, 0, n, 1);
                    else wl.albedo(tr.albedo, alb);
                    if (imWasser) grund = true;
                    if (!ofen) {
                        if (imWasser) neeWasser(x, d, nn, alb);
                        else neeLuftBoden(x, nn);
                    }
                    if (imWasser) ereignis = true;
                    double[] ks = keule > 0 && !ofen && !imWasser ? keulenRichtung() : null;
                    if (ks == null) {
                        d = Phasen.umAchse(nn, Math.sqrt(1 - r.nextDouble()), r);
                        for (int k = 0; k < n; k++) w[k] *= alb[k];
                    } else {
                        d = r.nextDouble() < keule ? Phasen.umAchse(ks, Phasen.hgZiehe(r.nextDouble(), KEULE_G), r) : Phasen.umAchse(nn, Math.sqrt(1 - r.nextDouble()), r);
                        double c = Vek.dot(d, nn);
                        if (c <= 0) break;
                        double pm = (1 - keule) * c / Math.PI + keule * Phasen.hg(Vek.dot(d, ks), KEULE_G);
                        for (int k = 0; k < n; k++) w[k] *= alb[k] * c / Math.PI / pm;
                    }
                } else {
                    facette = facetteFuer(tr.sigma2);
                    double[] o = Vek.neg(d);
                    double no = imWasser ? eta : 1, nd = imWasser ? 1 : eta;
                    if (!ofen && !(imWasser && ereignis)) neeFlaeche(x, o, nn, imWasser);
                    double[] ks = keule > 0 && !ofen ? keulenRichtung() : null;
                    double[] nu = Vek.dot(o, nn) >= 0 ? nn : Vek.neg(nn), luft = nn[1] >= 0 ? nn : Vek.neg(nn);
                    double[] i;
                    double gw;
                    boolean durch;
                    if (ks != null && r.nextDouble() < keule) {
                        // aus der Keule zur Lichtquelle in die Luft: Spiegelung von oben, Brechung von unten
                        i = Phasen.umAchse(ks, Phasen.hgZiehe(r.nextDouble(), KEULE_G), r);
                        if (Vek.dot(i, luft) <= 0) break;
                        double pm = (1 - keule) * facette.dichte(o, i, nu, no, nd) + keule * Phasen.hg(Vek.dot(i, ks), KEULE_G);
                        gw = pm > 0 ? facette.wert(i, o, nu, no, nd) / pm : 0;
                        durch = imWasser;
                    } else {
                        double[][] s = facette.ziehe(o, nn, no, nd, r);
                        if (s == null) break;
                        i = s[0];
                        durch = s[2][0] == 1;
                        if (ks == null) gw = s[1][0];
                        else if (Vek.dot(i, luft) <= 0) gw = s[1][0] / (1 - keule);
                        else {
                            double pm = (1 - keule) * facette.dichte(o, i, nu, no, nd) + keule * Phasen.hg(Vek.dot(i, ks), KEULE_G);
                            gw = pm > 0 ? facette.wert(i, o, nu, no, nd) / pm : 0;
                        }
                    }
                    if (!(gw > 1e-250)) break;
                    for (int k = 0; k < n; k++) w[k] *= gw;
                    if (durch && !imWasser && ohneSaeule) break;
                    if (durch) {
                        imWasser = !imWasser;
                        if (imWasser) {
                            getaucht = true;
                            double c = szene.truebung(x);                 // seit P10d (S8): Trübung am Eintrittspunkt
                            if (!(c == med.truebung) && !(Double.isNaN(c) && Double.isNaN(med.truebung))) med.truebung(c);
                        }
                        ereignis = false;
                    }
                    d = i;
                }
                p = Vek.plus(x, EPS, d);
            }
            if (tiefe >= 8) {
                double q = 0;
                for (int k = 0; k < n; k++) q = Math.max(q, Math.abs(w[k]));
                if (q < 0.25) {
                    if (r.nextDouble() >= q / 0.25) break;
                    for (int k = 0; k < n; k++) w[k] /= q / 0.25;
                }
            }
        }
        return L;
    }

    // ------------------------------------------------------------------ seit P12c3: Blatt und Spiegel

    /**
     * Blatt: Lambert nach beiden Seiten; Reflexion ρ = Albedo, Durchlass τ = {@code durchlass} · ρ. Ereignis zur Quelle
     * von der Seite, auf der sie steht. Neue Richtung: mit ρ / (ρ + τ) auf der Seite des Auges, sonst dahinter; Gewicht
     * ρ + τ je Bahn. Im Ofen ρ + τ = 1. Gibt null, wenn der Pfad endet.
     */
    private double[] blatt(double[] x, double[] d, double[] nn) {
        int n = wl.n;
        if (Vek.dot(nn, d) > 0) nn = Vek.neg(nn);
        double tau = tr.durchlass;
        if (ofen) Arrays.fill(alb, 0, n, 1 / (1 + tau));
        else wl.albedo(tr.albedo, alb);
        if (!ofen) {
            Licht li = licht();
            if (li != null) {
                double[] l = li.ziehe(r);
                double c = Vek.dot(l, nn);
                double g = c > 0 ? c : -tau * c;
                if (g > 0) {
                    for (int k = 0; k < n; k++) f[k] = alb[k] / Math.PI * g;
                    zurQuelleAusLuft(Vek.plus(x, EPS, c > 0 ? nn : Vek.neg(nn)), l, li);
                }
            }
        }
        boolean vorn = r.nextDouble() * (1 + tau) < 1;
        double[] neu = Phasen.umAchse(vorn ? nn : Vek.neg(nn), Math.sqrt(1 - r.nextDouble()), r);
        for (int k = 0; k < n; k++) w[k] *= alb[k] * (1 + tau);
        return neu;
    }

    /** Schlick: Fresnel für den Kosinus c. */
    private static double schlick(double f0, double c) {
        double m = 1 - Math.max(0, Math.min(1, c));
        return f0 + (1 - f0) * m * m * m * m * m;
    }

    /** Beckmann D(h) zur Normale nn mit α². */
    private static double beckmann(double[] h, double[] nn, double a2) {
        double c = Vek.dot(h, nn);
        if (c <= 0) return 0;
        double c2 = c * c, t2 = (1 - c2) / c2;
        return Math.exp(-t2 / a2) / (Math.PI * a2 * c2 * c2);
    }

    /**
     * Spiegel: glatte Fläche (Fresnel nach Schlick, Facetten nach Beckmann ohne Abschattung) über einem Körper nach
     * Lambert (Albedo). Ereignis zur Quelle mit beiden Teilen; neue Richtung mit F(Auge) gespiegelt an einer gezogenen
     * Facette, sonst nach Lambert. Gibt die neue Richtung oder null.
     */
    private double[] spiegel(double[] x, double[] d, double[] nn) {
        int n = wl.n;
        double[] o = Vek.neg(d);
        if (Vek.dot(nn, o) < 0) nn = Vek.neg(nn);
        double co = Vek.dot(nn, o);
        if (co <= 1e-9) return null;
        double f0 = tr.f0, a2 = Math.max(1e-8, tr.alpha2);
        if (ofen) Arrays.fill(alb, 0, n, 1);
        else wl.albedo(tr.albedo, alb);
        double F = schlick(f0, co);
        if (!ofen) {
            Licht li = licht();
            if (li != null) {
                double[] l = li.ziehe(r);
                double ci = Vek.dot(l, nn);
                if (ci > 0) {
                    double[] h = Vek.norm(new double[] {l[0] + o[0], l[1] + o[1], l[2] + o[2]});
                    double spec = schlick(f0, Vek.dot(o, h)) * beckmann(h, nn, a2) / (4 * co);
                    for (int k = 0; k < n; k++) f[k] = spec + (1 - F) * alb[k] / Math.PI * ci;
                    zurQuelleAusLuft(Vek.plus(x, EPS, nn), l, li);
                }
            }
        }
        if (r.nextDouble() < F) {
            // Facette nach Beckmann: tan² θ = −α² ln(1 − u)
            double t2 = -a2 * Math.log(1 - r.nextDouble()), ch = 1 / Math.sqrt(1 + t2);
            double[] h = Phasen.umAchse(nn, ch, r);
            double oh = Vek.dot(o, h);
            if (oh <= 0) return null;
            double[] i = {2 * oh * h[0] - o[0], 2 * oh * h[1] - o[1], 2 * oh * h[2] - o[2]};
            if (Vek.dot(i, nn) <= 0) return null;
            double g = schlick(f0, oh) * oh / (F * co * ch);
            for (int k = 0; k < n; k++) w[k] *= g;
            return i;
        }
        for (int k = 0; k < n; k++) w[k] *= alb[k];
        return Phasen.umAchse(nn, Math.sqrt(1 - r.nextDouble()), r);
    }

    /** Neue Richtung nach einer Streuung in der Luft (Rayleigh oder Mie, gemischt nach dem Rayleigh-Anteil der Bahnen). */
    private double[] streueLuft(double[] d) {
        int n = wl.n;
        double piR = 0;
        for (int k = 0; k < n; k++) piR += med.anteilR[k];
        piR /= n;
        double g = com.dan.core.szene.Atmosphere.MIE_G;
        double[] ks = keule > 0 && !ofen ? keulenRichtung() : null;
        double[] neu;
        double mu;
        if (ks != null && r.nextDouble() < keule) {
            neu = Phasen.umAchse(ks, Phasen.hgZiehe(r.nextDouble(), KEULE_G), r);
            mu = Math.max(-1, Math.min(1, Vek.dot(neu, d)));
        } else {
            mu = r.nextDouble() < piR ? Phasen.rayleighZiehe(r.nextDouble()) : Phasen.hgZiehe(r.nextDouble(), g);
            neu = Phasen.umAchse(d, mu, r);
        }
        double pR = Phasen.rayleigh(mu), pM = Phasen.cornetteShanks(mu, g), q = piR * pR + (1 - piR) * Phasen.hg(mu, g);
        if (ks != null) q = (1 - keule) * q + keule * Phasen.hg(Vek.dot(neu, ks), KEULE_G);
        for (int k = 0; k < n; k++) w[k] *= (med.anteilR[k] * pR + (1 - med.anteilR[k]) * pM) / q;
        return neu;
    }

    // ------------------------------------------------------------------ Ereignisse zur Lichtquelle

    /**
     * Eine Lichtquelle für ein Ereignis, gezogen nach ihrer Beleuchtungsstärke außerhalb der Luft, unter dem Horizont
     * kleiner [G] (bis −6° voll, bis −18° ein Hundertstel, darunter 10⁻⁸): bei Tag die Sonne, bei Nacht der Mond. Setzt
     * {@link #nachWahl}; null ohne Lichtquelle. Unverzerrt, weil jede Quelle eine Wahrscheinlichkeit über 0 hat.
     */
    private Licht licht() {
        int n = lichter.size();
        if (n == 0) return null;
        if (n == 1) {
            nachWahl = 1;
            return lichter.get(0);
        }
        double sum = 0;
        for (Licht li : lichter) sum += gewicht(li);
        double u = r.nextDouble() * sum;
        for (Licht li : lichter) {
            double g = gewicht(li);
            if (u < g || li == lichter.get(n - 1)) {
                nachWahl = sum / g;
                return li;
            }
            u -= g;
        }
        return null;
    }

    /** Richtung der stärksten Lichtquelle (nach {@link #gewicht}) für die Keule; null ohne. */
    private double[] keulenRichtung() {
        Licht best = null;
        double bg = 0;
        for (Licht li : lichter) {
            double g = gewicht(li);
            if (g > bg) {
                bg = g;
                best = li;
            }
        }
        return best == null ? null : best.richtung;
    }

    private static double gewicht(Licht li) {
        double h = li.richtung[1];
        return Math.max(1e-30, li.faktor) * (h > -0.1045 ? 1 : h > -0.309 ? 0.01 : 1e-8);
    }

    /** Ereignis an einem vorläufigen Stoß der Luft (vor der Wahl des Ereignisses): f = (σ_R p_R + σ_M p_M) / σ̄ je Bahn. */
    private final Medien.Stoss stossNee = this::stossNee;

    private void stossNee(double t, double[] sR, double[] sM, double mj) {
        Licht li = licht();
        if (li == null) return;
        double[] l = li.ziehe(r);
        double mu = Vek.dot(l, dFlug), g = com.dan.core.szene.Atmosphere.MIE_G;
        double pR = Phasen.rayleigh(mu), pM = Phasen.cornetteShanks(mu, g);
        for (int k = 0; k < wl.n; k++) f[k] = (sR[k] * pR + sM[k] * pM) / mj;
        zurQuelleAusLuft(Vek.plus(pFlug, t, dFlug), l, li);
    }

    private double strahl(Licht li, int k) { return li.sonne ? wl.sonne[k] : wl.mond[k]; }

    /** Streuung in der Luft: Phasenfunktion je Bahn. */
    private void neeLuftStreuung(double[] x, double[] d) {
        double g = com.dan.core.szene.Atmosphere.MIE_G;
        Licht li = licht();
        if (li != null) {
            double[] l = li.ziehe(r);
            double mu = Vek.dot(l, d);
            double pR = Phasen.rayleigh(mu), pM = Phasen.cornetteShanks(mu, g);
            for (int k = 0; k < wl.n; k++) f[k] = med.anteilR[k] * pR + (1 - med.anteilR[k]) * pM;
            zurQuelleAusLuft(x, l, li);
        }
    }

    /** Boden in der Luft: Lambert. */
    private void neeLuftBoden(double[] x, double[] nn) {
        Licht li = licht();
        if (li != null) {
            double[] l = li.ziehe(r);
            double c = Vek.dot(l, nn);
            if (c <= 0) return;
            for (int k = 0; k < wl.n; k++) f[k] = alb[k] / Math.PI * c;
            zurQuelleAusLuft(Vek.plus(x, EPS, nn), l, li);
        }
    }

    /** Wasseroberfläche: von oben Spiegelung, von unten Brechung (das Licht der Quelle kommt aus der Luft). */
    private void neeFlaeche(double[] x, double[] o, double[] nn, boolean vonUnten) {
        double[] up = nn[1] >= 0 ? nn : Vek.neg(nn);
        Licht li = licht();
        if (li != null) {
            double[] l = li.ziehe(r);
            if (Vek.dot(l, up) <= 0) return;
            double v = vonUnten ? facette.wert(l, o, up, eta, 1) : facette.wert(l, o, up, 1, eta);
            if (v <= 0) return;
            Arrays.fill(f, 0, wl.n, v);
            direkt = vonUnten;
            zurQuelleAusLuft(Vek.plus(x, EPS, up), l, li);
            direkt = false;
        }
    }

    /** Von x in der Luft zur Quelle: verdeckt? Durchlässigkeit, Beitrag mit f je Bahn. */
    private void zurQuelleAusLuft(double[] x, double[] l, Licht li) {
        if (szene.schneide(x, l, Double.POSITIVE_INFINITY, tr2)) return;
        Arrays.fill(T, 0, wl.n, 1);
        med.durchLuft(x, l, Double.POSITIVE_INFINITY, T, r);
        double s = li.strahldichte(l) * li.raumwinkel * nachWahl;
        for (int k = 0; k < wl.n; k++) plus(k, w[k] * f[k] * T[k] * strahl(li, k) * s, direkt);
    }

    /**
     * Unter Wasser: das Licht der Quelle durch die raue Oberfläche. Bei einer Streuung (nn == null) die Phasenfunktion,
     * am Grund (nn, Albedo) Lambert. Seit P7c zwei Richtungen mit Multiple Importance Sampling (Balance, Veach 1997):
     * eine über die Mikronormale zur Lichtquelle, eine nach der Phasenfunktion von Fournier-Forand bzw. nach Lambert. Die
     * Spitze von Fournier-Forand nach vorn (bis 10⁴ je sr) gäbe mit der ersten allein seltene, sehr große Beiträge. Der
     * Weg Lichtquelle → Fläche → x ist damit ganz hier geschätzt; das Ereignis an der Fläche von unten entfällt danach.
     */
    private void neeWasser(double[] x, double[] d, double[] nn, double[] albedo) {
        Licht li = licht();
        if (li == null) return;
        double[] ls = li.ziehe(r);
        if (ls[1] <= 0) return;
        double s = li.strahldichte(ls) * li.raumwinkel * nachWahl;
        double[] x0 = nn == null ? x : Vek.plus(x, EPS, nn);
        double[] m = facette.ziehe(OBEN, r);
        double[] t = Vek.breche(Vek.neg(ls), m, 1 / eta);
        if (t != null && t[1] < 0) wasserBeitrag(x0, d, nn, albedo, li, ls, Vek.neg(t), s);
        double[] u = nn == null ? Phasen.umAchse(d, Phasen.ffZiehe(r.nextDouble()), r) : Phasen.umAchse(nn, Math.sqrt(1 - r.nextDouble()), r);
        if (u[1] > 0) wasserBeitrag(x0, d, nn, albedo, li, ls, u, s);
    }

    /** Die Facetten am Treffer: ohne Rest des Seegangs die aus dem Wind, sonst mit α² + Rest (P8c). */
    private Facette facetteFuer(double rest) {
        if (!(rest > 0)) return basis;
        if (letzte == null || letzte.alpha2 != basis.alpha2 + rest) letzte = new Facette(basis.alpha2 + rest, eta);
        return letzte;
    }

    /** Dichte, mit der die Mikronormale die Richtung u (von x zur Oberfläche) zur Lichtrichtung ls gibt; 0, wenn keine. */
    private double dichteMikro(double[] ls, double[] t) {
        double[] h = Vek.norm(new double[] {-(ls[0] + eta * t[0]), -(ls[1] + eta * t[1]), -(ls[2] + eta * t[2])});
        if (h[1] < 0) h = Vek.neg(h);
        double den = Vek.dot(ls, h) + eta * Vek.dot(t, h);
        if (den == 0) return 0;
        return facette.dichte(h, OBEN) * eta * eta * Math.abs(Vek.dot(t, h)) / (den * den);
    }

    /** Beitrag des Lichts aus Richtung u (von x0 zur Oberfläche) mit dem Gewicht der Mischung der Strategien. */
    private void wasserBeitrag(double[] x0, double[] d, double[] nn, double[] albedo, Licht li, double[] ls, double[] u, double s) {
        double fx;
        if (nn == null) fx = Phasen.ff(Vek.dot(u, d));
        else {
            double c = Vek.dot(u, nn);
            if (c <= 0) return;
            fx = c / Math.PI;
        }
        if (!szene.schneide(x0, u, wasserWeite, tr2) || tr2.art != Szene.WASSER) return;
        double[] y = Vek.plus(x0, tr2.t, u);
        double[] up = tr2.n[1] >= 0 ? tr2.n.clone() : Vek.neg(tr2.n);
        double[] t = Vek.neg(u);
        double ft = facetteFuer(tr2.sigma2).wert(ls, t, up, eta, 1);
        if (ft <= 0) return;
        double pu = dichteMikro(ls, t), p = pu + fx;
        if (!(p > 0)) return;
        double wy = tr2.t;
        if (szene.schneide(Vek.plus(y, EPS, up), ls, Double.POSITIVE_INFINITY, tr2)) return;
        Arrays.fill(T, 0, wl.n, 1);
        med.durchWasser(wy, T);
        med.durchLuft(Vek.plus(y, EPS, up), ls, Double.POSITIVE_INFINITY, T, r);
        for (int k = 0; k < wl.n; k++) {
            double fk = nn == null ? fx : fx * albedo[k];
            plus(k, w[k] * fk * T[k] * ft * strahl(li, k) * s / p, true);
        }
    }

    private static double tr2Weg(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ für die Prüfungen (ReferenceCheck)

    /** Ofentest: ohne Lichtquellen, Umgebung 1, nichts absorbiert. */
    public void ofen() {
        ofen = true;
        med.ohneAbsorption = true;
    }

    /** Freier Flug durch s m Wasser mit spektralem Tracking; 0: durch, sonst Ereignis. */
    public int pruefeWasserflug(double s, double[] gewichte) { return med.fliegeWasser(s, gewichte, r); }

    /** Ratio-Tracking der Luft von p in Richtung d bis zur Oberkante. */
    public void pruefeLuft(double[] p, double[] d, double[] T) { med.durchLuftRatio(p, d, Double.POSITIVE_INFINITY, T, r); }

    /** Durchlässigkeit der Luft durch Quadratur von p in Richtung d bis zur Oberkante (Prüfung, seit P7c). */
    public void pruefeLuftQuadratur(double[] p, double[] d, double[] T) { med.durchLuft(p, d, Double.POSITIVE_INFINITY, T, r); }

    /**
     * Einfachstreuung der Luft längs (p0, d) als Stoßschätzer (für Prüfstein 3): an jedem vorläufigen Stoß der
     * Majorante der Beitrag T · σ_s / σ̄ · p(μ) · Quelle, dann T mal (1 − σ_t / σ̄). Unverzerrt wie das analoge Tracking,
     * aber mit kleiner Streuung; der Pfad endet an der Oberkante.
     */
    public double[] einfachStoesse(double[] p0, double[] d) {
        int n = wl.n;
        Arrays.fill(L, 0, n, 0);
        double[] Tv = new double[n], sR = new double[n], sM = new double[n], ab = new double[n];
        Arrays.fill(Tv, 1);
        double tEnde = med.bisOben(p0, d), t = 0, g = com.dan.core.szene.Atmosphere.MIE_G;
        while (t < tEnde) {
            double h0 = med.hoehe(Vek.plus(p0, t, d));
            double len = Math.min(Medien.ABSCHNITT_MAX, Math.max(200, 2 * (h0 + 500)));
            double b = Math.min(tEnde, t + len);
            double mj = med.majoranteLuft(med.tiefste(p0, d, t, b), Math.max(h0, med.hoehe(Vek.plus(p0, b, d))));
            while (true) {
                t -= Math.log(1 - r.nextDouble()) / mj;
                if (t >= b) {
                    t = b;
                    break;
                }
                double[] x = Vek.plus(p0, t, d);
                med.luft(med.hoehe(x), sR, sM, ab);
                for (Licht li : lichter) {
                    double[] l = li.ziehe(r);
                    double mu = Vek.dot(l, d), pR = Phasen.rayleigh(mu), pM = Phasen.cornetteShanks(mu, g);
                    if (szene.schneide(x, l, Double.POSITIVE_INFINITY, tr2)) continue;
                    Arrays.fill(T, 0, n, 1);
                    med.durchLuft(x, l, Double.POSITIVE_INFINITY, T, r);
                    double s = li.strahldichte(l) * li.raumwinkel;
                    for (int k = 0; k < n; k++) L[k] += Tv[k] * (sR[k] * pR + sM[k] * pM) / mj * T[k] * strahl(li, k) * s;
                }
                for (int k = 0; k < n; k++) Tv[k] *= 1 - (sR[k] + sM[k] + ab[k]) / mj;
            }
        }
        return L;
    }

    /** Freier Flug in der Luft mit spektralem Tracking bis zur Oberkante; 0: durch. */
    public int pruefeLuftflug(double[] p, double[] d, double[] gewichte) { return med.fliegeLuft(p, d, Double.POSITIVE_INFINITY, gewichte, r); }

    /** Ohne Luft und Nachthimmel (Prüfung des Wassers, seit P7c). */
    public void ohneLuft() {
        med.mitRayleigh = false;
        med.mitMie = false;
        med.mitOzon = false;
        Arrays.fill(wl.nacht, 0);
    }

    /** Nur Rayleigh in der Luft, ohne Nachthimmel (einfacher Himmel). */
    public void nurRayleigh() {
        med.mitMie = false;
        med.mitOzon = false;
        Arrays.fill(wl.nacht, 0);
    }

    /** Die Wellenlängen für den nächsten Pfad (spektral) neu ziehen. */
    public void neueWellen() { if (wl.spektral) wl.setze(r.nextDouble()); }

    public SplittableRandom zufall() { return r; }

    /**
     * BSDF · |i·n| der rauen Wasserfläche (Walter u. a. 2007, Cox und Munk) für Prüfungen: Strahldichte von i nach o,
     * nO Brechzahl auf der Seite von o, nA auf der anderen.
     */
    public static double flaeche(double sigma2, double eta, double[] i, double[] o, double[] n, double nO, double nA) {
        return new Facette(sigma2, eta).wert(i, o, n, nO, nA);
    }

    /** Faktor auf die Majorante der Luft für die Ereignisse an jedem Stoß (≥ 1, Vorgabe 1). */
    public void dichte(double d) { med.dichte = Math.max(1, d); }
}
