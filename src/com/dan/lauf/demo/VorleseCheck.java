package com.dan.lauf.demo;

import com.dan.core.archiv.Archiv;
import com.dan.core.gitter.Gitter;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Buehne;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Vorlesers (W5c, Werkbuch HalligHooge 131, F221 A, A47) ohne Fenster und Grafikkarte: eine nachgestellte
 * Platte, die nach {@code SCHLAF_NACH} s ohne Zugriff einschläft und {@code SCHLAF} s zum Aufwachen braucht, unter einem
 * Bild, das wie die Bildschleife je Bild fragt ({@code bereit}) und dann die Stände liest.
 * <pre>
 * java -cp dist\Renderer.jar com.dan.lauf.demo.VorleseCheck --szenen ...\Hooge.szenen [--szenario sturm]
 * </pre>
 */
public final class VorleseCheck {
    private static final Attest A = new Attest("lauf.Archivquelle (Vorleser)", "W5c");
    static final double SCHLAF_NACH = 3, SCHLAF = 6, RAFFER = 300, BILD = 1 / 60.0;

    public static void main(String[] args) throws Exception {
        String datei = null, sk = "sturm", archiv = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--szenario" -> sk = args[++i];
                case "--archiv" -> archiv = args[++i];
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
        System.out.println("VorleseCheck – Vorleser der Archivquelle (Werkbuch HalligHooge 131, W5c)");
        try {
            Path ordner;
            java.time.Instant beginn = null;
            if (archiv != null) {
                ordner = Path.of(archiv);
                A.info("Archiv", archiv);
            } else {
                Szenario s = Szenen.lies(new File(datei)).szenario(sk);
                ordner = Path.of(s.archiv.getPath());
                beginn = s.beginn;
                A.info("Szenario", s.name + ", " + s.archiv);
            }
            A.info("Nachgestellte Platte", f("schläft nach %.0f s ohne Zugriff ein, wacht in %.0f s auf; nur Stände außerhalb des Dateipuffers", SCHLAF_NACH, SCHLAF));
            Archiv.platteSimulieren(SCHLAF_NACH, SCHLAF);

            A.teil("1 Ohne Vorleser: A47 nachgestellt");
            try (Archivquelle q = new Archivquelle(ordner)) {
                double[] z = q.zeiten();
                Thread.sleep((long) ((SCHLAF_NACH + 0.5) * 1000));
                long n0 = System.nanoTime();
                lies(q, z[z.length / 2]);
                double d = (System.nanoTime() - n0) / 1e9;
                A.pruefe(d >= SCHLAF - 0.5, "Prüfstein V1: ein Stand nach Ruhe ohne Vorleser (wie die Bildschleife bis W5b)", f("%.2f s", d), f("≥ %.1f s (die Platte schläft)", SCHLAF - 0.5));
            }

            A.teil("2 Mit Vorleser: Abspielen mit Pause");
            try (Archivquelle q = new Archivquelle(ordner)) {
                double[] z = q.zeiten();
                double t = beginn != null ? Math.max(z[0], q.start.until(beginn, java.time.temporal.ChronoUnit.MILLIS) / 1000.0) : z[0];
                lies(q, t);
                Lauf l = spiele(q, t, 40, 12, 6, Double.NaN);
                A.info("Ablauf", f("%d Bilder in %.1f s, Szenenzeit %.0f s weit, %d Stände vorgelesen (%.0f MB), langsamster %.2f s",
                        l.bilder, l.sekunden, l.weit, q.vorStaende, q.vorBytes / 1048576.0, q.vorLangsamst));
                A.pruefe(l.bereit99 <= 0.1 && l.bereitMax <= 50, "Prüfstein V2: bereit() je Bild, auch während die Platte aufwacht",
                        f("99 %% der Bilder ≤ %.4f ms, größte %.2f ms", l.bereit99, l.bereitMax), "≤ 0,1 ms; größte ≤ 50 ms (wartet nie auf die Platte) [G]");
                A.pruefe(l.lesenMax <= Buehne.LANG * 1000, "Prüfstein V3: Lesen der Stände im Bild, wenn bereit (wartet nicht auf die Platte)", f("größte %.1f ms in %d Bildern mit neuem Stand", l.lesenMax, l.neu), "≤ 500 ms = Buehne.LANG (F222 A) [G]");
                A.pruefe(q.vorLangsamst >= SCHLAF - 0.5 && l.halten <= 0.5, "Prüfstein V4: die Platte schlief nach der Pause ein, das Bild musste nicht halten",
                        f("Vorleser wartete %.2f s; die Uhr hielt %.2f s (%d Mal)", q.vorLangsamst, l.halten, l.halteZahl), "Vorleser ≥ 5,5 s; Halten ≤ 0,5 s");
            }

            A.teil("3 Mit Vorleser: Sprung in kalte Stände");
            try (Archivquelle q = new Archivquelle(ordner)) {
                double[] z = q.zeiten();
                double t = z[2];
                lies(q, t);
                Lauf l0 = spiele(q, t, 3, 0, 3, Double.NaN);   // Zeitraffer 0: der Vorleser hat bald alles, die Platte schläft ein
                Thread.sleep((long) ((SCHLAF_NACH + 0.5) * 1000));
                double ziel = z[z.length - 3];
                Lauf l = spiele(q, l0.ende, 15, Double.NaN, 0, ziel);
                A.pruefe(l.bereitMax <= 50 && l.bisBereit >= SCHLAF - 0.5 && l.bisBereit <= SCHLAF + 2,
                        "Prüfstein V5: Sprung in kalte Stände – das Bild läuft weiter, die Uhr hält, bis sie da sind",
                        f("bereit nach %.2f s; bereit() größte %.2f ms; Bild danach bei %.0f s (Ziel %.0f s)", l.bisBereit, l.bereitMax, l.ende, ziel), "≥ 5,5 s und ≤ 8 s; bereit() ≤ 50 ms");
            }
        } catch (Throwable x) {
            A.fehler(x);
        } finally {
            Archiv.platteSimulieren(-1, 0);
        }
        Path at = A.schreibe(Path.of("docs", "status", "W5c_Vorleser.md"), "W5c – Vorleser");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    record Lauf(int bilder, double sekunden, double weit, double bereitMax, double bereit99, double lesenMax, int neu, double halten, int halteZahl, double bisBereit, double ende) { }

    /**
     * Spielt wie die Bildschleife: je Bild vorlesen und bereit fragen; bereit: die Stände um t lesen, sonst hält die Uhr.
     * Pause von {@code pauseAb} s an für {@code pauseS} s (Zeitraffer 0). {@code sprung}: die Uhr springt zu Beginn dorthin.
     */
    static Lauf spiele(Archivquelle q, double t, double sekunden, double pauseAb, double pauseS, double sprung) throws Exception {
        double[] z = q.zeiten();
        java.util.List<Double> bereitMs = new java.util.ArrayList<>();
        double gezeigt = t, uhr = Double.isNaN(sprung) ? t : sprung, weit0 = t, bereitMax = 0, lesenMax = 0, halten = 0, bisBereit = Double.NaN;
        int bilder = 0, neu = 0, halteZahl = 0;
        boolean hielt = false;
        int paar = -1;
        long n0 = System.nanoTime();
        while (true) {
            double el = (System.nanoTime() - n0) / 1e9;
            if (el >= sekunden) break;
            boolean pause = !Double.isNaN(pauseAb) && el >= pauseAb && el < pauseAb + pauseS;
            double raffer = pause ? 0 : RAFFER;
            double gewollt = Math.min(z[z.length - 1], uhr + raffer * BILD);
            q.vorlesen(gewollt, raffer, com.dan.lauf.Buehne.VORLESEN_S);
            long b0 = System.nanoTime();
            boolean bereit = q.bereit(gewollt);
            double bm = (System.nanoTime() - b0) / 1e6;
            bereitMax = Math.max(bereitMax, bm);
            bereitMs.add(bm);
            if (bereit) {
                if (Double.isNaN(bisBereit) && !Double.isNaN(sprung)) bisBereit = el;
                uhr = gewollt;
                gezeigt = gewollt;
                int k = index(z, gezeigt);
                if (k != paar) {
                    long l0 = System.nanoTime();
                    lies(q, z[k]);
                    if (k + 1 < z.length) lies(q, z[k + 1]);
                    lesenMax = Math.max(lesenMax, (System.nanoTime() - l0) / 1e6);
                    paar = k;
                    neu++;
                }
                hielt = false;
            } else {
                // die Uhr hält; gezeigt bleibt
                halten += BILD;
                if (!hielt) halteZahl++;
                hielt = true;
            }
            bilder++;
            Thread.sleep((long) (BILD * 1000));
        }
        bereitMs.sort(null);
        double b99 = bereitMs.isEmpty() ? 0 : bereitMs.get((int) Math.floor(0.99 * (bereitMs.size() - 1)));
        return new Lauf(bilder, (System.nanoTime() - n0) / 1e9, gezeigt - weit0, bereitMax, b99, lesenMax, neu, halten, halteZahl, bisBereit, gezeigt);
    }

    static int index(double[] z, double t) {
        int i = java.util.Arrays.binarySearch(z, t);
        if (i >= 0) return i;
        return Math.max(0, -i - 2);
    }

    /** Liest wie das Bild alle Gitter und den Wind des Stands bei der Zeit des nächsten Stands bis t. */
    static void lies(Archivquelle q, double t) {
        double[] z = q.zeiten();
        double ts = z[index(z, t)];
        List<Gitter> gs = q.gitter();
        for (int k = 0; k < gs.size(); k++) {
            int n = gs.get(k).zellen();
            q.stand(ts, k, new float[n], new float[n], new float[n]);
        }
        Gitter w = q.windGitter();
        if (w != null) q.wind(ts, new float[w.zellen()], new float[w.zellen()]);
    }
}
