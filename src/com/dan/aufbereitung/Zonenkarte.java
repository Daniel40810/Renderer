package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Zonen;
import com.dan.core.gitter.Ueberflutung;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schritt zonen (seit P13b, Werkbuch HalligHooge Teil A, 12.14; F151 A, F152 A): die Zonen der Salzwiese zu 1 m aus
 * der Höhe des Geländes über dem örtlichen MThw. Das MThw kommt aus der Statistik eines Laufs ({@link Ueberflutung},
 * Lauf B1 über eine Spring-Nipp-Folge auf G2), bilinear auf den Meter. Zonen nur auf Fenne und Salzwiese der Gestalt
 * (Deich und Warft bleiben Rasen); Kacheln wie die Gestalt (500 m), 1 m je Bildpunkt.
 */
public final class Zonenkarte {
    public double zelle = 1.0;
    /** Untere Grenze der Pionierzone und Grenze untere/obere Salzwiese, m über MThw [Literatur, G]. */
    public double pionierUnten = -0.40, grenzeOben = 0.35;
    /** F155 A: die Fenne (hinter dem Sommerdeich) ohne Tidezone, als Zone {@link Zonen#FENNE}; false: Zonen nach der Höhe wie die Salzwiese. */
    public boolean fenneOhneTide = true;

    // Ergebnis
    public Zonen zonen;
    /** Fläche (m²) je Zone (0–3), getrennt nach Fenne (Index 0) und Salzwiese (Index 1). */
    public final double[][] flaeche = new double[2][5];
    public double mthwMin = Double.POSITIVE_INFINITY, mthwMax = Double.NEGATIVE_INFINITY;
    public final StringBuilder bericht = new StringBuilder();

    /**
     * NDVI des DOP20 je Zone (Prüfstein 5): je Bildpunkt der Zonen das Mittel von Rot und Infrarot über die 5 × 5
     * Bildpunkte zu 20 cm darunter, NDVI = (NIR − R) / (NIR + R). Gibt je Zone {Summe, Zahl} zurück (Index 0–4).
     */
    public static double[][] ndviJeZone(Zonen z, java.nio.file.Path dopOrdner) throws IOException {
        double[][] r = new double[5][2];
        java.util.Map<Long, java.nio.file.Path> dop = Gehoelzkarte.kacheln(dopOrdner, Gehoelzkarte.DOP);
        if (dop.isEmpty()) return r;
        java.util.Set<Long> km = new java.util.TreeSet<>();
        for (Zonen.Kachel k : z.kacheln) km.add(((long) (int) Math.floor(k.ost() / 1000) << 16) | (int) Math.floor(k.nord() / 1000));
        for (long key : km) {
            int ek = (int) (key >> 16), nk = (int) (key & 0xFFFF);
            double wx0 = ek * 1000.0, wy1 = nk * 1000.0 + 1000;
            int m = 5000;
            float[] rot = Gehoelzkarte.fenster(dop, wx0, wy1, m, 0, Float.NaN), nir = Gehoelzkarte.fenster(dop, wx0, wy1, m, 3, Float.NaN);
            for (Zonen.Kachel k : z.kacheln) {
                if ((int) Math.floor(k.ost() / 1000) != ek || (int) Math.floor(k.nord() / 1000) != nk) continue;
                for (int j = 0; j < z.n; j++)
                    for (int i = 0; i < z.n; i++) {
                        int zone = k.daten()[2 * (j * z.n + i)] & 0xFF;
                        if (zone > Zonen.FENNE) continue;
                        double ost = k.ost() + (i + 0.5) * z.zelle, nord = k.nord() + z.kantenlaenge() - (j + 0.5) * z.zelle;
                        int pi = (int) Math.floor((ost - wx0) / 0.2) - 2, pj = (int) Math.floor((wy1 - nord) / 0.2) - 2;
                        double sr = 0, sn = 0;
                        int c = 0;
                        for (int b = 0; b < 5; b++)
                            for (int a = 0; a < 5; a++) {
                                int x = pi + a, y = pj + b;
                                if (x < 0 || y < 0 || x >= m || y >= m) continue;
                                float rr = rot[y * m + x], nn = nir[y * m + x];
                                if (Float.isNaN(rr) || Float.isNaN(nn)) continue;
                                sr += rr; sn += nn; c++;
                            }
                        if (c == 0 || sr + sn <= 0) continue;
                        // nur Bildpunkte mit Zone zählen; „ohne Zone“ (0) ist Fenne oder Salzwiese unter der Pionierzone
                        if ((k.daten()[2 * (j * z.n + i) + 1] & 0xFF) == 0) continue;
                        r[zone][0] += (sn - sr) / (sn + sr);
                        r[zone][1]++;
                    }
            }
        }
        return r;
    }

    public void bauen(Ueberflutung u, Gelaende gel, Gestalt gestalt) throws IOException {
        int n = (int) Math.round(gestalt.kantenlaenge() / zelle);
        Gelaende.Abfrage ab = gel.abfrage();
        List<Zonen.Kachel> l = new ArrayList<>();
        double px = zelle * zelle;
        for (Gestalt.Kachel k : gestalt.kacheln) {
            byte[] d = new byte[2 * n * n];
            boolean etwas = false;
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double ost = k.ost() + (i + 0.5) * zelle, nord = k.nord() + gestalt.kantenlaenge() - (j + 0.5) * zelle;
                    int gi = Math.min(gestalt.n - 1, (int) Math.floor((ost - k.ost()) / gestalt.zelle)), gj = Math.min(gestalt.n - 1, (int) Math.floor((k.nord() + gestalt.kantenlaenge() - nord) / gestalt.zelle));
                    int art = k.daten()[2 * (gj * gestalt.n + gi)] & 0xFF;
                    if (art != Gestalt.FENNE && art != Gestalt.SALZWIESE) continue;
                    double z = ab.hoehe(ost, nord, zelle);
                    if (Double.isNaN(z)) continue;
                    double mthw = u.wert(u.mthw, ost, nord);
                    mthwMin = Math.min(mthwMin, mthw);
                    mthwMax = Math.max(mthwMax, mthw);
                    double ueber = z - mthw;
                    int zone = art == Gestalt.FENNE && fenneOhneTide ? Zonen.FENNE : Zonen.zone(ueber, pionierUnten, grenzeOben);
                    int o = 2 * (j * n + i);
                    d[o] = (byte) zone;
                    d[o + 1] = (byte) Zonen.hoeheByte(ueber);
                    flaeche[art == Gestalt.FENNE ? 0 : 1][zone] += px;
                    etwas = true;
                }
            if (etwas) l.add(new Zonen.Kachel(k.ost(), k.nord(), d));
        }
        zonen = new Zonen(zelle, n, l);
        bericht.append(String.format(Locale.ROOT, "%d Kacheln zu %d × %d Bildpunkten; MThw über Fenne und Salzwiese %.2f … %.2f m NHN", l.size(), n, n, mthwMin, mthwMax));
        String[] art = {"Fenne", "Salzwiese"};
        for (int a = 0; a < 2; a++) {
            double s = 0;
            for (double x : flaeche[a]) s += x;
            bericht.append(String.format(Locale.ROOT, "; %s %.1f ha: ohne %.0f %%, Pionier %.0f %%, unten %.0f %%, oben %.0f %%, Fenne ohne Tide %.0f %%", art[a], s / 1e4,
                    100 * flaeche[a][0] / Math.max(1, s), 100 * flaeche[a][1] / Math.max(1, s), 100 * flaeche[a][2] / Math.max(1, s), 100 * flaeche[a][3] / Math.max(1, s),
                    100 * flaeche[a][4] / Math.max(1, s)));
        }
    }
}
