package com.dan.fall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sucht Wasserfälle entlang von Bächen über einem Gelände (seit LituyaBay1958 W6 Runde 3, vorher dort in FaelleGl).
 * Ein Bach ist eine Linie in Metern (x, z) mit Abfluss und Namen. Die Linie wird alle {@link #step} m abgetastet; wo sie
 * über {@link #window} Schritte steiler als {@link #slopeDeg}° abfällt, beginnt eine steile Strecke, die mit Lücken bis
 * {@link #gap} Schritte zusammenhält. Eine Strecke mit mindestens {@link #minDrop} m Fall wird ein Kandidat mit der Kante
 * am oberen Ende. Ein Filter der App ({@link #accept}) kann Kanten verwerfen, etwa im Eis oder außerhalb eines Gebiets.
 * Die Kandidaten werden nach {@link Candidate#rank} geordnet. Höchstens {@link #max} bleiben, keine zwei dichter als
 * {@link #minSpacing} m. {@link #toFalls} macht daraus fertig gerechnete {@link Fall}s.
 *
 * <pre>
 * FallFinder ff = new FallFinder();
 * ff.ground = dgm::height;
 * ff.accept = (x, y, z) -&gt; y &lt; 1000;
 * List&lt;FallFinder.Candidate&gt; k = ff.find(List.of(new FallFinder.Stream("Bach", xz, 0.8)));
 * List&lt;Fall&gt; faelle = ff.toFalls(k, 1000, f -&gt; f.temperature = 9);
 * </pre>
 */
public final class FallFinder {

    /** Ein Bach: Punkte x0, z0, x1, z1, … (m, von oben nach unten), Abfluss (m³/s). */
    public record Stream(String name, double[] xz, double discharge) {}

    /** Kante x, y, z, Richtung des Bachs dx, dz (Einheit), Fallhöhe (m), Abfluss (m³/s), Name, Höhe am Fuß (m). */
    public record Candidate(double x, double y, double z, double dx, double dz, double drop, double discharge, String name, double footY) {
        /** Höhe × √Abfluss; Fälle, die bis fast an den Pegel fallen (Fuß unter 60 m über ihm), dreifach [G]. */
        public double rank(double waterLevel) { return drop * Math.sqrt(discharge) * (footY - waterLevel < 60 ? 3 : 1); }
    }

    /** Ob eine Kante bleiben darf (x, y, z in m). */
    public interface Accept { boolean test(double x, double y, double z); }

    /** Nachbearbeitung je Fall vor {@link Fall#update}. */
    public interface Setup { void apply(Fall f); }

    public Fall.Ground ground;
    public Accept accept = (x, y, z) -> true;
    /** Abtastung (m) und Fenster (Schritte) für das Gefälle; Lücke (Schritte), die eine Strecke noch zusammenhält. */
    public double step = 5;
    public int window = 3, gap = 2;
    /** Steiler als (Grad), mindestens so hoch (m), Abfluss von bis (m³/s). */
    public double slopeDeg = 40, minDrop = 22, minDischarge = 0.04, maxDischarge = 20;
    /** Höchstens so viele, nicht dichter als (m). */
    public int max = 48;
    public double minSpacing = 60;
    /** Pegel für den Rang und für {@link Fall#waterLevel}. */
    public double waterLevel = 0;
    /** Kante mindestens so breit (m); sonst aus dem Abfluss ({@link #width}). */
    public double minLipWidth = 3;

    /** Nach {@link #find}: Kandidaten vor Auswahl, verworfen vom Filter. */
    public int found, rejected;

    /** Breite (m) eines Bachs mit Abfluss q (m³/s), hydraulische Geometrie b = 2,5 · q^0,5, 0,5 bis 90 m [G]. */
    public static double width(double q) { return Math.max(0.5, Math.min(90, 2.5 * Math.sqrt(Math.max(0, q)))); }

    /** Linie auf höchstens d m Abstand verdichten. */
    public static double[] densify(double[] xz, double d) {
        List<Double> o = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length / 2; i++) {
            double ax = xz[2 * i], az = xz[2 * i + 1], bx = xz[2 * i + 2], bz = xz[2 * i + 3];
            int n = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) / d));
            for (int k = 0; k < n; k++) { o.add(ax + (bx - ax) * k / n); o.add(az + (bz - az) * k / n); }
        }
        if (xz.length >= 2) { o.add(xz[xz.length - 2]); o.add(xz[xz.length - 1]); }
        double[] r = new double[o.size()];
        for (int i = 0; i < r.length; i++) r[i] = o.get(i);
        return r;
    }

    /** Kandidaten suchen, geordnet und ausgewählt. */
    public List<Candidate> find(List<Stream> streams) {
        if (ground == null) throw new IllegalStateException("FallFinder: ground fehlt");
        double tan = Math.tan(Math.toRadians(slopeDeg));
        List<Candidate> k = new ArrayList<>();
        found = 0;
        rejected = 0;
        for (Stream s : streams) {
            double q = s.discharge;
            if (q < minDischarge || q > maxDischarge || s.xz == null || s.xz.length < 4) continue;
            double[] p = densify(s.xz, step);
            int np = p.length / 2, w = window;
            if (np < 2 * w) continue;
            double[] x = new double[np], z = new double[np], y = new double[np];
            boolean ok = true;
            for (int i = 0; i < np; i++) {
                x[i] = p[2 * i];
                z[i] = p[2 * i + 1];
                y[i] = ground.height(x[i], z[i]);
                if (Double.isNaN(y[i])) { ok = false; break; }
            }
            if (!ok) continue;
            int i = 0;
            while (i + w < np) {
                if (grade(x, y, z, i, w) < tan) { i++; continue; }
                int a = i, b = i, luecke = 0;
                while (b + w < np && luecke <= gap) {
                    if (grade(x, y, z, b, w) >= tan) luecke = 0; else luecke++;
                    b++;
                }
                int ende = Math.min(np - 1, b + 1);
                double drop = y[a] - y[ende];
                if (drop >= minDrop) {
                    found++;
                    double dx = x[Math.min(np - 1, a + 1)] - x[a], dz = z[Math.min(np - 1, a + 1)] - z[a], l = Math.hypot(dx, dz);
                    if (l > 1e-6) {
                        if (accept.test(x[a], y[a], z[a])) k.add(new Candidate(x[a], y[a], z[a], dx / l, dz / l, drop, q, s.name, y[ende]));
                        else rejected++;
                    }
                }
                i = ende + 1;
            }
        }
        k.sort((a, b) -> Double.compare(b.rank(waterLevel), a.rank(waterLevel)));
        List<Candidate> wahl = new ArrayList<>();
        for (Candidate c : k) {
            boolean frei = true;
            for (Candidate o : wahl) if (Math.hypot(o.x - c.x, o.z - c.z) < minSpacing) { frei = false; break; }
            if (frei) wahl.add(c);
            if (wahl.size() >= max) break;
        }
        return wahl;
    }

    private static double grade(double[] x, double[] y, double[] z, int i, int w) {
        double l = Math.hypot(x[i + w] - x[i], z[i + w] - z[i]);
        return (y[i] - y[i + w]) / Math.max(1, l);
    }

    /**
     * Fälle aus den Kandidaten: Kante quer zum Bach (Breite aus dem Abfluss, mindestens {@link #minLipWidth}) 0,3 m über
     * dem Gelände, über Fels, Startwert seed0 + Nummer. setup darf vor {@link Fall#update} weitere Felder setzen (null
     * = keine). Fälle, die nicht rechnen, fehlen in der Liste.
     */
    public List<Fall> toFalls(List<Candidate> cands, long seed0, Setup setup) {
        List<Fall> r = new ArrayList<>();
        int nr = 0;
        for (Candidate c : cands) {
            Fall f = new Fall();
            double b = Math.max(minLipWidth, width(c.discharge)) / 2;
            double sx = -c.dz, sz = c.dx;
            f.lipA = new double[]{c.x - sx * b, c.y + 0.3, c.z - sz * b};
            f.lipB = new double[]{c.x + sx * b, c.y + 0.3, c.z + sz * b};
            f.headingDeg = (Math.toDegrees(Math.atan2(c.dx, -c.dz)) + 360) % 360;
            f.discharge = c.discharge;
            f.overRock = true;
            f.ground = ground;
            f.waterLevel = waterLevel;
            f.seed = seed0 + nr;
            f.name = (c.name == null || c.name.isBlank() ? "Fall " + (nr + 1) : c.name) + String.format(Locale.GERMAN, " (%.0f m)", c.drop);
            if (setup != null) setup.apply(f);
            try {
                f.update();
                r.add(f);
            } catch (RuntimeException ex) {
                System.err.println("FallFinder: " + f.name + ": " + ex.getMessage());
            }
            nr++;
        }
        return r;
    }
}
