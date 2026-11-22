package com.dan.core.gitter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ein aufbereitetes Gebiet (Werkbuch HalligHooge Teil A, 10.4, Schritte 8 und 9): die Gitterkette aus
 * {@code gebiet.txt}, je Gitter {@code ground.z.f32} und die Subgrid-Packung {@code subgrid.bin}, bei G0 der nasse
 * Anteil {@code nass.f32}, bei G3 die Bauwerke {@code bauwerke.txt}. Die Leseseite für den Lauf; geschrieben wird von
 * der Aufbereitung (nur {@code java.*}).
 * <p>
 * <b>{@code gebiet.txt}</b> ist Text in UTF-8; Zeilen mit {@code #} sind Kommentar. Gelesen werden nur die Zeilen
 * {@code gitter <Name> <utm|geografisch> <Zone> <x0> <y0> <dx> <dy> <nx> <ny> <fein>} (Südwestecke, Zellgröße in m
 * oder Grad; fein 0: ohne Subgrid). Alle anderen Zeilen (Rezept, Quellen, Prüfwerte) sind für Menschen und die Prüfung.
 * <p>
 * <b>{@code subgrid.bin}</b>, alles Little Endian: 8 Byte {@code HHSUBGR2}; int32 nx, ny, Stufen, fein; float64
 * Zellfläche; dann die Basis (float32, Zellen + 2 · Kanten) und die Tabelle (float16, Zellen · 3 · Stufen + Kanten · 3 ·
 * Stufen) wie in {@link Subgrid.Packung}. Version 1 ({@code HHSUBGR1}, bis P3) hat 2 · Stufen je Kante.
 * <p>
 * <b>{@code ground.z.f32}</b> und {@code nass.f32}: float32 Little Endian je Zelle, von Südwest, i schnell.
 * <p>
 * <b>Seit P4d:</b> {@link #auge} backt G4 (das Auge) im Lauf aus dem Stapel, nach denselben Regeln wie die Aufbereitung
 * G3 ({@link Gelaende#mindestens}, {@link Gelaende#rauheit}); {@link #vorgabe} liest die Vorgabe der Tide am Rand
 * ({@code vorgabe.txt}, Schritt {@code vorgabe} der Aufbereitung).
 */
public final class Gebiet {
    /** Kennung am Anfang von {@code subgrid.bin}: Version 2 (seit P4, F25) mit drei Tabellen je Kante; Version 1 wird noch gelesen. */
    public static final String KENNUNG = "HHSUBGR2", KENNUNG_1 = "HHSUBGR1";

    /** Ein Gitter der Kette mit seinem Ordner und den Feinpixeln je Zellseite (0: ohne Subgrid). */
    public record Eintrag(Gitter gitter, int fein, File ordner) { }

    public final File ordner;
    private final Map<String, Eintrag> kette = new LinkedHashMap<>();
    /** Das Rezept der Aufbereitung, wie es in {@code gebiet.txt} steht (Zeilen {@code rezept<Tab>Schlüssel<Tab>Wert}). */
    private final Map<String, String> rezept = new LinkedHashMap<>();

    private Gebiet(File ordner) { this.ordner = ordner; }

    /** Liest {@code gebiet.txt} im Ordner. */
    public static Gebiet lies(File ordner) throws IOException {
        Gebiet g = new Gebiet(ordner);
        File f = new File(ordner, "gebiet.txt");
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String t = z.strip();
            if (t.startsWith("rezept\t")) {
                String[] r = t.split("\t", 3);
                if (r.length == 3) g.rezept.put(r[1].strip(), r[2].strip());
                continue;
            }
            if (!t.startsWith("gitter ")) continue;
            String[] p = t.split("\\s+");
            if (p.length < 11) throw new IOException(f + ": Zeile '" + t + "' zu kurz");
            String name = p[1];
            int zone = Integer.parseInt(p[3]), nx = Integer.parseInt(p[8]), ny = Integer.parseInt(p[9]), fein = Integer.parseInt(p[10]);
            double x0 = Double.parseDouble(p[4]), y0 = Double.parseDouble(p[5]), dx = Double.parseDouble(p[6]), dy = Double.parseDouble(p[7]);
            Gitter gi = switch (p[2]) {
                case "utm" -> Gitter.utm(name, zone, x0, y0, dx, nx, ny);
                case "geografisch" -> Gitter.geografisch(name, x0, y0, dx, dy, nx, ny);
                default -> throw new IOException(f + ": Lage '" + p[2] + "'");
            };
            if (p[2].equals("utm") && Math.abs(dx - dy) > 1e-9 * dx) throw new IOException(f + ": " + name + " hat keine quadratischen Zellen");
            g.kette.put(name, new Eintrag(gi, fein, new File(ordner, name)));
        }
        if (g.kette.isEmpty()) throw new IOException(f + ": keine Zeile 'gitter'");
        return g;
    }

    /** Die Zeile für {@code gebiet.txt}. */
    public static String zeile(Gitter g, int fein) {
        return String.format(Locale.ROOT, "gitter %s %s %d %s %s %s %s %d %d %d", g.name, g.lage == Gitter.Lage.UTM ? "utm" : "geografisch", g.zone, zahl(g.x0), zahl(g.y0),
                zahl(g.dx), zahl(g.dy), g.nx, g.ny, fein);
    }

    private static String zahl(double v) { return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : Double.toString(v); }

    /** Die Gitter, grob zuerst (Reihenfolge in {@code gebiet.txt}). */
    public List<Eintrag> kette() { return Collections.unmodifiableList(new ArrayList<>(kette.values())); }

    /** Ein Gitter der Kette. */
    public Eintrag eintrag(String name) {
        Eintrag e = kette.get(name);
        if (e == null) throw new IllegalArgumentException("Gebiet " + ordner + ": kein Gitter " + name + " (" + kette.keySet() + ")");
        return e;
    }

    public Gitter gitter(String name) { return eintrag(name).gitter; }

    /** {@code ground.z} je Zelle (m NHN). */
    public float[] groundZ(String name) throws IOException { return liesF32(new File(eintrag(name).ordner, "ground.z.f32"), gitter(name).zellen()); }

    /** Der nasse Anteil je Zelle (G0). */
    public float[] nass(String name) throws IOException { return liesF32(new File(eintrag(name).ordner, "nass.f32"), gitter(name).zellen()); }

    /** Die Subgrid-Packung eines Gitters; Größe und Stufen werden gegen die Kette geprüft. */
    public Subgrid.Packung packung(String name) throws IOException {
        Eintrag e = eintrag(name);
        return liesPackung(new File(e.ordner, "subgrid.bin"), e.gitter, e.fein);
    }

    /** Die Bauwerke eines Gitters ({@code bauwerke.txt}). */
    public Bauten bauwerke(String name) throws IOException { return Bauten.lies(new File(eintrag(name).ordner, "bauwerke.txt")); }

    /** Ein Wert des Rezepts aus {@code gebiet.txt}; null, wenn er fehlt. */
    public String rezept(String schluessel) { return rezept.get(schluessel); }

    private double[] rezeptZahlen(String schluessel, int n) {
        String v = rezept.get(schluessel);
        if (v == null) throw new IllegalArgumentException("Gebiet " + ordner + ": '" + schluessel + "' fehlt im Rezept in gebiet.txt (Aufbereitung --schritte backen)");
        String[] t = v.split("[\\s,;]+");
        if (t.length < n) throw new IllegalArgumentException("Gebiet " + ordner + ": '" + schluessel + "' braucht " + n + " Zahlen");
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = Double.parseDouble(t[i]);
        return d;
    }

    // ------------------------------------------------------------------ G4 (Auge)

    /**
     * G4, gebacken: Gitter, Feinpixel je Zellseite, Packung, {@code ground.z} (Zellmittel des Feingeländes aus der
     * Tabelle) und die Zeit für das Backen.
     */
    public record Auge(Gitter gitter, int fein, Subgrid.Packung packung, float[] groundZ, double sekunden) { }

    /**
     * Die Mindesthöhe der Bauwerke eines Gebietsordners (seit P6e): die des feinsten Gitters in UTM, das eine hat
     * ({@code <G3>/mindesthoehe}); ohne {@code gebiet.txt} oder ohne Stufe null. Für das Bild, das denselben Stapel lesen
     * soll wie hydro ({@link Gelaende#mitMindesthoehe}).
     */
    public static Gelaende.Stufe mindesthoehe(File ordner) throws IOException {
        if (!new File(ordner, "gebiet.txt").isFile()) return null;
        Gelaende.Stufe m = null;
        for (Eintrag e : lies(ordner).kette.values()) {
            File mo = new File(e.ordner, "mindesthoehe");
            if (e.gitter.lage == Gitter.Lage.UTM && new File(mo, Gelaende.BESCHREIBUNG).isFile()) m = Gelaende.Stufe.lies(mo);
        }
        return m;
    }

    /** G4 an der Stelle aus dem Rezept ({@code g4.suedwest}). */
    public Auge auge() throws IOException {
        double[] s = rezeptZahlen("g4.suedwest", 2);
        return auge(s[0], s[1]);
    }

    /**
     * G4 (das Auge, Werkbuch 10.1 und 10.4, Schritt 7; im Lauf seit P4d): Zelle, Zahl der Zellen je Seite und Feinpixel
     * aus dem Rezept ({@code g4.zelle}, {@code g4.zellen}, {@code g4.fein}); die Südwestecke wird auf die Zellgrenze des
     * feinsten abgelegten Gitters (G3) davor gelegt, und G4 muss ganz in ihm liegen. Gebacken aus dem Stapel
     * {@code gelaende/} mit der Mindesthöhe dieses Gitters ({@code <G3>/mindesthoehe}) und der Rauheit
     * ({@code rauheit/1m}, {@code 10m}, {@code klassen.txt}), wie G3 in der Aufbereitung.
     */
    public Auge auge(double ost, double nord) throws IOException { return auge(ost, nord, 0); }

    /** G4 mit {@code zellen} Zellen je Seite statt der Zahl im Rezept (0: aus dem Rezept; für Proben). */
    public Auge auge(double ost, double nord, int zellen) throws IOException {
        Eintrag fe = null;
        for (Eintrag e : kette.values()) if (e.fein > 0 && e.gitter.lage == Gitter.Lage.UTM) fe = e;
        if (fe == null) throw new IOException("Gebiet " + ordner + ": kein Gitter in UTM mit Subgrid für G4");
        Gitter g = fe.gitter;
        double zelle = rezeptZahlen("g4.zelle", 1)[0];
        int n = zellen > 0 ? zellen : (int) Math.round(rezeptZahlen("g4.zellen", 1)[0]), fein = (int) Math.round(rezeptZahlen("g4.fein", 1)[0]);
        double x0 = g.x0 + Math.floor((ost - g.x0) / g.dx + 1e-9) * g.dx, y0 = g.y0 + Math.floor((nord - g.y0) / g.dy + 1e-9) * g.dy;
        Gitter g4 = Gitter.utm("G4", g.zone, x0, y0, zelle, n, n);
        if (g4.passtIn(g) == 0) throw new IllegalArgumentException("G4 " + g4 + " liegt nicht auf ganzen Zellen in " + g);
        Gelaende gel = Gelaende.ordner(new File(ordner, "gelaende"));
        File mo = new File(fe.ordner, "mindesthoehe"), ro = new File(ordner, "rauheit");
        Gelaende.Stufe mindest = new File(mo, Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(mo) : null;
        Gelaende.Stufe r1 = new File(ro, "1m/" + Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(new File(ro, "1m")) : null;
        Gelaende.Stufe r10 = new File(ro, "10m/" + Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(new File(ro, "10m")) : null;
        File kl = new File(ro, "klassen.txt");
        long t0 = System.nanoTime();
        Subgrid.Feingelaende h = Gelaende.mindestens(gel.feingelaende(g4, fein), mindest, g4, fein, null);
        Subgrid.Feingelaende r = kl.isFile() ? Gelaende.rauheit(r1, r10, Gelaende.manning(kl), g4, fein) : null;
        Subgrid sg = Subgrid.bauen(g4, fein, h, r, Subgrid.STUFEN);
        float[] gz = new float[g4.zellen()];
        for (int c = 0; c < gz.length; c++) {
            double oben = sg.hoechste(c);
            gz[c] = (float) (oben - sg.volumen(c, oben) / sg.zellflaeche);
        }
        Subgrid.Packung p = sg.packen();
        return new Auge(g4, fein, p, gz, (System.nanoTime() - t0) / 1e9);
    }

    // ------------------------------------------------------------------ Vorgabe der Tide

    /**
     * Die Vorgabe der Tide am Rand eines Gitters (Werkbuch 12.5, Plan P4d; bis P5 {@code tide.bc} liefert): je Randkante
     * Mittelwasser, Amplitude und Phase einer Partialtide, η = Mittel + A cos(ω t − φ), dazu Pegel mit denselben Werten.
     * Randkanten in der Folge West (j), Ost (j), Süd (i), Nord (i); ohne Wert NaN (die Seite ist dann Wand, wenn keine
     * Kante einen Wert hat).
     */
    public record Vorgabe(String gitter, String tide, double omega, double[] mittel, double[] amplitude, double[] phase, double[] abstand, List<Pegel> pegel,
            List<String> quellen) {
        /** Ein Pegel: Name, Ort, Mittelwasser (m NHN), Amplitude (m), Phase (°), Abstand zum Pixel mit Wert (m). */
        public record Pegel(String name, double ost, double nord, double mittel, double amplitude, double phase, double abstand) { }

        /** Wasserstand der Randkante k zur Zeit t (s; t = 0 zur Phase 0), NaN ohne Wert. */
        public double eta(int k, double t) { return mittel[k] + amplitude[k] * Math.cos(omega * t - Math.toRadians(phase[k])); }

        public int kanten() { return mittel.length; }

        public Pegel pegel(String name) {
            for (Pegel p : pegel) if (p.name.equals(name)) return p;
            return null;
        }
    }

    /** Gibt es eine Vorgabe der Tide ({@code vorgabe.txt})? */
    public boolean hatVorgabe() { return new File(ordner, "vorgabe.txt").isFile(); }

    /** Liest {@code vorgabe.txt}. */
    public Vorgabe vorgabe() throws IOException {
        File f = new File(ordner, "vorgabe.txt");
        if (!f.isFile()) throw new IOException(f + " fehlt: Aufbereitung --schritte vorgabe");
        String gi = null, tide = null;
        double om = Double.NaN;
        List<double[]> rand = new ArrayList<>();
        List<Vorgabe.Pegel> pegel = new ArrayList<>();
        List<String> quellen = new ArrayList<>();
        for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String t = z.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+");
            switch (p[0]) {
                case "gitter" -> gi = p[1];
                case "tide" -> { tide = p[1]; om = Math.toRadians(Double.parseDouble(p[2])) / 3600; }
                case "quelle" -> quellen.add(t.substring(7).strip());
                case "rand" -> rand.add(new double[] {Integer.parseInt(p[1]), Integer.parseInt(p[2]), d(p[5]), d(p[6]), d(p[7]), d(p[8])});
                case "pegel" -> pegel.add(new Vorgabe.Pegel(p[1], d(p[2]), d(p[3]), d(p[4]), d(p[5]), d(p[6]), d(p[7])));
                default -> { }
            }
        }
        if (gi == null || tide == null) throw new IOException(f + ": 'gitter' oder 'tide' fehlt");
        Gitter g = gitter(gi);
        int n = 2 * g.nx + 2 * g.ny;
        if (rand.size() != n) throw new IOException(f + ": " + rand.size() + " Randkanten statt " + n + " für " + g);
        double[] m = new double[n], a = new double[n], ph = new double[n], ab = new double[n];
        int[] anfang = {0, g.ny, 2 * g.ny, 2 * g.ny + g.nx};
        for (double[] r : rand) {
            int k = anfang[(int) r[0]] + (int) r[1];
            m[k] = r[2];
            a[k] = r[3];
            ph[k] = r[4];
            ab[k] = r[5];
        }
        return new Vorgabe(gi, tide, om, m, a, ph, ab, List.copyOf(pegel), List.copyOf(quellen));
    }

    private static double d(String v) { return v.equalsIgnoreCase("nan") ? Double.NaN : Double.parseDouble(v); }

    // ------------------------------------------------------------------ Dateien

    /** Schreibt float32 Little Endian (über eine Datei {@code .neu}, dann umbenannt). */
    public static void schreibeF32(File datei, float[] werte) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(werte.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().put(werte);
        File tmp = new File(datei.getPath() + ".neu");
        Files.createDirectories(datei.getParentFile().toPath());
        Files.write(tmp.toPath(), bb.array());
        Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Liest float32 Little Endian; die Zahl der Werte muss stimmen. */
    public static float[] liesF32(File datei, int n) throws IOException {
        try (FileChannel ch = FileChannel.open(datei.toPath(), StandardOpenOption.READ)) {
            if (ch.size() != 4L * n) throw new IOException(datei + ": " + ch.size() + " Byte statt " + 4L * n);
            ByteBuffer bb = ByteBuffer.allocate(4 * n).order(ByteOrder.LITTLE_ENDIAN);
            while (bb.hasRemaining() && ch.read(bb) >= 0) { /* lesen */ }
            bb.flip();
            float[] v = new float[n];
            bb.asFloatBuffer().get(v);
            return v;
        }
    }

    /** Schreibt eine Packung ({@code subgrid.bin}). */
    public static void schreibePackung(File datei, Subgrid.Packung p, Gitter g, int fein) throws IOException {
        if (p.zellen() != g.zellen()) throw new IllegalArgumentException("Packung mit " + p.zellen() + " Zellen für " + g);
        if (p.kantenWerte() != 3) throw new IllegalArgumentException("Geschrieben wird nur die Packung Version 2 (drei Tabellen je Kante)");
        Files.createDirectories(datei.getParentFile().toPath());
        File tmp = new File(datei.getPath() + ".neu");
        try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(tmp.toPath()), 1 << 20)) {
            ByteBuffer k = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
            k.put(KENNUNG.getBytes(StandardCharsets.US_ASCII)).putInt(g.nx).putInt(g.ny).putInt(p.stufen()).putInt(fein).putDouble(p.zellflaeche());
            o.write(k.array());
            ByteBuffer b = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : p.basis()) {
                if (b.remaining() < 4) { o.write(b.array(), 0, b.position()); b.clear(); }
                b.putFloat(v);
            }
            for (short v : p.tabelle()) {
                if (b.remaining() < 2) { o.write(b.array(), 0, b.position()); b.clear(); }
                b.putShort(v);
            }
            o.write(b.array(), 0, b.position());
        }
        Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Liest eine Packung; {@code g} und {@code fein} müssen zum Kopf passen. */
    public static Subgrid.Packung liesPackung(File datei, Gitter g, int fein) throws IOException {
        try (FileChannel ch = FileChannel.open(datei.toPath(), StandardOpenOption.READ)) {
            ByteBuffer k = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
            while (k.hasRemaining() && ch.read(k) >= 0) { /* lesen */ }
            k.flip();
            byte[] kn = new byte[8];
            k.get(kn);
            String kennung = new String(kn, StandardCharsets.US_ASCII);
            int kw = KENNUNG.equals(kennung) ? 3 : KENNUNG_1.equals(kennung) ? 2 : 0;
            if (kw == 0) throw new IOException(datei + ": keine Subgrid-Packung");
            int nx = k.getInt(), ny = k.getInt(), stufen = k.getInt(), f = k.getInt();
            double zf = k.getDouble();
            if (nx != g.nx || ny != g.ny || f != fein) throw new IOException(datei + ": " + nx + " × " + ny + ", fein " + f + " statt " + g.nx + " × " + g.ny + ", fein " + fein);
            int zellen = nx * ny, kanten = (nx + 1) * ny + nx * (ny + 1);
            long nb = (long) zellen + 2L * kanten, nt = (long) zellen * 3 * stufen + (long) kanten * kw * stufen;
            if (ch.size() != 32 + 4 * nb + 2 * nt) throw new IOException(datei + ": " + ch.size() + " Byte statt " + (32 + 4 * nb + 2 * nt));
            float[] basis = new float[(int) nb];
            short[] tab = new short[(int) nt];
            ByteBuffer b = ByteBuffer.allocate(1 << 22).order(ByteOrder.LITTLE_ENDIAN);
            b.limit(0);
            for (int i = 0; i < basis.length; i++) {
                bereit(ch, b, 4, datei);
                basis[i] = b.getFloat();
            }
            for (int j = 0; j < tab.length; j++) {
                bereit(ch, b, 2, datei);
                tab[j] = b.getShort();
            }
            return new Subgrid.Packung(basis, tab, zellen, kanten, stufen, zf, kw);
        }
    }

    /** Sorgt dafür, dass mindestens {@code min} Byte im Puffer bereitstehen. */
    private static void bereit(FileChannel ch, ByteBuffer b, int min, File datei) throws IOException {
        if (b.remaining() >= min) return;
        b.compact();
        while (b.position() < min) if (ch.read(b) < 0) throw new IOException(datei + ": zu kurz");
        b.flip();
    }

    // ------------------------------------------------------------------ Bauwerke

    /**
     * Die Bauwerke eines Gitters aus {@code bauwerke.txt} (Werkbuch 10.4, Schritt 5): Deich- und Sielkanten mit Krone und
     * Profil, Siele und Ringe.
     */
    public record Bauten(List<Kante> kanten, List<Siel> siele, List<Ring> ringe, int fein) {
        /** Eine Kante: Index wie {@code Subgrid.kanteX/kanteY}, quer zu x?, Zelle (i, j), Art (deich, siel, …), Krone, Mittel, Tor angehoben, Profil. */
        public record Kante(int index, boolean querX, int i, int j, String art, double krone, double mittel, boolean angehoben, float[] profil) { }

        /** Ein Siel: Name, Kante, Ort, Sohle, Krone (geschlossen), A (m²), μ, Quellen. */
        public record Siel(String name, int kante, double ost, double nord, double sohle, double krone, double a, double mu, String quellen) { }

        /** Ein Ring: Nummer, Zellen, Fläche (m²), niedrigste und höchste Krone am Rand, Namen. */
        public record Ring(int nummer, int zellen, double flaeche, double niedrigste, double hoechste, List<String> namen) { }

        public static Bauten lies(File datei) throws IOException {
            List<Kante> k = new ArrayList<>();
            List<Siel> s = new ArrayList<>();
            List<Ring> r = new ArrayList<>();
            int fein = 0;
            for (String z : Files.readAllLines(datei.toPath(), StandardCharsets.UTF_8)) {
                if (z.startsWith("#") || z.isBlank()) continue;
                String[] t = z.strip().split("\\s+");
                switch (t[0]) {
                    case "fein" -> fein = Integer.parseInt(t[1]);
                    case "kante" -> {
                        float[] p = new float[t.length - 10];
                        for (int q = 0; q < p.length; q++) p[q] = t[10 + q].equals("nan") ? Float.NaN : Float.parseFloat(t[10 + q]);
                        k.add(new Kante(Integer.parseInt(t[1]), t[2].equals("x"), Integer.parseInt(t[3]), Integer.parseInt(t[4]), t[5], d(t[6]), d(t[7]), t[8].equals("1"), p));
                    }
                    case "siel" -> s.add(new Siel(t[1], Integer.parseInt(t[2]), d(t[3]), d(t[4]), d(t[5]), d(t[6]), d(t[7]), d(t[8]), t.length > 9 ? t[9] : ""));
                    case "ring" -> r.add(new Ring(Integer.parseInt(t[1]), Integer.parseInt(t[2]), d(t[3]), d(t[4]), d(t[5]),
                            t[t.length - 1].equals("-") ? List.of() : List.of(t[t.length - 1].split(","))));
                    default -> { }
                }
            }
            return new Bauten(List.copyOf(k), List.copyOf(s), List.copyOf(r), fein);
        }

        private static double d(String v) { return v.equals("nan") || v.equals("NaN") ? Double.NaN : Double.parseDouble(v); }
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("Gebiet " + ordner.getName() + ":");
        for (Eintrag e : kette.values()) b.append(' ').append(e.gitter.name).append(" (").append(e.gitter.nx).append(" × ").append(e.gitter.ny).append(e.fein > 0 ? ", fein " + e.fein : "").append(')');
        return b.toString();
    }
}
