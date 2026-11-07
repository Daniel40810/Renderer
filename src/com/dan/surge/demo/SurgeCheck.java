package com.dan.surge.demo;

import com.dan.surge.FloodMap;
import com.dan.surge.Front;
import com.dan.surge.Lip;
import com.dan.surge.Noise;
import com.dan.surge.Surge;
import com.dan.surge.Veil;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.surge} ohne Grafikkarte: Flutkarte (Füllen, Textur, Blöcke, Hülle der Gischt), Lippe an einer
 * kreisrunden Front (Lage der Linie, Richtung, Querschnitt, Vorhang, Tropfen, flaches Wasser, Wiederholbarkeit),
 * Schleier am Auflauf (Abwurf, Bahn mit Luftwiderstand, Samen), die Regeln der Front und das Rauschen. Endet mit
 * „Alles in Ordnung.“
 */
public final class SurgeCheck {
    static int bad, all;
    static final double C = 40, LEVEL = 0;   // Tempo der Front (m/s), Pegel

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("com.dan.surge – Prüfung");
        // eine kreisrunde Front um (0, 0): Ankunft r / C, Welle 40 m bis 2 km, dann abnehmend; 12 × 12 km zu 8 m
        FloodMap f = ring(1500);
        int dryBefore = count(f.arrival);
        f.fillDry(2);
        check(count(f.arrival) > dryBefore, fmt("Flutkarte: trockene Zellen am Rand bekommen die Ankunft der Nachbarn (%d → %d Zellen)", dryBefore, count(f.arrival)));
        float[] tx = f.textureData();
        check(tx[0] == 1e6f && tx[1] == -1000f && tx.length == f.nx * f.nz * 2, "Textur: nie 1e6, trocken −1000, zwei Werte je Zelle");
        f.blocks(32, LEVEL);
        double[] b30 = f.sprayBox(30, LEVEL, 40), b80 = f.sprayBox(80, LEVEL, 40);
        check(b30 != null && b80 != null && (b80[2] - b80[0]) > (b30[2] - b30[0]) && b30[5] > LEVEL + 140,
                fmt("Hülle der Gischt wächst mit der Front: %.0f m nach 30 s, %.0f m nach 80 s breit", b30[2] - b30[0], b80[2] - b80[0]));

        // Lippe nach 40 s: Linie bei rund C · (40 − 0,35) = 1 586 m
        Lip lip = Surge.lituya1958().lip;
        lip.range = 6000;
        lip.build(f, 40, 0, 0, LEVEL, (x, z) -> LEVEL - 200);
        double r0 = C * (40 - lip.behind);
        double rmin = 1e9, rmax = 0, dirErr = 0, uMax = 0, yMin = 1e9, tipMin = 1e9, tipMax = 0, nErr = 0;
        for (int v = 0; v < lip.vertices; v++) {
            int o = v * Lip.FL;
            double bx = lip.mesh[o + 12], bz = lip.mesh[o + 13], r = Math.hypot(bx, bz);
            rmin = Math.min(rmin, r); rmax = Math.max(rmax, r);
            dirErr = Math.max(dirErr, Math.abs(lip.mesh[o + 8] * bx / r + lip.mesh[o + 9] * bz / r - 1));
            double nl = Math.sqrt(lip.mesh[o + 3] * lip.mesh[o + 3] + lip.mesh[o + 4] * lip.mesh[o + 4] + lip.mesh[o + 5] * lip.mesh[o + 5]);
            nErr = Math.max(nErr, Math.abs(nl - 1));
            float u = lip.mesh[o + 6];
            if (u > 1.5) yMin = Math.min(yMin, lip.mesh[o + 1]);
            if (Math.abs(u - 0.62) < 1e-4) { double hh = (lip.mesh[o + 1] - LEVEL) / lip.mesh[o + 7]; tipMin = Math.min(tipMin, hh); tipMax = Math.max(tipMax, hh); }
            uMax = Math.max(uMax, u);
        }
        check(lip.segments > 500, fmt("Lippe: %d Stücke auf dem Kreis (Umfang %.0f m)", lip.segments, 2 * Math.PI * r0));
        check(rmin > r0 - 16 && rmax < r0 + 16, fmt("Linie der Lippe bei %.0f–%.0f m (soll %.0f m: Krone 0,35 s hinter dem Fuß)", rmin, rmax, r0));
        check(dirErr < 0.01, fmt("Laufrichtung aus dem Gefälle der Ankunft: radial (größter Fehler %.4f)", dirErr));
        check(nErr < 1e-5, "Normalen der Ecken haben die Länge 1");
        check(tipMin >= 0.59 && tipMax <= 0.75, fmt("Spitze bei %.2f–%.2f der Höhe (früh hoch, eingerollt tiefer)", tipMin, tipMax));
        check(yMin < LEVEL && Math.abs(uMax - 3.0) < 1e-6, fmt("Vorhang reicht unter den Pegel (%.2f m), u bis 3", yMin));
        check(lip.dropCount > 1000, fmt("Tropfen und Spritzer: %d", lip.dropCount));
        double dyMin = 1e9, dMax = 0;
        for (int k = 0; k < lip.dropCount; k++) {
            int o = k * Lip.FT;
            dyMin = Math.min(dyMin, lip.drops[o + 1]);
            dMax = Math.max(dMax, Math.hypot(lip.drops[o], lip.drops[o + 2]) - r0);
        }
        check(dyMin >= LEVEL - 0.3 && dMax < 120, fmt("Tropfen über dem Wasser (tiefster %.2f m), höchstens %.0f m vor der Linie", dyMin, dMax));
        float[] m1 = java.util.Arrays.copyOf(lip.mesh, lip.meshFloats());
        int d1 = lip.dropCount;
        lip.build(f, 40, 0, 0, LEVEL, (x, z) -> LEVEL - 200);
        check(java.util.Arrays.equals(m1, java.util.Arrays.copyOf(lip.mesh, lip.meshFloats())) && d1 == lip.dropCount, "Lippe wiederholbar (gleiche Ecken, gleiche Tropfen)");
        lip.build(f, 40, 0, 0, LEVEL, (x, z) -> LEVEL - 5);
        check(lip.segments == 0 && lip.rejected[2] > 0, fmt("flaches Wasser (Grund 5 m unter dem Pegel): keine Lippe (%d verworfen)", lip.rejected[2]));
        lip.build(f, 40, 0, 0, LEVEL, (x, z) -> LEVEL - 200);
        lip.range = 1000;
        lip.build(f, 40, 0, 0, LEVEL, (x, z) -> LEVEL - 200);
        check(lip.segments == 0, "außerhalb des Umkreises um das Auge: keine Lippe");

        // Schleier: ein Hang nach Osten, die Zunge läuft bis 400 m hinauf
        int nx = 200, nz = 60;
        double c = 2;
        float[] mx = new float[nx * nz], an = new float[nx * nz], bd = new float[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double x = (i + 0.5) * c, ground = 50 + x * 1.0;   // 45° Hang ab 50 m
                int q = j * nx + i;
                bd[q] = (float) ground;
                boolean nass = ground < 400 + 8 * Math.sin(j * 0.3);
                mx[q] = nass ? (float) Math.min(ground + 6, 406) : Float.NaN;
                an[q] = nass ? (float) (120 + x / 20) : Float.NaN;
            }
        Veil veil = new Veil();
        float[] th = veil.threads(mx, an, bd, nx, nz, c, Veil.grid(0, 0, c));
        int n = th.length / Veil.FL;
        double tMin = 1e9, vMax = 0, xMin = 1e9;
        for (int k = 0; k < n; k++) {
            int o = k * Veil.FL;
            tMin = Math.min(tMin, th[o + 3]);
            vMax = Math.max(vMax, Math.hypot(th[o + 4], th[o + 6]));
            xMin = Math.min(xMin, th[o]);
        }
        check(n > 1000 && n % veil.perPoint == 0, fmt("Schleier: %d Fäden in Garben zu %d am oberen Rand der Zunge", n, veil.perPoint));
        check(xMin > 300, fmt("nur am Rand zum Trockenen (Grund über 60 m): Abwurf ab x = %.0f m", xMin));
        check(tMin >= 120 + 300 / 20.0 + 0.2 - 1, fmt("Abwurf nach der Ankunft (+0,2 bis 1,8 s): erster bei %.1f s", tMin));
        check(vMax <= 45 * 0.8 * 1.15 + 1e-3, fmt("Wurf höchstens 0,8 · 1,15 des Tempos der Front (höchstens 45 m/s): %.1f m/s", vMax));
        float[] th2 = veil.threads(mx, an, bd, nx, nz, c, Veil.grid(0, 0, c));
        check(java.util.Arrays.equals(th, th2), "Schleier wiederholbar (Samen 524)");
        double[] p = Veil.position(new double[]{0, 0, 0}, new double[]{10, 10, 0}, 30);
        double[] p2 = Veil.position(new double[]{0, 0, 0}, new double[]{10, 10, 0}, 31);
        check(Math.abs(p[0] - 10 / Veil.K) < 1e-4 && Math.abs((p2[1] - p[1]) + Veil.G / Veil.K) < 1e-3,
                fmt("Bahn mit Luftwiderstand: Weg waagerecht gegen v₀/k = %.1f m, Fall gegen g/k = %.1f m/s", 10 / Veil.K, Veil.G / Veil.K));

        // Regeln der Front
        check(Front.steepenLength(3, 1) == 0 && Front.steepenLength(30, 1) == 150 && Front.steepenLength(90, 1) == 0,
                fmt("Aufsteilen: unter 6 m nichts, bei 30 m %.0f m, über 80 m nichts (dort ist die Rechnung schon steil)", Front.steepenLength(30, 1)));
        check(Front.lipShare(-2, 30, 30) == 0 && Front.lipShare(0.5, 30, 30) > 0.9 && Front.lipShare(5, 30, 30) == 0 && Front.lipShare(0.5, 3, 30) == 0,
                "Lippe: nur in den ersten Sekunden, oben im Kamm, bei hohen Wellen");
        check(Math.abs(Front.sprayHeight(62, 0, 0) - 122) < 1e-9 && Math.abs(Front.sprayHeight(300, 10, 1) - 21) < 1e-9 && Math.abs(Front.sprayRise(1.5) - (1 - Math.exp(-1))) < 1e-12,
                "Gischtwand: über Wasser bis 122 m über dem Stand, an Land 8 m + 1,3 × Tiefe, steigt in 1,5 s");

        byte[] z1 = Noise.tile(64, 1958), z2 = Noise.tile(64, 1958);
        check(java.util.Arrays.equals(z1, z2), "Rauschen 64³ gleich bei gleichem Samen");
        Surge low = new Surge().quality(Surge.Quality.LOW);
        check(low.lip.dropSpacing > Surge.lituya1958().lip.dropSpacing && low.veil.perPoint < 40, "Stufe LOW: weniger Tropfen und Fäden");

        System.out.println(bad == 0 ? "Alles in Ordnung. (" + all + " Prüfungen)" : bad + " von " + all + " Prüfungen FEHLER");
        if (bad > 0) System.exit(1);
    }

    /** Kreisrunde Front um (0, 0) bis zum Halbmesser rMax… über 12 × 12 km zu 8 m. */
    static FloodMap ring(double unused) {
        double cell = 8, e = 12000;
        int n = (int) (e / cell);
        return FloodMap.of(n, n, cell, -e / 2, e / 2, (x, z) -> {
            double r = Math.hypot(x, z);
            if (r > 5200) return null;
            double h = r < 2000 ? 40 : 40 * Math.exp(-(r - 2000) / 1500);
            return new double[]{r / C, LEVEL + h};
        });
    }

    static int count(float[] a) { int n = 0; for (float v : a) if (!Float.isNaN(v)) n++; return n; }

    static void check(boolean ok, String what) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
    }

    static String fmt(String f, Object... a) { return String.format(Locale.GERMANY, f, a); }

    private SurgeCheck() { }
}
