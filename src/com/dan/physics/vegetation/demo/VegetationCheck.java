package com.dan.physics.vegetation.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Ueberflutung;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Zonen;
import com.dan.physics.vegetation.Salzwiese;
import com.dan.physics.vegetation.VegetationModul;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Kerns der Salzwiese ohne Lauf und ohne Karte (P13b, Werkbuch HalligHooge Teil A, 12.14): Statistik der
 * Überflutung an einer gerechneten Tide, Zonen, Pflanzen je Zone und Jahreszeit, Anteile und Rauheit je Zelle, Zonen in
 * der Gestalt und zurück. Attest {@code docs/status/P13b_Vegetation.md}.
 */
public final class VegetationCheck {
    private static final Attest A = new Attest("com.dan.physics.vegetation", VegetationModul.VERSION);

    public static void main(String[] args) {
        System.out.println("VegetationCheck – Kern der Salzwiese (Werkbuch HalligHooge Teil A, P13b)");
        try {
            ueberflutung();
            zonen();
            pflanzen();
            anteile();
            gestalt();
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P13b_Vegetation.md"), "P13b – VegetationCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Tide η(t) = 0,1 + a(t) cos(ω t) mit Spring-Nipp a = 1,5 + 0,3 cos(2π t / 14,77 d); drei Zellen: tief, am MThw, trocken. */
    static void ueberflutung() {
        A.teil("Statistik der Überflutung an einer gerechneten Tide");
        Gitter g = Gitter.utm("T", 32, 0, 0, 20, 3, 1);
        Ueberflutung u = new Ueberflutung(g, 0);
        double[] grund = {-5, 1.45, 5};
        double om = 2 * Math.PI / Ueberflutung.TIDE, sn = 2 * Math.PI / (14.77 * 86400), dt = 300;
        int tiden = 29;
        double ende = tiden * Ueberflutung.TIDE;
        float[] eta = new float[3], h = new float[3];
        double[] max = new double[tiden];
        java.util.Arrays.fill(max, Double.NEGATIVE_INFINITY);
        for (double t = dt; t <= ende + 1e-6; t += dt) {
            double e = 0.1 + (1.5 + 0.3 * Math.cos(sn * t)) * Math.cos(om * t);
            for (int c = 0; c < 3; c++) { eta[c] = (float) e; h[c] = (float) Math.max(0, e - grund[c]); }
            u.stand(t, dt, eta, h);
            int w = (int) Math.floor(t / Ueberflutung.TIDE);
            if (w < tiden) max[w] = Math.max(max[w], (float) e);
        }
        u.abschliessen(ende);
        double soll = 0;
        for (double x : max) soll += x;
        soll /= tiden;
        A.pruefe(u.tiden == tiden && Math.abs(u.mthw[0] - soll) < 1e-4, "MThw der tiefen Zelle gegen die Scheitel der Tide", f("%.4f gegen %.4f m, %d Tiden", u.mthw[0], soll, u.tiden), "± 0,1 mm");
        A.pruefe(u.anteil[0] == 1 && u.anteil[1] > 0 && u.anteil[1] < 1 && u.anteil[2] == 0, "Anteil der Tiden mit Wasser: tief, am MThw, trocken",
                f("%.2f, %.2f, %.2f", u.anteil[0], u.anteil[1], u.anteil[2]), "1; zwischen 0 und 1; 0");
        A.pruefe(u.gefuellt[0] == 0 && u.gefuellt[1] == 1 && u.gefuellt[2] == 1 && u.mthw[2] == u.mthw[0], "Zellen, die nicht in jeder Tide nass sind, bekommen das MThw der Nachbarn",
                f("gefüllt %.0f, %.0f, %.0f; MThw %.4f, %.4f, %.4f", u.gefuellt[0], u.gefuellt[1], u.gefuellt[2], u.mthw[0], u.mthw[1], u.mthw[2]), "0, 1, 1; gleich");
        A.pruefe(u.mspthw[0] > u.mthw[0] && u.mthw[0] > u.mnpthw[0] && u.dauer[0] == 1 && u.dauer[2] == 0, "Spring über Mittel über Nipp; Dauer",
                f("MSpThw %.3f, MThw %.3f, MNpThw %.3f m; Dauer %.2f / %.2f / %.2f", u.mspthw[0], u.mthw[0], u.mnpthw[0], u.dauer[0], u.dauer[1], u.dauer[2]), "ja");
    }

    static void zonen() {
        A.teil("Zonen nach der Höhe über MThw");
        double[] h = {-0.5, -0.40, -0.01, 0, 0.34, 0.35, 1.2, Double.NaN};
        int[] soll = {Zonen.KEINE, Zonen.PIONIER, Zonen.PIONIER, Zonen.UNTEN, Zonen.UNTEN, Zonen.OBEN, Zonen.OBEN, Zonen.KEINE};
        boolean ok = true;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            int z = Zonen.zone(h[i], -0.40, 0.35);
            ok &= z == soll[i];
            b.append(f("%s→%d ", Double.isNaN(h[i]) ? "NaN" : f("%+.2f", h[i]), z));
        }
        A.pruefe(ok, "Grenzen −0,40 / 0 / +0,35 m über MThw", b.toString().strip(), "wie gesetzt");
        boolean byte_ = true;
        for (double x = -1.2; x <= 1.2; x += 0.013) byte_ &= Math.abs(Zonen.hoehe(Zonen.hoeheByte(x)) - x) <= 0.005 + 1e-9;
        A.pruefe(byte_, "Höhe über MThw als Byte (1 cm) hin und zurück", "−1,20 … +1,20 m", "± 0,5 cm");
    }

    static void pflanzen() {
        A.teil("Pflanzen je Zone und Jahreszeit");
        Salzwiese s = new Salzwiese();
        A.pruefe(Math.abs(s.sommer(20)) < 1e-9 && s.sommer(203) > 0.99, "Jahreszeit: Mitte Januar 0, Ende Juli 1", f("%.3f, %.3f", s.sommer(20), s.sommer(203)), "0; > 0,99");
        StringBuilder b = new StringBuilder();
        for (int z = 1; z <= 3; z++)
            b.append(f("%s: n Sommer %.4f, Winter %.4f, beweidet %.4f; ", Zonen.name(z), Salzwiese.manning(s.z0(z, false, 203)), Salzwiese.manning(s.z0(z, false, 20)), Salzwiese.manning(s.z0(z, true, 203))));
        A.info("Manning-n nach Strickler aus z0 (zur Auskunft; Packung: Fenne 0,030, Salzwiese 0,035)", b.toString().strip());
        A.pruefe(s.deckung(1, 20) < s.deckung(1, 203) && s.hoehe(3, true, 203) < s.hoehe(3, false, 203), "Pionierzone im Winter lichter; Weide kürzer",
                f("Deckung Pionier %.2f / %.2f; Höhe oben %.3f / %.3f m", s.deckung(1, 20), s.deckung(1, 203), s.hoehe(3, true, 203), s.hoehe(3, false, 203)), "ja");
    }

    /** Eine Kachel der Zonen 500 m: links obere Salzwiese, rechts halb untere, halb nichts. */
    static Zonen probeZonen() {
        int n = 500;
        byte[] d = new byte[2 * n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int o = 2 * (j * n + i);
                if (i < 250) { d[o] = Zonen.OBEN; d[o + 1] = (byte) Zonen.hoeheByte(0.6); }
                else if (j % 2 == 0) { d[o] = Zonen.UNTEN; d[o + 1] = (byte) Zonen.hoeheByte(0.1); }
            }
        return new Zonen(1, n, List.of(new Zonen.Kachel(1000, 2000, d)));
    }

    static void anteile() {
        A.teil("Anteile und Rauheit je Zelle (VegetationModul)");
        Zonen z = probeZonen();
        Gitter g = Gitter.utm("G3", 32, 1000, 2000, 20, 25, 25);
        Salzwiese s = new Salzwiese();
        VegetationModul vm = new VegetationModul(List.of(g), z, null, s, 300, java.time.Instant.parse("2026-07-22T00:00:00Z"));
        int tag = 203;
        vm.rechne(tag);
        float[] z0 = vm.z0(0), dk = vm.deckung(0), bew = vm.bewachsen(0);
        int links = g.index(2, 10), rechts = g.index(20, 10);
        double soll = s.z0(Zonen.OBEN, false, tag);
        A.pruefe(Math.abs(bew[links] - 1) < 1e-6 && Math.abs(z0[links] - soll) < 1e-6 && Math.abs(dk[links] - s.deckung(Zonen.OBEN, tag)) < 1e-6,
                "Zelle ganz obere Salzwiese: Anteil, z0, Bedeckung", f("%.3f, %.4f m (soll %.4f), %.3f", bew[links], z0[links], soll, dk[links]), "1; wie Salzwiese");
        A.pruefe(Math.abs(bew[rechts] - 0.5) < 1e-6 && Math.abs(z0[rechts] - s.z0(Zonen.UNTEN, false, tag)) < 1e-6 && Math.abs(dk[rechts] - 0.5 * s.deckung(Zonen.UNTEN, tag)) < 1e-6,
                "Zelle halb untere Salzwiese: z0 über den bewachsenen Teil, Bedeckung über die Zelle", f("%.3f, %.4f m, %.3f", bew[rechts], z0[rechts], dk[rechts]), "0,5; z0 der Zone; halbe Bedeckung");
        // F155 A: Zone „Fenne ohne Tide“ wie obere Salzwiese, beweidet; im Bild bleibt die Fenne
        int n = 500;
        byte[] d = new byte[2 * n * n];
        for (int k = 0; k < n * n; k++) { d[2 * k] = Zonen.FENNE; d[2 * k + 1] = (byte) Zonen.hoeheByte(0.1); }
        Zonen zf = new Zonen(1, n, List.of(new Zonen.Kachel(1000, 2000, d)));
        VegetationModul vf = new VegetationModul(List.of(g), zf, null, s, 300, java.time.Instant.parse("2026-07-22T00:00:00Z"));
        vf.rechne(tag);
        double sollF = s.z0(Zonen.OBEN, true, tag);
        byte[] ga = new byte[2 * 1000 * 1000];
        for (int k = 0; k < 1000 * 1000; k++) ga[2 * k] = (byte) Gestalt.FENNE;
        Gestalt gf = zf.aufGestalt(new Gestalt(0.5, 1000, List.of(new Gestalt.Kachel(1000, 2000, ga))));
        boolean bleibt = java.util.Arrays.equals(gf.kacheln.get(0).daten(), ga);
        A.pruefe(Math.abs(vf.z0(0)[links] - sollF) < 1e-6 && Math.abs(vf.deckung(0)[links] - s.deckung(Zonen.OBEN, tag)) < 1e-6 && bleibt,
                "Fenne ohne Tidezone (F155 A): z0 wie obere Salzwiese beweidet, Bedeckung wie obere; im Bild Fenne",
                f("z0 %.4f m (soll %.4f), Bedeckung %.3f; Bild %s", vf.z0(0)[links], sollF, vf.deckung(0)[links], bleibt ? "unverändert" : "geändert"), "ja");
        vm.mindestAnteil = 0.6;
        vm.rechne(tag + 1);
        A.pruefe(vm.z0(0)[rechts] == 0 && vm.z0(0)[links] > 0, "Unter dem Mindestanteil keine Rauheit der Pflanzen (die Packung gilt)", f("%.4f / %.4f m", vm.z0(0)[rechts], vm.z0(0)[links]), "0 / > 0");
    }

    static void gestalt() {
        A.teil("Zonen in der Gestalt und zurück (Bild, Prüfstein 7)");
        Zonen z = probeZonen();
        int n = 1000;
        byte[] d = new byte[2 * n * n];
        Random r = new Random(1);
        int[] arten = {0, Gestalt.FENNE, Gestalt.SALZWIESE, Gestalt.DEICH, Gestalt.WARFT, Gestalt.GRABEN, Gestalt.KLINKER};
        for (int k = 0; k < n * n; k++) { d[2 * k] = (byte) arten[r.nextInt(arten.length)]; d[2 * k + 1] = (byte) r.nextInt(256); }
        Gestalt g = new Gestalt(0.5, n, List.of(new Gestalt.Kachel(1000, 2000, d)));
        Gestalt mz = z.aufGestalt(g);
        byte[] m = mz.kacheln.get(0).daten();
        long anders = 0, zurueck = 0, neu = 0;
        for (int k = 0; k < n * n; k++) {
            if (m[2 * k] != d[2 * k]) neu++;
            if ((byte) Zonen.ohneZone(m[2 * k] & 0xFF) != d[2 * k] || m[2 * k + 1] != d[2 * k + 1]) anders++;
            else zurueck++;
        }
        A.pruefe(anders == 0 && neu > 0, "Art ohne Zone gleich der Art der Gestalt (Bildpunkte)", f("%d mit Zone, %d gleich zurück, %d anders", neu, zurueck, anders), "0 anders");
        // F158 A: Salzwiese unter der Pionierzone (Zone keine, Höhe bekannt) wird Schlick, aus wieder Salzwiese
        byte[] ds = new byte[2 * 500 * 500];
        for (int k = 0; k < 500 * 500; k++) { ds[2 * k] = Zonen.KEINE; ds[2 * k + 1] = (byte) Zonen.hoeheByte(-0.8); }
        byte[] gs = new byte[2 * n * n];
        for (int k = 0; k < n * n; k++) gs[2 * k] = (byte) Gestalt.SALZWIESE;
        byte[] ms = new Zonen(1, 500, List.of(new Zonen.Kachel(1000, 2000, ds))).aufGestalt(new Gestalt(0.5, n, List.of(new Gestalt.Kachel(1000, 2000, gs)))).kacheln.get(0).daten();
        A.pruefe((ms[0] & 0xFF) == Zonen.ART_SCHLICK && Zonen.ohneZone(ms[0] & 0xFF) == Gestalt.SALZWIESE, "Salzwiese unter der Pionierzone im Bild Schlick, aus wieder Salzwiese (F158 A)",
                f("Art %d, ohne Zone %d", ms[0] & 0xFF, Zonen.ohneZone(ms[0] & 0xFF)), Zonen.ART_SCHLICK + ", " + Gestalt.SALZWIESE);
    }
}
