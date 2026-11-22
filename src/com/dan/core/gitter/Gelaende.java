package com.dan.core.gitter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.IntStream;

/**
 * Das Feingelände: ein Stapel von Höhenkacheln, von fein nach grob (Werkbuch HalligHooge Teil A, 10.4). Jedes Gitter
 * backt daraus mit seiner Zellgröße ({@link #feingelaende}), und das Bild liest denselben Stapel (Leitidee 2). So
 * stimmen die Volumen geschachtelter Gitter bis auf die Tabellen überein.
 * <p>
 * <b>Format</b> (wie {@code clipmap.TileFolder}, das es für Lituya eingeführt hat): je Stufe ein Ordner mit
 * {@code STUFE.txt} ({@code zelle}, {@code kachel_zellen}, {@code kacheln_ost}, {@code kacheln_nord},
 * {@code suedwest_ost}, {@code suedwest_nord}) und je Kachel {@code E<Ost>_N<Nord>.r32} mit der Südwestecke in m:
 * float32 Little Endian, Zeilen von Nord nach Süd, in der Zeile von West nach Ost, NaN = kein Wert. Eine Kachel, die es
 * nicht gibt, hat keine Werte. Die Zelle (i, j) einer Kachel hat ihre Mitte bei Ost + (i + ½)·zelle,
 * Nord + Kachel − (j + ½)·zelle.
 * <p>
 * <b>Regel</b> (wie {@code clipmap.TileStack}): Für eine Abtastung mit Schritt c gilt zuerst die gröbste Stufe, die
 * noch feiner oder gleich c ist (die feinste, wenn keine so fein ist); wo sie nichts hat, die nächst gröberen, dann die
 * feineren. In einer Stufe wird bilinear zwischen den Pixelmitten gelesen; fehlende Nachbarn zählen nicht, solange die
 * übrigen mindestens ein Viertel des Gewichts tragen. Ist die Abtastung gröber als die Stufe, gilt das Mittel aus 2 × 2
 * (ab 1,5 Zellen) bzw. 4 × 4 Punkten (ab 6 Zellen); die Aufbereitung legt aber für jede Zellgröße eine Stufe mit
 * Mitteln an, damit das nicht vorkommt. An einer Pixelmitte mit dem Schritt der Stufe ist der Wert genau der der
 * Kachel.
 * <p>
 * Nur {@code java.*}. Gelesene Kacheln bleiben in einem Speicher je Stufe (zuletzt benutzt bleibt, Vorgabe 768 MB);
 * Lesen ist aus mehreren Fäden erlaubt.
 */
public final class Gelaende {
    /** Name der Beschreibung einer Stufe. */
    public static final String BESCHREIBUNG = "STUFE.txt";
    static final float[] LEER = new float[0];

    private final Stufe[] stufen;
    /** Mindesthöhe der Bauwerke (null: keine), seit P6e; gilt bei Abtastungen bis zu ihrer Zelle. */
    private final Stufe mindest;

    /** Ein Stapel aus Stufen; sie werden nach ihrer Zellgröße geordnet, fein zuerst. */
    public Gelaende(Stufe... stufen) { this(null, stufen); }

    private Gelaende(Stufe mindest, Stufe[] stufen) {
        if (stufen.length == 0) throw new IllegalArgumentException("Gelände ohne Stufe");
        this.stufen = stufen.clone();
        Arrays.sort(this.stufen, Comparator.comparingDouble(s -> s.zelle));
        this.mindest = mindest;
    }

    /**
     * Derselbe Stapel mit der Mindesthöhe der Bauwerke (seit P6e, Werkbuch 12.7, Stand P6e; Leitidee 2): bei einer
     * Abtastung bis zur Zelle der Stufe {@code m} ist jeder Wert mindestens der Wert des Pixels von {@code m}, in dem
     * der Ort liegt, wie {@link #mindestens} beim Backen von G3 und G4. So liest das Bild die Deiche so hoch, wie hydro
     * sie rechnet. Gröbere Abtastungen bleiben, wie sie sind.
     */
    public Gelaende mitMindesthoehe(Stufe m) { return new Gelaende(m, stufen); }

    /** Die Mindesthöhe (null: keine). */
    public Stufe mindesthoehe() { return mindest; }

    /** Alle Unterordner von {@code dir} mit {@code STUFE.txt}. */
    public static Gelaende ordner(File dir) throws IOException {
        File[] d = dir.listFiles(f -> new File(f, BESCHREIBUNG).isFile());
        if (d == null || d.length == 0) throw new IOException("keine Stufe mit " + BESCHREIBUNG + " in " + dir.getPath());
        List<Stufe> l = new ArrayList<>();
        for (File f : d) l.add(Stufe.lies(f));
        return new Gelaende(l.toArray(new Stufe[0]));
    }

    /**
     * Eine Abfrage mit eigenem Speicher der zuletzt gelesenen Kacheln (seit P7c, für die Referenz): dieselbe Regel wie
     * {@link #hoehe(double, double, double)}, ohne bei jedem Aufruf Verweise anzulegen. Nicht zwischen Fäden teilen.
     */
    public final class Abfrage {
        private final Stufe.Ref[] refs = neueRefs();

        public float hoehe(double ost, double nord, double c) { return Gelaende.this.hoehe(ost, nord, c, refs); }
    }

    /** Eine neue {@link Abfrage} (je Faden eine). */
    public Abfrage abfrage() { return new Abfrage(); }

    /** Die Stufen, fein zuerst. */
    public Stufe[] stufen() { return stufen.clone(); }

    /** Index der Stufe, die für den Schritt c zuerst gefragt wird. */
    int erste(double c) {
        int p = 0;
        for (int i = 0; i < stufen.length; i++) if (stufen[i].zelle <= c * 1.0001) p = i;
        return p;
    }

    /** Höhe (m NHN) am Ort für eine Abtastung mit Schritt c (m), NaN außerhalb aller Stufen. */
    public float hoehe(double ost, double nord, double c) { return hoehe(ost, nord, c, neueRefs()); }

    Stufe.Ref[] neueRefs() {
        Stufe.Ref[] r = new Stufe.Ref[stufen.length + 1];
        for (int i = 0; i < r.length; i++) r[i] = new Stufe.Ref();
        return r;
    }

    float hoehe(double ost, double nord, double c, Stufe.Ref[] refs) {
        float v = stapel(ost, nord, c, refs);
        if (mindest != null && !Float.isNaN(v) && c <= mindest.zelle * 1.0001) {
            float m = mindest.wert((int) Math.floor((ost - mindest.suedwestOst) / mindest.zelle),
                    (int) Math.floor((mindest.suedwestNord + mindest.kachelnNord * mindest.kachel() - nord) / mindest.zelle), refs[stufen.length]);
            if (m > v) v = m;
        }
        return v;
    }

    private float stapel(double ost, double nord, double c, Stufe.Ref[] refs) {
        int p = erste(c);
        for (int i = p; i < stufen.length; i++) {
            float v = stufen[i].hoehe(ost, nord, c, refs[i]);
            if (!Float.isNaN(v)) return v;
        }
        for (int i = p - 1; i >= 0; i--) {
            float v = stufen[i].hoehe(ost, nord, c, refs[i]);
            if (!Float.isNaN(v)) return v;
        }
        return Float.NaN;
    }

    /**
     * Das feine Gelände unter einem Gitter in UTM: {@code fein} Pixel je Zellseite, Pixel (i, j) mit der Mitte bei
     * x0 + (i + ½)·dx/fein, y0 + (j + ½)·dy/fein (von Südwest). Für {@link Subgrid#bauen}.
     */
    public Subgrid.Feingelaende feingelaende(Gitter g, int fein) {
        if (g.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Feingelände: nur für Gitter in UTM, nicht " + g);
        if (Math.abs(g.dx - g.dy) > 1e-9 * g.dx) throw new IllegalArgumentException("Feingelände: nur quadratische Zellen, nicht " + g);
        double s = g.dx / fein;
        return (i0, j0, breite, hoehe, ziel) -> {
            ThreadLocal<Stufe.Ref[]> refs = ThreadLocal.withInitial(this::neueRefs);
            IntStream.range(0, hoehe).parallel().forEach(v -> {
                Stufe.Ref[] r = refs.get();
                double nord = g.y0 + (j0 + v + 0.5) * s;
                for (int u = 0; u < breite; u++) ziel[u + breite * v] = hoehe(g.x0 + (i0 + u + 0.5) * s, nord, s, r);
            });
        };
    }

    /**
     * Das Feingelände mit Mindesthöhe (Werkbuch 10.4, Schritt 5): jedes Pixel wird mindestens so hoch wie der Wert der
     * Stufe {@code mindest} in dem Pixel, in dem seine Mitte liegt (ohne Wert: unverändert). So backen G3 und G4 (seit P4d
     * im Kern, damit der Lauf G4 backen kann). {@code gehoben} zählt die gehobenen Pixel (darf null sein).
     */
    public static Subgrid.Feingelaende mindestens(Subgrid.Feingelaende basis, Stufe mindest, Gitter g, int fein, java.util.concurrent.atomic.AtomicLong gehoben) {
        if (mindest == null) return basis;
        double s = g.dx / fein;
        return (i0, j0, breite, hoehe, ziel) -> {
            basis.lies(i0, j0, breite, hoehe, ziel);
            IntStream.range(0, hoehe).parallel().forEach(v -> {
                double y = g.y0 + (j0 + v + 0.5) * s;
                long hoch = 0;
                Stufe.Leser lh = mindest.leser();
                for (int u = 0; u < breite; u++) {
                    double x = g.x0 + (i0 + u + 0.5) * s;
                    int p = u + breite * v;
                    float m = lh.imPixel(x, y);
                    if (!Float.isNaN(m) && m > ziel[p]) { ziel[p] = m; hoch++; }
                }
                if (gehoben != null) gehoben.addAndGet(hoch);
            });
        };
    }

    /**
     * Manning-n je Feinpixel aus den Rauheitsklassen (Werkbuch 10.4, Schritt 6): die Klasse des Pixels der Stufe 1 m, in
     * dem die Mitte liegt, sonst der Stufe 10 m (an einer Pixelgrenze das östliche bzw. südliche); n aus {@code manning}
     * (Index = Klasse); ohne Klasse {@link Subgrid#MANNING}. Seit P4d im Kern.
     */
    public static Subgrid.Feingelaende rauheit(Stufe r1, Stufe r10, double[] manning, Gitter g, int fein) {
        double s = g.dx / fein;
        return (i0, j0, breite, hoehe, ziel) -> IntStream.range(0, hoehe).parallel().forEach(v -> {
            double y = g.y0 + (j0 + v + 0.5) * s;
            Stufe.Leser l1 = r1 == null ? null : r1.leser(), l10 = r10 == null ? null : r10.leser();
            for (int u = 0; u < breite; u++) {
                double x = g.x0 + (i0 + u + 0.5) * s;
                float k = l1 == null ? Float.NaN : l1.imPixel(x, y);
                if (Float.isNaN(k) && l10 != null) k = l10.imPixel(x, y);
                int c = Float.isNaN(k) ? 0 : Math.round(k);
                ziel[u + breite * v] = c >= 1 && c < manning.length && manning[c] > 0 ? manning[c] : Subgrid.MANNING;
            }
        });
    }

    /**
     * Manning-n je Rauheitsklasse aus {@code klassen.txt} (Zeilen „Nummer n Name“, # Kommentar); Index = Klasse, 0 ohne
     * Wert.
     */
    public static double[] manning(File klassen) throws IOException {
        double[] n = new double[1];
        for (String z : Files.readAllLines(klassen.toPath(), StandardCharsets.UTF_8)) {
            String t = z.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] p = t.split("\\s+");
            int k = Integer.parseInt(p[0]);
            if (k >= n.length) n = Arrays.copyOf(n, k + 1);
            n[k] = Double.parseDouble(p[1]);
        }
        return n;
    }

    /** Ein Rechteck von Werten mit Schritt c ab der Mitte (ost0, nord0) des südwestlichen Pixels, Zeilen von Süd nach Nord. */
    public void fuelle(double ost0, double nord0, double c, int breite, int hoehe, float[] aus) {
        Stufe.Ref[] r = neueRefs();
        for (int v = 0; v < hoehe; v++)
            for (int u = 0; u < breite; u++) aus[u + breite * v] = hoehe(ost0 + u * c, nord0 + v * c, c, r);
    }

    /** „1m: Zelle 1 m, 24 × 32 Kacheln zu 1000 · 10m: …“ */
    public String beschreibe() {
        StringBuilder b = new StringBuilder();
        for (Stufe s : stufen) b.append(b.length() > 0 ? " · " : "").append(s);
        if (mindest != null) b.append(" · Mindesthöhe ").append(mindest);
        return b.toString();
    }

    /** Bilinear aus vier Nachbarn mit den Anteilen fx, fy; fehlende zählen nicht, solange die übrigen ein Viertel tragen. */
    public static float bilinear(float a, float b, float c, float d, double fx, double fy) {
        double wa = (1 - fx) * (1 - fy), wb = fx * (1 - fy), wc = (1 - fx) * fy, wd = fx * fy, s = 0, w = 0;
        if (!Float.isNaN(a)) { s += a * wa; w += wa; }
        if (!Float.isNaN(b)) { s += b * wb; w += wb; }
        if (!Float.isNaN(c)) { s += c * wc; w += wc; }
        if (!Float.isNaN(d)) { s += d * wd; w += wd; }
        return w >= 0.25 ? (float) (s / w) : Float.NaN;
    }

    /**
     * Eine Stufe des Stapels: ein Ordner mit Kacheln gleicher Zellgröße. Spalten zählen von West, Zeilen von Nord, über
     * die ganze Stufe.
     */
    public static final class Stufe {
        public final File ordner;
        public final String name;
        /** Zellgröße (m), Südwestecke der Stufe (m, UTM). */
        public final double zelle, suedwestOst, suedwestNord;
        public final int kachelZellen, kachelnOst, kachelnNord;
        /** Höchstens so viele Bytes gelesener Kacheln bleiben im Speicher. */
        public volatile long speicher = 768L << 20;
        private final LinkedHashMap<Long, float[]> kacheln = new LinkedHashMap<>(64, 0.75f, true);
        private long gehalten;

        private Stufe(File ordner, double zelle, int kachelZellen, int kachelnOst, int kachelnNord, double suedwestOst, double suedwestNord) {
            if (!(zelle > 0) || kachelZellen < 1 || kachelnOst < 1 || kachelnNord < 1)
                throw new IllegalArgumentException("Stufe " + ordner + ": Zelle " + zelle + ", " + kachelnOst + " × " + kachelnNord + " Kacheln zu " + kachelZellen);
            this.ordner = ordner;
            this.name = ordner.getName();
            this.zelle = zelle;
            this.kachelZellen = kachelZellen;
            this.kachelnOst = kachelnOst;
            this.kachelnNord = kachelnNord;
            this.suedwestOst = suedwestOst;
            this.suedwestNord = suedwestNord;
        }

        /** Liest {@code STUFE.txt}. */
        public static Stufe lies(File ordner) throws IOException {
            File d = new File(ordner, BESCHREIBUNG);
            if (!d.isFile()) throw new IOException("keine Beschreibung " + d.getPath());
            Properties p = new Properties();
            for (String z : Files.readAllLines(d.toPath(), StandardCharsets.UTF_8)) {
                int k = z.indexOf('#');
                String s = (k >= 0 ? z.substring(0, k) : z).strip();
                int e = s.indexOf('=');
                if (e > 0) p.setProperty(s.substring(0, e).strip(), s.substring(e + 1).strip());
            }
            return new Stufe(ordner, zahl(p, "zelle", d), (int) zahl(p, "kachel_zellen", d), (int) zahl(p, "kacheln_ost", d), (int) zahl(p, "kacheln_nord", d),
                    zahl(p, "suedwest_ost", d), zahl(p, "suedwest_nord", d));
        }

        private static double zahl(Properties p, String k, File f) throws IOException {
            String v = p.getProperty(k);
            if (v == null) throw new IOException(f.getPath() + ": " + k + " fehlt");
            return Double.parseDouble(v);
        }

        /** Legt eine Stufe an: Ordner und {@code STUFE.txt}; vorhandene Kacheln bleiben. */
        public static Stufe anlegen(File ordner, double zelle, int kachelZellen, double suedwestOst, double suedwestNord, int kachelnOst, int kachelnNord,
                                    String kommentar) throws IOException {
            Stufe s = new Stufe(ordner, zelle, kachelZellen, kachelnOst, kachelnNord, suedwestOst, suedwestNord);
            Files.createDirectories(ordner.toPath());
            String t = (kommentar == null || kommentar.isEmpty() ? "" : "# " + kommentar.replace("\n", "\n# ") + "\n")
                    + "zelle = " + zahl(zelle) + "\nkachel_zellen = " + kachelZellen + "\nkacheln_ost = " + kachelnOst + "\nkacheln_nord = " + kachelnNord
                    + "\nsuedwest_ost = " + zahl(suedwestOst) + "\nsuedwest_nord = " + zahl(suedwestNord) + "\n";
            Files.writeString(new File(ordner, BESCHREIBUNG).toPath(), t, StandardCharsets.UTF_8);
            return s;
        }

        static String zahl(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

        /** Seitenlänge einer Kachel (m). */
        public double kachel() { return kachelZellen * zelle; }

        /** Westrand, Südrand, Ostrand, Nordrand (m). */
        public double[] ausdehnung() {
            return new double[] {suedwestOst, suedwestNord, suedwestOst + kachelnOst * kachel(), suedwestNord + kachelnNord * kachel()};
        }

        public int spalten() { return kachelnOst * kachelZellen; }
        public int zeilen() { return kachelnNord * kachelZellen; }

        /** Spalte (gebrochen, Pixelmitte = ganz) zum Ost-Wert. */
        public double spalte(double ost) { return (ost - suedwestOst) / zelle - 0.5; }
        /** Zeile von Nord (gebrochen, Pixelmitte = ganz) zum Nord-Wert. */
        public double zeile(double nord) { return (suedwestNord + kachelnNord * kachel() - nord) / zelle - 0.5; }
        /** Ost der Mitte einer Spalte, Nord der Mitte einer Zeile. */
        public double ost(int spalte) { return suedwestOst + (spalte + 0.5) * zelle; }
        public double nord(int zeile) { return suedwestNord + kachelnNord * kachel() - (zeile + 0.5) * zelle; }

        /** Die Datei der Kachel (tx von West, ty von Nord). */
        public File datei(int tx, int ty) {
            double e = suedwestOst + tx * kachel(), n = suedwestNord + (kachelnNord - 1 - ty) * kachel();
            return new File(ordner, String.format(Locale.ROOT, "E%d_N%d.r32", Math.round(e), Math.round(n)));
        }

        /** Gibt es die Kachel als Datei? */
        public boolean hat(int tx, int ty) { return tx >= 0 && ty >= 0 && tx < kachelnOst && ty < kachelnNord && datei(tx, ty).isFile(); }

        /** Die Werte einer Kachel (Zeilen von Nord), eine leere Reihung, wenn es sie nicht gibt. Nicht verändern. */
        public float[] kachel(int tx, int ty) {
            if (tx < 0 || ty < 0 || tx >= kachelnOst || ty >= kachelnNord) return LEER;
            long key = schluessel(tx, ty);
            synchronized (kacheln) {
                float[] t = kacheln.get(key);
                if (t != null) return t;
            }
            float[] t = lies(tx, ty);
            synchronized (kacheln) {
                float[] o = kacheln.put(key, t);
                if (o != null) gehalten -= o.length * 4L;
                gehalten += t.length * 4L;
                var it = kacheln.entrySet().iterator();
                while (gehalten > speicher && kacheln.size() > 1 && it.hasNext()) {
                    Map.Entry<Long, float[]> e = it.next();
                    if (e.getKey() == key) continue;
                    gehalten -= e.getValue().length * 4L;
                    it.remove();
                }
            }
            return t;
        }

        private static long schluessel(int tx, int ty) { return ((long) tx << 32) | (ty & 0xffffffffL); }

        private float[] lies(int tx, int ty) {
            File f = datei(tx, ty);
            if (!f.isFile()) return LEER;
            int n = kachelZellen * kachelZellen;
            try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
                if (ch.size() != n * 4L) throw new IOException("Größe " + ch.size() + " statt " + n * 4L);
                ByteBuffer bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN);
                while (bb.hasRemaining() && ch.read(bb) >= 0) { /* lesen */ }
                bb.flip();
                float[] v = new float[n];
                bb.asFloatBuffer().get(v);
                return v;
            } catch (IOException e) {
                throw new IllegalStateException("Gelände: Kachel " + f + " nicht lesbar: " + e.getMessage(), e);
            }
        }

        /** Schreibt eine Kachel (Zeilen von Nord); eine Kachel nur aus NaN wird nicht geschrieben (und eine alte gelöscht). */
        public void schreibe(int tx, int ty, float[] werte) throws IOException {
            if (werte.length != kachelZellen * kachelZellen) throw new IllegalArgumentException("Kachel mit " + werte.length + " Werten statt " + kachelZellen * kachelZellen);
            File f = datei(tx, ty);
            boolean leer = true;
            for (float v : werte) if (!Float.isNaN(v)) { leer = false; break; }
            if (leer) Files.deleteIfExists(f.toPath());
            else {
                ByteBuffer bb = ByteBuffer.allocate(werte.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                bb.asFloatBuffer().put(werte);
                File tmp = new File(f.getPath() + ".neu");
                Files.write(tmp.toPath(), bb.array());
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (kacheln) {
                float[] o = kacheln.remove(schluessel(tx, ty));
                if (o != null) gehalten -= o.length * 4L;
            }
        }

        /** Vergisst alle gelesenen Kacheln. */
        public void vergiss() {
            synchronized (kacheln) {
                kacheln.clear();
                gehalten = 0;
            }
        }

        /** Zuletzt benutzte Kachel eines Lesers. */
        static final class Ref {
            long key = Long.MIN_VALUE;
            float[] t;
        }

        /** Wert eines Pixels (Spalte von West, Zeile von Nord), NaN außerhalb. Für viele Werte: {@link #leser()}. */
        public float wert(int spalte, int zeile) { return wert(spalte, zeile, new Ref()); }

        /** Ein Leser, der die zuletzt benutzte Kachel behält; je Faden einen (nicht teilen). */
        public Leser leser() { return new Leser(this); }

        /** Liest Pixel einer Stufe und behält die zuletzt benutzte Kachel. */
        public static final class Leser {
            private final Stufe stufe;
            private final Ref ref = new Ref();

            private Leser(Stufe stufe) { this.stufe = stufe; }

            /** Wert eines Pixels (Spalte von West, Zeile von Nord), NaN außerhalb. */
            public float wert(int spalte, int zeile) { return stufe.wert(spalte, zeile, ref); }

            /** Wert des Pixels, in dem der Punkt liegt (an einer Grenze das östliche bzw. südliche). */
            public float imPixel(double ost, double nord) {
                return stufe.wert((int) Math.floor((ost - stufe.suedwestOst) / stufe.zelle), (int) Math.floor((stufe.suedwestNord + stufe.kachelnNord * stufe.kachel() - nord) / stufe.zelle), ref);
            }

            /** Bilinear zwischen den Pixelmitten. */
            public float bilinear(double ost, double nord) { return stufe.bilinear(ost, nord, ref); }
        }

        float wert(int spalte, int zeile, Ref ref) {
            if (spalte < 0 || zeile < 0 || spalte >= spalten() || zeile >= zeilen()) return Float.NaN;
            int tx = spalte / kachelZellen, ty = zeile / kachelZellen;
            long key = schluessel(tx, ty);
            float[] t;
            if (ref.key == key) t = ref.t;
            else {
                t = kachel(tx, ty);
                ref.key = key;
                ref.t = t;
            }
            if (t == LEER) return Float.NaN;
            return t[(zeile - ty * kachelZellen) * kachelZellen + (spalte - tx * kachelZellen)];
        }

        /** Bilinear zwischen den Pixelmitten (Regel der Klasse). */
        public float bilinear(double ost, double nord) { return bilinear(ost, nord, new Ref()); }

        float bilinear(double ost, double nord, Ref ref) {
            double c = spalte(ost), r = zeile(nord);
            int i = (int) Math.floor(c), j = (int) Math.floor(r);
            double fx = c - i, fy = r - j;
            return Gelaende.bilinear(wert(i, j, ref), wert(i + 1, j, ref), wert(i, j + 1, ref), wert(i + 1, j + 1, ref), fx, fy);
        }

        /** Höhe für eine Abtastung mit Schritt c (Regel der Klasse); NaN ohne Suche, wenn der Ort weit außerhalb liegt. */
        float hoehe(double ost, double nord, double c, Ref ref) {
            double rand = Math.max(c, zelle);
            if (ost < suedwestOst - rand || nord < suedwestNord - rand || ost > suedwestOst + kachelnOst * kachel() + rand
                    || nord > suedwestNord + kachelnNord * kachel() + rand) return Float.NaN;
            double q = c / zelle;
            int n = q < 1.5 ? 1 : q < 6 ? 2 : 4;
            if (n == 1) return bilinear(ost, nord, ref);
            double s = 0;
            int k = 0;
            for (int b = 0; b < n; b++)
                for (int a = 0; a < n; a++) {
                    float v = bilinear(ost + ((a + 0.5) / n - 0.5) * c, nord + ((b + 0.5) / n - 0.5) * c, ref);
                    if (!Float.isNaN(v)) { s += v; k++; }
                }
            return k * 2 >= n * n ? (float) (s / k) : Float.NaN;
        }

        @Override public String toString() {
            return String.format(Locale.GERMAN, "%s: Zelle %s m, %d × %d Kacheln zu %d ab O %s, N %s", name, zahl(zelle), kachelnOst, kachelnNord, kachelZellen,
                    zahl(suedwestOst), zahl(suedwestNord));
        }
    }
}
