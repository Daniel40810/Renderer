package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Buehne;
import com.dan.lauf.Lauf;
import com.dan.lauf.Pegelreihe;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Bühne ohne Fenster (Werkbuch HalligHooge Teil B, B.6): liest eine Szenendatei und prüft
 * <ol>
 * <li>die Datei: Gebiet, Archive und Ansichten sind da, die Ansichten liegen im Gebiet;</li>
 * <li>die Pegelreihe je Archiv gegen {@code BildCheck.zeiten} (Prüfstein 6): dieselbe Zelle, derselbe Pegel je
 * Stand, Hoch- und Niedrigwasser als Marken, der höchste Stand;</li>
 * <li>die Gleichheit (Prüfstein 3): je Archiv ein Standbild der Bühne gegen dasselbe Bild auf dem Weg von
 * {@code BildCheck} (gleiche Ansicht, gleiche Zeit).</li>
 * </ol>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BuehneCheck --szenen ..\HalligHooge\szenen\Hooge.szenen [--nur 1,2,3]
 * </pre>
 */
public final class BuehneCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    static final int BREITE = 960, HOEHE = 540;

    public static void main(String[] args) throws Exception {
        String datei = "..\\HalligHooge\\szenen\\Hooge.szenen", nur = "1,2,3";
        boolean attest = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> datei = args[++i];
                case "--nur" -> nur = args[++i];
                case "--ohne-attest" -> attest = false;
                default -> { }
            }
        }
        System.out.println("BuehneCheck – com.dan.lauf " + Lauf.VERSION + " (Werkbuch HalligHooge Teil B, B.6)");
        try {
            Szenen sz = Szenen.lies(new File(datei));
            A.info("Szenendatei", sz.datei.getPath());
            Gebiet ge = Gebiet.lies(sz.gebiet());
            if (nur.contains("1")) teil1(sz, ge);
            if (nur.contains("2")) teil2(sz, ge);
            if (nur.contains("3")) teil3(sz, ge);
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "TeilB_Buehne.md"), "Teil B – BuehneCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void teil1(Szenen sz, Gebiet ge) throws Exception {
        A.teil("1 Szenendatei");
        A.pruefe(sz.gebiet().isDirectory() && !ge.kette().isEmpty(), "Gebiet", sz.gebiet() + ", " + ge.kette().size() + " Gitter", "Ordner mit Gitterkette");
        A.pruefe(!sz.ansichten().isEmpty() && !sz.szenarien().isEmpty(), "Ansichten und Szenarien", sz.ansichten().size() + " Ansichten, " + sz.szenarien().size() + " Szenarien", "je mindestens 1");
        int aussen = 0;
        List<String> namen = new ArrayList<>();
        for (Ansicht a : sz.ansichten()) {
            boolean drin = false;
            for (Gebiet.Eintrag e : ge.kette()) if (e.gitter().lage == com.dan.core.gitter.Gitter.Lage.UTM && e.gitter().enthaelt(a.ost, a.nord)) drin = true;
            if (!drin) aussen++;
            namen.add(a.schluessel + (a.zurSonne() ? " (zur Sonne)" : ""));
        }
        A.pruefe(aussen == 0, "Ansichten außerhalb der Gitter in UTM", aussen + " von " + sz.ansichten().size() + " (" + String.join(", ", namen) + ")", "0");
        int fehlt = 0;
        List<String> sn = new ArrayList<>();
        for (Szenario s : sz.szenarien()) {
            if (!s.live() && !new File(s.archiv, "G3" + File.separator + "lauf.txt").isFile() && !hatLauf(s.archiv)) fehlt++;
            sn.add(s.schluessel + (s.live() ? " (live ab " + s.start + ", " + s.stufen + ")" : " (" + s.archiv + ")"));
        }
        A.pruefe(fehlt == 0, "Archive, die fehlen", fehlt + " (" + String.join("; ", sn) + ")", "0");
        double[] p = sz.zahlen("pegel.ort");
        A.info("Pegel", p == null ? "keiner" : f("%s, O %.0f, N %.0f, Umkreis %.0f m; Marke %s über %.2f m NHN", sz.text("pegel.name", "Pegel"), p[0], p[1], sz.zahl("pegel.umkreis", 300),
                sz.text("pegel.marke.name", "–"), sz.zahl("pegel.marke", Double.NaN)));
    }

    private static boolean hatLauf(File ordner) {
        File[] u = ordner.listFiles(File::isDirectory);
        if (u != null) for (File d : u) if (new File(d, "lauf.txt").isFile()) return true;
        return false;
    }

    static void teil2(Szenen sz, Gebiet ge) throws Exception {
        A.teil("2 Pegelreihe und Marken (Prüfstein 6)");
        double[] p = sz.zahlen("pegel.ort");
        if (p == null) {
            A.info("Pegel", "keiner in der Szenendatei; Teil 2 entfällt");
            return;
        }
        BildCheck.P_OST = p[0];
        BildCheck.P_NORD = p[1];
        double umkreis = sz.zahl("pegel.umkreis", 300), marke = sz.zahl("pegel.marke", Double.NaN);
        for (Szenario s : sz.szenarien()) {
            if (s.live()) continue;
            try (Archivquelle q = new Archivquelle(s.archiv.toPath())) {
                long n0 = System.nanoTime();
                Pegelreihe r = Pegelreihe.aus(q, q.start, ge, sz.text("pegel.name", "Pegel"), p[0], p[1], umkreis);
                double sek = (System.nanoTime() - n0) / 1e9;
                BildCheck.Zeiten z = BildCheck.zeiten(q, ge);
                A.info(s.name, r + f(", gelesen in %.1f s; BildCheck: %s", sek, z.ort()));
                double[] pr = r.pegel(), tr = r.zeiten();
                double groesste = 0;
                int trocken = 0;
                for (int i = 0; i < pr.length; i++) {
                    if (Double.isNaN(pr[i])) trocken++;
                    else groesste = Math.max(groesste, Math.abs(pr[i] - z.pegel()[i]));
                }
                A.pruefe(r.zelle == z.cp() && pr.length == z.z().length && groesste == 0, s.name + ": Zelle und Pegel je Stand gegen BildCheck",
                        f("Zelle %d gegen %d; %d gegen %d Stände; größte Abweichung %.3g m; %d Stände trocken", r.zelle, z.cp(), pr.length, z.z().length, groesste, trocken), "gleiche Zelle, gleiche Zahl, 0 m");
                List<Pegelreihe.Marke> ms = r.marken();
                List<String> txt = new ArrayList<>();
                for (Pegelreihe.Marke m : ms) txt.add((m.art() == Pegelreihe.Art.HOCHWASSER ? "HW " : "NW ") + BildCheck.zeit(q.start, m.t()) + f(" %+.2f m", m.pegel()));
                A.info(s.name + ": Marken", txt.isEmpty() ? "keine" : String.join("; ", txt));
                double step = tr.length > 1 ? tr[1] - tr[0] : 300;
                double dHW = naechste(ms, Pegelreihe.Art.HOCHWASSER, z.z()[z.iHW()]), dNW = naechste(ms, Pegelreihe.Art.NIEDRIGWASSER, z.z()[z.iNW()]);
                // BildCheck nimmt Ersatz, wenn es kein Extrem mit Abstand gibt; dann gibt es auch hier keine Marke
                boolean ersatzHW = z.ersatz().contains("kein Hochwasser"), ersatzNW = z.ersatz().contains("kein Niedrigwasser");
                A.pruefe(ersatzHW || dHW <= step, s.name + ": Hochwasser von BildCheck (" + BildCheck.zeit(q.start, z.z()[z.iHW()]) + f(", %+.2f m) als Marke", z.pegel()[z.iHW()]),
                        ersatzHW ? "BildCheck nimmt Ersatz, keine Marke verlangt" : f("Abstand %.0f s", dHW), f("≤ %.0f s (ein Stand)", step));
                A.pruefe(ersatzNW || dNW <= step, s.name + ": Niedrigwasser von BildCheck (" + BildCheck.zeit(q.start, z.z()[z.iNW()]) + f(", %+.2f m) als Marke", z.pegel()[z.iNW()]),
                        ersatzNW ? "BildCheck nimmt Ersatz, keine Marke verlangt" : f("Abstand %.0f s", dNW), f("≤ %.0f s (ein Stand)", step));
                Pegelreihe.Marke h = r.hoechster();
                if (z.iLU() != z.iHW()) A.pruefe(h != null && Math.abs(h.t() - z.z()[z.iLU()]) <= step, s.name + ": höchster Stand gegen das Landunter von BildCheck (" + BildCheck.zeit(q.start, z.z()[z.iLU()]) + ")",
                        h == null ? "keiner" : BildCheck.zeit(q.start, h.t()) + f(", %+.2f m", h.pegel()), f("± %.0f s (ein Stand)", step));
                else if (h != null) A.info(s.name + ": höchster Stand", BildCheck.zeit(q.start, h.t()) + f(", %+.2f m", h.pegel()));
                if (!Double.isNaN(marke)) {
                    List<String> b = new ArrayList<>();
                    for (double[] ab : r.ueber(marke)) b.add(BildCheck.zeit(q.start, ab[0]) + " bis " + BildCheck.zeit(q.start, ab[1]));
                    A.info(s.name + f(": %s (Pegel über %.2f m NHN)", sz.text("pegel.marke.name", "Marke"), marke), b.isEmpty() ? "nie" : String.join("; ", b));
                }
            }
        }
    }

    private static double naechste(List<Pegelreihe.Marke> ms, Pegelreihe.Art art, double t) {
        double d = Double.MAX_VALUE;
        for (Pegelreihe.Marke m : ms) if (m.art() == art) d = Math.min(d, Math.abs(m.t() - t));
        return d;
    }

    /** Ein Standbild der Prüfung: Szenario, Ansicht, Blick, Zeit und das Bild der Bühne. */
    private record Probe(Szenario s, Ansicht a, Blick b, Instant t0, double t, float[] bild) { }

    static void teil3(Szenen sz, Gebiet ge) throws Exception {
        A.teil("3 Gleichheit: Bühne gegen den Weg von BildCheck (Prüfstein 3)");
        File gb = sz.gebiet();
        List<Probe> proben = new ArrayList<>();
        // je Szenario eine frische Bühne, wie der Vergleich je Probe eine frische Szene nimmt: nichts aus dem vorigen Bild bleibt
        for (Szenario s : sz.szenarien()) {
            if (s.live()) continue;
            long n0 = System.nanoTime();
            try (Buehne b = Buehne.ohneFenster(gb, BREITE, HOEHE)) {
                if (proben.isEmpty()) A.info("Gerät", b.geraet + f("; Bühne ohne Fenster in %.1f s geöffnet", (System.nanoTime() - n0) / 1e9));
                double[] po = sz.zahlen("pegel.ort");
                if (po != null) b.pegel(sz.text("pegel.name", "Pegel"), po[0], po[1], sz.zahl("pegel.umkreis", 300));
                long n1 = System.nanoTime();
                b.oeffne(s);
                double t = b.uhr().sekunden();
                Ansicht a = s.ansicht != null ? sz.ansicht(s.ansicht) : sz.ansichten().get(0);
                Blick bl = new Blick();
                a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                bl.groesse(BREITE, HOEHE);
                float[] bild = b.standbild(bl, t);
                proben.add(new Probe(s, a, bl, b.uhr().start, t, bild));
                A.info(s.name, f("Ansicht %s, %s; Szenario geöffnet und Standbild in %.1f s; Pegel %s", a.name, BildCheck.zeit(b.uhr().start, t), (System.nanoTime() - n1) / 1e9,
                        b.pegelreihe() == null ? "–" : f("%+.2f m", b.pegelreihe().bei(t))));
            }
        }
        if (proben.isEmpty()) {
            A.info("Gleichheit", "kein Szenario aus dem Archiv; Teil 3 entfällt");
            return;
        }
        try (Geraet g = new Geraet()) {
            Konto konto = g.konto("BuehneCheck", 3L << 30);
            for (Probe p : proben) {
                float[] ref;
                try (Archivquelle q = new Archivquelle(p.s().archiv.toPath()); BildCheck.Szene szene = new BildCheck.Szene(g, konto, gb, ge, q, BREITE, HOEHE)) {
                    // der Schwebstoff des Szenarios wie in der Bühne (seit P9b)
                    if (!Double.isNaN(p.s().schwebstoff)) szene.wasser.optik.schwebstoff = p.s().schwebstoff;
                    for (int k = 0; k < 12; k++) {
                        szene.rahmen.belichtung.sofort();
                        szene.zeichne(p.b(), p.t0(), p.t(), null);
                    }
                    ref = szene.aus.lies();
                }
                double groesste = 0, summe = 0;
                long ungleich = 0;
                for (int i = 0; i < ref.length; i++) {
                    double d = Math.abs(ref[i] - p.bild()[i]) * 255;
                    groesste = Math.max(groesste, d);
                    summe += d;
                    if (d > 0.5) ungleich++;
                }
                A.pruefe(ref.length == p.bild().length && groesste <= 1, "Prüfstein 3: " + p.s().name + ", " + p.a().name + " (" + BildCheck.zeit(p.t0(), p.t()) + ") – Bühne gegen BildCheck",
                        f("größte Abweichung %.2f von 255, im Mittel %.4f; %d von %d Werten über 0,5", groesste, summe / Math.max(1, ref.length), ungleich, ref.length), "≤ 1 von 255 [G]");
            }
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach Teil 3", konto.toString(), "0 belegt");
        }
    }
}
