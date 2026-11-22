package com.dan.render.reference;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Erde;
import com.dan.core.szene.Boden;
import com.dan.core.szene.Spiegel;
import com.dan.core.szene.Spiegelbild;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/**
 * Die Szene der Referenz aus dem Gebiet (P7c, Werkbuch HalligHooge Teil A, 12.8): das Gelände und der Wasserspiegel
 * einer Ansicht, relativ zum Auge (x Ost, y oben, z Süd), beide gesenkt um d² / 2 R_eff ({@link Erde#R_OPTISCH}) wie im
 * Echtzeitbild.
 * <ul>
 * <li><b>Gelände:</b> {@code Gelaende.hoehe(Ost, Nord, 1 m)} mit der Mindesthöhe der Bauwerke; außerhalb aller Stufen der
 * Meeresgrund −30 m [G] wie im Echtzeitbild. Material aus {@link Boden#pixel}, dunkler mit der Nässe aus
 * {@code water.wetAge} nach der Regel von {@code gelaende.frag}.</li>
 * <li><b>Wasser:</b> η_Bild aus {@link Spiegelbild#etaBild} (dieselbe Regel wie das Echtzeitbild, in float relativ zum
 * Auge), wo es Wasser gibt. Eine trockene Zelle hat keine Fläche: ein Strahl, der seitlich in den Wasserkörper läuft,
 * geht hindurch wie im Echtzeitbild (gezählt in {@link #waende}).</li>
 * <li><b>Schnitt:</b> über die {@link Schranke} mit dem Wasser darüber als Pyramide: eine Zelle zählt nur, wenn der
 * Strahl (auf der gesenkten Fläche gemessen eine Parabel) in ihr unter ihre Schranke kommt; darin in Schritten der
 * Schranke, dann durch Halbieren auf 10⁻⁷ m.</li>
 * <li><b>Normalen</b> aus zentralen Differenzen über ±0,5 m [G], mit der Neigung der gesenkten Fläche.</li>
 * </ul>
 * Für viele Fäden: je Faden ein eigener Zustand.
 */
public final class Gelaendeszene implements Szene {
    /** Halbieren bis zu dieser Weite des Strahls (m); Abstand der Differenzen der Normalen (m). */
    public static final double GENAU = 1e-7, DIFF = 0.5;
    static final int OBEN = 13;

    public final Schranke schranke;
    public final Gelaende gelaende;
    public final Spiegelbild wasser;
    public final Boden boden;
    /** Auge (UTM, m NHN); Kehrwert von 2 R_eff. */
    public final double ost, nord, hAuge, k;
    /** Der örtliche Seegang (P8c, F77 A): Neigung der langen Wellen und Rest der Neigungsvarianz am Treffer; null ohne. */
    public Seegangsflaeche seegang;
    /**
     * Seit P12c3: die Gestaltkarte (Wasser der Gräben und Fethinge, Wege) wie {@code gelaende.frag}, im Mittel der Muster
     * (F143 A); null ohne.
     */
    public Gestaltfeld gestalt;
    /** Seit P10d (S8): das Sediment der Szene ({@link #sediment}); null ohne. */
    private com.dan.core.szene.Bodennetz sediment;
    private double sedimentZeit, schwebstoffSzene;
    private boolean mitDz;
    private com.dan.core.szene.Bodennetz dzNetz;
    /** Nässe: Gitter, wetAge (s), Restnässe, Abdunklung, Halbwertszeit (s) wie {@code Gelaendebild}. */
    final Gitter nassG;
    final float[] nassAlter;
    final double rest, dunkel, halbwert;
    /** Wasser je Basiszelle und je Ziegelzelle: Schranke (−∞ ohne) und Schritt. */
    final float[] wb, ws, wz, wsz;
    /** Pyramide der Basis (Stufe 0 = 256 m) und die Ziegel in 32, 64, 128 m. */
    final float[][] p = new float[OBEN - 2][];
    final float[] q0, q1, q2;
    /** Für das Attest: Schnitte, Proben, Halbierungen, Strahlen seitlich in den Wasserkörper, Starts unter dem Gelände. */
    public final LongAdder schnitte = new LongAdder(), proben = new LongAdder(), waende = new LongAdder(), startUnter = new LongAdder();
    private final ThreadLocal<Lauf> lauf;

    public Gelaendeszene(Schranke s, Spiegelbild wasser, Boden boden, Gitter nassGitter, float[] nassAlter, double rest, double dunkel, double halbwert,
                         double ost, double nord, double hAuge) {
        this.schranke = s;
        this.gelaende = s.gelaende;
        this.wasser = wasser;
        this.boden = boden;
        this.ost = ost;
        this.nord = nord;
        this.hAuge = hAuge;
        this.k = 1 / (2 * Erde.R_OPTISCH);
        this.nassG = nassGitter;
        this.nassAlter = nassAlter;
        this.rest = rest;
        this.dunkel = dunkel;
        this.halbwert = halbwert;
        int N = Schranke.N, nz = s.ziegelZahl;
        wb = new float[N * N];
        ws = new float[N * N];
        wz = new float[nz * Schranke.Z2];
        wsz = new float[nz * Schranke.Z2];
        Arrays.fill(wb, Float.NEGATIVE_INFINITY);
        Arrays.fill(wz, Float.NEGATIVE_INFINITY);
        Arrays.fill(ws, Float.POSITIVE_INFINITY);
        Arrays.fill(wsz, Float.POSITIVE_INFINITY);
        if (wasser != null) for (Spiegel sp : wasser.netze) wasserSchranke(sp);
        // Pyramide
        p[0] = new float[N * N];
        for (int c = 0; c < N * N; c++) p[0][c] = Math.max(s.hb[c], wb[c]);
        for (int l = 1; l < p.length; l++) {
            int n = N >> l, m = n * 2;
            float[] a = p[l - 1], b = new float[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++)
                    b[i + n * j] = Math.max(Math.max(a[2 * i + m * 2 * j], a[2 * i + 1 + m * 2 * j]), Math.max(a[2 * i + m * (2 * j + 1)], a[2 * i + 1 + m * (2 * j + 1)]));
            p[l] = b;
        }
        q0 = new float[nz * 64];
        q1 = new float[nz * 16];
        q2 = new float[nz * 4];
        for (int z = 0; z < nz; z++) {
            for (int c = 0; c < 64; c++) q0[z * 64 + c] = Math.max(s.hz[z * 64 + c], wz[z * 64 + c]);
            for (int j = 0; j < 4; j++)
                for (int i = 0; i < 4; i++)
                    q1[z * 16 + i + 4 * j] = max4(q0, z * 64, 8, i, j);
            for (int j = 0; j < 2; j++)
                for (int i = 0; i < 2; i++)
                    q2[z * 4 + i + 2 * j] = max4(q1, z * 16, 4, i, j);
        }
        lauf = ThreadLocal.withInitial(Lauf::new);
    }

    private static float max4(float[] a, int o, int w, int i, int j) {
        return Math.max(Math.max(a[o + 2 * i + w * 2 * j], a[o + 2 * i + 1 + w * 2 * j]), Math.max(a[o + 2 * i + w * (2 * j + 1)], a[o + 2 * i + 1 + w * (2 * j + 1)]));
    }

    /** Das Wasser eines Gitters in die Schranke: jede Zelle, die in einem der Stände nass ist, mit einer Zelle Rand. */
    private void wasserSchranke(Spiegel sp) {
        if (!sp.gesetzt()) return;
        Gitter g = sp.gitter;
        if (g.lage != Gitter.Lage.UTM) return;
        Schranke s = schranke;
        float schritt = (float) (Schranke.VIERTEL * g.dx);
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                int c = i + g.nx * j, a = sp.art[c];
                float v = Float.NEGATIVE_INFINITY;
                if ((a & Spiegel.NASS0) != 0) v = Math.max(v, sp.eta[0][c]);
                if ((a & Spiegel.NASS1) != 0) v = Math.max(v, sp.eta[1][c]);
                if (!(v > -1e29f)) continue;
                double e0 = g.x0 + (i - 1) * g.dx, e1 = g.x0 + (i + 2) * g.dx, n0 = g.y0 + (j - 1) * g.dy, n1 = g.y0 + (j + 2) * g.dy;
                int i0 = Math.max(0, s.bi(e0)), i1 = Math.min(Schranke.N - 1, s.bi(e1)), j0 = Math.max(0, s.bj(n0)), j1 = Math.min(Schranke.N - 1, s.bj(n1));
                for (int bj = j0; bj <= j1; bj++)
                    for (int bi = i0; bi <= i1; bi++) {
                        int bc = bi + Schranke.N * bj;
                        if (v > wb[bc]) wb[bc] = v;
                        if (schritt < ws[bc]) ws[bc] = schritt;
                        int zi = s.ziegel[bc];
                        if (zi < 0) continue;
                        double we = s.ost0 + bi * Schranke.BASIS, sn = s.nord0 + bj * Schranke.BASIS;
                        int a0 = Math.max(0, (int) Math.floor((e0 - we) / Schranke.FEIN)), a1 = Math.min(7, (int) Math.floor((e1 - we) / Schranke.FEIN));
                        int b0 = Math.max(0, (int) Math.floor((n0 - sn) / Schranke.FEIN)), b1 = Math.min(7, (int) Math.floor((n1 - sn) / Schranke.FEIN));
                        for (int b = b0; b <= b1; b++)
                            for (int aa = a0; aa <= a1; aa++) {
                                int kk = zi * 64 + aa + 8 * b;
                                if (v > wz[kk]) wz[kk] = v;
                                if (schritt < wsz[kk]) wsz[kk] = schritt;
                            }
                    }
            }
    }

    @Override public double augeHoehe() { return hAuge; }

    // ------------------------------------------------------------------ Flächen

    /** Höhe des Geländes (m NHN) am Ort (UTM); außerhalb aller Stufen der Meeresgrund; seit P10d (S8) mit {@code bed.dz}, wenn gesetzt. */
    public double grund(Gelaende.Abfrage a, double o, double n) {
        float h = a.hoehe(o, n, 1);
        if (Float.isNaN(h)) return Schranke.KEIN_GRUND;
        return mitDz ? h + dzNetz.dzAm(o, n) : h;
    }

    /**
     * Seit P10d (S8, F102 A): das Sediment wie im Echtzeitbild. Die Trübung am Ort gilt für jeden Pfad ab dem Punkt, an dem
     * er ins Wasser eintritt (Wege im Wasser sind Meter lang, die Zellen 20–100 m); der Boden hebt und senkt sich mit
     * {@code bed.dz}, wenn {@code dz} (wie {@code Gelaendebild.bodenHoehe}). Die Schranken heben sich dann um die größte
     * Hebung, damit kein Treffer verloren geht. Vor dem ersten Pfad aufrufen.
     *
     * @param schwebstoff der Schwebstoff der Szene (g/m³), wo das Netz nichts sagt
     * @param truebung    die Trübung am Ort nehmen (wie {@code Wasserbild.truebung})
     */
    public void sediment(com.dan.core.szene.Bodennetz b, double t, double schwebstoff, boolean truebung, boolean dz) {
        sediment = truebung && b != null && b.truebung.gesetzt() ? b : null;
        sedimentZeit = t;
        schwebstoffSzene = schwebstoff;
        boolean vorher = mitDz;
        mitDz = b != null && dz && b.boden.gesetzt();
        dzNetz = mitDz ? b : null;
        if (mitDz && !vorher) {
            float h = (float) b.dzMax();
            if (h > 0) {
                for (float[] q : p) for (int i = 0; i < q.length; i++) q[i] += h;
                for (float[] q : new float[][] {q0, q1, q2}) for (int i = 0; i < q.length; i++) q[i] += h;
            }
        }
    }

    /** Seit P10d (S8): Trübung am Eintrittspunkt p (relativ zum Auge) wie {@code truebungOrt}; NaN, wo sie der Szene gleich ist. */
    @Override public double truebung(double[] p) {
        if (sediment == null) return Double.NaN;
        double c = sediment.truebungAm(ost + p[0], nord - p[2], sedimentZeit, schwebstoffSzene, com.dan.core.szene.Bodennetz.RAND_ZELLEN);
        return Math.abs(c - schwebstoffSzene) <= 1e-9 * schwebstoffSzene ? Double.NaN : c;
    }

    /** η_Bild (m NHN) am Ort relativ zum Auge (x Ost, z Süd), NaN ohne Wasser: die Regel des Echtzeitbilds. */
    public double eta(double x, double z) { return wasser == null ? Double.NaN : wasser.etaBild((float) x, (float) z, null); }

    /** Absenkung (m) am Ort relativ zum Auge. */
    public double absenkung(double x, double z) { return k * (x * x + z * z); }

    /** Eine Abfrage des Geländes für den eigenen Faden. */
    public Gelaende.Abfrage abfrage() { return lauf.get().ab; }

    // ------------------------------------------------------------------ Schnitt

    @Override public boolean schneide(double[] p0, double[] d, double tMax, Treffer aus) {
        Lauf l = lauf.get();
        schnitte.increment();
        boolean ok = l.schneide(p0, d, tMax, aus);
        proben.add(l.proben);
        l.proben = 0;
        return ok;
    }

    /** Der Zustand eines Fadens. */
    final class Lauf {
        final Gelaende.Abfrage ab = gelaende.abfrage();
        double px, py, pz, dx, dy, dz, a, b, c;
        /** Gitter der Basis relativ zum Auge (Ost, Nord). */
        final double E0 = schranke.ost0 - ost, N0 = schranke.nord0 - nord, W = Schranke.N * Schranke.BASIS;
        double lastT, lastH, lastW;
        int startW;
        long proben;
        double hitT;
        int hitArt;

        boolean schneide(double[] p0, double[] d, double tMax, Treffer aus) {
            px = p0[0];
            py = p0[1];
            pz = p0[2];
            dx = d[0];
            dy = d[1];
            dz = d[2];
            a = k * (dx * dx + dz * dz);
            b = dy + 2 * k * (px * dx + pz * dz);
            c = py + hAuge + k * (px * px + pz * pz);
            // Anfang: Lage zu Gelände und Wasser
            lastT = 0;
            double o = ost + px, n = nord - pz;
            lastH = c - grund(ab, o, n);
            double w = eta(px, pz);
            lastW = Double.isNaN(w) ? Double.NaN : c - w;
            startW = Double.isNaN(lastW) || lastW >= 0 ? 1 : -1;
            if (lastH <= 0) startUnter.increment();
            hitT = Double.POSITIVE_INFINITY;
            // im Gitter der Basis: [tE, tA] (Ost und Nord als Platte)
            double tE = 0, tA = tMax, pn = -pz, dn = -dz;
            double[] r = platte(px, dx, E0, E0 + W, tE, tA);
            if (r != null) r = platte(pn, dn, N0, N0 + W, r[0], r[1]);
            boolean innen = r != null && r[1] > r[0];
            double vorher = innen ? r[0] : tMax;
            if (kugel(0, Math.min(vorher, tMax))) return treffer(aus);
            if (innen && besuche(OBEN, 0, 0, r[0], r[1])) return treffer(aus);
            if (innen && kugel(r[1], tMax)) return treffer(aus);
            return false;
        }

        /** Strahl x + t d gegen [lo, hi] in einer Achse, auf [t0, t1] beschnitten; null, wenn leer. */
        private double[] platte(double x, double d, double lo, double hi, double t0, double t1) {
            if (d == 0) return x >= lo && x <= hi ? new double[] {t0, t1} : null;
            double ta = (lo - x) / d, tb = (hi - x) / d;
            if (ta > tb) {
                double s = ta;
                ta = tb;
                tb = s;
            }
            double u = Math.max(t0, ta), v = Math.min(t1, tb);
            return v > u ? new double[] {u, v} : null;
        }

        double g(double t) { return (a * t + b) * t + c; }

        /** Kleinstes g auf [t0, t1]. */
        double gMin(double t0, double t1) {
            double m = Math.min(g(t0), g(t1));
            if (a > 0) {
                double tv = -b / (2 * a);
                if (tv > t0 && tv < t1) m = Math.min(m, g(tv));
            }
            return m;
        }

        /** Außerhalb des Gitters: die Kugel des Meeresgrunds; erster Abstieg unter sie auf [t0, t1]. */
        private boolean kugel(double t0, double t1) {
            if (!(t1 > t0)) return false;
            double cc = c - Schranke.KEIN_GRUND;
            if (g(t0) - Schranke.KEIN_GRUND <= 0) return false;
            double t;
            if (a > 0) {
                double D = b * b - 4 * a * cc;
                if (D < 0) return false;
                double s = Math.sqrt(D), q = -0.5 * (b + Math.copySign(s, b)), r1 = q / a, r2 = cc / q;
                t = Math.min(r1, r2);
                if (t <= t0) t = Math.max(r1, r2);
            } else {
                if (b >= 0) return false;
                t = -cc / b;
            }
            if (t <= t0 || t > t1) return false;
            hitT = t;
            hitArt = 3;
            return true;
        }

        /** Schranke einer Zelle der Stufe L (0 = 32 m … 13 = ganzes Gitter). */
        float schranke(int L, int i, int j) {
            if (L >= 3) {
                int n = Schranke.N >> (L - 3);
                return p[L - 3][i + n * j];
            }
            int sh = 3 - L, z = schranke.ziegel[(i >> sh) + Schranke.N * (j >> sh)];
            if (z < 0) return Float.NEGATIVE_INFINITY;
            int w = 8 >> L, m = w - 1;
            float[] q = L == 0 ? q0 : L == 1 ? q1 : q2;
            return q[z * w * w + (i & m) + w * (j & m)];
        }

        boolean besuche(int L, int i, int j, double t0, double t1) {
            float B = schranke(L, i, j);
            if (gMin(t0, t1) > B) return false;
            if (L == 0) {
                int z = schranke.ziegel[(i >> 3) + Schranke.N * (j >> 3)], kk = z * 64 + (i & 7) + 8 * (j & 7);
                return marsch(t0, t1, B, Math.min(schranke.sz[kk], wsz[kk]), wz[kk] > Float.NEGATIVE_INFINITY);
            }
            if (L == 3) {
                int bc = i + Schranke.N * j;
                if (schranke.ziegel[bc] < 0) return marsch(t0, t1, B, Math.min(schranke.sb[bc], ws[bc]), wb[bc] > Float.NEGATIVE_INFINITY);
            }
            double S = Schranke.FEIN * (1 << L), em = E0 + (i + 0.5) * S, nm = N0 + (j + 0.5) * S;
            double s1 = dx != 0 ? (em - px) / dx : Double.NaN, s2 = dz != 0 ? (nm + pz) / -dz : Double.NaN;
            if (!(s1 > t0 && s1 < t1)) s1 = Double.NaN;
            if (!(s2 > t0 && s2 < t1)) s2 = Double.NaN;
            if (Double.isNaN(s1)) {
                s1 = s2;
                s2 = Double.NaN;
            } else if (!Double.isNaN(s2) && s2 < s1) {
                double x = s1;
                s1 = s2;
                s2 = x;
            }
            double u = t0;
            for (int seg = 0; seg < 3; seg++) {
                double v = seg == 0 ? (Double.isNaN(s1) ? t1 : s1) : seg == 1 ? (Double.isNaN(s2) ? t1 : s2) : t1;
                if (v > u) {
                    double tm = 0.5 * (u + v);
                    int ce = px + tm * dx >= em ? 1 : 0, cn = -(pz + tm * dz) >= nm ? 1 : 0;
                    int ci = 2 * i + ce, cj = 2 * j + cn;
                    if (besuche(L - 1, ci, cj, u, v)) return true;
                }
                if (v >= t1) break;
                u = Math.max(u, v);
            }
            return false;
        }

        /** In einer Blattzelle: Teil des Strahls unter der Schranke B in Schritten s, Wasser nur wo möglich. */
        private boolean marsch(double t0, double t1, float B, double s, boolean mitWasser) {
            // wo g ≤ B
            double cb = c - B, ta, tb;
            if (a > 0) {
                double D = b * b - 4 * a * cb;
                if (D < 0) return false;
                double sq = Math.sqrt(D), q = -0.5 * (b + Math.copySign(sq, b));
                double r1 = q / a, r2 = q != 0 ? cb / q : r1;
                ta = Math.max(t0, Math.min(r1, r2));
                tb = Math.min(t1, Math.max(r1, r2));
            } else if (b != 0) {
                double r = -cb / b;
                if (b < 0) {
                    ta = Math.max(t0, r);
                    tb = t1;
                } else {
                    ta = t0;
                    tb = Math.min(t1, r);
                }
            } else {
                if (cb > 0) return false;
                ta = t0;
                tb = t1;
            }
            if (!(tb >= ta)) return false;
            double t = ta;
            while (true) {
                proben++;
                double gt = g(t), xe = px + t * dx, xz = pz + t * dz;
                double fh = gt - grund(ab, ost + xe, nord - xz), fw = Double.NaN;
                if (mitWasser) {
                    double w = eta(xe, xz);
                    if (!Double.isNaN(w)) fw = gt - w;
                }
                double th = Double.POSITIVE_INFINITY, tw = Double.POSITIVE_INFINITY;
                if (lastH > 0 && fh <= 0) th = halbiereGrund(lastT, t);
                if (!Double.isNaN(lastW) && !Double.isNaN(fw)) {
                    if (startW > 0 && lastW > 0 && fw <= 0 || startW < 0 && lastW < 0 && fw >= 0) tw = halbiereWasser(lastT, t);
                } else if (Double.isNaN(lastW) && !Double.isNaN(fw) && (startW > 0 ? fw <= 0 : fw >= 0)) waende.increment();
                if (th < Double.POSITIVE_INFINITY || tw < Double.POSITIVE_INFINITY) {
                    if (th <= tw) {
                        hitT = th;
                        hitArt = BODEN;
                    } else {
                        hitT = tw;
                        hitArt = WASSER;
                    }
                    return true;
                }
                lastT = t;
                lastH = fh;
                lastW = fw;
                if (t >= tb) return false;
                t = Math.min(tb, t + s);
            }
        }

        private double halbiereGrund(double lo, double hi) {
            while (hi - lo > GENAU) {
                double m = 0.5 * (lo + hi);
                if (g(m) - grund(ab, ost + px + m * dx, nord - pz - m * dz) > 0) lo = m;
                else hi = m;
            }
            return hi;
        }

        private double halbiereWasser(double lo, double hi) {
            while (hi - lo > GENAU) {
                double m = 0.5 * (lo + hi), w = eta(px + m * dx, pz + m * dz);
                boolean oben = Double.isNaN(w) ? startW > 0 : g(m) - w > 0;
                if (oben == startW > 0) lo = m;
                else hi = m;
            }
            return hi;
        }

        /** Treffer ausfüllen: Ort, Normale, Material. */
        private boolean treffer(Treffer aus) {
            double t = hitT, x = px + t * dx, z = pz + t * dz, o = ost + x, n = nord - z;
            aus.t = t;
            aus.sigma2 = 0;
            aus.spiegel = 0;
            aus.leuchtet = false;
            aus.aus = AUS_GELAENDE;
            double gx, gz;                                       // ∂y/∂x, ∂y/∂z der Fläche relativ zum Auge
            if (hitArt == WASSER) {
                aus.art = WASSER;
                double w0 = eta(x, z), we = eta(x + DIFF, z), ww = eta(x - DIFF, z), ws = eta(x, z + DIFF), wn = eta(x, z - DIFF);
                gx = diff(ww, w0, we);
                gz = diff(wn, w0, ws);
                Arrays.fill(aus.albedo, 0);
                if (seegang != null) {
                    double[] sg = seegang.am(o, n);
                    if (sg != null) {
                        gx += sg[0];
                        gz -= sg[1];                                    // z Süd: ∂/∂z = −∂/∂Nord
                        aus.sigma2 = sg[2];
                    }
                }
            } else {
                aus.art = BODEN;
                if (hitArt == 3) {
                    gx = 0;
                    gz = 0;
                } else {
                    gx = (grund(ab, o + DIFF, n) - grund(ab, o - DIFF, n)) / (2 * DIFF);
                    gz = -(grund(ab, o, n - DIFF) - grund(ab, o, n + DIFF)) / (2 * DIFF);   // z Süd: ∂/∂z = −∂/∂Nord
                }
                albedo(o, n, aus.albedo);
                if (gestalt != null && hitArt != 3) aus.spiegel = gestalt.anwenden(o, n, grund(ab, o, n), aus.albedo);
            }
            gx -= 2 * k * x;
            gz -= 2 * k * z;
            double l = Math.sqrt(gx * gx + 1 + gz * gz);
            aus.n[0] = -gx / l;
            aus.n[1] = 1 / l;
            aus.n[2] = -gz / l;
            return true;
        }

        private double diff(double m, double o, double p) {
            if (!Double.isNaN(m) && !Double.isNaN(p)) return (p - m) / (2 * DIFF);
            if (!Double.isNaN(p) && !Double.isNaN(o)) return (p - o) / DIFF;
            if (!Double.isNaN(m) && !Double.isNaN(o)) return (o - m) / DIFF;
            return 0;
        }
    }

    /** Albedo (linear RGB) am Ort (UTM): trocken aus {@link Boden#pixel}, dunkler mit der Nässe wie {@code gelaende.frag}. */
    public void albedo(double o, double n, double[] aus) {
        double[] px = boden.pixel(o, n);
        double f = 1 - dunkel * naesse(o, n) * Math.max(0, Math.min(1, px[3]));
        for (int c = 0; c < 3; c++) aus[c] = px[c] * f;
    }

    /**
     * Nässe (0–1) am Ort: Restnässe plus Abklingen mit dem Alter der Benetzung; außerhalb des Gitters 0. Seit C2b
     * (Werkbuch HalligHooge 138, A52) bilinear zwischen den vier Zellmitten wie {@code gelaende.frag}.
     */
    public double naesse(double o, double n) {
        if (nassG == null || nassAlter == null) return 0;
        double ux = (o - nassG.x0) / nassG.dx, uy = (n - nassG.y0) / nassG.dy;
        int i = (int) Math.floor(ux), j = (int) Math.floor(uy);
        if (i < 0 || j < 0 || i >= nassG.nx || j >= nassG.ny) return 0;
        double bx = ux - 0.5, by = uy - 0.5;
        int i0 = (int) Math.floor(bx), j0 = (int) Math.floor(by);
        double fx = bx - i0, fy = by - j0;
        double a00 = naesseZelle(i0, j0), a10 = naesseZelle(i0 + 1, j0), a01 = naesseZelle(i0, j0 + 1), a11 = naesseZelle(i0 + 1, j0 + 1);
        double e0 = a00 + (a10 - a00) * fx, e1 = a01 + (a11 - a01) * fx;
        return e0 + (e1 - e0) * fy;
    }

    private double naesseZelle(int i, int j) {
        if (i < 0 || j < 0 || i >= nassG.nx || j >= nassG.ny) return 0;
        float t = nassAlter[i + nassG.nx * j];
        if (Float.isNaN(t)) return 0;
        if (!(halbwert > 0)) return Math.max(0, Math.min(1, t));            // seit P10b: surf.wet, die Sättigung selbst
        return rest + (1 - rest) * Math.pow(2, -Math.max(t, 0) / halbwert);
    }
}
