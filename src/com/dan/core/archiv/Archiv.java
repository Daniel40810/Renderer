package com.dan.core.archiv;

import com.dan.core.feld.Eintrag;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gitter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Archiv der Zustände (Werkbuch HalligHooge Teil A, 5.7): ein Lauf in einem Ordner. Er besteht aus dem Kopf
 * ({@code lauf.txt}: Name, Startzeit, seed, Antrieb, Gitter, Modulversionen, Spuren), den Zeiten der Stände
 * ({@code zeiten.f64}), je Feld einer Datei mit einem Satz je Stand ({@code <feld>.f16} oder {@code .f32}, Little
 * Endian) und Textdateien für Bilanzen und Prüfwerte.
 * <p>
 * Ein Lauf lässt sich in jeder Geschwindigkeit abspielen, auch rückwärts: {@link #lies(String, double, Feld)}
 * interpoliert linear zwischen den beiden Ständen um t; die beiden zuletzt gelesenen Sätze je Spur bleiben im
 * Speicher. Der Tidenatlas und die Sturmfluten sind solche Läufe.
 * <p>
 * <b>Größe:</b> float16 hält bei Werten um 1 m rund 0,5 mm, bei 30 m rund 16 mm (11 Bit Mantisse). Was genauer
 * sein muss, wird als float32 abgelegt. Dateien dürfen größer als 2 GB werden; gelesen wird satzweise.
 * <p>
 * Der Katalog der Läufe in Oracle (F16) gehört zu Teil B; der Kern schreibt nur Dateien.
 * <p>
 * <b>Gepackt</b> (seit D1, Werkbuch HalligHooge 162): Eine Spur mit {@code ;Z} im Kopf liegt verlustfrei gepackt in
 * {@code <feld>.f16z} bzw. {@code .f32z} ({@link Packung}, gepackt vom {@link Packer}); gelesen wird dasselbe, Bit für
 * Bit. Der Vorleser entpackt solche Stände voraus in einen Vorrat im Prozess ({@link #VORRAT_BYTES}), eine Sonde
 * ({@link #wert}) entpackt nur den Block ihrer Zelle.
 */
public final class Archiv implements Closeable {
    /** Zahlentyp einer Spur in der Datei. */
    public enum Ablage {
        HALB(2, "f16"), FLOAT(4, "f32");
        public final int bytes;
        public final String endung;
        Ablage(int bytes, String endung) { this.bytes = bytes; this.endung = endung; }
    }

    /** Eine Spur: ein Feld über alle Stände; seit D1 (Werkbuch HalligHooge 162) auch gepackt ({@link Packung}). */
    public record Spur(String feld, String gitter, Ablage ablage, int schichten, int laenge, boolean gepackt) {
        public Spur(String feld, String gitter, Ablage ablage, int schichten, int laenge) { this(feld, gitter, ablage, schichten, laenge, false); }

        public long bytesJeStand() { return (long) laenge * ablage.bytes; }

        /** Name der Datei im Ordner des Laufs: {@code <feld>.f16}, {@code .f32} oder gepackt {@code .f16z}, {@code .f32z}. */
        public String datei() { return feld + "." + ablage.endung + (gepackt ? "z" : ""); }

        /** Dieselbe Spur gepackt. */
        public Spur alsGepackt() { return new Spur(feld, gitter, ablage, schichten, laenge, true); }
    }

    /** Der Kopf eines Laufs. */
    public static final class Lauf {
        public String name = "Lauf";
        public Instant start = Instant.EPOCH;
        public long seed = 1;
        /** Woraus der Lauf angetrieben ist (frei: Quelle, Zeitraum, Wetterregie). */
        public final Map<String, String> antrieb = new LinkedHashMap<>();
        /** Modul → Version. */
        public final Map<String, String> module = new LinkedHashMap<>();
        public final Map<String, Gitter> gitter = new LinkedHashMap<>();
        public final Map<String, Spur> spuren = new LinkedHashMap<>();
    }

    public final Path ordner;
    public final Lauf lauf;
    private final boolean schreiben;
    private final Map<String, FileChannel> kanaele = new LinkedHashMap<>();
    /**
     * Seit C1b (Werkbuch HalligHooge 137, A51): eigene Kanäle des Vorlesers. Unter Windows nimmt ein Lesen an fester
     * Stelle ({@code FileChannel.read(ByteBuffer, long)}) die Sperre der Position des Kanals; teilt sich der Vorleser den
     * Kanal mit dem Bild, wartet das Bild auf einen Stand im Dateipuffer, solange der Vorleser an einer aufwachenden
     * Platte hängt (Lauf C1: 16,9 s). Mit eigenen Kanälen berühren sich die beiden nicht.
     */
    private final Map<String, FileChannel> vorKanaele = new LinkedHashMap<>();
    /** Seit D1: gepackte Spuren – eine Packung für Bild und Sonde, eine eigene für den Vorleser. */
    private final Map<String, Packung> packungen = new LinkedHashMap<>(), vorPackungen = new LinkedHashMap<>();
    private final List<Double> zeiten = new ArrayList<>();
    private FileChannel zeitKanal;
    private ByteBuffer puffer = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
    /** Je Spur die beiden zuletzt gelesenen Sätze. */
    private final Map<String, float[][]> satz = new LinkedHashMap<>();
    private final Map<String, int[]> satzNr = new LinkedHashMap<>();

    private Archiv(Path ordner, Lauf lauf, boolean schreiben) {
        this.ordner = ordner;
        this.lauf = lauf;
        this.schreiben = schreiben;
    }

    // ------------------------------------------------------------------ Schreiben

    /** Legt einen neuen Lauf an; der Ordner muss leer sein oder fehlen. Die Spuren folgen mit {@link #spur}. */
    public static Archiv anlegen(Path ordner, Lauf lauf) throws IOException {
        Files.createDirectories(ordner);
        if (Files.exists(ordner.resolve("lauf.txt"))) throw new IOException("In " + ordner + " liegt schon ein Lauf");
        Archiv a = new Archiv(ordner, lauf, true);
        a.zeitKanal = FileChannel.open(ordner.resolve("zeiten.f64"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return a;
    }

    /** Meldet eine Spur an: Felder dieser Art werden mit jedem Stand abgelegt. Vor dem ersten Stand. */
    public Archiv spur(Feld muster, Ablage ablage) throws IOException {
        if (!schreiben || !zeiten.isEmpty()) throw new IllegalStateException("Spuren werden vor dem ersten Stand angemeldet");
        String n = muster.eintrag.name(), g = muster.gitter == null ? "" : muster.gitter.name;
        if (lauf.spuren.containsKey(n)) throw new IllegalArgumentException("Spur doppelt: " + n);
        if (muster.gitter != null) {
            Gitter schon = lauf.gitter.putIfAbsent(g, muster.gitter);
            if (schon != null && !schon.gleich(muster.gitter)) throw new IllegalArgumentException("Zwei verschiedene Gitter heißen " + g);
        }
        lauf.spuren.put(n, new Spur(n, g, ablage, muster.schichten, muster.laenge));
        kanaele.put(n, FileChannel.open(ordner.resolve(n + "." + ablage.endung), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));
        return this;
    }

    /** Legt einen Stand zur Zeit t (s seit Start) ab: je angemeldeter Spur genau ein Feld. */
    public void stand(double t, Feld... felder) throws IOException {
        if (!schreiben) throw new IllegalStateException("Der Lauf ist zum Lesen geöffnet");
        if (!zeiten.isEmpty() && !(t > zeiten.get(zeiten.size() - 1))) throw new IllegalArgumentException("Stände müssen in der Zeit fortschreiten: " + t);
        if (felder.length != lauf.spuren.size()) throw new IllegalArgumentException(felder.length + " Felder für " + lauf.spuren.size() + " Spuren");
        for (Feld f : felder) {
            Spur s = lauf.spuren.get(f.eintrag.name());
            if (s == null) throw new IllegalArgumentException("Keine Spur für " + f.eintrag.name());
            if (s.laenge != f.laenge) throw new IllegalArgumentException(f.eintrag.name() + ": " + f.laenge + " Werte statt " + s.laenge);
            FileChannel k = kanaele.get(s.feld);
            for (int i = 0; i < f.laenge; ) {
                puffer.clear();
                int n = Math.min(f.laenge - i, puffer.capacity() / s.ablage.bytes);
                if (s.ablage == Ablage.HALB) for (int e = i + n; i < e; i++) puffer.putShort(Float.floatToFloat16((float) f.hol(i)));
                else for (int e = i + n; i < e; i++) puffer.putFloat((float) f.hol(i));
                puffer.flip();
                while (puffer.hasRemaining()) k.write(puffer);
            }
        }
        puffer.clear();
        puffer.putDouble(t).flip();
        while (puffer.hasRemaining()) zeitKanal.write(puffer);
        zeiten.add(t);
    }

    /** Hängt eine Zeile an eine Textdatei des Laufs, etwa {@code bilanzen.csv} oder {@code pruefwerte.txt}. */
    public void zeile(String datei, String zeile) throws IOException {
        Files.writeString(ordner.resolve(datei), zeile + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Schreibt den Kopf und schließt die Dateien. */
    @Override public void close() throws IOException {
        for (FileChannel k : kanaele.values()) k.close();
        kanaele.clear();
        synchronized (vorKanaele) {
            for (FileChannel k : vorKanaele.values()) k.close();
            vorKanaele.clear();
            for (Packung p : vorPackungen.values()) p.close();
            vorPackungen.clear();
        }
        for (Packung p : packungen.values()) p.close();
        packungen.clear();
        synchronized (vorrat) {
            vorrat.clear();
        }
        if (zeitKanal != null) zeitKanal.close();
        zeitKanal = null;
        if (schreiben) Files.writeString(ordner.resolve("lauf.txt"), kopf(), StandardCharsets.UTF_8);
    }

    private String kopf() {
        StringBuilder b = new StringBuilder();
        b.append("lauf = ").append(lauf.name).append('\n').append("start = ").append(lauf.start).append('\n').append("seed = ").append(lauf.seed).append('\n');
        b.append("staende = ").append(zeiten.size()).append('\n');
        lauf.antrieb.forEach((k, v) -> b.append("antrieb.").append(k).append(" = ").append(v).append('\n'));
        lauf.module.forEach((k, v) -> b.append("modul.").append(k).append(" = ").append(v).append('\n'));
        for (Gitter g : lauf.gitter.values())
            b.append("gitter.").append(g.name).append(" = ").append(g.lage).append(';').append(g.zone).append(';').append(g.x0).append(';').append(g.y0).append(';')
                    .append(g.dx).append(';').append(g.dy).append(';').append(g.nx).append(';').append(g.ny).append('\n');
        for (Spur s : lauf.spuren.values()) b.append(spurZeile(s)).append('\n');
        return b.toString();
    }

    /** Die Zeile einer Spur im Kopf ({@code spur.<feld> = <Gitter>;<Ablage>;<Schichten>;<Länge>}, gepackt dazu {@code ;Z}). */
    public static String spurZeile(Spur s) {
        return "spur." + s.feld + " = " + s.gitter + ';' + s.ablage + ';' + s.schichten + ';' + s.laenge + (s.gepackt ? ";Z" : "");
    }

    // ------------------------------------------------------------------ Lesen

    /** Öffnet einen Lauf zum Abspielen. */
    public static Archiv oeffnen(Path ordner) throws IOException {
        Lauf l = new Lauf();
        for (String z : Files.readAllLines(ordner.resolve("lauf.txt"), StandardCharsets.UTF_8)) {
            int p = z.indexOf(" = ");
            if (p < 0) continue;
            String k = z.substring(0, p), v = z.substring(p + 3);
            if (k.equals("lauf")) l.name = v;
            else if (k.equals("start")) l.start = Instant.parse(v);
            else if (k.equals("seed")) l.seed = Long.parseLong(v);
            else if (k.startsWith("antrieb.")) l.antrieb.put(k.substring(8), v);
            else if (k.startsWith("modul.")) l.module.put(k.substring(6), v);
            else if (k.startsWith("gitter.")) {
                String[] t = v.split(";");
                String n = k.substring(7);
                l.gitter.put(n, Gitter.Lage.valueOf(t[0]) == Gitter.Lage.UTM
                        ? Gitter.utm(n, Integer.parseInt(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Integer.parseInt(t[6]), Integer.parseInt(t[7]))
                        : Gitter.geografisch(n, Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]), Double.parseDouble(t[5]), Integer.parseInt(t[6]), Integer.parseInt(t[7])));
            } else if (k.startsWith("spur.")) {
                String[] t = v.split(";", -1);
                String n = k.substring(5);
                l.spuren.put(n, new Spur(n, t[0], Ablage.valueOf(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]), t.length > 4 && t[4].strip().equals("Z")));
            }
        }
        Archiv a = new Archiv(ordner, l, false);
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(ordner.resolve("zeiten.f64"))).order(ByteOrder.LITTLE_ENDIAN);
        while (b.remaining() >= 8) a.zeiten.add(b.getDouble());
        try {
            for (Spur s : l.spuren.values()) {
                Path p = ordner.resolve(s.datei());
                if (s.gepackt) {
                    a.packungen.put(s.feld, Packung.oeffnen(p, s.ablage.bytes, s.laenge, a.zeiten.size()));
                    a.rohJeStand += s.bytesJeStand();
                    continue;
                }
                long soll = s.bytesJeStand() * a.zeiten.size();
                if (Files.size(p) != soll) throw new IOException(p + ": " + Files.size(p) + " Byte statt " + soll);
                a.kanaele.put(s.feld, FileChannel.open(p, StandardOpenOption.READ));
            }
        } catch (IOException | RuntimeException e) {
            a.close();
            throw e;
        }
        return a;
    }

    /** Seit D1: ob der Lauf gepackte Spuren hält. */
    public boolean gepackt() { return !packungen.isEmpty(); }

    /** Seit D1: die Packung einer gepackten Spur (für Prüfungen und den Packer) oder null. */
    public Packung packung(String name) { return packungen.get(name); }

    public int staende() { return zeiten.size(); }

    /** Zeit des Stands k (s seit Start). */
    public double zeit(int k) { return zeiten.get(k); }

    /** Größe eines Stands über alle Spuren in Byte. */
    public long bytesJeStand() {
        long n = 0;
        for (Spur s : lauf.spuren.values()) n += s.bytesJeStand();
        return n;
    }

    /** Ein leeres Feld für eine Spur (float), mit dem Gitter aus dem Kopf. */
    public Feld feld(String name, Katalog katalog) {
        Spur s = spur(name);
        Eintrag e = katalog.verlange(name);
        return new Feld(e, s.gitter.isEmpty() ? null : lauf.gitter.get(s.gitter), Feld.Zahl.FLOAT, s.schichten);
    }

    /** Liest den Stand k einer Spur nach {@code ziel}. */
    public void lies(String name, int k, Feld ziel) throws IOException {
        float[] w = hole(spur(name), k, -1);
        if (ziel.laenge != w.length) throw new IllegalArgumentException(name + ": Ziel hat " + ziel.laenge + " Werte statt " + w.length);
        for (int i = 0; i < w.length; i++) ziel.setz(i, w[i]);
        ziel.zeit = zeiten.get(k);
    }

    /**
     * Liest eine Spur zur Zeit t (s seit Start), linear zwischen den beiden Ständen um t; vor dem ersten und nach dem
     * letzten Stand gilt dieser. Gibt die Zeit zurück, für die der Wert gilt.
     */
    public double lies(String name, double t, Feld ziel) throws IOException {
        Spur s = spur(name);
        int n = zeiten.size();
        if (n == 0) throw new IOException("Der Lauf hat keinen Stand");
        int lo = 0, hi = n - 1;
        if (t <= zeiten.get(0)) hi = 0;
        else if (t >= zeiten.get(n - 1)) lo = n - 1;
        else {
            while (hi - lo > 1) {
                int m = (lo + hi) >>> 1;
                if (zeiten.get(m) <= t) lo = m; else hi = m;
            }
        }
        if (lo == hi) {
            lies(name, lo, ziel);
            return ziel.zeit;
        }
        float[] a = hole(s, lo, hi), b = hole(s, hi, lo);
        if (ziel.laenge != a.length) throw new IllegalArgumentException(name + ": Ziel hat " + ziel.laenge + " Werte statt " + a.length);
        double w = (t - zeiten.get(lo)) / (zeiten.get(hi) - zeiten.get(lo));
        for (int i = 0; i < a.length; i++) ziel.setz(i, a[i] + w * (b[i] - a[i]));
        ziel.zeit = t;
        return t;
    }

    private Spur spur(String name) {
        Spur s = lauf.spuren.get(name);
        if (s == null) throw new IllegalArgumentException("Keine Spur im Lauf: " + name);
        return s;
    }

    /**
     * Holt den Satz k einer Spur; zwei Sätze je Spur bleiben im Speicher. Überschrieben wird nie der Satz
     * {@code behalte} (der zweite, den der Aufrufer gerade braucht).
     */
    private float[] hole(Spur s, int k, int behalte) throws IOException {
        long w0 = System.nanoTime();
        synchronized (this) {
            long w1 = System.nanoTime();
            Messung m = MESSUNG.get();
            m.wartenNs += w1 - w0;
            float[] r = holeGesperrt(s, k, behalte, m);
            long l = System.nanoTime() - w1;
            if (l > m.langsamstNs) {
                m.langsamstNs = l;
                m.langsamst = s.feld + " Stand " + k;
            }
            return r;
        }
    }

    private float[] holeGesperrt(Spur s, int k, int behalte, Messung m) throws IOException {
        if (schreiben) throw new IllegalStateException("Der Lauf wird geschrieben; zum Lesen neu öffnen");
        if (k < 0 || k >= zeiten.size()) throw new IndexOutOfBoundsException("Stand " + k + " von " + zeiten.size());
        float[][] sa = satz.computeIfAbsent(s.feld, n -> new float[2][]);
        int[] nr = satzNr.computeIfAbsent(s.feld, n -> new int[] {-1, -1});
        if (nr[0] == k) return sa[0];
        if (nr[1] == k) return sa[1];
        int platz = nr[0] == behalte ? 1 : nr[1] == behalte ? 0 : Math.abs(nr[0] - k) >= Math.abs(nr[1] - k) ? 0 : 1;
        nr[platz] = -1;
        if (sa[platz] == null) sa[platz] = new float[s.laenge];
        float[] w = sa[platz];
        zuletzt = k;
        if (s.gepackt) {
            long l0 = System.nanoTime();
            byte[] roh = ausVorrat(k, s.feld);
            if (roh == null) {
                roh = rohPuffer.get(s.feld);
                if (roh == null) rohPuffer.put(s.feld, roh = new byte[(int) s.bytesJeStand()]);
                m.bytes += packungen.get(s.feld).lies(k, roh);
            }
            umwandeln(roh, s, w);
            nr[platz] = k;
            m.lesenNs += System.nanoTime() - l0;
            m.saetze++;
            return w;
        }
        FileChannel kanal = kanaele.get(s.feld);
        long ort = s.bytesJeStand() * k;
        long l0 = System.nanoTime();
        if (!imPuffer(k)) platte();
        for (int i = 0; i < s.laenge; ) {
            puffer.clear();
            int n = Math.min(s.laenge - i, puffer.capacity() / s.ablage.bytes);
            puffer.limit(n * s.ablage.bytes);
            while (puffer.hasRemaining()) {
                int r = kanal.read(puffer, ort + puffer.position());
                if (r < 0) throw new IOException("Unerwartetes Ende in " + s.feld);
            }
            ort += puffer.position();
            puffer.flip();
            if (s.ablage == Ablage.HALB) for (int e = i + n; i < e; i++) w[i] = Float.float16ToFloat(puffer.getShort());
            else for (int e = i + n; i < e; i++) w[i] = puffer.getFloat();
        }
        nr[platz] = k;
        synchronized (vorgelesen) {
            gelesen.set(k);
        }
        m.lesenNs += System.nanoTime() - l0;
        m.bytes += s.bytesJeStand();
        m.saetze++;
        return w;
    }

    /**
     * Seit W5b (Werkbuch HalligHooge 128, F216 A): ein einzelner Wert der Spur, Stand {@code k}, Zelle {@code zelle} (Index
     * im Feld), ohne den Satz zu lesen: eine Sonde braucht je Stand nur ihre Zelle. Berührt die zwei gehaltenen Sätze nicht.
     */
    public double wert(String name, int k, int zelle) throws IOException {
        Spur s = spur(name);
        if (k < 0 || k >= zeiten.size()) throw new IndexOutOfBoundsException("Stand " + k + " von " + zeiten.size());
        if (zelle < 0 || zelle >= s.laenge) throw new IndexOutOfBoundsException("Zelle " + zelle + " von " + s.laenge);
        if (schreiben) throw new IllegalStateException("Der Lauf wird geschrieben; zum Lesen neu öffnen");
        if (s.gepackt) {
            // seit D1: aus dem Vorrat, sonst nur den Block der Zelle entpacken
            byte[] roh = ausVorrat(k, s.feld);
            if (roh != null) return wertAus(roh, (long) zelle * s.ablage.bytes, s.ablage);
            int[] anfang = new int[1];
            byte[] block = packungen.get(s.feld).block(k, zelle, anfang);
            return wertAus(block, anfang[0], s.ablage);
        }
        ByteBuffer b = ByteBuffer.allocate(s.ablage.bytes).order(ByteOrder.LITTLE_ENDIAN);
        FileChannel kanal = kanaele.get(s.feld);
        if (!imPuffer(k)) platte();
        long ort = s.bytesJeStand() * k + (long) zelle * s.ablage.bytes;
        while (b.hasRemaining()) if (kanal.read(b, ort + b.position()) < 0) throw new IOException("Unerwartetes Ende in " + s.feld);
        b.flip();
        return s.ablage == Ablage.HALB ? Float.float16ToFloat(b.getShort()) : b.getFloat();
    }

    // ------------------------------------------------------------------ Vorlesen (seit W5c)

    private final java.util.BitSet vorgelesen = new java.util.BitSet(), gelesen = new java.util.BitSet();

    /**
     * Seit W5c (Werkbuch HalligHooge 131, F221 A): liest den Stand k aller Spuren einmal ganz aus den Dateien, ohne
     * etwas zu behalten – danach liegt er im Dateipuffer des Systems, und {@link #vorgelesen(int)} sagt ja. Für einen
     * eigenen Faden: nimmt keine Sperre des Archivs, die gehaltenen Sätze bleiben, wie sie sind. Gibt die gelesenen Bytes.
     */
    public long vorlesen(int k) throws IOException {
        if (k < 0 || k >= zeiten.size() || vorgelesen(k)) return 0;
        if (!imPuffer(k)) platte();
        ByteBuffer b = ByteBuffer.allocate(1 << 20);
        long summe = 0;
        Map<String, byte[]> entpackt = packungen.isEmpty() ? null : new LinkedHashMap<>();
        for (Spur s : lauf.spuren.values()) {
            if (s.gepackt) {
                // seit D1: entpackt in den Vorrat im Prozess (der Dateipuffer hielte nur die gepackten Bytes)
                Packung p = vorPackung(s);
                if (p == null) continue;
                byte[] roh = freierPuffer(s);
                summe += p.lies(k, roh);
                entpackt.put(s.feld, roh);
                continue;
            }
            FileChannel kanal = vorKanal(s);
            if (kanal == null) continue;
            long ort = s.bytesJeStand() * k, ende = ort + s.bytesJeStand();
            while (ort < ende) {
                b.clear();
                b.limit((int) Math.min(b.capacity(), ende - ort));
                int r = kanal.read(b, ort);
                if (r < 0) throw new IOException("Unerwartetes Ende in " + s.feld);
                ort += r;
                summe += r;
            }
        }
        if (entpackt != null) inVorrat(k, entpackt);
        else synchronized (vorgelesen) {
            vorgelesen.set(k);
        }
        return summe;
    }

    // ------------------------------------------------------------------ Vorrat der gepackten Spuren (seit D1)

    /**
     * Wie viel ein Archiv mit gepackten Spuren entpackt im Speicher hält (MB, {@code -Darchiv.vorrat.mb}, Vorgabe 192):
     * daraus die Zahl der Stände, die der Vorleser voraus haben darf ({@link #vorausPlatz()}).
     */
    public static final long VORRAT_BYTES = Long.getLong("archiv.vorrat.mb", 192) << 20;

    /** Je Stand die entpackten Bytes der gepackten Spuren; der Vorleser füllt, das Bild nimmt. */
    private final Map<Integer, Map<String, byte[]>> vorrat = new LinkedHashMap<>();
    private final Map<String, byte[]> rohPuffer = new LinkedHashMap<>();
    private long rohJeStand;
    /** Der zuletzt vom Bild gelesene Stand: verdrängt wird, was am weitesten davon liegt. */
    private volatile int zuletzt;

    /**
     * Seit D1: wie viele Stände der Vorrat hält (ohne gepackte Spuren: unbegrenzt, dann hält der Dateipuffer); der
     * Vorleser liest höchstens die Hälfte davon voraus.
     */
    public int vorausPlatz() {
        if (packungen.isEmpty()) return Integer.MAX_VALUE;
        if (platzGesetzt > 0) return platzGesetzt;
        return (int) Math.max(8, Math.min(400, VORRAT_BYTES / Math.max(1, rohJeStand)));
    }

    private volatile int platzGesetzt;

    /** Für Prüfungen: der Platz des Vorrats in Ständen (0: nach {@link #VORRAT_BYTES}). */
    public void vorratPlatz(int staende) { platzGesetzt = staende; }

    /** Entpackte Bytes je Stand der gepackten Spuren. */
    public long rohJeStand() { return rohJeStand; }

    /** Seit D1: um welchen Stand das Bild gerade spielt (der Vorleser sagt es vor dem Lesen); verdrängt wird, was am weitesten davon liegt. */
    public void bezug(int k) { zuletzt = k; }

    private byte[] ausVorrat(int k, String feld) {
        synchronized (vorrat) {
            Map<String, byte[]> m = vorrat.get(k);
            return m == null ? null : m.get(feld);
        }
    }

    /**
     * Ein neuer Puffer für einen Stand. Verdrängte Puffer werden nicht wiederverwendet: das Bild darf einen Satz aus dem
     * Vorrat noch umwandeln, während der Vorleser ihn verdrängt – ein Satz im Vorrat ändert sich nie.
     */
    private byte[] freierPuffer(Spur s) { return new byte[(int) s.bytesJeStand()]; }

    private void inVorrat(int k, Map<String, byte[]> entpackt) {
        int platz = vorausPlatz();
        synchronized (vorrat) {
            vorrat.put(k, entpackt);
            synchronized (vorgelesen) {
                vorgelesen.set(k);
            }
            int bezug = zuletzt;
            while (vorrat.size() > platz) {
                int weg = -1;
                for (int j : vorrat.keySet()) if (weg < 0 || Math.abs(j - bezug) > Math.abs(weg - bezug)) weg = j;
                vorrat.remove(weg);
                synchronized (vorgelesen) {
                    vorgelesen.clear(weg);
                }
            }
        }
    }

    /** Seit D1: die eigene Packung des Vorlesers für eine gepackte Spur, beim ersten Gebrauch geöffnet. */
    private Packung vorPackung(Spur s) throws IOException {
        if (!packungen.containsKey(s.feld)) return null;
        synchronized (vorKanaele) {
            Packung p = vorPackungen.get(s.feld);
            if (p == null) {
                p = Packung.oeffnen(ordner.resolve(s.datei()), s.ablage.bytes, s.laenge, zeiten.size());
                vorPackungen.put(s.feld, p);
            }
            return p;
        }
    }

    /** Ungepackte Bytes (Little Endian) eines Stands in Werte. */
    static void umwandeln(byte[] roh, Spur s, float[] w) {
        ByteBuffer b = ByteBuffer.wrap(roh).order(ByteOrder.LITTLE_ENDIAN);
        if (s.ablage == Ablage.HALB) for (int i = 0; i < s.laenge; i++) w[i] = Float.float16ToFloat(b.getShort(i << 1));
        else for (int i = 0; i < s.laenge; i++) w[i] = b.getFloat(i << 2);
    }

    private static double wertAus(byte[] b, long ab, Ablage a) {
        int i = (int) ab;
        if (a == Ablage.HALB) return Float.float16ToFloat((short) ((b[i] & 0xff) | (b[i + 1] << 8)));
        return Float.intBitsToFloat((b[i] & 0xff) | (b[i + 1] & 0xff) << 8 | (b[i + 2] & 0xff) << 16 | (b[i + 3] << 24));
    }

    /** Seit C1b (A51): der eigene Kanal des Vorlesers für die Spur, beim ersten Gebrauch geöffnet; null ohne Datei. */
    private FileChannel vorKanal(Spur s) throws IOException {
        if (!kanaele.containsKey(s.feld)) return null;
        synchronized (vorKanaele) {
            FileChannel k = vorKanaele.get(s.feld);
            if (k == null) {
                k = FileChannel.open(ordner.resolve(s.feld + "." + s.ablage.endung), StandardOpenOption.READ);
                vorKanaele.put(s.feld, k);
            }
            return k;
        }
    }

    /** Seit W5c: ob der Stand k vorgelesen ist (alle Spuren im Dateipuffer). */
    public boolean vorgelesen(int k) {
        synchronized (vorgelesen) {
            return vorgelesen.get(k);
        }
    }

    private boolean imPuffer(int k) {
        synchronized (vorgelesen) {
            return vorgelesen.get(k) || gelesen.get(k);
        }
    }

    /** Seit W5c: der Index des letzten Stands bis t (0 vor dem ersten). */
    public int standBis(double t) {
        int n = zeiten.size(), lo = 0, hi = n - 1;
        if (n == 0 || t <= zeiten.get(0)) return 0;
        if (t >= zeiten.get(n - 1)) return n - 1;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (zeiten.get(m) <= t) lo = m; else hi = m;
        }
        return lo;
    }

    // Eine schlafende Platte zum Prüfen (A47): liegt der letzte Zugriff auf einen Stand außerhalb des Dateipuffers länger
    // als SCHLAF_NACH zurück, dauert der nächste SCHLAF; -Dlauf.platte.schlaf=<nach s>:<dauer s>, Vorgabe aus
    private static volatile long schlafNachNs = -1, schlafNs;
    private static long letzterZugriffNs = System.nanoTime();
    private static final Object PLATTE = new Object();

    static {
        String p = System.getProperty("lauf.platte.schlaf");
        if (p != null && p.contains(":")) {
            String[] t = p.split(":");
            platteSimulieren(Double.parseDouble(t[0]), Double.parseDouble(t[1]));
        }
    }

    /** Seit W5c, für Prüfungen: eine Platte, die nach {@code nachS} ohne Zugriff einschläft und {@code dauerS} zum Aufwachen braucht (nachS < 0: aus). */
    public static void platteSimulieren(double nachS, double dauerS) {
        synchronized (PLATTE) {
            schlafNachNs = nachS < 0 ? -1 : (long) (nachS * 1e9);
            schlafNs = (long) (dauerS * 1e9);
            letzterZugriffNs = System.nanoTime();
        }
    }

    private static void platte() {
        if (schlafNachNs < 0) return;
        synchronized (PLATTE) {
            if (System.nanoTime() - letzterZugriffNs > schlafNachNs) {
                try {
                    Thread.sleep(schlafNs / 1_000_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            letzterZugriffNs = System.nanoTime();
        }
    }

    /**
     * Seit W5b (A47, F220 A): was ein Faden beim Lesen von Sätzen verbracht hat (alle Archive, seit dem letzten
     * {@link #messung()}): Warten auf die Sperre, Lesen aus der Datei, Bytes, Sätze und der langsamste Satz.
     */
    public static final class Messung {
        public long wartenNs, lesenNs, bytes, langsamstNs;
        public int saetze;
        public String langsamst = "";

        /** Etwa „Lesen 19,40 s (12 Sätze, 6,7 MB, langsamster water.h Stand 96 19,31 s), Warten 0,00 s“. */
        @Override public String toString() {
            return String.format(java.util.Locale.GERMAN, "Lesen %.2f s (%d Sätze, %.1f MB, langsamster %s %.2f s), Warten auf die Sperre %.2f s",
                    lesenNs / 1e9, saetze, bytes / 1048576.0, langsamst.isEmpty() ? "–" : langsamst, langsamstNs / 1e9, wartenNs / 1e9);
        }
    }

    private static final ThreadLocal<Messung> MESSUNG = ThreadLocal.withInitial(Messung::new);

    /** Gibt die Messung dieses Fadens seit dem letzten Aufruf und beginnt eine neue. */
    public static Messung messung() {
        Messung m = MESSUNG.get();
        MESSUNG.set(new Messung());
        return m;
    }

    /** Zählt Warten auf eine äußere Sperre (etwa die einer Quelle) zur Messung dieses Fadens. */
    public static void warten(long ns) { MESSUNG.get().wartenNs += ns; }
}
