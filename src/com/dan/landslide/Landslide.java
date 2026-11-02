package com.dan.landslide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Ein Strom aus Fels, Eis, Schnee oder Schutt als tiefengemittelte Schicht auf einem Gitter (Werkbuch 6.2;
 * LituyaBay1958 Phase 9). Kennt kein Beben, keinen Bruch und kein Wasser als Welle: wann er losgeht, woher die Masse
 * kommt ({@link #inject}) und was das Wasser daraus macht, entscheidet die App.
 * <p>
 * <b>Gleichungen</b> (Savage und Hutter 1989, Voellmy 1955), waagerecht projiziert, h senkrechte Mächtigkeit (m),
 * u waagerechte Geschwindigkeit (m/s):
 * <pre>
 *   ∂h/∂t + ∇·(h u) = 0
 *   ∂(h u)/∂t + ∇·(h u⊗u + ½ k g' h² I) = − g' h ∇b − S
 *   S = (μ g cos α + g |u_s|² / (ξ h_n)) · u/|u| (längs der Oberfläche), g' = g cos² α
 * </pre>
 * Das g' = g / (1 + |∇b|²) macht die waagerechte Projektion auf steilen Hängen richtig: ein Block gleitet reibungsfrei
 * mit g sin α längs des Hangs. k ist der Erddruckbeiwert nach Savage und Hutter (aktiv beim Auseinanderlaufen, passiv
 * beim Stauchen, {@link Material#earthPressure()}); u_s die Geschwindigkeit längs der Oberfläche, h_n = h cos α.
 * <p>
 * <b>Verfahren:</b> finite Volumen erster Ordnung, Fluss nach HLL mit trockenen Zuständen (Davis-Geschwindigkeiten,
 * 2√(k g h) an der trockenen Seite), Hangabtrieb zentral (nicht die hydrostatische Rekonstruktion: sie bremst dünne
 * Schichten auf Hängen, die steiler sind als h/dx, und das sind am Rand eines Bergsturzes auf 40° alle). Schritt nach CFL
 * ({@link #cfl}), h bleibt ≥ 0. Danach die Reibung: Turbulenz implizit, Coulomb als Bremse, die höchstens bis zum
 * Stillstand bremst – so bleibt ein Haufen liegen, wo der Hang flacher ist als δ.
 * <p>
 * <b>Wasser:</b> mit {@link #waterLevel} läuft der Strom unter Wasser mit Auftrieb (g mal 1 − ρw/ρ im untergetauchten
 * Anteil) und Formwiderstand ({@link #waterDrag}); jeder Schritt meldet, was über die Uferlinie ins Wasser geht
 * ({@link Entry}: Rate, Geschwindigkeit, Mächtigkeit, Breite) – das ist die Schnittstelle zur Welle (Phase 11).
 */
public final class Landslide {
    public enum Quality {
        LOW(8), MEDIUM(4), HIGH(2);
        public final double dx;
        Quality(double dx) { this.dx = dx; }
    }

    /** Stand des Packages: 9 = Kern in Java (LituyaBay1958 Phase 9). */
    public static final int STAGE = 9;

    /** Gelände: Höhe der Gleitbahn (m) an x, z. */
    @FunctionalInterface public interface Terrain { double heightAt(double x, double z); }

    /** Zufluss über die Uferlinie in einem Schritt. */
    public record Entry(double t, double rate, double volume, double speed, double thickness, double thicknessNormal, double width) { }

    public final int nx, nz;
    public final double dx, x0, z0;
    public String name = "Strom", source = "";
    public Material material = Material.rock();
    public double gravity = 9.81;
    /** Wasserspiegel (m); NaN: kein Wasser. */
    public double waterLevel = Double.NaN;
    public double waterDensity = 1000;
    /** Formwiderstand unter Wasser (Beiwert, ½ C ρw u² je Fläche) [G]. */
    public double waterDrag = 0.5;
    public double cfl = 0.45;
    /** Unter dieser Mächtigkeit (m) steht der Stoff. */
    public double hDry = 1e-3;
    /** Erddruck nach Savage und Hutter; aus: k = 1 (Voellmy, RAMMS). */
    public boolean earthPressure = true;
    /** Faktor auf den Druck (1); 0 ist der Grenzfall des starren Blocks ohne inneren Druck (Prüfung). */
    public double pressure = 1;
    /** Längster Schritt (s). */
    public double maxDt = 0.05;

    final double[] b, bx, bz, cos2, h, qx, qz, k;
    final double[] maxH, maxV;
    private final double[] fx0, fx1, fx2, fz0, fz1, fz2;
    private final boolean[] under;
    private final double[] rowClip, rowKe;
    private double energy, energyPeak;
    private int[] shore = new int[0];
    private double time, lost, injected, clipped, waveMax, prevUnder = Double.NaN, inWater;
    private long steps;
    private final List<Entry> entries = new ArrayList<>();
    private double crownX = Double.NaN, crownZ, crownY;

    public Landslide(int nx, int nz, double dx, double x0, double z0) {
        if (nx < 3 || nz < 3) throw new IllegalArgumentException("Strom: mindestens 3 × 3 Zellen");
        this.nx = nx; this.nz = nz; this.dx = dx; this.x0 = x0; this.z0 = z0;
        int n = nx * nz;
        b = new double[n]; bx = new double[n]; bz = new double[n]; cos2 = new double[n];
        h = new double[n]; qx = new double[n]; qz = new double[n]; k = new double[n];
        maxH = new double[n]; maxV = new double[n]; under = new boolean[n];
        rowClip = new double[nz]; rowKe = new double[nz];
        java.util.Arrays.fill(cos2, 1);
        java.util.Arrays.fill(k, 1);
        fx0 = new double[(nx + 1) * nz]; fx1 = new double[(nx + 1) * nz]; fx2 = new double[(nx + 1) * nz];
        fz0 = new double[nx * (nz + 1)]; fz1 = new double[nx * (nz + 1)]; fz2 = new double[nx * (nz + 1)];
    }

    /** Gitter über das Rechteck [xa, xb] × [za, zb] mit Zellen von dx. */
    public static Landslide over(double xa, double za, double xb, double zb, double dx) {
        return new Landslide((int) Math.ceil((xb - xa) / dx), (int) Math.ceil((zb - za) / dx), dx, xa, za);
    }

    // ---------------------------------------------------------------- Gelände, Masse

    public double cx(int i) { return x0 + (i + 0.5) * dx; }
    public double cz(int j) { return z0 + (j + 0.5) * dx; }
    public int index(int i, int j) { return j * nx + i; }

    /** Zelle an x, z oder −1. */
    public int cellAt(double x, double z) {
        int i = (int) Math.floor((x - x0) / dx), j = (int) Math.floor((z - z0) / dx);
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
    }

    /** Legt die Gleitbahn fest (Höhe in der Mitte jeder Zelle). */
    public Landslide bed(Terrain t) {
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) b[j * nx + i] = t.heightAt(cx(i), cz(j));
        return bedChanged();
    }

    /** Nach Änderungen an der Gleitbahn: Gefälle, cos² α, Wasser und Uferlinie neu. */
    public Landslide bedChanged() {
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            int il = Math.max(0, i - 1), ir = Math.min(nx - 1, i + 1), jl = Math.max(0, j - 1), jr = Math.min(nz - 1, j + 1);
            bx[c] = (b[j * nx + ir] - b[j * nx + il]) / ((ir - il) * dx);
            bz[c] = (b[jr * nx + i] - b[jl * nx + i]) / ((jr - jl) * dx);
            cos2[c] = 1 / (1 + bx[c] * bx[c] + bz[c] * bz[c]);
        }
        water(waterLevel);
        return this;
    }

    /** Setzt den Wasserspiegel (NaN: kein Wasser) und sucht die Uferlinie. */
    public Landslide water(double level) {
        waterLevel = level;
        List<Integer> s = new ArrayList<>();
        for (int c = 0; c < b.length; c++) under[c] = !Double.isNaN(level) && b[c] < level;
        if (!Double.isNaN(level)) for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            if (under[c]) continue;
            if ((i > 0 && under[c - 1]) || (i < nx - 1 && under[c + 1]) || (j > 0 && under[c - nx]) || (j < nz - 1 && under[c + nx])) s.add(c);
        }
        shore = s.stream().mapToInt(Integer::intValue).toArray();
        prevUnder = Double.NaN;
        return this;
    }

    public double bedAt(double x, double z) { int c = cellAt(x, z); return c < 0 ? Double.NaN : b[c]; }
    public double thicknessAt(double x, double z) { int c = cellAt(x, z); return c < 0 ? 0 : h[c]; }
    public double thickness(int c) { return h[c]; }
    public double bed(int c) { return b[c]; }
    public boolean underWater(int c) { return under[c]; }
    /** Geschwindigkeit längs der Oberfläche (m/s) in der Zelle c. */
    public double speed(int c) { return h[c] <= hDry ? 0 : surfaceSpeed(c, qx[c] / h[c], qz[c] / h[c]); }
    public double[] velocity(int c) { return h[c] <= hDry ? new double[2] : new double[]{qx[c] / h[c], qz[c] / h[c]}; }
    public double maxThickness(int c) { return maxH[c]; }
    public double maxSpeed(int c) { return maxV[c]; }
    /** Mächtigkeit senkrecht zur Gleitbahn (m). */
    public double normalThickness(int c) { return h[c] * Math.sqrt(cos2[c]); }

    double surfaceSpeed(int c, double u, double v) { double w = u * bx[c] + v * bz[c]; return Math.sqrt(u * u + v * v + w * w); }

    /**
     * Legt eine Platte auf die Gleitbahn: Mitte (cx, cz), waagerechte Richtung hangabwärts (dirX, dirZ), Länge längs
     * des Hangs, Breite quer, größte Mächtigkeit senkrecht zum Hang hMax, Form (1 − |ũ|^p)(1 − |ṽ|^p). Gibt das Volumen
     * zurück (m³). Merkt die Abrisskante für die Fahrböschung.
     */
    public double addPlate(double cx, double cz, double dirX, double dirZ, double length, double width, double hMax, double p) {
        double l = Math.hypot(dirX, dirZ), ux = dirX / l, uz = dirZ / l, vol = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            double ex = cx(i) - cx, ez = cz(j) - cz;
            double a = ex * ux + ez * uz, q = -ex * uz + ez * ux;
            double slope = bx[c] * ux + bz[c] * uz;                     // Gefälle in Plattenrichtung (negativ: abwärts)
            double s = a * Math.sqrt(1 + slope * slope);                // längs des Hangs
            double tu = Math.abs(2 * s / length), tv = Math.abs(2 * q / width);
            if (tu >= 1 || tv >= 1) continue;
            double t = hMax * (1 - Math.pow(tu, p)) * (1 - Math.pow(tv, p));
            double hv = t / Math.sqrt(cos2[c]);
            h[c] += hv;
            vol += hv * dx * dx;
        }
        crown();
        return vol;
    }

    /** Skaliert alle Mächtigkeiten auf das Volumen v (m³). */
    public void scaleTo(double v) {
        double f = v / volume();
        for (int c = 0; c < h.length; c++) { h[c] *= f; qx[c] *= f; qz[c] *= f; }
    }

    /** Merkt die Abrisskante: der höchste Punkt der Gleitbahn unter Stoff (für die Fahrböschung). */
    public void crown() {
        double best = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            if (h[c] > 0.5 && b[c] > best) { best = b[c]; crownX = cx(i); crownZ = cz(j); crownY = b[c]; }
        }
    }

    /**
     * Masse kommt dazu (Übergabe aus einem Bruch, Werkbuch 6.2): Volumen (m³) an x, z mit der waagerechten
     * Geschwindigkeit vx, vz (m/s). Außerhalb des Gitters: nichts (gibt false).
     */
    public synchronized boolean inject(double x, double z, double volume, double vx, double vz) {
        int c = cellAt(x, z);
        if (c < 0 || !(volume > 0)) return false;
        double dh = volume / (dx * dx);
        h[c] += dh; qx[c] += dh * vx; qz[c] += dh * vz;
        injected += volume;
        if (Double.isNaN(crownX) || b[c] > crownY) { crownX = cx(c % nx); crownZ = cz(c / nx); crownY = b[c]; }
        return true;
    }

    /**
     * Wie {@link #inject(double, double, double, double, double)}, aber gleichmäßig auf die Zellen im Kreis mit dem
     * Halbmesser r (m) verteilt – für große Brocken (eine Zelle aus einem Bruch mit 50 000 m³ wäre in einer Zelle von
     * 4 m eine Säule von 3 km). Gibt das angekommene Volumen zurück.
     */
    public synchronized double inject(double x, double z, double volume, double vx, double vz, double r) {
        if (r <= dx) return inject(x, z, volume, vx, vz) ? volume : 0;
        int i0 = (int) Math.floor((x - r - x0) / dx), i1 = (int) Math.floor((x + r - x0) / dx);
        int j0 = (int) Math.floor((z - r - z0) / dx), j1 = (int) Math.floor((z + r - z0) / dx);
        List<Integer> in = new ArrayList<>();
        for (int j = Math.max(0, j0); j <= Math.min(nz - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(nx - 1, i1); i++)
                if (Math.hypot(cx(i) - x, cz(j) - z) <= r) in.add(j * nx + i);
        if (in.isEmpty()) return inject(x, z, volume, vx, vz) ? volume : 0;
        double part = volume / in.size();
        for (int c : in) inject(cx(c % nx), cz(c / nx), part, vx, vz);
        return volume;
    }

    // ---------------------------------------------------------------- Rechnung

    public double time() { return time; }
    public long steps() { return steps; }
    /** Volumen im Gitter (m³). */
    public double volume() { double s = 0; for (double v : h) s += v; return s * dx * dx; }
    /** Über den Rand hinaus (m³). */
    public double lost() { return lost; }
    /** Über {@link #inject} dazugekommen (m³). */
    public double injected() { return injected; }
    /** Durch Abschneiden negativer Mächtigkeit (Rundung) verloren (m³). */
    public double clipped() { return clipped; }
    public List<Entry> entries() { return Collections.unmodifiableList(entries); }
    /** Volumen, das bisher über die Uferlinie ins Wasser ging (m³). */
    public double inWater() { return inWater; }

    /** Ein Schritt mit der Länge nach CFL (höchstens {@link #maxDt}); gibt die Länge zurück. */
    public synchronized double step() { return step(maxDt); }

    /** Ein Schritt, höchstens dtMax lang. */
    public synchronized double step(double dtMax) {
        final double g = gravity;
        final double[] kap = material.earthPressure();
        // Erddruck je Zelle aus der Divergenz, Wellengeschwindigkeit
        double amax = IntStream.range(0, nz).parallel().mapToDouble(j -> {
            double m = 0;
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (h[c] <= hDry) { k[c] = 1; continue; }
                if (earthPressure) {
                    double ul = i > 0 && h[c - 1] > hDry ? qx[c - 1] / h[c - 1] : qx[c] / h[c];
                    double ur = i < nx - 1 && h[c + 1] > hDry ? qx[c + 1] / h[c + 1] : qx[c] / h[c];
                    double vl = j > 0 && h[c - nx] > hDry ? qz[c - nx] / h[c - nx] : qz[c] / h[c];
                    double vr = j < nz - 1 && h[c + nx] > hDry ? qz[c + nx] / h[c + nx] : qz[c] / h[c];
                    k[c] = (ur - ul) + (vr - vl) >= 0 ? kap[0] : kap[1];
                } else k[c] = 1;
                double cc = Math.sqrt(pressure * k[c] * gEff(c, g) * h[c]);
                m = Math.max(m, Math.abs(qx[c] / h[c]) + Math.abs(qz[c] / h[c]) + 2 * cc);
            }
            return m;
        }).max().orElse(0);
        waveMax = amax;
        double dt = amax > 0 ? Math.min(dtMax, cfl * dx / amax) : dtMax;
        final double r = dt / dx;
        // Flüsse über die senkrechten (x) und waagerechten (z) Kanten
        IntStream.range(0, nz).parallel().forEach(j -> {
            double[] o = new double[3];
            for (int i = 0; i <= nx; i++) {
                if (i == 0) edge(j * nx, true, -1, g, o);
                else if (i == nx) edge(j * nx + nx - 1, true, 1, g, o);
                else hll(j * nx + i - 1, j * nx + i, true, g, o);
                int f = j * (nx + 1) + i;
                fx0[f] = o[0]; fx1[f] = o[1]; fx2[f] = o[2];
            }
        });
        IntStream.range(0, nz + 1).parallel().forEach(j -> {
            double[] o = new double[3];
            for (int i = 0; i < nx; i++) {
                if (j == 0) edge(i, false, -1, g, o);
                else if (j == nz) edge((nz - 1) * nx + i, false, 1, g, o);
                else hll((j - 1) * nx + i, j * nx + i, false, g, o);
                int f = j * nx + i;
                fz0[f] = o[0]; fz1[f] = o[1]; fz2[f] = o[2];
            }
        });
        // was über den Rand geht
        double out = 0;
        for (int j = 0; j < nz; j++) out += Math.max(0, -fx0[j * (nx + 1)]) + Math.max(0, fx0[j * (nx + 1) + nx]);
        for (int i = 0; i < nx; i++) out += Math.max(0, -fz0[i]) + Math.max(0, fz0[nz * nx + i]);
        lost += out * dt * dx;
        final double mu = material.mu, xi = material.xi, rho = material.density;
        IntStream.range(0, nz).parallel().forEach(j -> {
            double cl = 0, ke = 0;
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i, fl = j * (nx + 1) + i, fr = fl + 1, fu = j * nx + i, fd = fu + nx;
                double h0 = h[c];
                double hn = h0 - r * (fx0[fr] - fx0[fl] + fz0[fd] - fz0[fu]);
                double un = qx[c] - r * (fx1[fr] - fx1[fl] + fz2[fd] - fz2[fu]);
                double vn = qz[c] - r * (fx2[fr] - fx2[fl] + fz1[fd] - fz1[fu]);
                if (hn < 0) { cl -= hn; hn = 0; }
                if (h0 > hDry) {
                    double ge = gEff(c, g);
                    un -= dt * ge * h0 * bx[c];
                    vn -= dt * ge * h0 * bz[c];
                }
                if (hn <= hDry) { h[c] = hn; qx[c] = 0; qz[c] = 0; continue; }
                double u = un / hn, v = vn / hn, us = surfaceSpeed(c, u, v);
                if (us > 0) {
                    double cosa = Math.sqrt(cos2[c]), sub = submerged(c, hn), buoy = 1 - sub * waterDensity / rho;
                    double ixi = 1 / xi + sub * waterDrag * waterDensity / (2 * rho * g);
                    double hnorm = hn * cosa;
                    double s1 = us / (1 + dt * g * us * ixi / hnorm);              // Turbulenz, implizit
                    double s2 = Math.max(0, s1 - dt * mu * g * cosa * buoy);        // Coulomb, bremst bis zum Halt
                    double f = s2 / us;
                    u *= f; v *= f; us = s2;
                }
                h[c] = hn; qx[c] = u * hn; qz[c] = v * hn;
                ke += hn * us * us;
                if (hn > maxH[c]) maxH[c] = hn;
                if (us > maxV[c]) maxV[c] = us;
            }
            rowClip[j] = cl;
            rowKe[j] = ke;
        });
        double clip = 0, ke = 0;
        for (int j = 0; j < nz; j++) { clip += rowClip[j]; ke += rowKe[j]; }
        clipped += clip * dx * dx;
        energy = 0.5 * ke * dx * dx * material.density;
        energyPeak = Math.max(energyPeak, energy);
        time += dt;
        steps++;
        if (shore.length > 0) gauge(dt);
        return dt;
    }

    /** Anteil der Zelle unter Wasser (0..1). */
    double submerged(int c, double hc) {
        if (Double.isNaN(waterLevel) || hc <= 0) return 0;
        return Math.max(0, Math.min(1, (waterLevel - b[c]) / hc));
    }

    double gEff(int c, double g) {
        double s = submerged(c, h[c]);
        return g * cos2[c] * (s > 0 ? 1 - s * waterDensity / material.density : 1);
    }

    /** HLL zwischen den Zellen l und r; xDir: Kante senkrecht zu x. Ergebnis: Masse, Normalimpuls, Tangentialimpuls. */
    private void hll(int l, int r, boolean xDir, double g, double[] o) {
        double hl = h[l], hr = h[r];
        if (hl <= hDry && hr <= hDry) { o[0] = o[1] = o[2] = 0; return; }
        double ul = 0, vl = 0, ur = 0, vr = 0;
        if (hl > hDry) { ul = (xDir ? qx[l] : qz[l]) / hl; vl = (xDir ? qz[l] : qx[l]) / hl; } else hl = 0;
        if (hr > hDry) { ur = (xDir ? qx[r] : qz[r]) / hr; vr = (xDir ? qz[r] : qx[r]) / hr; } else hr = 0;
        double pl = pressure * k[l] * gEff(l, g), pr = pressure * k[r] * gEff(r, g);
        double cl = Math.sqrt(pl * hl), cr = Math.sqrt(pr * hr), sl, sr;
        if (hl == 0) { sl = ur - 2 * cr; sr = ur + cr; }
        else if (hr == 0) { sl = ul - cl; sr = ul + 2 * cl; }
        else { sl = Math.min(ul - cl, ur - cr); sr = Math.max(ul + cl, ur + cr); }
        double f0l = hl * ul, f1l = hl * ul * ul + 0.5 * pl * hl * hl, f2l = hl * ul * vl;
        double f0r = hr * ur, f1r = hr * ur * ur + 0.5 * pr * hr * hr, f2r = hr * ur * vr;
        if (sl >= 0) { o[0] = f0l; o[1] = f1l; o[2] = f2l; }
        else if (sr <= 0) { o[0] = f0r; o[1] = f1r; o[2] = f2r; }
        else {
            double d = 1 / (sr - sl), ss = sl * sr;
            o[0] = (sr * f0l - sl * f0r + ss * (hr - hl)) * d;
            o[1] = (sr * f1l - sl * f1r + ss * (hr * ur - hl * ul)) * d;
            o[2] = (sr * f2l - sl * f2r + ss * (hr * vr - hl * vl)) * d;
        }
    }

    /** Rand: Stoff, der hinaus will, geht (frei); sonst eine Wand (nur der Druck). side −1: links/oben, +1: rechts/unten. */
    private void edge(int c, boolean xDir, int side, double g, double[] o) {
        double hc = h[c];
        if (hc <= hDry) { o[0] = o[1] = o[2] = 0; return; }
        double u = (xDir ? qx[c] : qz[c]) / hc, v = (xDir ? qz[c] : qx[c]) / hc, p = 0.5 * pressure * k[c] * gEff(c, g) * hc * hc;
        if (u * side > 0) { o[0] = hc * u; o[1] = hc * u * u + p; o[2] = hc * u * v; }
        else { o[0] = 0; o[1] = p; o[2] = 0; }
    }

    /** Uferlinie: was ins Wasser geht. */
    private void gauge(double dt) {
        double vu = 0;
        for (int c = 0; c < h.length; c++) if (under[c]) vu += h[c];
        vu *= dx * dx;
        if (Double.isNaN(prevUnder)) prevUnder = vu;
        double rate = (vu - prevUnder) / dt;
        prevUnder = vu;
        inWater += Math.max(0, rate) * dt;
        double m = 0, ms = 0, th = 0, tn = 0;
        int wet = 0;
        for (int c : shore) {
            if (h[c] < 0.5) continue;
            wet++;
            double us = speed(c);
            m += h[c]; ms += h[c] * us;
            th = Math.max(th, h[c]); tn = Math.max(tn, normalThickness(c));
        }
        entries.add(new Entry(time, rate, inWater, m > 0 ? ms / m : 0, th, tn, wet * dx));
    }

    // ---------------------------------------------------------------- Auswertung

    /** Rechnet bis t (s) oder bis alles steht. */
    public void run(double tEnd) { while (time < tEnd && !atRest()) step(Math.min(maxDt, tEnd - time + 1e-9)); }

    /** Rechnet bis t (s) oder bis der Strom zur Ruhe gekommen ist ({@link #settled()}). */
    public void runToRest(double tEnd) { while (time < tEnd && !settled()) step(Math.min(maxDt, tEnd - time + 1e-9)); }

    /**
     * Zur Ruhe gekommen: die Bewegungsenergie ist unter {@link #settleFraction} ihrer Spitze. Was dann noch kriecht (dünne
     * Lagen auf dem Hang, die Ablagerung, die unter dem Erddruck ausläuft), ändert die Reichweite kaum.
     */
    public boolean settled() { return steps > 10 && energyPeak > 0 && energy < settleFraction * energyPeak; }

    /** Anteil der größten Bewegungsenergie, unter dem der Strom als zur Ruhe gekommen gilt. */
    public double settleFraction = 1e-3;

    /** Steht alles (kein Stoff über 0,5 m schneller als {@link #restSpeed})? Erst nach dem ersten Schritt. */
    public boolean atRest() {
        if (steps == 0) return false;
        for (int c = 0; c < h.length; c++) if (h[c] > 0.5 && speed(c) > restSpeed) return false;
        return true;
    }

    /** Ab hier gilt der Stoff als still (m/s). */
    public double restSpeed = 0.1;

    public double maxSpeedNow() {
        double m = 0;
        for (int c = 0; c < h.length; c++) if (h[c] > 0.05) m = Math.max(m, speed(c));
        return m;
    }

    /** Mittlere Geschwindigkeit längs der Oberfläche, nach Masse gewichtet (m/s). */
    public double meanSpeed() {
        double m = 0, s = 0;
        for (int c = 0; c < h.length; c++) if (h[c] > hDry) { m += h[c]; s += h[c] * speed(c); }
        return m > 0 ? s / m : 0;
    }

    /** Schwerpunkt: x, z, Höhe (Gleitbahn + halbe Mächtigkeit). */
    public double[] centroid() {
        double m = 0, x = 0, z = 0, y = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            if (h[c] <= hDry) continue;
            m += h[c]; x += h[c] * cx(i); z += h[c] * cz(j); y += h[c] * (b[c] + h[c] / 2);
        }
        return m > 0 ? new double[]{x / m, z / m, y / m} : new double[]{Double.NaN, Double.NaN, Double.NaN};
    }

    /** Bewegungsenergie nach dem letzten Schritt (J) und ihre Spitze. */
    public double kineticEnergy() { return energy; }
    public double kineticEnergyPeak() { return energyPeak; }

    /** Abrisskante: x, z, Höhe; NaN vor dem ersten Stoff. */
    public double[] crownPoint() { return new double[]{crownX, crownZ, crownY}; }

    /**
     * Fahrböschung nach HEIM32: Winkel (Grad) der Linie von der Abrisskante zur weitesten Spitze der Ablagerung
     * (mächtiger als hMin), dazu H und L (m) und die Spitze x, z.
     */
    public double[] fahrboeschung(double hMin) {
        double best = -1, tx = 0, tz = 0, ty = 0;
        for (int j = 0; j < nz; j++) for (int i = 0; i < nx; i++) {
            int c = j * nx + i;
            if (h[c] < hMin) continue;
            double d = Math.hypot(cx(i) - crownX, cz(j) - crownZ);
            if (d > best) { best = d; tx = cx(i); tz = cz(j); ty = b[c]; }
        }
        double hh = crownY - ty;
        return new double[]{Math.toDegrees(Math.atan2(hh, best)), hh, best, tx, tz};
    }

    /** Fahrböschung nach der Volumenregel von SCHEIDEGGER73 (Grad): log10(H/L) = −0,15666 log10 V + 0,62419. */
    public static double scheidegger(double volume) { return Math.toDegrees(Math.atan(Math.pow(10, -0.15666 * Math.log10(volume) + 0.62419))); }

    /** Größte Wellengeschwindigkeit des letzten Schritts (m/s). */
    public double waveSpeed() { return waveMax; }

    /** Kurzer Stand. */
    public String status() {
        double[] c = centroid();
        return String.format(Locale.GERMAN, "%s t %.1f s: V %.3e m³, v̄ %.1f m/s, max %.1f m/s, Schwerpunkt %.0f m ü. M., im Wasser %.3e m³",
                name, time, volume(), meanSpeed(), maxSpeedNow(), c[2], inWater);
    }

    // ---------------------------------------------------------------- Kennwerte des Zuflusses

    /**
     * Zusammenfassung des Zuflusses über die Uferlinie: Anfang und Ende (5 % und 95 % des Volumens im Wasser, s), Dauer,
     * nach Rate gewichtete Geschwindigkeit (m/s), größte Geschwindigkeit, größte Mächtigkeit senkrecht (m) und normal,
     * größte Breite (m), Volumen im Wasser (m³), größte Rate (m³/s).
     */
    public double[] entrySummary() {
        double vEnd = inWater, t5 = Double.NaN, t95 = Double.NaN, rs = 0, r = 0, vmax = 0, th = 0, tn = 0, w = 0, rmax = 0;
        double prevT = 0;
        for (Entry e : entries) {
            double dt = e.t() - prevT;
            prevT = e.t();
            if (Double.isNaN(t5) && e.volume() >= 0.05 * vEnd) t5 = e.t();
            if (Double.isNaN(t95) && e.volume() >= 0.95 * vEnd) t95 = e.t();
            if (e.rate() > 0 && e.speed() > 0) { rs += e.rate() * dt * e.speed(); r += e.rate() * dt; }
            vmax = Math.max(vmax, e.speed()); th = Math.max(th, e.thickness()); tn = Math.max(tn, e.thicknessNormal());
            w = Math.max(w, e.width()); rmax = Math.max(rmax, e.rate());
        }
        return new double[]{t5, t95, t95 - t5, r > 0 ? rs / r : 0, vmax, th, tn, w, vEnd, rmax};
    }
}
