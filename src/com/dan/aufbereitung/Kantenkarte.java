package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Kanten;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Kanten der Brandung aus Gelände und Gestalt (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A), Schritt
 * {@code brandung}. Gesucht werden Böschungen steiler als {@link #neigungMin} über eine Grundlänge von {@link #basis} m
 * (wie {@code com.dan.surf.WaveTrain.slopeBase}), mindestens {@link #hoeheMin} m hoch, deren Fuß unter
 * {@link #fussMax} m NHN liegt (dort kommt Wasser hin) [G]:
 * <ol>
 * <li>Höhen im Raster {@link #raster} m; Neigung aus Mittendifferenzen über ± {@link #basis}/2.</li>
 * <li>Je Zelle von {@link Kanten#LAENGE} m der steilste Punkt.</li>
 * <li>Von ihm hangab bis zum Fuß (dort, wo es flach wird oder wieder steigt) und hangauf bis zur Krone, je höchstens
 * {@link #weg} m in Schritten von 1 m.</li>
 * <li>Gräben fallen weg: Steigt das Gelände hinter dem Fuß innerhalb von {@link #graben} m wieder um 0,5 m, ist es ein
 * Graben oder ein schmaler Priel, keine Kante zur See.</li>
 * <li>Doppelte fallen weg: Füße näher als 3 m mit ähnlicher Richtung.</li>
 * </ol>
 * Die Art kommt aus der Gestalt über den Hang (Deckwerk vor Warft vor Deich); die Rauheit für den Auflauf: Deckwerk aus gesetzten Steinen
 * γ_f = 0,9, Gras 1,0 (EurOtop 2018, Tafel 5.2) [G].
 */
public final class Kantenkarte {
    public double raster = 2.0, basis = 12.0, neigungMin = 1.0 / 15, hoeheMin = 0.5, fussMax = 2.5, weg = 40.0, graben = 15.0;
    public double rauDeckwerk = 0.9, rauGras = 1.0;
    public Kanten kanten;
    public final StringBuilder bericht = new StringBuilder();

    public void bauen(Gelaende gel, Gestalt gestalt, double[] gebiet) {
        Gelaende.Abfrage ab = gel.abfrage();
        int nx = (int) Math.round((gebiet[2] - gebiet[0]) / raster), ny = (int) Math.round((gebiet[3] - gebiet[1]) / raster);
        float[] z = new float[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) z[j * nx + i] = ab.hoehe(gebiet[0] + (i + 0.5) * raster, gebiet[1] + (j + 0.5) * raster, 1.0);
        int r = Math.max(1, (int) Math.round(basis / 2 / raster));
        // je Zelle von LAENGE m der steilste Punkt
        Map<Long, double[]> beste = new HashMap<>();
        for (int j = r; j < ny - r; j++)
            for (int i = r; i < nx - r; i++) {
                float c = z[j * nx + i], a = z[j * nx + i - r], b = z[j * nx + i + r], u = z[(j - r) * nx + i], o = z[(j + r) * nx + i];
                if (Float.isNaN(c) || Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(u) || Float.isNaN(o)) continue;
                if (c < -1 || c > 6) continue;
                double gx = (b - a) / (2 * r * raster), gy = (o - u) / (2 * r * raster), g = Math.hypot(gx, gy);
                if (g < neigungMin) continue;
                double ost = gebiet[0] + (i + 0.5) * raster, nord = gebiet[1] + (j + 0.5) * raster;
                long key = ((long) Math.floor(ost / Kanten.LAENGE) << 32) ^ (long) Math.floor(nord / Kanten.LAENGE);
                double[] v = beste.get(key);
                if (v == null || g > v[2]) beste.put(key, new double[] {ost, nord, g, -gx / g, -gy / g});
            }
        List<Kanten.Kante> l = new ArrayList<>();
        Map<Long, List<Kanten.Kante>> fuesse = new HashMap<>();
        int graeben = 0, niedrig = 0, hoch = 0, doppelt = 0;
        for (double[] v : beste.values()) {
            double ost = v[0], nord = v[1], nxs = v[3], nys = v[4];
            // hangab zum Fuß
            double zmin = ab.hoehe(ost, nord, 1.0), sf = 0;
            double zv = zmin;
            int flach = 0;
            for (double s = 1; s <= weg; s += 1) {
                double zz = ab.hoehe(ost + nxs * s, nord + nys * s, 1.0);
                if (Double.isNaN(zz)) break;
                if (zz < zmin) { zmin = zz; sf = s; }
                if (zz > zmin + 0.3) break;
                flach = Math.abs(zz - zv) < 0.02 ? flach + 1 : 0;
                zv = zz;
                if (flach >= 5) break;
            }
            // hangauf zur Krone
            double zmax = ab.hoehe(ost, nord, 1.0), sk = 0;
            zv = zmax;
            flach = 0;
            for (double s = 1; s <= weg; s += 1) {
                double zz = ab.hoehe(ost - nxs * s, nord - nys * s, 1.0);
                if (Double.isNaN(zz)) break;
                if (zz > zmax) { zmax = zz; sk = s; }
                if (zz < zmax - 0.3) break;
                flach = Math.abs(zz - zv) < 0.02 ? flach + 1 : 0;
                zv = zz;
                if (flach >= 5) break;
            }
            if (zmax - zmin < hoeheMin) { niedrig++; continue; }
            if (zmin > fussMax) { hoch++; continue; }
            double fo = ost + nxs * sf, fn = nord + nys * sf;
            // Graben: hinter dem Fuß steigt es bald wieder
            boolean istGraben = false;
            for (double s = 1; s <= graben; s += 1) {
                double zz = ab.hoehe(fo + nxs * s, fn + nys * s, 1.0);
                if (!Double.isNaN(zz) && zz > zmin + 0.5) { istGraben = true; break; }
            }
            if (istGraben) { graeben++; continue; }
            double neig = (zmax - zmin) / Math.max(1, sf + sk);
            // Art über den Hang (fünf Punkte vom Fuß bis zur Krone): Deckwerk vor Warft vor Deich
            int a = 0;
            if (gestalt != null)
                for (int q = 0; q <= 4; q++) {
                    int art = gestalt.bei(fo - nxs * q * 0.25 * (sf + sk), fn - nys * q * 0.25 * (sf + sk))[0];
                    if (art == Gestalt.DECKWERK) a = art;
                    else if (art == Gestalt.WARFT && a != Gestalt.DECKWERK) a = art;
                    else if (art == Gestalt.DEICH && a == 0) a = art;
                }
            Kanten.Kante k = new Kanten.Kante(fo, fn, nxs, nys, zmin, zmax, neig, a == Gestalt.DECKWERK ? rauDeckwerk : rauGras, a);
            long key = ((long) Math.floor(fo / 3) << 32) ^ (long) Math.floor(fn / 3);
            boolean dop = false;
            for (int dj = -1; dj <= 1 && !dop; dj++)
                for (int di = -1; di <= 1 && !dop; di++) {
                    List<Kanten.Kante> q = fuesse.get(((long) (Math.floor(fo / 3) + di) << 32) ^ (long) (Math.floor(fn / 3) + dj));
                    if (q == null) continue;
                    for (Kanten.Kante x : q)
                        if (Math.hypot(x.ost() - fo, x.nord() - fn) < 3 && x.nx() * nxs + x.ny() * nys > 0.7) { dop = true; break; }
                }
            if (dop) { doppelt++; continue; }
            fuesse.computeIfAbsent(key, x -> new ArrayList<>()).add(k);
            l.add(k);
        }
        l.sort((p, q) -> p.nord() != q.nord() ? Double.compare(p.nord(), q.nord()) : Double.compare(p.ost(), q.ost()));
        kanten = new Kanten(l);
        int[] je = new int[6];
        for (Kanten.Kante k : l) je[Math.min(5, k.art())]++;
        double[] nm = l.stream().mapToDouble(Kanten.Kante::neigung).sorted().toArray();
        bericht.append(String.format(Locale.ROOT, "Raster %.1f m (%d × %d); %d steile Zellen; %d Abschnitte (Deckwerk %d, Warft %d, Deich %d, Kante %d); weggelassen: %d zu niedrig, %d Fuß über %.1f m NHN, %d Gräben, %d doppelt; Neigung Median %s",
                raster, nx, ny, beste.size(), l.size(), je[5], je[4], je[3], je[0] + je[1] + je[2], niedrig, hoch, fussMax, graeben, doppelt,
                nm.length > 0 ? String.format(Locale.ROOT, "1:%.1f", 1 / nm[nm.length / 2]) : "–"));
    }
}
