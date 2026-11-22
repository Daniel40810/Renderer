package com.dan.render.reference;

/**
 * Eine einfache Szene für Prüfungen (P7b): ein waagerechter Boden (Lambert) und darüber wahlweise eine waagerechte
 * Wasserfläche, unendlich ausgedehnt, ohne Erdkrümmung. Höhen relativ zum Auge (m). Mit {@link #kugel} (seit P7e) ist
 * der Boden die Kugel um die Mitte der Luft ({@link Medien}), wie im Echtzeitbild: In der Dämmerung scheint die Sonne
 * über die Kimm in die hohe Luft, was eine unendliche Ebene abschatten würde. Seit P10d (F105 A) ist dann auch das Wasser
 * eine Kugel.
 */
public final class Ebenen implements Szene {
    /** Höhe des Bodens und des Wassers relativ zum Auge (NaN: ohne). */
    public final double boden, wasser;
    public final double[] albedo;
    public double sigma2;
    /** Boden als Kugel (Radius Boden der Luft + Höhe des Auges + boden, Mitte unter dem Auge) statt Ebene. */
    public boolean kugel;
    /**
     * Seit P10d (Messung f): Neigung der Wasserfläche am Ort (x Ost, z Süd relativ zum Auge) als {∂η/∂Ost, ∂η/∂Nord,
     * zusätzliche Neigungsvarianz der Facetten}, wie {@code Seegangsflaeche.am} in {@link Gelaendeszene}; die Fläche
     * bleibt eben, nur ihre Normale kippt. null: ohne.
     */
    public java.util.function.BiFunction<Double, Double, double[]> neigung;
    final double hAuge;

    public Ebenen(double boden, double wasser, double[] albedo, double hAuge) {
        this.boden = boden;
        this.wasser = wasser;
        this.albedo = albedo.clone();
        this.hAuge = hAuge;
    }

    @Override public double augeHoehe() { return hAuge; }

    @Override public boolean schneide(double[] p, double[] d, double tMax, Treffer aus) {
        double best = tMax;
        int art = 0;
        double[] nk = null;
        if (!Double.isNaN(boden) && kugel) {
            double rb = Medien.R + hAuge + boden, cy = -(Medien.R + hAuge);
            double ox = p[0], oy = p[1] - cy, oz = p[2];
            double ro = Math.sqrt(ox * ox + oy * oy + oz * oz), b = ox * d[0] + oy * d[1] + oz * d[2];
            double cc = (ro - rb) * (ro + rb), disc = b * b - cc;
            if (disc >= 0 && b < 0) {
                double t = cc / (-b + Math.sqrt(disc));             // erste Lösung, ohne Auslöschung
                if (t > 1e-9 && t < best) {
                    best = t;
                    art = BODEN;
                    double qx = ox + d[0] * t, qy = oy + d[1] * t, qz = oz + d[2] * t, qr = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    nk = new double[] {qx / qr, qy / qr, qz / qr};
                }
            }
        } else if (!Double.isNaN(boden) && d[1] != 0) {
            double t = (boden - p[1]) / d[1];
            if (t > 1e-9 && t < best) {
                best = t;
                art = BODEN;
            }
        }
        if (!Double.isNaN(wasser) && kugel) {
            // seit P10d (F105 A): mit {@link #kugel} auch das Wasser als Kugel (von oben und von unten)
            double rw = Medien.R + hAuge + wasser, cy = -(Medien.R + hAuge);
            double ox = p[0], oy = p[1] - cy, oz = p[2];
            double ro = Math.sqrt(ox * ox + oy * oy + oz * oz), b = ox * d[0] + oy * d[1] + oz * d[2];
            double cc = (ro - rw) * (ro + rw), disc = b * b - cc;
            if (disc >= 0) {
                double sq = Math.sqrt(disc), t1 = b < 0 ? cc / (-b + sq) : -b - sq, t2 = b < 0 ? -b + sq : cc / (-b - sq);
                double t = t1 > 1e-9 ? t1 : t2;
                if (t > 1e-9 && t < best) {
                    best = t;
                    art = WASSER;
                    double qx = ox + d[0] * t, qy = oy + d[1] * t, qz = oz + d[2] * t, qr = Math.sqrt(qx * qx + qy * qy + qz * qz);
                    nk = new double[] {qx / qr, qy / qr, qz / qr};
                }
            }
        } else if (!Double.isNaN(wasser) && d[1] != 0) {
            double t = (wasser - p[1]) / d[1];
            if (t > 1e-9 && t < best) {
                best = t;
                art = WASSER;
                nk = null;
            }
        }
        if (art == 0) return false;
        aus.t = best;
        aus.art = art;
        aus.n[0] = nk == null ? 0 : nk[0];
        aus.n[1] = nk == null ? 1 : nk[1];
        aus.n[2] = nk == null ? 0 : nk[2];
        System.arraycopy(albedo, 0, aus.albedo, 0, 3);
        aus.sigma2 = sigma2;
        if (art == WASSER && neigung != null) {
            double[] g = neigung.apply(p[0] + d[0] * best, p[2] + d[2] * best);
            if (g != null) {
                double gx = g[0], gz = -g[1], l = Math.sqrt(gx * gx + 1 + gz * gz);   // z Süd: ∂/∂z = −∂/∂Nord
                aus.n[0] = -gx / l;
                aus.n[1] = 1 / l;
                aus.n[2] = -gz / l;
                aus.sigma2 = sigma2 + g[2];
            }
        }
        return true;
    }
}
