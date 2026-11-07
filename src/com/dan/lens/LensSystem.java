package com.dan.lens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Ein Linsenschnitt: Flächen in Lichtrichtung, Scheitel auf der Achse bei z (mm, erste Fläche bei 0), dahinter der Sensor.
 * Gerechnet wird auf zwei Arten:
 * <ul>
 * <li><b>paraxial</b> (Gauß’sche Optik, y-nu-Verfahren): Brennweite, Schnittweite, Eintrittspupille, Fokussieren durch
 * Verschieben des ganzen Objektivs, Fokusatmung, Längsfarbfehler, Vignettierung durch die Fassungen und die
 * <b>Geisterbilder</b>. Spiegelungen rechnet es wie in den Programmen der Optikrechner mit umgekehrtem Vorzeichen der
 * Brechzahl (n' = −n): nach einer Spiegelung läuft der Strahl rückwärts, Abstände und Brechzahlen sind negativ, die Formeln
 * bleiben dieselben (Brechung n'u' = nu − y·(n' − n)·c, Übertragung y' = y + Δz·u).</li>
 * <li><b>mit echten Strahlen</b> (Snellius in Vektorform an Kugelflächen): Verzeichnung, Farbquerfehler, sphärische
 * Aberration und die Prüfung der paraxialen Werte.</li>
 * </ul>
 * Ein Geisterbild entsteht, wenn Licht an Fläche j zurück und an Fläche i &lt; j wieder nach vorn gespiegelt wird; bei
 * F Flächen ohne die Blende gibt es F·(F−1)/2. Weil die Rechnung linear ist, ist jedes ein Bild der Blende: Für einfallendes
 * paralleles Licht unter dem Winkel θ gilt an der Blende y_B = A·y + B·θ, auf dem Sensor y_S = C·y + D·θ; durch die Blende
 * kommen die Strahlen mit |y_B| ≤ r_B, auf dem Sensor liegt also die Blende, um C/A vergrößert, bei (D − C·B/A)·θ (nach
 * Hullin u. a. 2011 und Lee und Eisemann 2013).
 */
public final class LensSystem {
    public final String name;
    public final List<Surface> surfaces;
    /** Scheitel der Flächen auf der Achse (mm). */
    public final double[] z;
    public final int stop;

    public LensSystem(String name, List<Surface> s) {
        this.name = name;
        this.surfaces = Collections.unmodifiableList(new ArrayList<>(s));
        this.z = new double[s.size()];
        int st = -1;
        for (int i = 0; i < s.size(); i++) {
            if (i > 0) z[i] = z[i - 1] + s.get(i - 1).thickness();
            if (s.get(i).stop()) st = i;
        }
        if (st < 0) throw new IllegalArgumentException(name + ": keine Blende");
        this.stop = st;
    }

    /**
     * Doppel-Gauß 50 mm f/2 (W. Tronnier, US-Patent 2 673 491; aus W. J. Smith, „Modern Lens Design“, auf 50 mm
     * gebracht; derselbe Schnitt wie im Renderer pbrt). Brechzahlen aus dem Patent, Gläser mit diesen Brechzahlen aus dem
     * Katalog (für die Abbe-Zahlen [G]). Das ist das Normalobjektiv schlechthin: so sind Planar, Summicron und Xenon gebaut.
     */
    public static LensSystem doubleGauss50(Coating c) {
        List<Surface> s = new ArrayList<>();
        s.add(Surface.glass(29.475, 3.76, Glass.BAF10, 25.2, c));
        s.add(Surface.glass(84.83, 0.12, Glass.AIR, 25.2, c));
        s.add(Surface.glass(19.275, 4.025, Glass.BAF10, 23.0, c));
        s.add(Surface.glass(40.77, 3.275, Glass.SF15, 23.0, c));
        s.add(Surface.glass(12.75, 5.705, Glass.AIR, 18.0, c));
        s.add(Surface.stop(4.5, 17.1));
        s.add(Surface.glass(-14.495, 1.18, Glass.F5, 17.0, c));
        s.add(Surface.glass(40.77, 6.065, Glass.SSK5, 20.0, c));
        s.add(Surface.glass(-20.385, 0.19, Glass.AIR, 20.0, c));
        s.add(Surface.glass(437.065, 3.22, Glass.LAF3, 20.0, c));
        s.add(Surface.glass(-39.73, 5.0, Glass.AIR, 20.0, c));
        return new LensSystem("Doppel-Gauß 50 mm f/2 (Tronnier)", s);
    }

    /**
     * Weitwinkel für ±38° (Nakamura; W. J. Smith, „Modern Lens Design“; derselbe Schnitt wie „wide.22mm“ in pbrt). Gerechnet
     * f = 15,5 mm, f/1,9: negativer Meniskus vorn, dahinter ein verkitteter Lanthan-Achromat, die Blende, zwei Glieder
     * hinten mit einem verkitteten N-LAK8/SF6. Der Schnitt nennt nur n_d; Gläser mit diesen Brechzahlen aus dem Katalog,
     * wo es eindeutig ist, sonst mit angenommener Abbe-Zahl [G]. Tonnenverzeichnung −6 % bei 38°.
     */
    public static LensSystem wideAngle(Coating c) {
        List<Surface> s = new ArrayList<>();
        s.add(Surface.glass(35.98738, 1.21638, Glass.of("n 1,540 [G]", 1.540, 59.7), 23.716, c));
        s.add(Surface.glass(11.69718, 9.9957, Glass.AIR, 17.996, c));
        s.add(Surface.glass(13.08714, 5.12622, Glass.LAF34, 12.364, c));
        s.add(Surface.glass(-22.63294, 1.76924, Glass.F2, 9.812, c));
        s.add(Surface.glass(71.05802, 0.8184, Glass.AIR, 9.152, c));
        s.add(Surface.stop(2.27766, 8.756));
        s.add(Surface.glass(-9.58584, 2.43254, Glass.of("n 1,617 [G]", 1.617, 54.0), 8.184, c));
        s.add(Surface.glass(-11.28864, 0.11506, Glass.AIR, 9.152, c));
        s.add(Surface.glass(-166.7765, 3.09606, Glass.LAK8, 10.648, c));
        s.add(Surface.glass(-7.5911, 1.32682, Glass.SF6, 11.44, c));
        s.add(Surface.glass(-16.7662, 3.98068, Glass.AIR, 12.276, c));
        return new LensSystem("Weitwinkel ±38° (Nakamura)", s);
    }

    /**
     * Teleobjektiv 250 mm f/5,6 (W. J. Smith, „Modern Lens Design“; derselbe Schnitt wie „telephoto.250mm“ in pbrt): ein
     * positiver verkitteter Achromat vorn, die Blende, ein negativer Achromat hinten; die Baulänge ist kürzer als die
     * Brennweite. Gerechnet f = 250,1 mm, f/5,4. Gläser: Kron [G], Flint [G], F3, N-SK14. Kissen +0,5 % bei 5°.
     */
    public static LensSystem telephoto250(Coating c) {
        List<Surface> s = new ArrayList<>();
        s.add(Surface.glass(54.6275, 12.52, Glass.of("n 1,529 [G]", 1.529, 60.0), 47.5, c));
        s.add(Surface.glass(-86.365, 3.755, Glass.of("n 1,599 [G]", 1.599, 39.0), 44.5, c));
        s.add(Surface.glass(271.7625, 2.8175, Glass.AIR, 41.5, c));
        s.add(Surface.stop(67.4125, 40.5));
        s.add(Surface.glass(-32.1, 3.755, Glass.F3, 31.5, c));
        s.add(Surface.glass(49.5325, 12.52, Glass.SK14, 33.5, c));
        s.add(Surface.glass(-50.9725, 0, Glass.AIR, 37.0, c));
        return new LensSystem("Tele 250 mm f/5,6", s);
    }

    /** Derselbe Schnitt um k vergrößert (Brennweite mal k, Bildwinkel und Fehler im Verhältnis gleich). */
    public LensSystem scaled(double k) {
        List<Surface> s = new ArrayList<>();
        for (Surface f : surfaces) s.add(f.scaled(k));
        return new LensSystem(name, s);
    }

    /**
     * Dieselben Flächen mit um k größeren Fassungen (die Blende bleibt): ein Objektiv derselben Rechnung für einen größeren
     * Bildkreis. Die Fehler bleiben, die Vignettierung durch die Fassungen wird kleiner.
     */
    public LensSystem covering(double k) {
        List<Surface> s = new ArrayList<>();
        for (Surface f : surfaces) s.add(f.stop() ? f : new Surface(f.radius(), f.thickness(), f.glass(), f.semiAperture() * k, false, f.coating()));
        return new LensSystem(name, s);
    }

    public LensSystem coated(Coating c) {
        List<Surface> s = new ArrayList<>();
        for (Surface f : surfaces) s.add(f.stop() ? f : f.withCoating(c));
        return new LensSystem(name, s);
    }

    public int size() { return surfaces.size(); }

    /** Brechzahl hinter Fläche k bei λ (k = −1: Luft davor). */
    public double n(int k, double lambda) { return k < 0 ? 1.0 : surfaces.get(k).glass().n(lambda); }

    /** Lage der letzten Fläche (mm). */
    public double back() { return z[z.length - 1]; }

    // ======================================================================== paraxial

    /** Ein Ereignis auf dem Weg: an Fläche k brechen oder spiegeln. */
    record Event(int k, boolean reflect) { }

    /** Der Weg ohne Spiegelung. */
    List<Event> primary() {
        List<Event> e = new ArrayList<>();
        for (int k = 0; k < size(); k++) e.add(new Event(k, false));
        return e;
    }

    /** Der Weg des Geisterbilds: vorwärts bis j, dort zurück, rückwärts bis i, dort wieder vorwärts. */
    List<Event> ghostPath(int i, int j) {
        List<Event> e = new ArrayList<>();
        for (int k = 0; k < j; k++) e.add(new Event(k, false));
        e.add(new Event(j, true));
        for (int k = j - 1; k > i; k--) e.add(new Event(k, false));
        e.add(new Event(i, true));
        for (int k = i + 1; k < size(); k++) e.add(new Event(k, false));
        return e;
    }

    /** Ergebnis eines paraxialen Strahls: Höhe und Neigung am Ende, Höhen an den Flächen (je Ereignis), Durchgänge durch die Blende. */
    record Trace(double y, double u, double[] heights, List<Double> stopHeights) { }

    /** Paraxialer Strahl ab zStart (Höhe y, Neigung u in Luft) über die Ereignisse bis zEnd. */
    Trace paraxial(List<Event> path, double y, double u, double lambda, double zStart, double zEnd) {
        double n = 1, nu = u, zz = zStart;
        double[] hs = new double[path.size()];
        List<Double> sh = new ArrayList<>(2);
        for (int e = 0; e < path.size(); e++) {
            Event ev = path.get(e);
            int k = ev.k();
            y += (z[k] - zz) * nu / n;
            zz = z[k];
            hs[e] = y;
            if (k == stop && !ev.reflect()) { sh.add(y); continue; }
            double c = surfaces.get(k).curvature();
            double nTo;
            if (ev.reflect()) nTo = -n;
            else nTo = n > 0 ? n(k, lambda) : -n(k - 1, lambda);
            nu = nu - y * (nTo - n) * c;
            n = nTo;
        }
        y += (zEnd - zz) * nu / n;
        return new Trace(y, nu / n, hs, sh);
    }

    /** Brennweite (mm) bei λ: −1 / u eines achsparallelen Strahls der Höhe 1. */
    public double efl(double lambda) { return -1 / paraxial(primary(), 1, 0, lambda, 0, back()).u(); }

    /** Schnittweite (mm hinter der letzten Fläche) für Unendlich bei λ. */
    public double bfl(double lambda) {
        Trace t = paraxial(primary(), 1, 0, lambda, 0, back());
        return -t.y() / t.u();
    }

    /** Bildort (mm, absolut auf der Achse) eines Gegenstands im Abstand s (mm) vor der ersten Fläche; s ≤ 0: Unendlich. */
    public double imageZ(double objectMm, double lambda) {
        if (objectMm <= 0 || Double.isInfinite(objectMm)) return back() + bfl(lambda);
        Trace t = paraxial(primary(), 0, 1, lambda, -objectMm, back());
        return back() - t.y() / t.u();
    }

    /** Vergrößerung von der Eintrittsebene zur Blende (∂y_B/∂y) und ∂y_B/∂u: A und B des Hauptwegs. */
    double[] stopAB(double lambda) {
        double a = paraxial(primary(), 1, 0, lambda, 0, back()).stopHeights().get(0);
        double b = paraxial(primary(), 0, 1, lambda, 0, back()).stopHeights().get(0);
        return new double[]{a, b};
    }

    /** Halbmesser der Eintrittspupille (mm) bei voller Öffnung. */
    public double entrancePupilRadius(double lambda) {
        return surfaces.get(stop).semiAperture() / Math.abs(stopAB(lambda)[0]);
    }

    /** Lage der Eintrittspupille (mm, absolut auf der Achse). */
    public double entrancePupilZ(double lambda) {
        double[] ab = stopAB(lambda);
        return ab[1] / ab[0];
    }

    /** Kleinste Blendenzahl (volle Öffnung). */
    public double fNumberMin(double lambda) { return efl(lambda) / (2 * entrancePupilRadius(lambda)); }

    /**
     * Bildhöhe je Feldwinkel (mm/rad) für den Hauptstrahl auf dem Sensor bei zSensor: D − C·B/A. Für Unendlich gleich
     * der Brennweite, beim Fokussieren auf nah größer (Fokusatmung: der Bildwinkel wird kleiner).
     */
    public double imageScale(double zSensor, double lambda) {
        Trace t1 = paraxial(primary(), 1, 0, lambda, 0, zSensor), t2 = paraxial(primary(), 0, 1, lambda, 0, zSensor);
        double a = t1.stopHeights().get(0), b = t2.stopHeights().get(0);
        return t2.y() - t1.y() * b / a;
    }

    // ======================================================================== Geisterbilder

    /**
     * Ein Geisterbild für eine Wellenlänge: Flächen i &lt; j, Lage auf dem Sensor als Vielfaches der Lage der Sonne
     * ({@code ratio}), Vergrößerung der Blende ({@code scale} = C/A, mit Vorzeichen), Eintrittsfläche je Blendenhalbmesser
     * ({@code entry} = 1/|A|) und der Anteil, der nach beiden Spiegelungen und allen Durchgängen übrig ist ({@code rt}).
     */
    public record Ghost(int i, int j, double ratio, double scale, double entry, double rt) { }

    /** Alle Geisterbilder bei λ für den Sensor bei zSensor. */
    public List<Ghost> ghosts(double lambda, double zSensor) {
        List<Ghost> out = new ArrayList<>();
        double k0 = imageScale(zSensor, lambda);
        for (int j = 1; j < size(); j++) {
            if (j == stop) continue;
            for (int i = 0; i < j; i++) {
                if (i == stop) continue;
                List<Event> p = ghostPath(i, j);
                Trace t1 = paraxial(p, 1, 0, lambda, 0, zSensor), t2 = paraxial(p, 0, 1, lambda, 0, zSensor);
                // die engste Stelle unter den Durchgängen durch die Blende
                double a = 0, b = 0;
                for (int s = 0; s < t1.stopHeights().size(); s++) {
                    double as = t1.stopHeights().get(s);
                    if (Math.abs(as) > Math.abs(a)) { a = as; b = t2.stopHeights().get(s); }
                }
                if (Math.abs(a) < 1e-9) continue;
                double c = t1.y(), d = t2.y();
                double offset = d - c * b / a;
                out.add(new Ghost(i, j, offset / k0, c / a, 1 / Math.abs(a), transmission(p, lambda)));
            }
        }
        return out;
    }

    /** Was von 1 übrig bleibt: Reflexionsgrad an den Spiegelungen, 1 − R an jeder Brechung (senkrechter Einfall). */
    double transmission(List<Event> path, double lambda) {
        double t = 1;
        for (Event e : path) {
            int k = e.k();
            if (k == stop) continue;
            double r = surfaces.get(k).coating().reflectance(n(k - 1, lambda), n(k, lambda), lambda);
            t *= e.reflect() ? r : 1 - r;
        }
        return t;
    }

    /** Durchlässigkeit des Hauptwegs (alle Flächen 1 − R). */
    public double primaryTransmission(double lambda) { return transmission(primary(), lambda); }

    // ======================================================================== Vignettierung durch die Fassungen

    /**
     * Die Fassungen als Kreise in der Eintrittspupille (Halbmesser 1 = Pupille bei der Blende rStop): für jede Fläche
     * Halbmesser ρ und Versatz σ je Bogenmaß Feldwinkel (Mitte bei σ·θ, nach außen positiv). Wo sich die Kreise der Vorder-
     * und Hinterlinse mit der Pupille schneiden, entsteht das Katzenauge am Bildrand.
     */
    public double[][] mountCircles(double rStop, double lambda) {
        double[] ab = stopAB(lambda);
        double rep = rStop / Math.abs(ab[0]);
        Trace t1 = paraxial(primary(), 1, 0, lambda, 0, back()), t2 = paraxial(primary(), 0, 1, lambda, 0, back());
        double[][] c = new double[size()][];
        for (int k = 0; k < size(); k++) {
            double a = t1.heights()[k], b = t2.heights()[k];
            if (k == stop || Math.abs(a) < 1e-9) { c[k] = null; continue; }
            double rho = surfaces.get(k).semiAperture() / (Math.abs(a) * rep);
            double sigma = (ab[1] / ab[0] - b / a) / rep;
            c[k] = new double[]{rho, sigma};
        }
        return c;
    }

    // ======================================================================== echte Strahlen

    /** Ein echter Strahl: Ort und Richtung (Einheitsvektor), z längs der Achse. */
    record Ray(double[] o, double[] d) { }

    /**
     * Echter Strahl vom Ort o in Richtung d (Einheitsvektor) vorwärts durch alle Flächen bis zur Ebene zEnd; null, wenn er
     * an einer Fassung (checkApertures; die Blende mit dem Halbmesser rStop) hängen bleibt oder total reflektiert.
     */
    public double[] realTrace(double[] o, double[] d, double lambda, double zEnd, boolean checkApertures, double rStop) {
        double[] p = o.clone(), v = d.clone();
        double n = 1;
        for (int k = 0; k < size(); k++) {
            Surface s = surfaces.get(k);
            double t;
            double[] nrm;
            if (s.radius() == 0) {
                t = (z[k] - p[2]) / v[2];
                p = add(p, v, t);
                nrm = new double[]{0, 0, -1};
            } else {
                double r = s.radius();
                double[] cc = {0, 0, z[k] + r};
                double[] oc = {p[0] - cc[0], p[1] - cc[1], p[2] - cc[2]};
                double b = dot(oc, v), q = dot(oc, oc) - r * r, disc = b * b - q;
                if (disc < 0) return null;
                double sq = Math.sqrt(disc);
                t = r > 0 ? -b - sq : -b + sq;
                p = add(p, v, t);
                nrm = new double[]{(p[0] - cc[0]) / r, (p[1] - cc[1]) / r, (p[2] - cc[2]) / r};
                // zur einfallenden Seite drehen
                if (dot(nrm, v) > 0) nrm = new double[]{-nrm[0], -nrm[1], -nrm[2]};
            }
            double h = Math.hypot(p[0], p[1]);
            double lim = s.stop() ? rStop : s.semiAperture();
            if (checkApertures && h > lim) return null;
            if (s.stop()) continue;
            double n2 = n(k, lambda);
            double eta = n / n2, ci = -dot(nrm, v), kk = 1 - eta * eta * (1 - ci * ci);
            if (kk < 0) return null;
            double f = eta * ci - Math.sqrt(kk);
            v = new double[]{eta * v[0] + f * nrm[0], eta * v[1] + f * nrm[1], eta * v[2] + f * nrm[2]};
            n = n2;
        }
        double t = (zEnd - p[2]) / v[2];
        return add(p, v, t);
    }

    /** Höhe (mm) eines echten Strahls aus der Unendlichen unter dem Winkel θ, der die Eintrittspupille bei (px, py) (mm) trifft. */
    double[] realFromInfinity(double theta, double px, double py, double lambda, double zSensor, boolean check, double rStop) {
        double zep = entrancePupilZ(lambda);
        double[] d = {Math.sin(theta), 0, Math.cos(theta)};
        double back = 20 + Math.abs(zep);
        double[] o = {px - d[0] * back / d[2], py, zep - back};
        return realTrace(o, d, lambda, zSensor, check, rStop);
    }

    /**
     * Echter Hauptstrahl unter θ: der Strahl durch die Mitte der Blende (gesucht mit dem Sekantenverfahren). Ergebnis:
     * Höhe auf dem Sensor (mm) oder NaN.
     */
    public double chiefHeight(double theta, double lambda, double zSensor) {
        double rs = surfaces.get(stop).semiAperture();
        double x0 = 0, x1 = 0.01 * entrancePupilRadius(lambda) + 1e-3;
        double f0 = stopHit(theta, x0, lambda), f1 = stopHit(theta, x1, lambda);
        for (int it = 0; it < 30 && !Double.isNaN(f0) && !Double.isNaN(f1); it++) {
            if (Math.abs(f1) < 1e-9 || Math.abs(f1 - f0) < 1e-15) break;
            double x2 = x1 - f1 * (x1 - x0) / (f1 - f0);
            x0 = x1; f0 = f1; x1 = x2; f1 = stopHit(theta, x1, lambda);
        }
        double[] p = realFromInfinity(theta, x1, 0, lambda, zSensor, false, rs);
        return p == null ? Double.NaN : p[0];
    }

    /** Höhe in der Blendenebene eines Strahls unter θ mit Pupillenhöhe x (ohne Fassungen). */
    private double stopHit(double theta, double x, double lambda) {
        double[] p = realFromInfinity(theta, x, 0, lambda, z[stop], false, 1e9);
        return p == null ? Double.NaN : p[0];
    }

    /** Querabweichung (mm) eines Randstrahls der Pupillenhöhe h (achsparallel) in der Ebene zSensor. */
    public double transverse(double h, double lambda, double zSensor) {
        double[] p = realTrace(new double[]{h, 0, -10}, new double[]{0, 0, 1}, lambda, zSensor, false, 1e9);
        return p == null ? Double.NaN : p[0];
    }

    /**
     * Anteil des Lichts, der unter θ durch alle Fassungen und die Blende rStop kommt, relativ zur Achse (echte Strahlen,
     * Raster n × n über das 1,4-Fache der Eintrittspupille; die Blende schneidet selbst aus, auch mit Pupillenfehlern).
     */
    public double vignetting(double theta, double rStop, double lambda, double zSensor, int n) {
        double on = pupilPass(0, rStop, lambda, zSensor, n);
        return on <= 0 ? 0 : pupilPass(theta, rStop, lambda, zSensor, n) / on;
    }

    private double pupilPass(double theta, double rStop, double lambda, double zSensor, int n) {
        double rep = 1.4 * rStop / Math.abs(stopAB(lambda)[0]);
        int ok = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = (i + 0.5) / n * 2 - 1, y = (j + 0.5) / n * 2 - 1;
                if (x * x + y * y > 1) continue;
                if (realFromInfinity(theta, x * rep, y * rep, lambda, zSensor, true, rStop) != null) ok++;
            }
        return ok;
    }

    public String describe(double lambda) {
        return String.format(Locale.GERMAN, "%s: f = %.2f mm, Schnittweite %.2f mm, f/%.2f, Eintrittspupille Ø %.2f mm bei z = %.2f mm, %d Flächen, %d Geisterbilder",
                name, efl(lambda), bfl(lambda), fNumberMin(lambda), 2 * entrancePupilRadius(lambda), entrancePupilZ(lambda), size(), ghosts(lambda, back() + bfl(lambda)).size());
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] add(double[] p, double[] v, double t) { return new double[]{p[0] + v[0] * t, p[1] + v[1] * t, p[2] + v[2] * t}; }
}
