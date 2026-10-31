package com.dan.flock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Seit Sylt Phase 13: Vögel am Spülsaum eines Sandstrands an der offenen See. Anders als im Watt ({@link Waders}) läuft
 * die Kante des Wassers hier mit jeder Welle hinauf und wieder hinab, und die Vögel leben mit ihr:
 * <ul>
 * <li><b>Sanderlinge</b> ({@link WaderSpecies#SANDERLING}) in Trupps von 5 bis 60: jeder hält sich einen eigenen,
 * kleinen Abstand landwärts der Kante (0,2 bis 1,4 m). Läuft der Schwall hinauf, rennen sie vor ihm her (bis
 * {@link #RUN} m/s); holt er einen ein, flattert der Vogel ein Stück hoch und landwärts. Fällt das Wasser zurück, rennen
 * sie hinterher und stochern im frisch freigelegten Sand.</li>
 * <li><b>Austernfischer</b> zu zweit bis zu sechst weiter oben auf dem nassen Sand (2 bis 14 m über der Kante): gehen,
 * stochern, weichen dem Wasser aus.</li>
 * <li><b>Silbermöwen</b> stehen einzeln und in kleinen Gruppen am Spülsaum und auf dem Strand, in den Wind gedreht,
 * gehen ab und zu; nachts ruhen sie.</li>
 * </ul>
 * Ein Störer (die Kamera, ein Hund) näher als die Fluchtdistanz am Strand (Sanderling 15 m, Austernfischer 35 m, Möwe 12 m
 * [G], am belebten Strand kürzer als im Watt) scheucht die Gruppe auf: sie fliegt die Küste entlang vom Störer fort und
 * landet wieder an der Kante. Reicht der Strand nicht mehr (Sturmflut: Wasser bis an den Fuß des Kliffs) oder weht es
 * über Bft 9, sind alle fort und kommen zurück, wenn wieder Strand frei ist.
 * <p>
 * Kennt keinen Strand und keine Brandung: die App gibt über {@link Shore} Grund, Wassertiefe, die Lage der Kante und
 * die Oberkante des Strands und legt mit {@link #frame} den Rahmen (Ursprung, landwärts, längs) und das Stück längs, in
 * dem die Vögel leben. Jeder Schritt auf dem Sand meldet {@link #onStep} (für Spuren im nassen Sand, die der nächste
 * Schwall wieder löscht). Gezeichnet wird mit {@code gl/WaderGl} (dieselben Netze wie im Watt). Reproduzierbar: gleicher
 * {@code seed}, gleiche Kante, gleiche Schritte, gleiche Vögel.
 */
public final class Shorebirds {
    /** Der Strand, von der App. Lage quer ist im Rahmen gemessen (m, landwärts positiv), längs ebenso. */
    public interface Shore {
        /** Grund (m) am Ort der Szene. */
        double ground(double x, double z);
        /** Wassertiefe (m) am Ort der Szene, Schwall eingeschlossen; 0 trocken. */
        double depth(double x, double z);
        /** Lage der Wasserkante quer an der Stelle v längs (m); NaN, wenn hier unbekannt. */
        double edge(double v);
        /** Wie weit der Strand landwärts reicht (m quer, Fuß von Kliff oder Düne) an der Stelle v. */
        double top(double v);
    }

    /** Ein Tritt: Ort, Laufrichtung, Art (für die Größe der Spur). */
    @FunctionalInterface public interface Step { void step(double x, double z, double dirX, double dirZ, WaderSpecies s); }

    /** Die Arten am Strand in ihrer Reihenfolge ({@link #base}, {@link #enabled}). */
    public static final List<WaderSpecies> SPECIES = List.of(WaderSpecies.SANDERLING, WaderSpecies.AUSTERNFISCHER, WaderSpecies.SILBERMOEWE);
    /** Höchste Laufgeschwindigkeit eines Sanderlings (m/s) [G: im Sprint vor der Welle um 2 bis 3 m/s]. */
    public static final double RUN = 2.6;
    /** Schrittlänge je Art (m) [G]: Sanderling 6 cm, Austernfischer 12 cm, Möwe 15 cm. */
    static final double[] STRIDE = {0.06, 0.12, 0.15};
    /** Fluchtdistanz am Strand (m) [G]. */
    static final double[] FLEE = {15, 35, 12};

    // ------------------------------------------------------------------ Einstellungen
    public long seed = 7;
    public volatile Waders.Quality quality = Waders.Quality.MEDIUM;
    public Shore shore;
    /** Rahmen: Ursprung, Einheitsvektor landwärts (sx, sz), längs (ax, az); das Stück längs, in dem die Vögel leben. */
    public double ox, oz, sx = 1, sz = 0, ax = 0, az = 1, v0 = -70, v1 = 70;
    /** Monat (1..12), Sonnenhöhe (Grad), Wind (m/s, aus Grad). */
    public volatile int month = 9;
    public volatile double sunAltDeg = 20, windSpeed = 6, windFromDeg = 270;
    /** Zahl je Art bei Anwesenheit 1 und Stufe MEDIUM (nach {@link #SPECIES}): Sanderlinge, Austernfischer, Möwen [G]. */
    public final int[] base = {36, 6, 6};
    public final boolean[] enabled = {true, true, true};
    /** Störer: x, z, Stärke (vor jedem Schritt von der App). */
    public final List<double[]> disturbers = new ArrayList<>();
    public Waders.Call onCall;
    public Step onStep;

    /** Eine Gruppe am Strand: die Gruppe zum Zeichnen und je Vogel der eigene Zustand. */
    static final class Trupp {
        final Waders.Group g;
        final int art;
        /** Mitte längs (m im Rahmen), Ziel des Flugs längs, Zeit bis zur nächsten Entscheidung, Flugzeit. */
        double v, flyTo, timer, airTime;
        /** je Vogel: Platz längs (m zur Mitte), Abstand zur Kante (m), Hüpfen (s), Weg seit dem letzten Tritt (m), Fuß. */
        double[] dv, du, hop, walked;
        int[] foot;
        /** Mit dem Rahmen aus dem Stück gefallen: beim nächsten Schritt neu auf den Strand setzen. */
        boolean lost;
        Trupp(Waders.Group g, int art) { this.g = g; this.art = art; }
    }

    private final List<Trupp> trupps = new ArrayList<>();
    private final List<Waders.Group> groups = new ArrayList<>();
    private SplittableRandom rng = new SplittableRandom(7);
    private String built = "";
    private double time;
    private boolean away;
    /** Zähler (Prüfung, HUD): Hüpfer vor dem Schwall, Fluchten, Tritte, Rufe, eingeholt vom Wasser (sollte 0 bleiben). */
    public int hops, flights, steps, calls, soaked;

    public List<Waders.Group> groups() { return Collections.unmodifiableList(groups); }
    public double time() { return time; }
    /** Ob gerade alle fort sind (kein Strand, Sturm). */
    public boolean away() { return away; }

    /**
     * Den Rahmen setzen: Ursprung, landwärts, längs; das Stück längs von a bis b. Wandert der Rahmen (ein neues Stück
     * Strand), bleiben die Vögel, wo sie sind; nur ihre Lage im Rahmen wird umgerechnet.
     */
    public void frame(double ox, double oz, double sx, double sz, double ax, double az, double a, double b) {
        boolean moved = ox != this.ox || oz != this.oz || sx != this.sx || sz != this.sz || ax != this.ax || az != this.az;
        double[][] keep = new double[trupps.size()][];
        if (moved) for (int i = 0; i < keep.length; i++) {
            Trupp t = trupps.get(i);
            double[] p = world(0, t.v), q = world(0, t.flyTo);
            keep[i] = new double[]{p[0], p[1], q[0], q[1]};
        }
        this.ox = ox; this.oz = oz; this.sx = sx; this.sz = sz; this.ax = ax; this.az = az; v0 = a; v1 = b;
        if (moved) for (int i = 0; i < keep.length; i++) {
            Trupp t = trupps.get(i);
            t.v = local(keep[i][0], keep[i][1])[1];
            t.flyTo = local(keep[i][2], keep[i][3])[1];
            // aus dem neuen Stück gefallen (das Auge ist gesprungen): der Trupp steht beim nächsten Schritt im neuen Stück,
            // an der Kante, die dann gilt (laufend käme er nie an, fliegend erst nach Minuten)
            if (t.v < v0 - 30 || t.v > v1 + 30) { t.v = Math.max(v0 + 6, Math.min(v1 - 6, t.v)); t.flyTo = t.v; t.lost = true; }
        }
    }

    /** Szenenpunkt aus quer u und längs v. */
    public double[] world(double u, double v) { return new double[]{ox + u * sx + v * ax, oz + u * sz + v * az}; }

    /** Quer und längs aus dem Szenenpunkt. */
    public double[] local(double x, double z) {
        double dx = x - ox, dz = z - oz;
        return new double[]{dx * sx + dz * sz, dx * ax + dz * az};
    }

    /** Zahl der Vögel einer Art nach Monat und Stufe. */
    public int target(int i) {
        WaderSpecies s = SPECIES.get(i);
        double pres = i == 2 ? 1 : s.presence(month);
        return enabled[i] ? (int) Math.round(base[i] * pres * quality.factor) : 0;
    }

    private String key() {
        StringBuilder b = new StringBuilder().append(seed).append('/').append(quality).append('/').append(month).append('/');
        for (int i = 0; i < base.length; i++) b.append(target(i)).append(',');
        return b.toString();
    }

    /** Gruppen anlegen und auf den Strand setzen (nach dem Rahmen und der Kante). */
    public void populate() {
        trupps.clear();
        groups.clear();
        rng = new SplittableRandom(seed);
        hops = flights = steps = calls = soaked = 0;
        int id = 0;
        int[] min = {5, 2, 1}, max = {60, 6, 4};
        for (int i = 0; i < SPECIES.size(); i++) {
            int n = target(i);
            while (n > 0) {
                int g = Math.min(n, min[i] + rng.nextInt(max[i] - min[i] + 1));
                if (n - g < min[i]) g = n;
                Trupp t = new Trupp(new Waders.Group(id++, SPECIES.get(i)), i);
                for (int k = 0; k < g; k++) t.g.birds.add(new Waders.Bird(t.g));
                t.dv = new double[g]; t.du = new double[g]; t.hop = new double[g]; t.walked = new double[g]; t.foot = new int[g];
                double spread = i == 0 ? 0.9 * Math.sqrt(g) + 1.5 : i == 1 ? 3 : 5;
                for (int k = 0; k < g; k++) {
                    t.dv[k] = (rng.nextDouble() - 0.5) * 2 * spread;
                    t.du[k] = i == 0 ? 0.2 + 1.2 * rng.nextDouble() : i == 1 ? 2 + 12 * rng.nextDouble() : 1 + 18 * Math.pow(rng.nextDouble(), 1.5);
                    t.g.birds.get(k).probePhase = rng.nextDouble();
                }
                t.v = v0 + (v1 - v0) * (0.1 + 0.8 * rng.nextDouble());
                t.timer = 5 + 20 * rng.nextDouble();
                trupps.add(t);
                groups.add(t.g);
                n -= g;
            }
        }
        built = key();
        settle();
    }

    /** Sofort auf ihre Plätze (Start, Sprünge der Uhr, neuer Rahmen). */
    public void settle() {
        if (shore == null) return;
        if (!built.equals(key())) { populate(); return; }
        for (Trupp t : trupps) {
            if (t.v < v0 + 5 || t.v > v1 - 5) t.v = v0 + (v1 - v0) * (0.1 + 0.8 * rng.nextDouble());
            place(t);
        }
    }

    /** Einen Trupp sofort auf seinen Platz (fressend, jeder Vogel in seinem Abstand zur Kante). */
    private void place(Trupp t) {
        {
            t.g.mode = Waders.Mode.FRESSEN;
            for (int k = 0; k < t.g.birds.size(); k++) {
                Waders.Bird b = t.g.birds.get(k);
                double v = t.v + t.dv[k], u = edgeAt(v) + t.du[k];
                double[] w = world(u, v);
                b.x = w[0]; b.z = w[1]; b.y = shore.ground(b.x, b.z);
                b.air = false; b.vx = b.vy = b.vz = 0; b.fold = 1; b.legs = 1; b.flap = 0; b.bank = 0;
                b.heading = Math.toRadians(windFromDeg) + (rng.nextDouble() - 0.5) * 0.6;
                t.hop[k] = 0;
            }
            t.g.cx = world(edgeAt(t.v), t.v)[0];
            t.g.cz = world(edgeAt(t.v), t.v)[1];
        }
    }

    /** Die Kante an v (aus dem Strand; ohne Angabe die nächste bekannte, sonst 0). */
    double edgeAt(double v) {
        double e = shore.edge(v);
        if (!Double.isNaN(e)) return e;
        for (double d = 2; d < 80; d *= 1.6) {
            double a = shore.edge(Math.max(v0, Math.min(v1, v - d))), b = shore.edge(Math.max(v0, Math.min(v1, v + d)));
            if (!Double.isNaN(a)) return a;
            if (!Double.isNaN(b)) return b;
        }
        return 0;
    }

    // ------------------------------------------------------------------ Schritt

    /** Szenenzeit dt (s) weiter; in Teilschritten von höchstens 1/30 s (die Kante läuft schnell). */
    public void step(double dt) {
        if (shore == null || dt <= 0) return;
        if (!built.equals(key()) || trupps.isEmpty() && target(0) + target(1) + target(2) > 0) populate();
        for (Trupp t : trupps) if (t.lost) { t.lost = false; t.flyTo = t.v; t.airTime = 0; place(t); }
        int n = (int) Math.ceil(dt / (1 / 30.0));
        double h = dt / n;
        for (int i = 0; i < n; i++) sub(h);
    }

    private void sub(double h) {
        time += h;
        // kein Strand oder Sturm: alle fort
        int dry = 0, probes = 9;
        for (int k = 0; k < probes; k++) {
            double v = v0 + (v1 - v0) * (k + 0.5) / probes;
            if (shore.top(v) - edgeAt(v) > 4) dry++;
        }
        boolean gone = windSpeed > 20.7 || dry < 3;
        if (gone != away) {
            away = gone;
            for (Trupp t : trupps) t.g.mode = gone ? Waders.Mode.FORT : Waders.Mode.FRESSEN;
            if (!gone) settle();
        }
        if (away) return;
        for (Trupp t : trupps) {
            t.timer -= h;
            if (t.g.mode == Waders.Mode.FLIEGEN) { fly(t, h); continue; }
            // aufgescheucht?
            double cu = edgeAt(t.v) + (t.art == 0 ? 0.8 : 4), cv = t.v;
            double[] c = world(cu, cv);
            double[] d = nearestDisturber(c[0], c[1]);
            if (d[0] < FLEE[t.art] * Math.max(0.3, d[3])) { takeOff(t, d); continue; }
            // gelegentlich ein Stück die Küste entlang wandern (der Trupp sucht weiter), im Stück bleiben
            if (t.timer <= 0) {
                t.timer = 8 + 25 * rng.nextDouble();
                double dvMax = t.art == 0 ? 6 : 3;
                t.flyTo = Math.max(v0 + 6, Math.min(v1 - 6, t.v + (rng.nextDouble() - 0.5) * 2 * dvMax));
            }
            double walk = t.art == 0 ? 0.25 : 0.12;
            if (Math.abs(t.flyTo - t.v) > 0.05) t.v += Math.signum(t.flyTo - t.v) * Math.min(Math.abs(t.flyTo - t.v), walk * h);
            for (int k = 0; k < t.g.birds.size(); k++) ground(t, k, h);
            t.g.cx = c[0]; t.g.cz = c[1];
        }
    }

    /** Ein Vogel am Boden: zur Kante (Sanderling) oder auf dem nassen Sand (Austernfischer, Möwe), Hüpfen vor dem Wasser. */
    private void ground(Trupp t, int k, double h) {
        Waders.Bird b = t.g.birds.get(k);
        WaderSpecies s = t.g.species;
        double[] lu = local(b.x, b.z);
        double u = lu[0], v = lu[1];
        double vt = t.v + t.dv[k], e = edgeAt(vt), top = shore.top(vt);
        boolean night = sunAltDeg < -8;
        double wade = s.wade(), depth = shore.depth(b.x, b.z);
        // Hüpfen: kurz in der Luft, landwärts
        if (t.hop[k] > 0) {
            t.hop[k] -= h;
            double up = Math.max(e + 0.8, u + RUN * 1.4 * h);
            double[] w = world(Math.min(up, u + RUN * 1.6 * h), v);
            double ph = Math.max(0, t.hop[k]) / 0.9;
            b.x = w[0]; b.z = w[1];
            b.y = shore.ground(b.x, b.z) + 0.35 * Math.sin(Math.PI * Math.min(1, 1 - ph));
            b.air = true; b.flap = 1; b.fold = 0.2; b.legs = 0.6;
            b.wingPhase += 2 * Math.PI * s.flapHz() * h;
            b.vx = sx * RUN; b.vz = sz * RUN; b.vy = 0;
            if (t.hop[k] <= 0) { b.air = false; b.fold = 1; b.legs = 1; b.flap = 0; b.vx = b.vz = 0; }
            return;
        }
        // eingeholt? Der Sanderling flattert hoch, sobald das Wasser über die Zehen läuft (halbe Watschtiefe); die anderen
        // gehen hinauf und flattern erst, wenn es ihnen an die Beine geht
        if (depth > (t.art == 0 ? 0.5 * wade + 0.002 : 0.12)) {
            {
                t.hop[k] = 0.6 + 0.4 * rng.nextDouble();
                hops++;
                if (depth > wade + 0.06) soaked++;
                return;
            }
        }
        double tu, tv = vt, speed;
        if (t.art == 0) {
            // Sanderling: der eigene Abstand zur Kante; nachts dichter am Wasser, sie fressen weiter
            tu = e + t.du[k] * (night ? 0.8 : 1);
            double gap = tu - u;
            // läuft der Schwall auf ihn zu, rennt er; sonst trippelt er hinterher
            speed = gap > 0.05 ? RUN : Math.min(1.4, 0.4 + Math.abs(gap) * 1.5);
        } else {
            // weiter oben: um den eigenen Platz, über der Kante, unter der Oberkante
            double lo = e + (t.art == 1 ? 1.8 : 0.6), hi = Math.max(lo + 1, top - 2);
            tu = Math.max(lo, Math.min(hi, e + t.du[k]));
            if (Math.abs(tu - u) < 0.3 && b.pause <= 0) {   // ein neuer Fleck zum Stochern
                b.pause = (t.art == 1 ? 3 : 15) + 10 * rng.nextDouble();
                t.dv[k] += (rng.nextDouble() - 0.5) * (t.art == 1 ? 1.5 : 0.8);
                t.dv[k] = Math.max(-6, Math.min(6, t.dv[k]));
            }
            b.pause -= h;
            speed = s.walk();
        }
        double du = tu - u, dvv = tv - v, dl = Math.hypot(du, dvv);
        double step = Math.min(dl, speed * h);
        boolean walking = step > 1e-4 && dl > 0.02;
        if (walking) {
            u += du / dl * step;
            v += dvv / dl * step;
            double[] w = world(u, v);
            double mx = w[0] - b.x, mz = w[1] - b.z;
            b.x = w[0]; b.z = w[1];
            double want = Math.atan2(mx, -mz);
            b.heading += Waders.angleTo(b.heading, want) * Math.min(1, h * 12);
            // Tritte im Sand
            t.walked[k] += step;
            double stride = STRIDE[t.art];
            while (t.walked[k] >= stride) {
                t.walked[k] -= stride;
                steps++;
                t.foot[k] ^= 1;
                if (onStep != null) {
                    double ml = Math.max(1e-9, Math.hypot(mx, mz)), side = (t.foot[k] == 0 ? 1 : -1) * s.length() * 0.06;
                    onStep.step(b.x - mz / ml * side, b.z + mx / ml * side, mx / ml, mz / ml, s);
                }
            }
        } else if (t.art == 2 || night) {
            b.heading += Waders.angleTo(b.heading, Math.toRadians(windFromDeg)) * Math.min(1, h * 0.8);   // in den Wind
        }
        b.y = shore.ground(b.x, b.z);
        b.air = false; b.fold = 1; b.legs = 1; b.flap = 0; b.bank = 0;
        // Stochern: Sanderlinge, wenn sie stehen; Austernfischer langsam; Möwen selten, nachts nie
        boolean rest = t.art == 2 && (night || rng.nextDouble() < 0.0005);
        b.probePhase += h * s.probeHz() * (walking ? (t.art == 0 ? 0.0 : 0.3) : 1);
        double ph = b.probePhase % 1.0;
        b.probe = rest ? -0.25 : t.art == 2 ? 0 : (ph < 0.45 ? Math.sin(ph / 0.45 * Math.PI) : 0);
    }

    /** Auffliegen: die Küste entlang, vom Störer fort, im Stück. */
    private void takeOff(Trupp t, double[] d) {
        double[] dl = local(d[1], d[2]);
        double dir = Math.signum(t.v - dl[1]);
        if (dir == 0) dir = rng.nextBoolean() ? 1 : -1;
        double dist = t.art == 0 ? 25 + 35 * rng.nextDouble() : t.art == 1 ? 50 + 60 * rng.nextDouble() : 30 + 40 * rng.nextDouble();
        double to = t.v + dir * dist;
        if (to > v1 - 8 || to < v0 + 8) to = t.v - dir * dist;          // auf der anderen Seite des Störers landen
        t.flyTo = Math.max(v0 + 8, Math.min(v1 - 8, to));
        t.g.mode = Waders.Mode.FLIEGEN;
        t.airTime = 0;
        flights++;
        for (int k = 0; k < t.g.birds.size(); k++) {
            Waders.Bird b = t.g.birds.get(k);
            b.air = true; b.vy = 1 + rng.nextDouble(); b.fold = 0.3; b.legs = 0.5; b.flap = 1;
            t.hop[k] = 0;
        }
        call(t, 0.8);
    }

    /** Flug zum neuen Platz längs: niedrig über dem Strand (Sanderlinge über dem Wasser), dann landen an der Kante. */
    private void fly(Trupp t, double h) {
        WaderSpecies s = t.g.species;
        t.airTime += h;
        double v = Math.min(s.speed() * 0.8, 10), dir = Math.signum(t.flyTo - t.v);
        double prog = Math.min(Math.abs(t.flyTo - t.v), v * h);
        t.v += dir * prog;
        boolean arrived = Math.abs(t.flyTo - t.v) < 0.5 && t.airTime > 1.5;
        double height = t.art == 0 ? 1.2 : t.art == 1 ? 4 : 6;
        for (int k = 0; k < t.g.birds.size(); k++) {
            Waders.Bird b = t.g.birds.get(k);
            double vt = t.v + t.dv[k] * 0.6, ut = edgeAt(vt) + (t.art == 0 ? -1.5 : 3) + t.du[k] * 0.3;
            double[] w = world(ut, vt);
            double gy = shore.ground(w[0], w[1]);
            double ty = Math.max(gy, 0) + height * Math.min(1, Math.min(t.airTime, Math.abs(t.flyTo - t.v) / 6 + 0.2)) + 0.3 * Math.sin(time * 1.3 + k);
            double kx = (w[0] - b.x), kz = (w[1] - b.z), ky = ty - b.y;
            double ovx = b.vx, ovz = b.vz;
            b.vx += (kx * 2.5 - b.vx) * Math.min(1, h * 4);
            b.vz += (kz * 2.5 - b.vz) * Math.min(1, h * 4);
            b.vy += (ky * 2.0 - b.vy) * Math.min(1, h * 4);
            double vv = Math.hypot(b.vx, b.vz), vmax = s.speed() * 1.3;
            if (vv > vmax) { b.vx *= vmax / vv; b.vz *= vmax / vv; }
            b.x += b.vx * h; b.y += b.vy * h; b.z += b.vz * h;
            double hv = Math.max(0.5, Math.hypot(b.vx, b.vz));
            double turn = ((b.vx - ovx) * -b.vz + (b.vz - ovz) * b.vx) / (hv * h);
            b.bank += (Math.max(-1, Math.min(1, Math.atan2(turn, 9.81))) - b.bank) * Math.min(1, h * 6);
            b.heading = Math.atan2(b.vx, -b.vz);
            b.flap = 1; b.fold = Math.max(0, b.fold - h * 3); b.legs = Math.max(0, b.legs - h * 2);
            b.wingPhase += 2 * Math.PI * s.flapHz() * h;
            b.probe = 0;
        }
        if (arrived) {
            t.g.mode = Waders.Mode.FRESSEN;
            t.timer = 10 + 20 * rng.nextDouble();
            for (Waders.Bird b : t.g.birds) { b.air = false; b.fold = 1; b.legs = 1; b.flap = 0; b.bank = 0; b.vx = b.vy = b.vz = 0; }
            call(t, 0.5);
        }
    }

    private void call(Trupp t, double strength) {
        calls++;
        if (onCall != null) onCall.call(t.g.cx, shore.ground(t.g.cx, t.g.cz), t.g.cz, strength, t.g.species);
    }

    /** {Abstand, x, z, Stärke} des nächsten Störers. */
    double[] nearestDisturber(double x, double z) {
        double bd = Double.POSITIVE_INFINITY, bx = 0, bz = 0, bs = 1;
        for (double[] d : disturbers) { double dd = Math.hypot(d[0] - x, d[1] - z); if (dd < bd) { bd = dd; bx = d[0]; bz = d[1]; bs = d.length > 2 ? d[2] : 1; } }
        return new double[]{bd, bx, bz, bs};
    }

    // ------------------------------------------------------------------ Ausgabe

    public int birds() { int n = 0; for (Waders.Group g : groups) n += g.size(); return n; }

    /** Zahl der Vögel je Art (nach {@link #SPECIES}). */
    public int[] census() {
        int[] c = new int[SPECIES.size()];
        for (Trupp t : trupps) c[t.art] += t.g.size();
        return c;
    }

    /** Wie weit (m) die Sanderlinge im Mittel landwärts der Kante stehen (am Boden, ohne Hüpfer); NaN ohne. */
    public double meanGap() {
        double s = 0;
        int n = 0;
        for (Trupp t : trupps) {
            if (t.art != 0 || t.g.mode != Waders.Mode.FRESSEN) continue;
            for (int k = 0; k < t.g.birds.size(); k++) {
                Waders.Bird b = t.g.birds.get(k);
                if (b.air) continue;
                double[] l = local(b.x, b.z);
                s += l[0] - edgeAt(l[1]);
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    /** „Am Spülsaum 48 Vögel: 36 Sanderlinge, 6 Austernfischer, 6 Silbermöwen · 12 Hüpfer, 3 Fluchten“ */
    public String describe() {
        if (away) return "Am Spülsaum: alle fort (kein Strand oder Sturm)";
        int[] c = census();
        return String.format(Locale.GERMAN, "Am Spülsaum %d Vögel: %d Sanderlinge, %d Austernfischer, %d Silbermöwen · %d Hüpfer vor dem Schwall, %d Fluchten, %,d Tritte",
                birds(), c[0], c[1], c[2], hops, flights, steps);
    }
}
