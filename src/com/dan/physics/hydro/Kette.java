package com.dan.physics.hydro;

import com.dan.core.gitter.Gitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geschachtelte Gitter in beide Richtungen (Werkbuch HalligHooge Teil A, 12.5, Plan P4b, neu gefasst in P4c; Muster
 * {@code Bay}): Ebene 0 ist die gröbste, jede weitere liegt auf ganzen Zellen der vorigen.
 * <p>
 * <b>Gitter als ein Netz.</b> Die feinen Zellen ersetzen die groben darunter. Am Rand des feinen Gitters liegt je feiner
 * Randzelle eine Kante zur groben Zelle außen, deren Reihe sie berührt („außen“); ihr Gefälle geht über den Abstand der
 * beiden Mitten, (r + 1) / 2 feine Zellweiten ({@link Hydro#randAbstand}), ihr Fluss geht in diese grobe Zelle und aus
 * ihr. Die groben Kanten unter und an dem feinen Gitter sind gesperrt (kein Fluss, kein eigener Impuls); ihr u kommt
 * am Ende aus den feinen Durchflüssen.
 * <p>
 * <b>Zeit.</b> Die groben Kanten sind langsam (Schritt Δt), die feinen und die Randkanten schnell (m = ⌈Δt / Δt_CFL⌉
 * Unterschritte h = Δt / m). Ein Schritt der Ebene k ist eine symmetrische Teilung (Strang): Masse der Ebene k über
 * Δt / 2, die Ebene k + 1 über Δt (Verlet: Impuls h / 2, dann Masse und Impuls h im Wechsel, zuletzt Impuls h / 2), wieder
 * Masse über Δt / 2, dann der Impuls der Ebene k über Δt. Die Zellen außen gehören zu beiden: Ihr Volumen ändern die
 * groben Kanten in den Hälften und die Randkanten in jedem Unterschritt. So bleibt das Verfahren auch bei m = 10 stabil
 * (Befund P4c: Werden die Zellen außen mit dem Fluss der groben Kanten gleichmäßig über die Unterschritte geführt oder
 * steht der Impuls des groben Gitters vor dem feinen, wächst ein Rauschen von 10⁻⁶ m in Stunden über jede Grenze).
 * <p>
 * Das Volumen aller Ebenen ({@link #volumen()}) bleibt genau erhalten; ein ruhender See bleibt genau in Ruhe.
 */
public final class Kette {
    public final Hydro[] ebene;
    /** je Ebene k ≥ 1: Verhältnis zur Ebene k − 1 und Lage ihrer ersten Zelle dort (Zellen der Ebene k − 1). */
    private final int[] r, i0, j0, ni, nj;
    /** je Ebene k < n − 1: liegt die Zelle unter Ebene k + 1? */
    private final boolean[][] unter;
    /**
     * je Ebene k ≥ 1 und Randkante zur Ebene k − 1: die Kante, ihr Vorzeichen (+1: positiver Fluss geht hinein), die
     * grobe Zelle außen, deren Reihe sie berührt, die Nachbarin in der Reihe zu ihr hin (−1: keine) und deren Gewicht.
     */
    private final int[][] randKante, randZeichen, randZelle, randNachbar;
    private final double[][] randGewicht;
    /** je Ebene k ≥ 1: erste Randkante je Seite (−1: Wand). */
    private final int[][] seitenAnfang;
    /** je Ebene k ≥ 1: die Zellen außen (Ebene k − 1); je Randkante die Nummer ihrer Zelle und der Nachbarin dort. */
    private final int[][] aussen, randO1, randO2;
    private final double[][] aussenRein;
    /** Negative Volumen in den Zellen außen (m³). */
    public double abgeschnitten;
    /** Schritte je Ebene. */
    public final long[] schritte;

    public Kette(Hydro... ebene) {
        this.ebene = ebene.clone();
        int n = ebene.length;
        r = new int[n];
        i0 = new int[n];
        j0 = new int[n];
        ni = new int[n];
        nj = new int[n];
        unter = new boolean[n][];
        randKante = new int[n][];
        randZeichen = new int[n][];
        randZelle = new int[n][];
        randNachbar = new int[n][];
        randGewicht = new double[n][];
        seitenAnfang = new int[n][];
        aussen = new int[n][];
        randO1 = new int[n][];
        randO2 = new int[n][];
        aussenRein = new double[n][];
        schritte = new long[n];
        for (int k = 1; k < n; k++) einrichten(k);
    }

    private void einrichten(int k) {
        Hydro g = ebene[k - 1], f = ebene[k];
        Gitter gg = g.gitter, gf = f.gitter;
        if (gg.zone != gf.zone) throw new IllegalArgumentException("Kette: Ebene " + k + " in einer anderen Zone");
        double rx = gg.dx / gf.dx, ry = gg.dy / gf.dy, ix = (gf.x0 - gg.x0) / gg.dx, jy = (gf.y0 - gg.y0) / gg.dy;
        int rr = (int) Math.round(rx);
        if (Math.abs(rx - rr) > 1e-9 || Math.abs(ry - rr) > 1e-9 || rr < 2) throw new IllegalArgumentException("Kette: Ebene " + k + ": Verhältnis " + rx + " × " + ry + " ist nicht ganz und gleich");
        if (Math.abs(ix - Math.round(ix)) > 1e-9 || Math.abs(jy - Math.round(jy)) > 1e-9 || gf.nx % rr != 0 || gf.ny % rr != 0)
            throw new IllegalArgumentException("Kette: Ebene " + k + " liegt nicht auf ganzen Zellen der Ebene " + (k - 1));
        r[k] = rr;
        i0[k] = (int) Math.round(ix);
        j0[k] = (int) Math.round(jy);
        ni[k] = gf.nx / rr;
        nj[k] = gf.ny / rr;
        if (i0[k] < 0 || j0[k] < 0 || i0[k] + ni[k] > gg.nx || j0[k] + nj[k] > gg.ny) throw new IllegalArgumentException("Kette: Ebene " + k + " ragt aus Ebene " + (k - 1));
        unter[k - 1] = new boolean[g.zellen];
        for (int j = 0; j < nj[k]; j++)
            for (int i = 0; i < ni[k]; i++) {
                int c = i0[k] + i + g.nx * (j0[k] + j);
                unter[k - 1][c] = true;
                for (int e : g.kantenVon(c)) g.sperren(e);
            }
        // Seiten: auf dem Rand des groben Wand, sonst je feiner Randzelle eine Kante zu den Zellen außen
        boolean[] amRand = {i0[k] == 0, i0[k] + ni[k] == gg.nx, j0[k] == 0, j0[k] + nj[k] == gg.ny};
        List<int[]> rand = new ArrayList<>();
        List<Double> gewicht = new ArrayList<>();
        List<Integer> au = new ArrayList<>();
        int[] auNr = new int[g.zellen];
        Arrays.fill(auNr, -1);
        seitenAnfang[k] = new int[] {-1, -1, -1, -1};
        for (int s = 0; s < 4; s++) {
            if (amRand[s]) {
                if (g.rand[s] != Hydro.Rand.WAND) throw new IllegalArgumentException("Kette: Ebene " + k + " liegt auf dem Rand der Ebene " + (k - 1) + ", der dort keine Wand ist");
                f.rand[s] = Hydro.Rand.WAND;
                continue;
            }
            boolean quer = s == Hydro.WEST || s == Hydro.OST;
            int lang = quer ? nj[k] : ni[k], vz = s == Hydro.WEST || s == Hydro.SUED ? 1 : -1;
            int[] zelle = new int[lang];
            for (int a = 0; a < lang; a++) {
                zelle[a] = s == Hydro.WEST ? i0[k] - 1 + g.nx * (j0[k] + a) : s == Hydro.OST ? i0[k] + ni[k] + g.nx * (j0[k] + a)
                        : s == Hydro.SUED ? i0[k] + a + g.nx * (j0[k] - 1) : i0[k] + a + g.nx * (j0[k] + nj[k]);
                auNr[zelle[a]] = au.size();
                au.add(zelle[a]);
            }
            seitenAnfang[k][s] = rand.size();
            for (int b = 0; b < lang * rr; b++) {
                int a = b / rr;
                double t = ((b % rr) + 0.5) / rr - 0.5;                // Lage in der groben Zelle, −½ … ½
                int nb = t > 0 ? a + 1 : t < 0 ? a - 1 : -1;
                if (nb < 0 || nb >= lang) nb = -1;
                int ef = s == Hydro.WEST ? f.kanteX(0, b) : s == Hydro.OST ? f.kanteX(f.nx, b) : s == Hydro.SUED ? f.kanteY(b, 0) : f.kanteY(b, f.ny);
                rand.add(new int[] {ef, vz, zelle[a], nb < 0 ? -1 : zelle[nb]});
                gewicht.add(nb < 0 ? 0.0 : Math.abs(t));
            }
            f.rand[s] = Hydro.Rand.WASSERSTAND;
            f.randAbstand[s] = 0.5 * (rr + 1);
            final int ss = s;
            f.randwert[s] = (t, x, y) -> aussenWert(k, randNr(k, ss, x, y));
            f.randBoden[s] = (t, x, y) -> aussenTrocken(k, randNr(k, ss, x, y)) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        randKante[k] = rand.stream().mapToInt(a -> a[0]).toArray();
        randZeichen[k] = rand.stream().mapToInt(a -> a[1]).toArray();
        randZelle[k] = rand.stream().mapToInt(a -> a[2]).toArray();
        randNachbar[k] = rand.stream().mapToInt(a -> a[3]).toArray();
        randGewicht[k] = gewicht.stream().mapToDouble(Double::doubleValue).toArray();
        aussen[k] = au.stream().mapToInt(Integer::intValue).toArray();
        randO1[k] = Arrays.stream(randZelle[k]).map(c -> auNr[c]).toArray();
        randO2[k] = Arrays.stream(randNachbar[k]).map(c -> c < 0 ? -1 : auNr[c]).toArray();
        aussenRein[k] = new double[aussen[k].length];
        f.randGrenze = dt -> aussenBegrenzen(k, dt);
    }

    /**
     * In jeder Masse der Ebene k ≥ 1, nach der Begrenzung je Zelle: Flüsse über den Rand hinein ziehen aus den Zellen
     * außen höchstens deren Volumen (seit P4d). Je Zelle außen der Zug Σ (Anteil · Fluss hinein) über τ gegen ihr Volumen;
     * eine Randkante bekommt den kleineren Anteil ihrer beiden Zellen, wie die Begrenzung je Zelle in {@link Hydro}.
     * Ohne sie lief eine Zelle außen beim Trockenfallen leer, und das Abschneiden schuf Wasser (Fall 9 auf der 680M:
     * 1,6 · 10⁶ m³ über eine Tide).
     */
    private void aussenBegrenzen(int k, double tau) {
        Hydro g = ebene[k - 1], f = ebene[k];
        int[] rk = randKante[k], rz = randZeichen[k], au = aussen[k];
        double[] zug = aussenRein[k];
        Arrays.fill(zug, 0);
        boolean etwas = false;
        for (int q = 0; q < rk.length; q++) {
            double fl = rz[q] * f.fluss[rk[q]];
            if (!(fl > 0)) continue;
            etwas = true;
            double w = gewicht(k, q);
            if (w < 1) zug[randO1[k][q]] += (1 - w) * fl;
            if (w > 0) zug[randO2[k][q]] += w * fl;
        }
        if (!etwas) return;
        for (int o = 0; o < au.length; o++) {
            double v = g.vol[au[o]];
            zug[o] = zug[o] > 0 && zug[o] * tau > v ? v / (zug[o] * tau) : 1;
        }
        for (int q = 0; q < rk.length; q++) {
            int e = rk[q];
            double fl = rz[q] * f.fluss[e];
            if (!(fl > 0)) continue;
            double w = gewicht(k, q), fk = 1;
            if (w < 1) fk = Math.min(fk, zug[randO1[k][q]]);
            if (w > 0) fk = Math.min(fk, zug[randO2[k][q]]);
            if (fk < 1) {
                f.fluss[e] *= fk;
                f.u[e] *= fk;
            }
        }
    }

    /** Nummer der Randkante der Ebene k auf der Seite s am Randpunkt (x, y). */
    private int randNr(int k, int s, double x, double y) {
        Gitter gf = ebene[k].gitter;
        boolean quer = s == Hydro.WEST || s == Hydro.OST;
        int b = quer ? (int) Math.floor((y - gf.y0) / gf.dy) : (int) Math.floor((x - gf.x0) / gf.dx);
        return seitenAnfang[k][s] + Math.max(0, Math.min((quer ? ebene[k].ny : ebene[k].nx) - 1, b));
    }

    /** Gewicht der Nachbarin für die Randkante q der Ebene k im jetzigen Stand: nur nasse Zellen zählen. */
    private double gewicht(int k, int q) {
        Hydro g = ebene[k - 1];
        int b = randNachbar[k][q];
        if (b < 0 || randGewicht[k][q] == 0) return 0;
        boolean nassA = g.vol[randZelle[k][q]] > 0, nassB = g.vol[b] > 0;
        return nassA == nassB ? randGewicht[k][q] : nassB ? 1 : 0;
    }

    /** Wasserstand außen für die Randkante q der Ebene k: linear längs der Reihe über die nassen Zellen außen. */
    private double aussenWert(int k, int q) {
        Hydro g = ebene[k - 1];
        int a = randZelle[k][q], b = randNachbar[k][q];
        double w = gewicht(k, q);
        if (w == 0) return g.eta[a];
        if (w == 1 || g.eta[a] == g.eta[b]) return g.eta[b];         // gleiche Werte genau (ruhender See)
        return (1 - w) * g.eta[a] + w * g.eta[b];
    }

    private boolean aussenTrocken(int k, int q) {
        Hydro g = ebene[k - 1];
        int b = randNachbar[k][q];
        return !(g.vol[randZelle[k][q]] > 0) && (b < 0 || !(g.vol[b] > 0));
    }

    // ------------------------------------------------------------------ Lauf

    /** Ein Schritt der gröbsten Ebene mit Δt (s), die feineren in Unterschritten. */
    public void schritt(double dt) {
        Hydro g = ebene[0];
        masse(0, dt);
        g.impuls(dt, g.zeit + dt);
        g.zeit += dt;
        g.schritte++;
        schritte[0]++;
    }

    /**
     * Läuft bis zur Zeit {@code bis}, Schritte der gröbsten Ebene nach CFL (höchstens {@code dtMax}), abgeglichen auf die
     * feineren ({@link #abgleich}). Gibt die Zahl der Schritte zurück.
     */
    public int lauf(double bis, double dtMax) {
        int n = 0;
        while (ebene[0].zeit < bis - 1e-9) {
            schritt(dtKette(Math.min(dtMax, bis - ebene[0].zeit)));
            n++;
        }
        return n;
    }

    /** Schritt der gröbsten Ebene nach CFL, höchstens {@code hoechstens}, abgeglichen auf die feineren ({@link #abgleich}). */
    public double dtKette(double hoechstens) {
        double[] dt = new double[ebene.length];
        long[] z = new long[ebene.length];
        for (int k = 0; k < ebene.length; k++) {
            dt[k] = ebene[k].dtCfl();
            z[k] = ebene[k].zellen;
        }
        return abgleich(dt, z, hoechstens);
    }

    /**
     * Der Schritt der gröbsten Ebene, der die Kette am wenigsten kostet (Werkbuch HalligHooge Teil A, 12.6, P5b, zu
     * F32): Jede feinere Ebene rechnet ⌈τ / Δt_k⌉ gleiche Unterschritte; liegt τ knapp über einem Vielfachen ihres
     * Schritts, verschenkt sie fast einen ganzen. Geprüft werden Δt₀ selbst und jedes Vielfache m · Δt_k (k ≥ 1) bis
     * Δt₀; gewählt wird das kleinste Σ Zellen_k · Schritte_k je Sekunde, bei Gleichstand der längere Schritt. Am
     * Schritt einer Ebene ändert das nichts (jeder bleibt in seiner Grenze), nur wie oft gerechnet wird.
     *
     * @param dt         Schritt nach CFL je Ebene (s), dt[0] der gröbsten
     * @param zellen     Zellen je Ebene
     * @param hoechstens größter Schritt (s), etwa der Rest bis zu einem Zeitpunkt
     */
    public static double abgleich(double[] dt, long[] zellen, double hoechstens) {
        double d0 = Math.min(dt[0], hoechstens), best = d0, kosten = kosten(d0, dt, zellen);
        for (int k = 1; k < dt.length; k++) {
            if (!(dt[k] > 0) || Double.isInfinite(dt[k])) continue;
            int m = (int) Math.floor(d0 / dt[k] * (1 + 1e-12));
            for (int q = Math.max(1, m - 63); q <= m; q++) {
                double d = Math.min(d0, q * dt[k]), c = kosten(d, dt, zellen);
                if (c < kosten * (1 - 1e-9) || (c <= kosten * (1 + 1e-9) && d > best)) {
                    best = d;
                    kosten = c;
                }
            }
        }
        return best;
    }

    /** Zellschritte je Sekunde für den Schritt d der gröbsten Ebene. */
    static double kosten(double d, double[] dt, long[] zellen) {
        double tau = d, n = 1, s = zellen[0];
        for (int k = 1; k < dt.length; k++) {
            int m = Math.max(1, (int) Math.ceil(tau / dt[k] - 1e-9));
            n *= m;
            tau /= m;
            s += zellen[k] * n;
        }
        return s / d;
    }

    /** Masse der Ebene k über τ, mit allem darunter: Hälfte, die Ebene k + 1 über τ, Hälfte; dann grob unter fein. */
    private void masse(int k, double tau) {
        Hydro g = ebene[k];
        double t = g.zeit;
        if (k + 1 == ebene.length) {
            g.masse(tau, t);
            aussenNachziehen(k, tau);
            return;
        }
        g.masse(0.5 * tau, t);
        aussenNachziehen(k, 0.5 * tau);
        verlet(k + 1, tau);
        g.masse(0.5 * tau, t + 0.5 * tau);
        aussenNachziehen(k, 0.5 * tau);
        grobUnterFein(k + 1);
    }

    /** Die Ebene k ≥ 1 über τ in m Unterschritten nach Verlet. */
    private void verlet(int k, double tau) {
        Hydro f = ebene[k];
        double t0 = f.zeit;
        int m = Math.max(1, (int) Math.ceil(tau / f.dtCfl() - 1e-9));
        double h = tau / m;
        f.impuls(0.5 * h, t0);
        for (int s = 0; s < m; s++) {
            masse(k, h);
            f.zeit = s == m - 1 ? t0 + tau : t0 + (s + 1) * h;
            f.impuls(s == m - 1 ? 0.5 * h : h, f.zeit);
            f.schritte++;
            schritte[k]++;
        }
    }

    /**
     * Nach einer Masse der Ebene k (über τ): die Zellen außen der Ebene k − 1 mit den Flüssen der Randkanten, je Kante
     * auf ihre Zelle und die Nachbarin verteilt wie der Wasserstand außen (mit dem Stand, den die Masse gesehen hat).
     */
    private void aussenNachziehen(int k, double tau) {
        if (k == 0) return;
        Hydro g = ebene[k - 1], f = ebene[k];
        int[] rk = randKante[k], rz = randZeichen[k], au = aussen[k];
        double[] rein = aussenRein[k];
        Arrays.fill(rein, 0);
        boolean etwas = false;
        for (int q = 0; q < rk.length; q++) {
            double fl = rz[q] * f.fluss[rk[q]];
            if (fl == 0) continue;
            etwas = true;
            double w = gewicht(k, q);
            if (w < 1) rein[randO1[k][q]] += (1 - w) * fl;
            if (w > 0) rein[randO2[k][q]] += w * fl;
        }
        if (!etwas) return;
        for (int o = 0; o < au.length; o++) {
            if (rein[o] == 0) continue;
            int c = au[o];
            double v = g.vol[c] - rein[o] * tau;
            if (v < 0) {
                abgeschnitten -= v;
                v = 0;
            }
            g.vol[c] = v;
            g.eta[c] = g.etaAus(c, v);
        }
    }

    /** Grob unter fein aus fein; u und Fluss der gesperrten groben Kanten aus den feinen Durchflüssen. */
    private void grobUnterFein(int k) {
        Hydro g = ebene[k - 1], f = ebene[k];
        int rr = r[k];
        for (int jc = 0; jc < nj[k]; jc++)
            for (int ic = 0; ic < ni[k]; ic++) {
                double sw = 0, se = 0, mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY;
                for (int jf = jc * rr; jf < (jc + 1) * rr; jf++)
                    for (int iff = ic * rr; iff < (ic + 1) * rr; iff++) {
                        int c = iff + f.nx * jf;
                        if (!(f.vol[c] > 0)) continue;
                        double a = f.benetzt(c, f.eta[c]);
                        if (!(a > 0)) continue;
                        sw += a;
                        se += a * f.eta[c];
                        mn = Math.min(mn, f.eta[c]);
                        mx = Math.max(mx, f.eta[c]);
                    }
                int cg = i0[k] + ic + g.nx * (j0[k] + jc);
                if (sw == 0) {
                    g.vol[cg] = 0;
                    g.eta[cg] = g.grund(cg);
                } else g.setze(cg, mn == mx ? mn : se / sw);
            }
        for (int jc = 0; jc <= nj[k]; jc++)
            for (int ic = 0; ic <= ni[k]; ic++) {
                if (jc < nj[k]) {                                       // Kante quer zu x
                    double qq = 0;
                    for (int q = 0; q < rr; q++) qq += f.durchfluss(f.kanteX(ic * rr, jc * rr + q));
                    setzeU(g, g.kanteX(i0[k] + ic, j0[k] + jc), qq);
                }
                if (ic < ni[k]) {                                       // Kante quer zu y
                    double qq = 0;
                    for (int q = 0; q < rr; q++) qq += f.durchfluss(f.kanteY(ic * rr + q, jc * rr));
                    setzeU(g, g.kanteY(i0[k] + ic, j0[k] + jc), qq);
                }
            }
    }

    /** u und Fluss (für die Advektion im Impuls des groben) einer gesperrten Kante aus dem Durchfluss q (m³/s). */
    private static void setzeU(Hydro g, int e, double q) {
        if (g.seite(e) >= 0) return;                                // Rand des groben Gitters (dort Wand)
        g.fluss[e] = q;
        if (q == 0) { g.u[e] = 0; return; }
        int von = q > 0 ? g.links(e) : g.rechts(e);
        double a = von >= 0 && g.vol[von] > 0 ? g.hA(e, g.eta[von]) : 0;
        g.u[e] = a >= g.hTrocken ? q / (g.laenge(e) * a) : 0;
    }

    // ------------------------------------------------------------------ Bestand

    /** Volumen aller Ebenen (m³): je Ebene die Zellen, die nicht unter der nächsten liegen. */
    public double volumen() {
        double v = 0;
        for (int k = 0; k < ebene.length; k++) {
            Hydro h = ebene[k];
            for (int c = 0; c < h.zellen; c++) if (k + 1 == ebene.length || !unter[k][c]) v += h.vol[c];
        }
        return v;
    }

    /** Liegt die Zelle c der Ebene k unter der Ebene k + 1? */
    public boolean unter(int k, int c) { return k + 1 < ebene.length && unter[k][c]; }

    /** Verhältnis und Lage der Ebene k ≥ 1 in der Ebene k − 1: {r, i0, j0, ni, nj}. */
    public int[] lage(int k) { return new int[] {r[k], i0[k], j0[k], ni[k], nj[k]}; }

    /** Die Zellen außen der Ebene k ≥ 1 (Zellen der Ebene k − 1). */
    public int[] aussen(int k) { return aussen[k].clone(); }

    /**
     * Die Randkanten der Ebene k ≥ 1 zur Ebene k − 1, je vier Zahlen: Kante, Vorzeichen (+1: positiver Fluss geht
     * hinein), Nummer der Zelle außen in {@link #aussen}, Nummer der Nachbarin dort (−1: keine).
     */
    public int[] randKanten(int k) {
        int[] a = new int[4 * randKante[k].length];
        for (int q = 0; q < randKante[k].length; q++) {
            a[4 * q] = randKante[k][q];
            a[4 * q + 1] = randZeichen[k][q];
            a[4 * q + 2] = randO1[k][q];
            a[4 * q + 3] = randO2[k][q];
        }
        return a;
    }

    /** Je Randkante der Ebene k ≥ 1 das Gewicht der Nachbarin (0 … ½), wenn beide nass sind. */
    public double[] randGewichte(int k) { return randGewicht[k].clone(); }
}
