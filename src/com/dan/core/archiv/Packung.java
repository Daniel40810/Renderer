package com.dan.core.archiv;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.CRC32C;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Eine gepackte Spur (seit D1, Werkbuch HalligHooge 162, F253 A): dieselben Werte wie {@code <feld>.f16} oder
 * {@code .f32}, Stand für Stand verlustfrei mit Deflate gepackt ({@code java.util.zip}, ohne Fremdpakete), in Blöcken zu
 * 64 KB, damit eine Sonde nur den Block ihrer Zelle entpacken muss. Datei {@code <feld>.f16z} bzw. {@code .f32z}:
 * <pre>
 *   0  int    Kennung 0x5A524148 („HARZ“)
 *   4  int    Version 1
 *   8  int    Bytes je Wert (2 oder 4)
 *  12  int    Werte je Stand
 *  16  int    Stände
 *  20  int    Werte je Block
 *  24  int    Art: 0 Deflate, 1 Deflate mit nach Stelle umgeordneten Bytes
 *  28  int    0
 *  32  long[] Ort jedes Blocks ab Dateianfang (Stand für Stand, Block für Block), dann das Ende
 *  ..  int[]  je Stand CRC32C der ungepackten Bytes
 *  ..         die Blöcke
 * </pre>
 * Alles Little Endian. Umgeordnet heißt: in einem Block erst alle ersten Bytes der Werte, dann alle zweiten (und so
 * weiter); das packt Felder, deren Werte sich langsam ändern, um ein Viertel bis ein Drittel besser. Was besser ist,
 * entscheidet der {@link Packer} je Spur am ersten und am mittleren Stand.
 * <p>
 * Lesen: {@link #lies} entpackt einen Stand ganz, {@link #block} einen Block; beide halten die Sperre der Packung (ein
 * Inflater je Packung). Jeder Faden, der viel liest, nimmt seine eigene Packung.
 */
public final class Packung implements Closeable {
    public static final int KENNUNG = 0x5A524148, VERSION = 1, KOPF = 32;
    /** Bytes je Block (ungepackt). */
    public static final int BLOCK_BYTES = 1 << 16;

    public final Path datei;
    public final int bytesJeWert, laenge, staende, werteJeBlock, bloecke;
    public final boolean umgeordnet;
    private final long[] ort;
    private final int[] crc;
    private final FileChannel kanal;
    private final Inflater inflater = new Inflater();
    private byte[] gepackt = new byte[1 << 16], blockRoh;
    private int blockStand = -1, blockNr = -1;
    private byte[] blockWerte;
    private final byte[] SCHLUSS = new byte[1];

    private Packung(Path datei, FileChannel kanal, int w, int laenge, int staende, int wjb, boolean umgeordnet, long[] ort, int[] crc) {
        this.datei = datei;
        this.kanal = kanal;
        this.bytesJeWert = w;
        this.laenge = laenge;
        this.staende = staende;
        this.werteJeBlock = wjb;
        this.bloecke = (laenge + wjb - 1) / wjb;
        this.umgeordnet = umgeordnet;
        this.ort = ort;
        this.crc = crc;
    }

    /** Blöcke je Stand bei {@code laenge} Werten zu {@code w} Byte. */
    public static int bloecke(int laenge, int w) {
        int wjb = BLOCK_BYTES / w;
        return (laenge + wjb - 1) / wjb;
    }

    /** Öffnet eine gepackte Spur; prüft Kopf, Länge und Stände gegen den Kopf des Laufs. */
    public static Packung oeffnen(Path datei, int w, int laenge, int staende) throws IOException {
        FileChannel k = FileChannel.open(datei, StandardOpenOption.READ);
        try {
            ByteBuffer b = ByteBuffer.allocate(KOPF).order(ByteOrder.LITTLE_ENDIAN);
            liesGanz(k, b, 0);
            b.flip();
            int kennung = b.getInt(), version = b.getInt(), bw = b.getInt(), l = b.getInt(), n = b.getInt(), wjb = b.getInt(), art = b.getInt();
            if (kennung != KENNUNG || version != VERSION) throw new IOException(datei + ": keine gepackte Spur (Kennung " + Integer.toHexString(kennung) + ", Version " + version + ")");
            if (bw != w || l != laenge || n != staende || wjb <= 0 || art < 0 || art > 1)
                throw new IOException(datei + ": " + bw + " Byte je Wert, " + l + " Werte, " + n + " Stände statt " + w + ", " + laenge + ", " + staende);
            int bl = (laenge + wjb - 1) / wjb;
            long eintraege = (long) n * bl + 1;
            ByteBuffer ib = ByteBuffer.allocate((int) (eintraege * 8 + 4L * n)).order(ByteOrder.LITTLE_ENDIAN);
            liesGanz(k, ib, KOPF);
            ib.flip();
            long[] ort = new long[(int) eintraege];
            for (int i = 0; i < ort.length; i++) ort[i] = ib.getLong();
            int[] crc = new int[n];
            for (int i = 0; i < n; i++) crc[i] = ib.getInt();
            long daten = KOPF + eintraege * 8 + 4L * n;
            if (ort[0] != daten || ort[ort.length - 1] != k.size()) throw new IOException(datei + ": Index passt nicht zur Datei (" + ort[ort.length - 1] + " gegen " + k.size() + " Byte)");
            for (int i = 1; i < ort.length; i++) if (ort[i] < ort[i - 1]) throw new IOException(datei + ": Index nicht steigend bei Block " + i);
            return new Packung(datei, k, w, laenge, n, wjb, art == 1, ort, crc);
        } catch (IOException | RuntimeException e) {
            k.close();
            throw e;
        }
    }

    private static void liesGanz(FileChannel k, ByteBuffer b, long ab) throws IOException {
        while (b.hasRemaining()) if (k.read(b, ab + b.position()) < 0) throw new IOException("Unerwartetes Ende");
    }

    /** Bytes je Stand ungepackt. */
    public long bytesJeStand() { return (long) laenge * bytesJeWert; }

    /** Bytes des Stands k in der Datei (gepackt). */
    public long gepacktJeStand(int k) { return ort[(k + 1) * bloecke] - ort[k * bloecke]; }

    /** Die Datei ohne Kopf und Index: alle gepackten Blöcke. */
    public long gepacktGesamt() { return ort[ort.length - 1] - ort[0]; }

    /** CRC32C der ungepackten Bytes des Stands k, wie beim Packen vom Original gerechnet. */
    public int pruefsumme(int k) { return crc[k]; }

    /** Entpackt den Stand k nach {@code roh} ({@link #bytesJeStand()} Byte, Little Endian wie die ungepackte Datei); gibt die gelesenen Bytes. */
    public synchronized long lies(int k, byte[] roh) throws IOException {
        if (k < 0 || k >= staende) throw new IndexOutOfBoundsException("Stand " + k + " von " + staende);
        if (roh.length < bytesJeStand()) throw new IllegalArgumentException("Ziel zu klein: " + roh.length + " statt " + bytesJeStand());
        long a = ort[k * bloecke], e = ort[(k + 1) * bloecke];
        int n = (int) (e - a);
        if (gepackt.length < n) gepackt = new byte[Math.max(n, gepackt.length * 2)];
        liesGanz(kanal, ByteBuffer.wrap(gepackt, 0, n), a);
        for (int b = 0; b < bloecke; b++) {
            int von = (int) (ort[k * bloecke + b] - a), bis = (int) (ort[k * bloecke + b + 1] - a);
            int werte = Math.min(werteJeBlock, laenge - b * werteJeBlock), ziel = b * werteJeBlock * bytesJeWert;
            entpacke(gepackt, von, bis - von, roh, ziel, werte);
        }
        return n;
    }

    /** Entpackt einen Block nach roh[ziel …]; ordnet zurück, wenn die Spur umgeordnet ist. */
    private void entpacke(byte[] z, int von, int n, byte[] roh, int ziel, int werte) throws IOException {
        int bytes = werte * bytesJeWert;
        byte[] dst = roh;
        int off = ziel;
        if (umgeordnet) {
            if (blockRoh == null || blockRoh.length < bytes) blockRoh = new byte[BLOCK_BYTES];
            dst = blockRoh;
            off = 0;
        }
        inflater.reset();
        inflater.setInput(z, von, n);
        int g = 0;
        try {
            while (g < bytes) {
                int r = inflater.inflate(dst, off + g, bytes - g);
                if (r == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break;
                g += r;
            }
            // seit Lauf D1: auch den Schluss des Blocks lesen (Ende und Adler-32) – sonst fiele ein Fehler dort nicht auf
            if (g == bytes && !inflater.finished()) {
                if (inflater.inflate(SCHLUSS) != 0 || !inflater.finished()) throw new IOException(datei + ": Block länger als " + bytes + " Byte");
            }
        } catch (DataFormatException e) {
            throw new IOException(datei + ": Block nicht lesbar: " + e.getMessage(), e);
        }
        if (g != bytes) throw new IOException(datei + ": Block hat " + g + " Byte statt " + bytes);
        if (inflater.getRemaining() != 0) throw new IOException(datei + ": " + inflater.getRemaining() + " Byte hinter dem Block");
        if (umgeordnet) zurueckordnen(blockRoh, roh, ziel, werte, bytesJeWert);
    }

    /**
     * Die ungepackten Bytes des Blocks, in dem Zelle {@code zelle} des Stands k liegt; der zuletzt entpackte Block bleibt
     * (eine Sonde liest Stand für Stand dieselbe Zelle). Gibt den Anfang der Zelle im zurückgegebenen Feld.
     */
    public synchronized byte[] block(int k, int zelle, int[] anfang) throws IOException {
        if (k < 0 || k >= staende) throw new IndexOutOfBoundsException("Stand " + k + " von " + staende);
        if (zelle < 0 || zelle >= laenge) throw new IndexOutOfBoundsException("Zelle " + zelle + " von " + laenge);
        int b = zelle / werteJeBlock;
        if (blockStand != k || blockNr != b) {
            if (blockWerte == null) blockWerte = new byte[BLOCK_BYTES];
            long a = ort[k * bloecke + b], e = ort[k * bloecke + b + 1];
            int n = (int) (e - a);
            if (gepackt.length < n) gepackt = new byte[Math.max(n, gepackt.length * 2)];
            liesGanz(kanal, ByteBuffer.wrap(gepackt, 0, n), a);
            blockStand = -1;
            entpacke(gepackt, 0, n, blockWerte, 0, Math.min(werteJeBlock, laenge - b * werteJeBlock));
            blockStand = k;
            blockNr = b;
        }
        anfang[0] = (zelle - b * werteJeBlock) * bytesJeWert;
        return blockWerte;
    }

    @Override public synchronized void close() throws IOException {
        inflater.end();
        kanal.close();
    }

    // ------------------------------------------------------------------ Umordnen

    static void umordnen(byte[] a, int von, int werte, int w, byte[] ziel) {
        for (int i = 0; i < werte; i++) {
            int q = von + i * w;
            for (int k = 0; k < w; k++) ziel[k * werte + i] = a[q + k];
        }
    }

    static void zurueckordnen(byte[] b, byte[] a, int ziel, int werte, int w) {
        for (int k = 0; k < w; k++) {
            int o = k * werte;
            for (int i = 0; i < werte; i++) a[ziel + i * w + k] = b[o + i];
        }
    }

    // ------------------------------------------------------------------ Schreiben (für den Packer)

    /** Ein gepackter Stand: je Block die Bytes, dazu die Prüfsumme der ungepackten Bytes. */
    public record Stand(byte[][] bloecke, int crc) {
        public long bytes() {
            long n = 0;
            for (byte[] b : bloecke) n += b.length;
            return n;
        }
    }

    /**
     * Packt einen Stand ({@code roh}: {@code laenge · w} Byte) und entpackt ihn gleich wieder zur Probe: kommt nicht jedes
     * Byte zurück, ist das ein Fehler. Stufe 6; ein eigener Deflater und Inflater je Aufruf (für viele Fäden).
     */
    public static Stand packe(byte[] roh, int laenge, int w, boolean umgeordnet) throws IOException {
        int wjb = BLOCK_BYTES / w, bl = (laenge + wjb - 1) / wjb;
        byte[][] out = new byte[bl][];
        Deflater d = new Deflater(6);
        Inflater f = new Inflater();
        byte[] tmp = new byte[BLOCK_BYTES], probe = new byte[BLOCK_BYTES], zurueck = new byte[BLOCK_BYTES];
        byte[] puffer = new byte[BLOCK_BYTES + BLOCK_BYTES / 8 + 1024];
        try {
            for (int b = 0; b < bl; b++) {
                int werte = Math.min(wjb, laenge - b * wjb), bytes = werte * w, von = b * wjb * w;
                byte[] quelle = roh;
                int qv = von;
                if (umgeordnet) {
                    umordnen(roh, von, werte, w, tmp);
                    quelle = tmp;
                    qv = 0;
                }
                d.reset();
                d.setInput(quelle, qv, bytes);
                d.finish();
                int n = 0;
                while (!d.finished()) {
                    if (n == puffer.length) puffer = java.util.Arrays.copyOf(puffer, puffer.length * 2);
                    n += d.deflate(puffer, n, puffer.length - n);
                }
                out[b] = java.util.Arrays.copyOf(puffer, n);
                // Probe: entpacken und vergleichen
                f.reset();
                f.setInput(out[b]);
                int g = 0;
                while (g < bytes) {
                    int r = f.inflate(probe, g, bytes - g);
                    if (r == 0 && (f.finished() || f.needsInput())) break;
                    g += r;
                }
                if (g != bytes) throw new IOException("Probe: Block " + b + " hat " + g + " Byte statt " + bytes);
                if (umgeordnet) {
                    zurueckordnen(probe, zurueck, 0, werte, w);
                    if (!java.util.Arrays.equals(zurueck, 0, bytes, roh, von, von + bytes)) throw new IOException("Probe: Block " + b + " nicht bitgleich");
                } else if (!java.util.Arrays.equals(probe, 0, bytes, roh, von, von + bytes)) throw new IOException("Probe: Block " + b + " nicht bitgleich");
            }
        } catch (DataFormatException e) {
            throw new IOException("Probe: " + e.getMessage(), e);
        } finally {
            d.end();
            f.end();
        }
        CRC32C c = new CRC32C();
        c.update(roh, 0, laenge * w);
        return new Stand(out, (int) c.getValue());
    }

    /** Schreibt eine gepackte Spur Stand für Stand: {@link #schreibe} in der Folge der Stände, dann {@link #close}. */
    public static final class Schreiber implements Closeable {
        private final FileChannel k;
        private final int w, laenge, staende, bl;
        private final long[] ort;
        private final int[] crc;
        private final boolean umgeordnet;
        private int naechster;
        private long pos;

        public Schreiber(Path datei, int w, int laenge, int staende, boolean umgeordnet) throws IOException {
            this.w = w;
            this.laenge = laenge;
            this.staende = staende;
            this.umgeordnet = umgeordnet;
            this.bl = bloecke(laenge, w);
            ort = new long[staende * bl + 1];
            crc = new int[staende];
            k = FileChannel.open(datei, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            pos = KOPF + (long) ort.length * 8 + 4L * staende;
            ort[0] = pos;
        }

        public void schreibe(Stand s) throws IOException {
            if (naechster >= staende) throw new IllegalStateException("mehr als " + staende + " Stände");
            if (s.bloecke.length != bl) throw new IllegalArgumentException(s.bloecke.length + " Blöcke statt " + bl);
            for (int b = 0; b < bl; b++) {
                ByteBuffer bb = ByteBuffer.wrap(s.bloecke[b]);
                while (bb.hasRemaining()) pos += k.write(bb, pos);
                ort[naechster * bl + b + 1] = pos;
            }
            crc[naechster++] = s.crc;
        }

        @Override public void close() throws IOException {
            try {
                if (naechster != staende) throw new IOException(naechster + " Stände geschrieben statt " + staende);
                ByteBuffer b = ByteBuffer.allocate(KOPF + ort.length * 8 + 4 * staende).order(ByteOrder.LITTLE_ENDIAN);
                b.putInt(KENNUNG).putInt(VERSION).putInt(w).putInt(laenge).putInt(staende).putInt(BLOCK_BYTES / w).putInt(umgeordnet ? 1 : 0).putInt(0);
                for (long o : ort) b.putLong(o);
                for (int c : crc) b.putInt(c);
                b.flip();
                long p = 0;
                while (b.hasRemaining()) p += k.write(b, p);
                k.force(true);
            } finally {
                k.close();
            }
        }
    }
}
