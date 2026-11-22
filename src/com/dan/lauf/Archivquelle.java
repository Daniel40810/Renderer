package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gitter;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ein abgelegter Lauf als {@link Bild.Quelle} (P6e, F40 A): liest die Ordner, die {@link ArchivModul} schreibt, und
 * gibt jeden Stand genau so, wie er abgelegt ist (float16 auf float). Alle Stände sind gehalten: das Bild springt,
 * läuft vor- und rückwärts in jeder Geschwindigkeit.
 */
public final class Archivquelle implements Bild.Quelle, Closeable {
    public final Path ordner;
    public final Instant start;
    private final List<Archiv> archive = new ArrayList<>();
    private final List<Gitter> gitter = new ArrayList<>();
    private final Archiv windArchiv;
    private final Gitter wind;
    private final double[] zeiten;
    private final Feld[][] felder;
    private final Feld[] windFelder;
    public final Archiv.Lauf kopf;

    /** Hält der Unterordner eines Archivs die Spur eines Felds (Zeile {@code spur.<feld> = …} in {@code lauf.txt})? */
    static boolean haeltSpur(Path ordner, String feld) {
        try {
            for (String z : Files.readAllLines(ordner.resolve("lauf.txt"))) if (z.startsWith("spur." + feld + " ")) return true;
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** Öffnet einen Lauf; die Gitter in der Reihenfolge der Zellgröße, grob zuerst. */
    public Archivquelle(Path ordner) throws IOException {
        this.ordner = ordner;
        Katalog kat = Katalog.grundstufe();
        List<Path> teile = new ArrayList<>();
        try (var s = Files.list(ordner)) {
            // Wassergitter ist, was water.eta hält (seit P10c; davor: alles außer den bekannten Ordnern – ein neuer Ordner wie aeolian_ galt dann als Wasser)
            // seit P11c1 hält auch hydro_<Gitter> (die Strömung des Nachlaufs) water.eta – das ist kein Wassergitter des Laufs
            s.filter(p -> Files.isRegularFile(p.resolve("lauf.txt")) && haeltSpur(p, "water.eta") && !p.getFileName().toString().startsWith(Stroemung.ORDNER))
                    .sorted().forEach(teile::add);
        }
        if (teile.isEmpty()) throw new IOException(ordner + ": kein abgelegter Lauf (Unterordner mit lauf.txt)");
        List<Archiv> l = new ArrayList<>();
        for (Path p : teile) l.add(Archiv.oeffnen(p));
        l.sort((a, b) -> Double.compare(b.lauf.gitter.values().iterator().next().dx, a.lauf.gitter.values().iterator().next().dx));
        archive.addAll(l);
        felder = new Feld[l.size()][];
        for (int k = 0; k < l.size(); k++) {
            Archiv a = l.get(k);
            felder[k] = new Feld[] {a.feld("water.eta", kat), a.feld("water.h", kat), a.feld("water.wetAge", kat)};
            gitter.add(felder[k][0].gitter);
        }
        Archiv erstes = l.get(0);
        kopf = erstes.lauf;
        start = kopf.start;
        zeiten = new double[erstes.staende()];
        for (int i = 0; i < zeiten.length; i++) zeiten[i] = erstes.zeit(i);
        for (Archiv a : l)
            if (a.staende() != zeiten.length) throw new IOException(ordner + ": " + a.ordner.getFileName() + " hat " + a.staende() + " Stände statt " + zeiten.length);
        Path w = ordner.resolve("wind");
        if (Files.isRegularFile(w.resolve("lauf.txt"))) {
            windArchiv = Archiv.oeffnen(w);
            windFelder = new Feld[] {windArchiv.feld("air.u10", kat), windArchiv.feld("air.v10", kat)};
            wind = windFelder[0].gitter;
        } else {
            windArchiv = null;
            windFelder = null;
            wind = null;
        }
        // Seegang (P8b): Ordner waves_<Gitter>, grob zuerst; eigene Zeiten (alle 20 min)
        List<Path> wt = new ArrayList<>();
        try (var s = Files.list(ordner)) {
            s.filter(p -> Files.isRegularFile(p.resolve("lauf.txt")) && p.getFileName().toString().startsWith("waves_")).sorted().forEach(wt::add);
        }
        for (Path p : wt) seeArchive.add(Archiv.oeffnen(p));
        seeArchive.sort((a, b) -> Double.compare(b.lauf.gitter.values().iterator().next().dx, a.lauf.gitter.values().iterator().next().dx));
        seeFelder = new Feld[seeArchive.size()][];
        for (int k = 0; k < seeArchive.size(); k++) {
            Archiv a = seeArchive.get(k);
            seeFelder[k] = new Feld[ArchivModul.SEEGANG.length];
            for (int q = 0; q < seeFelder[k].length; q++) seeFelder[k][q] = a.feld(ArchivModul.SEEGANG[q], kat);
            seeGitter.add(seeFelder[k][0].gitter);
        }
        seeZeiten = new double[seeArchive.isEmpty() ? 0 : seeArchive.get(0).staende()];
        for (int i = 0; i < seeZeiten.length; i++) seeZeiten[i] = seeArchive.get(0).zeit(i);
        for (Archiv a : seeArchive)
            if (a.staende() != seeZeiten.length) throw new IOException(ordner + ": " + a.ordner.getFileName() + " hat " + a.staende() + " Stände statt " + seeZeiten.length);
        // Sediment (P9b): Schwebstoff in sediment_<Gitter>, Boden in boden_<Gitter> (eigene Zeiten)
        try (var st = Files.list(ordner)) {
            for (Path p : st.filter(q -> Files.isRegularFile(q.resolve("lauf.txt"))).sorted().toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith("sediment_") && sedArchiv == null) sedArchiv = Archiv.oeffnen(p);
                else if (n.startsWith("boden_") && bodenArchiv == null) bodenArchiv = Archiv.oeffnen(p);
            }
        }
        if (sedArchiv != null) {
            sedFeld = sedArchiv.feld("sed.c", kat);
            sedZeiten = new double[sedArchiv.staende()];
            for (int i = 0; i < sedZeiten.length; i++) sedZeiten[i] = sedArchiv.zeit(i);
        }
        if (bodenArchiv != null) {
            bodenFelder = new Feld[ArchivModul.BODEN.length];
            for (int q = 0; q < bodenFelder.length; q++) bodenFelder[q] = bodenArchiv.feld(ArchivModul.BODEN[q], kat);
            try {
                auflageFeld = bodenArchiv.feld("bed.fluff", kat);            // seit Archiv 0.4 (P9c); ältere Archive haben sie nicht
            } catch (RuntimeException x) {
                auflageFeld = null;
            }
            bodenZeiten = new double[bodenArchiv.staende()];
            for (int i = 0; i < bodenZeiten.length; i++) bodenZeiten[i] = bodenArchiv.zeit(i);
        }
        // atmos (P10b): Nässe der Oberfläche in atmos_<Gitter>, Brechung der Luft in luft_<Gitter der Säulen>
        try (var st = Files.list(ordner)) {
            for (Path p : st.filter(q -> Files.isRegularFile(q.resolve("lauf.txt"))).sorted().toList()) {
                String n = p.getFileName().toString();
                if (n.startsWith(AtmosArchiv.ATMOS) && atmosArchiv == null) atmosArchiv = Archiv.oeffnen(p);
                else if (n.startsWith(AtmosArchiv.LUFT) && luftArchiv == null) luftArchiv = Archiv.oeffnen(p);
            }
        }
        // Flugsand (P10c): aeolian_<Gitter> mit aeol.q und dem Anteil des Winds an bed.dz (eigene Zeiten, alle 1 200 s)
        try (var st = Files.list(ordner)) {
            for (Path p : st.filter(q -> Files.isRegularFile(q.resolve("lauf.txt"))).sorted().toList())
                if (p.getFileName().toString().startsWith(AtmosArchiv.AEOLIAN) && flugArchiv == null) flugArchiv = Archiv.oeffnen(p);
        }
        if (flugArchiv != null) {
            flugFeld = flugArchiv.feld("aeol.q", kat);
            flugDz = flugArchiv.feld("bed.dz", kat);
            flugZeiten = new double[flugArchiv.staende()];
            for (int i = 0; i < flugZeiten.length; i++) flugZeiten[i] = flugArchiv.zeit(i);
        }
        if (atmosArchiv != null) {
            nassFeld = atmosArchiv.feld("surf.wet", kat);
            atmosZeiten = new double[atmosArchiv.staende()];
            for (int i = 0; i < atmosZeiten.length; i++) atmosZeiten[i] = atmosArchiv.zeit(i);
        }
        // Strömung (P11c1): hydro_<Gitter> aus dem Nachlauf (Stroemung) mit water.u, water.v, water.q, water.eta
        try (var st = Files.list(ordner)) {
            for (Path p : st.filter(q -> Files.isRegularFile(q.resolve("lauf.txt"))).sorted().toList())
                if (p.getFileName().toString().startsWith(Stroemung.ORDNER) && stromArchiv == null) stromArchiv = Archiv.oeffnen(p);
        }
        if (stromArchiv != null) {
            // seit D1 (Werkbuch HalligHooge 162): water.eta des Nachlaufs darf fehlen – die App liest ihn nicht, gepackt bleibt er weg
            stromFelder = new Feld[] {stromArchiv.feld("water.u", kat), stromArchiv.feld("water.v", kat), stromArchiv.feld("water.q", kat),
                    stromArchiv.lauf.spuren.containsKey("water.eta") ? stromArchiv.feld("water.eta", kat) : null};
            stromZeiten = new double[stromArchiv.staende()];
            for (int i = 0; i < stromZeiten.length; i++) stromZeiten[i] = stromArchiv.zeit(i);
        }
        if (luftArchiv != null) {
            luftFeld = luftArchiv.feld("air.n", kat);
            luftZeiten = new double[luftArchiv.staende()];
            for (int i = 0; i < luftZeiten.length; i++) luftZeiten[i] = luftArchiv.zeit(i);
        }
    }

    private Archiv stromArchiv;
    private Feld[] stromFelder;
    private double[] stromZeiten = new double[0];

    /** Das Gitter der Strömung aus dem Nachlauf (P11c1, {@code hydro_<Gitter>}) oder null. */
    @Override public Gitter stroemungGitter() { return stromFelder == null ? null : stromFelder[0].gitter; }

    @Override public double[] stroemungZeiten() { return stromZeiten.clone(); }

    @Override public synchronized boolean stroemung(double ts, float[] u, float[] v) {
        if (stromArchiv == null) return false;
        try {
            stromArchiv.lies("water.u", ts, stromFelder[0]);
            stromArchiv.lies("water.v", ts, stromFelder[1]);
        } catch (IOException e) {
            return false;
        }
        System.arraycopy(stromFelder[0].f, 0, u, 0, u.length);
        System.arraycopy(stromFelder[1].f, 0, v, 0, v.length);
        return true;
    }

    @Override public synchronized boolean fluss(double ts, float[] q) {
        if (stromArchiv == null) return false;
        try {
            stromArchiv.lies("water.q", ts, stromFelder[2]);
        } catch (IOException e) {
            return false;
        }
        System.arraycopy(stromFelder[2].f, 0, q, 0, q.length);
        return true;
    }

    /** Der Wasserstand des Nachlaufs (zum Vergleich mit dem Archiv); false ohne Strömung oder ohne ihn (gepackt, D1). */
    public synchronized boolean stroemungEta(double ts, float[] eta) {
        if (stromArchiv == null || stromFelder[3] == null) return false;
        try {
            stromArchiv.lies("water.eta", ts, stromFelder[3]);
        } catch (IOException e) {
            return false;
        }
        System.arraycopy(stromFelder[3].f, 0, eta, 0, eta.length);
        return true;
    }

    private Archiv atmosArchiv, luftArchiv;
    private Feld nassFeld, luftFeld;
    private double[] atmosZeiten = new double[0], luftZeiten = new double[0];

    /** Das Gitter von atmos im Archiv (P10b; {@code surf.wet}) oder null. */
    @Override public Gitter atmosGitter() { return nassFeld == null ? null : nassFeld.gitter; }

    @Override public double[] atmosZeiten() { return atmosZeiten.clone(); }

    @Override public synchronized boolean naesse(double ts, float[] wet) {
        int i = Arrays.binarySearch(atmosZeiten, ts);
        if (i < 0) return false;
        try {
            atmosArchiv.lies("surf.wet", i, nassFeld);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(nassFeld.f, 0, wet, 0, wet.length);
        return true;
    }

    /** Das Gitter der Säulen der Luft im Archiv (P10b; {@code air.n}) oder null. */
    @Override public Gitter luftGitter() { return luftFeld == null ? null : luftFeld.gitter; }

    @Override public int luftStufen() { return luftFeld == null ? 0 : luftFeld.schichten; }

    @Override public double[] luftZeiten() { return luftZeiten.clone(); }

    @Override public synchronized boolean luft(double ts, float[] n) {
        int i = Arrays.binarySearch(luftZeiten, ts);
        if (i < 0) return false;
        try {
            luftArchiv.lies("air.n", i, luftFeld);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(luftFeld.f, 0, n, 0, n.length);
        return true;
    }

    private Archiv sedArchiv, bodenArchiv;
    private Feld sedFeld;
    private Feld[] bodenFelder;
    private Feld auflageFeld;
    private double[] sedZeiten = new double[0], bodenZeiten = new double[0];

    /** Das Gitter des Sediments im Archiv (P9b) oder null. */
    @Override public Gitter sedimentGitter() { return sedFeld == null ? null : sedFeld.gitter; }

    /** Zeiten (s seit Start) der Stände des Schwebstoffs und des Bodens. */
    @Override public double[] sedimentZeiten() { return sedZeiten.clone(); }

    @Override public double[] bodenZeiten() { return bodenZeiten.clone(); }

    /** {@code sed.c} zum Stand der Zeit ts: je Zelle Sand, Schlick (kg/m³); false, wenn dort kein Stand liegt. */
    @Override public synchronized boolean sediment(double ts, float[] c) {
        int i = Arrays.binarySearch(sedZeiten, ts);
        if (i < 0) return false;
        try {
            sedArchiv.lies("sed.c", i, sedFeld);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(sedFeld.f, 0, c, 0, c.length);
        return true;
    }

    /** Seit P9c: Der Lauf des Archivs hat hydro mit dem bewegten Boden gerechnet (steht im Kopf unter „sediment“). */
    @Override public boolean bodenBewegt() {
        String s = kopf.antrieb.get("sediment");
        return s != null && s.contains("Rückwirkung auf hydro");
    }

    /** Die lockere Auflage {@code bed.fluff} (kg/m²) zum Stand der Zeit ts; false, wenn das Archiv sie nicht hält (vor P9c). */
    public synchronized boolean auflage(double ts, float[] ziel) {
        int i = Arrays.binarySearch(bodenZeiten, ts);
        if (i < 0 || auflageFeld == null) return false;
        try {
            bodenArchiv.lies("bed.fluff", i, auflageFeld);
            System.arraycopy(auflageFeld.f, 0, ziel, 0, ziel.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    /** Der Boden zum Stand der Zeit ts: {@code bed.ripple} (je Zelle Höhe, Länge, Richtung), {@code bed.mud}, {@code bed.dz}; null lässt ein Feld aus. */
    @Override public synchronized boolean boden(double ts, float[] rippel, float[] schlick, float[] dz) {
        int i = Arrays.binarySearch(bodenZeiten, ts);
        if (i < 0) return false;
        float[][] ziel = {rippel, schlick, dz};
        try {
            for (int q = 0; q < ziel.length; q++) {
                if (ziel[q] == null) continue;
                bodenArchiv.lies(ArchivModul.BODEN[q], i, bodenFelder[q]);
                System.arraycopy(bodenFelder[q].f, 0, ziel[q], 0, ziel[q].length);
            }
            // seit P10c: dazu der Anteil des Winds (der jüngste Stand bis ts), wenn er auf demselben Gitter liegt
            if (dz != null && flugArchiv != null && flugDz.gitter.gleich(bodenFelder[2].gitter)) {
                int k = -1;
                while (k + 1 < flugZeiten.length && flugZeiten[k + 1] <= ts + 1e-6) k++;
                if (k >= 0) {
                    flugArchiv.lies("bed.dz", k, flugDz);
                    for (int c = 0; c < dz.length; c++) dz[c] += flugDz.f[c];
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    private Archiv flugArchiv;
    private Feld flugFeld, flugDz;
    private double[] flugZeiten = new double[0];

    @Override public Gitter flugGitter() { return flugFeld == null ? null : flugFeld.gitter; }

    @Override public double[] flugZeiten() { return flugZeiten.clone(); }

    @Override public synchronized boolean flugsand(double ts, float[] q) {
        int i = Arrays.binarySearch(flugZeiten, ts);
        if (i < 0) return false;
        try {
            flugArchiv.lies("aeol.q", i, flugFeld);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(flugFeld.f, 0, q, 0, q.length);
        return true;
    }

    private final List<Archiv> seeArchive = new ArrayList<>();
    private final List<Gitter> seeGitter = new ArrayList<>();
    private Feld[][] seeFelder;
    private double[] seeZeiten;

    @Override public List<Gitter> seegangGitter() { return List.copyOf(seeGitter); }

    @Override public double[] seegangZeiten() { return seeZeiten.clone(); }

    @Override public synchronized boolean seegang(double ts, int k, float[][] felder) {
        int i = Arrays.binarySearch(seeZeiten, ts);
        if (i < 0) return false;
        try {
            Archiv a = seeArchive.get(k);
            for (int q = 0; q < seeFelder[k].length; q++) {
                a.lies(ArchivModul.SEEGANG[q], i, seeFelder[k][q]);
                System.arraycopy(seeFelder[k][q].f, 0, felder[q], 0, felder[q].length);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    @Override public String herkunft() { return "Archiv " + ordner.getFileName(); }

    @Override public List<Gitter> gitter() { return List.copyOf(gitter); }

    @Override public Gitter windGitter() { return wind; }

    @Override public double[] zeiten() { return zeiten.clone(); }

    /** Zahl der Stände. */
    public int staende() { return zeiten.length; }

    /** Byte je Stand über alle Gitter und den Wind. */
    public long bytesJeStand() {
        long n = windArchiv == null ? 0 : windArchiv.bytesJeStand();
        for (Archiv a : archive) n += a.bytesJeStand();
        return n;
    }

    private int index(double ts) {
        int i = Arrays.binarySearch(zeiten, ts);
        return i >= 0 ? i : -1;
    }

    @Override public boolean stand(double ts, int k, float[] eta, float[] h, float[] wetAge) {
        long w0 = System.nanoTime();
        synchronized (this) {
            Archiv.warten(System.nanoTime() - w0);
            return standGesperrt(ts, k, eta, h, wetAge);
        }
    }

    private boolean standGesperrt(double ts, int k, float[] eta, float[] h, float[] wetAge) {
        int i = index(ts);
        if (i < 0) return false;
        try {
            Archiv a = archive.get(k);
            Feld[] f = felder[k];
            a.lies("water.eta", i, f[0]);
            a.lies("water.h", i, f[1]);
            a.lies("water.wetAge", i, f[2]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(felder[k][0].f, 0, eta, 0, eta.length);
        System.arraycopy(felder[k][1].f, 0, h, 0, h.length);
        System.arraycopy(felder[k][2].f, 0, wetAge, 0, wetAge.length);
        return true;
    }

    // seit W5b (F216 A): nur die Zelle lesen, ohne die gehaltenen Sätze zu berühren (Sonde, Pegelreihe)

    @Override public boolean standZelle(double ts, int k, int zelle, float[] aus) {
        int i = index(ts);
        if (i < 0) return false;
        try {
            Archiv a = archive.get(k);
            aus[0] = (float) a.wert("water.eta", i, zelle);
            aus[1] = (float) a.wert("water.h", i, zelle);
            aus[2] = (float) a.wert("water.wetAge", i, zelle);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    @Override public boolean windZelle(double ts, int zelle, float[] aus) {
        if (windArchiv == null) return false;
        int i = index(ts);
        if (i < 0) return false;
        try {
            aus[0] = (float) windArchiv.wert("air.u10", i, zelle);
            aus[1] = (float) windArchiv.wert("air.v10", i, zelle);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    @Override public boolean stroemungZelle(double ts, int zelle, float[] aus) {
        if (stromArchiv == null || stromZeiten.length == 0) return false;
        int n = stromZeiten.length, lo = 0, hi = n - 1;
        if (ts <= stromZeiten[0]) hi = 0;
        else if (ts >= stromZeiten[n - 1]) lo = n - 1;
        else {
            while (hi - lo > 1) {
                int m = (lo + hi) >>> 1;
                if (stromZeiten[m] <= ts) lo = m; else hi = m;
            }
            if (stromZeiten[lo] == ts) hi = lo;
        }
        try {
            // wie Archiv.lies(name, t, feld): linear zwischen den beiden Ständen um ts, in float wie dort
            for (int q = 0; q < 2; q++) {
                String sp = q == 0 ? "water.u" : "water.v";
                double a = stromArchiv.wert(sp, lo, zelle);
                if (lo == hi) aus[q] = (float) a;
                else {
                    double b = stromArchiv.wert(sp, hi, zelle), w = (ts - stromZeiten[lo]) / (stromZeiten[hi] - stromZeiten[lo]);
                    aus[q] = (float) (a + w * (b - a));
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Override public synchronized boolean wind(double ts, float[] u, float[] v) {
        if (windArchiv == null) return false;
        int i = index(ts);
        if (i < 0) return false;
        try {
            windArchiv.lies("air.u10", i, windFelder[0]);
            windArchiv.lies("air.v10", i, windFelder[1]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        System.arraycopy(windFelder[0].f, 0, u, 0, u.length);
        System.arraycopy(windFelder[1].f, 0, v, 0, v.length);
        return true;
    }

    // ------------------------------------------------------------------ Vorlesen (seit W5c, F221 A)

    /** Höchstens so viele Stände je Archiv voraus (rund 5 MB je Stand im Sturm: 1 GB im Dateipuffer). */
    public static final int VORAUS_HOECHSTENS = 200;

    private volatile double vorZiel = Double.NaN, vorRaffer, vorSekunden;
    private volatile boolean vorAus;
    private Thread vorleser;
    private final Object vorWecker = new Object();
    /** Für Prüfungen: gelesene Bytes und Stände des Vorlesers, Zeit des langsamsten Stands (s). */
    public volatile long vorBytes, vorStaende;
    public volatile double vorLangsamst;

    /** Die Archive, die das Bild je Bild liest (ohne die Strömung des Nachlaufs: die liest der Messblick in seinem Faden). */
    private List<Archiv> bildArchive() {
        List<Archiv> l = bildArchive;
        if (l != null) return l;
        l = new ArrayList<>(archive);
        if (windArchiv != null) l.add(windArchiv);
        l.addAll(seeArchive);
        for (Archiv a : new Archiv[] {sedArchiv, bodenArchiv, atmosArchiv, luftArchiv, flugArchiv}) if (a != null) l.add(a);
        return bildArchive = List.copyOf(l);
    }

    private volatile List<Archiv> bildArchive;

    @Override public boolean bereit(double ts) {
        for (Archiv a : bildArchive()) {
            int lo = a.standBis(ts), hi = Math.min(a.staende() - 1, lo + 1);
            if (!a.vorgelesen(lo) || !a.vorgelesen(hi)) return false;
        }
        return true;
    }

    @Override public void vorlesen(double ts, double zeitraffer, double sekunden) {
        vorZiel = ts;
        vorRaffer = zeitraffer;
        vorSekunden = sekunden;
        synchronized (vorWecker) {
            if (vorleser == null && !vorAus) {
                vorleser = new Thread(this::vorleserLauf, "Vorleser " + ordner.getFileName());
                vorleser.setDaemon(true);
                vorleser.setPriority(Thread.NORM_PRIORITY - 1);
                vorleser.start();
            }
            vorWecker.notifyAll();
        }
    }

    /**
     * Der Faden: liest von der gewollten Zeit aus nach vorn (in Richtung des Zeitraffers; steht er, beide Richtungen ein
     * Stück), den nächsten fehlenden Stand zuerst; springt das Ziel, fängt er dort neu an. Hat er alles, schläft er bis
     * zum nächsten Ruf (höchstens 0,2 s).
     */
    private void vorleserLauf() {
        while (!vorAus) {
            try {
                double t = vorZiel;
                if (!Double.isNaN(t) && liesNaechsten(t)) continue;
                synchronized (vorWecker) {
                    if (!vorAus) vorWecker.wait(200);
                }
            } catch (InterruptedException e) {
                return;
            } catch (IOException | RuntimeException e) {
                if (vorAus) return;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException x) {
                    return;
                }
            }
        }
    }

    /** Liest den nächsten fehlenden Stand um t (alle Archive des Bilds); false, wenn nichts fehlt. */
    private boolean liesNaechsten(double t) throws IOException {
        double r = vorRaffer, spanne = Math.abs(r) * vorSekunden;
        int richtung = r < 0 ? -1 : 1;
        for (int schritt = 0; schritt <= VORAUS_HOECHSTENS; schritt++) {
            for (Archiv a : bildArchive()) {
                int n = a.staende();
                if (n == 0) continue;
                int k0 = a.standBis(t);
                // die zwei Stände um t zuerst, dann voraus; steht die Uhr, je ein Stand zurück und voraus
                int k = schritt == 0 ? k0 : schritt == 1 ? Math.min(n - 1, k0 + 1) : k0 + richtung * schritt;
                if (k < 0 || k >= n) continue;
                // seit D1: gepackt hält der Vorrat im Prozess nur vorausPlatz() Stände; voraus höchstens die Hälfte, damit beim
                // Verdrängen (das Fernste vom Bild) immer Gespieltes geht und nie, was der Vorleser eben gelesen hat
                if (schritt > 1 && schritt > a.vorausPlatz() / 2 - 1) continue;
                // mindestens drei Stände voraus, sonst so weit, wie der Zeitraffer in vorSekunden kommt
                if (schritt > 3 && Math.abs(a.zeit(k) - t) > spanne) continue;
                if (a.vorgelesen(k)) continue;
                a.bezug(k0);
                long n0 = System.nanoTime();
                long b = a.vorlesen(k);
                double d = (System.nanoTime() - n0) / 1e9;
                vorBytes += b;
                vorStaende++;
                vorLangsamst = Math.max(vorLangsamst, d);
                return true;
            }
        }
        return false;
    }

    @Override public void close() throws IOException {
        vorAus = true;
        Thread v;
        synchronized (vorWecker) {
            v = vorleser;
            vorWecker.notifyAll();
        }
        if (v != null) {
            v.interrupt();
            try {
                v.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (Archiv a : archive) a.close();
        if (windArchiv != null) windArchiv.close();
        for (Archiv a : seeArchive) a.close();
        if (sedArchiv != null) sedArchiv.close();
        if (bodenArchiv != null) bodenArchiv.close();
        if (atmosArchiv != null) atmosArchiv.close();
        if (luftArchiv != null) luftArchiv.close();
        if (flugArchiv != null) flugArchiv.close();
        if (stromArchiv != null) stromArchiv.close();
    }
}
