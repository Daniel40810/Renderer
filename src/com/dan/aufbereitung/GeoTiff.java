package com.dan.aufbereitung;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Liest ein GeoTIFF mit einem Bild (Werkbuch HalligHooge Teil A, 10.4, Schritt 1): klassisch oder BigTIFF, beide
 * Bytefolgen, Streifen oder Kacheln, ohne Kompression, LZW, Deflate oder PackBits, Prädiktor 1, 2 (ganze Zahlen) und 3
 * (Gleitkomma), Proben mit 8, 16, 32 oder 64 Bit als ganze Zahl mit oder ohne Vorzeichen oder als Gleitkomma.
 * Dazu Bilder mit einem Bit je Pixel, ohne Kompression, mit LZW, Deflate, PackBits oder CCITT Gruppe 4 ({@link Fax}),
 * wie die Folien der DTK5 (seit P3c); ihr Wert ist das gespeicherte Bit, bei Gruppe 4 1 für schwarz kodiert.
 * <p>
 * <b>Lage:</b> aus {@code ModelTransformationTag} oder aus {@code ModelPixelScaleTag} und {@code ModelTiepointTag};
 * bei {@code PixelIsPoint} (GeoKey 1025 = 2) bezieht sich der Anknüpfpunkt auf die Mitte des Pixels und wird auf die
 * Ecke geschoben. {@link #x0}, {@link #y0} ist immer die äußere Ecke von Pixel (0, 0). Der Leerwert kommt aus
 * {@code GDAL_NODATA}; er und Werte außerhalb des Bilds werden NaN.
 * <p>
 * Nur {@code java.*}. Lesen aus mehreren Fäden ist erlaubt.
 */
public final class GeoTiff implements Closeable {
    public final Path datei;
    public final int breite, hoehe, proben, bits;
    /** SampleFormat: 1 ganz ohne Vorzeichen, 2 ganz mit Vorzeichen, 3 Gleitkomma. */
    public final int format;
    public final int kompression, praediktor, planar;
    public final boolean gekachelt, bigTiff;
    /** Breite und Höhe eines Blocks (Kachel, oder Bildbreite × Zeilen je Streifen). */
    public final int blockBreite, blockHoehe;
    public final ByteOrder ordnung;
    /** Äußere Ecke von Pixel (0, 0) und Schritt je Pixel; dy &lt; 0, wenn Norden oben ist. */
    public final double x0, y0, dx, dy;
    /** EPSG der Lage (0 unbekannt) und ob sie geographisch ist. */
    public final int epsg;
    public final boolean geografisch;
    /** Leerwert, NaN wenn keiner angegeben ist. */
    public final double leer;
    /** PhotometricInterpretation (0: bei einem Bit ist 1 schwarz, 1: 0 ist schwarz) und FillOrder (2: niedrigstes Bit zuerst). */
    public final int photometrie, bitfolge;
    private final long[] offsets, laengen;
    private int letzterBlock = -1;
    private byte[] letzteBits;
    private final FileChannel kanal;
    private final int blockeQuer, blockeHoch;

    private GeoTiff(Path datei) throws IOException {
        this.datei = datei;
        kanal = FileChannel.open(datei, StandardOpenOption.READ);
        try {
            ByteBuffer kopf = lies(0, 16);
            byte b0 = kopf.get(0), b1 = kopf.get(1);
            if (b0 == 'I' && b1 == 'I') ordnung = ByteOrder.LITTLE_ENDIAN;
            else if (b0 == 'M' && b1 == 'M') ordnung = ByteOrder.BIG_ENDIAN;
            else throw new IOException(datei + ": kein TIFF");
            kopf.order(ordnung);
            int kennung = kopf.getShort(2) & 0xffff;
            if (kennung == 42) bigTiff = false;
            else if (kennung == 43) bigTiff = true;
            else throw new IOException(datei + ": kein TIFF (Kennung " + kennung + ")");
            long ifd = bigTiff ? kopf.getLong(8) : kopf.getInt(4) & 0xffffffffL;
            Map<Integer, Object> t = tags(ifd);
            breite = (int) zahl(t, 256, -1);
            hoehe = (int) zahl(t, 257, -1);
            proben = (int) zahl(t, 277, 1);
            bits = (int) zahl(t, 258, 1);
            format = (int) zahl(t, 339, 1);
            kompression = (int) zahl(t, 259, 1);
            praediktor = (int) zahl(t, 317, 1);
            planar = (int) zahl(t, 284, 1);
            photometrie = (int) zahl(t, 262, 1);
            bitfolge = (int) zahl(t, 266, 1);
            if (breite < 1 || hoehe < 1) throw new IOException(datei + ": ohne Größe");
            if (bits == 1) {
                if (proben != 1 || format != 1 || praediktor != 1) throw new IOException(datei + ": ein Bit nur mit einer Probe, ohne Vorzeichen und ohne Prädiktor");
                if ((zahl(t, 293, 0) % 4) >= 2) throw new IOException(datei + ": Gruppe 4 mit unkomprimiertem Modus nicht unterstützt");
            } else if (bits != 8 && bits != 16 && bits != 32 && bits != 64) throw new IOException(datei + ": " + bits + " Bit je Probe nicht unterstützt");
            if (format == 3 && bits < 32) throw new IOException(datei + ": Gleitkomma mit " + bits + " Bit nicht unterstützt");
            if (kompression != 1 && kompression != 5 && kompression != 8 && kompression != 32946 && kompression != 32773 && !(kompression == 4 && bits == 1))
                throw new IOException(datei + ": Kompression " + kompression + (bits == 1 ? "" : " bei " + bits + " Bit") + " nicht unterstützt");
            if (praediktor < 1 || praediktor > 3 || praediktor == 3 && format != 3 || praediktor == 2 && format == 3)
                throw new IOException(datei + ": Prädiktor " + praediktor + " bei SampleFormat " + format + " nicht unterstützt");
            gekachelt = t.containsKey(322);
            if (gekachelt) {
                blockBreite = (int) zahl(t, 322, -1);
                blockHoehe = (int) zahl(t, 323, -1);
                offsets = lang(t, 324);
                laengen = lang(t, 325);
            } else {
                blockBreite = breite;
                blockHoehe = (int) Math.min(hoehe, zahl(t, 278, hoehe));
                offsets = lang(t, 273);
                laengen = lang(t, 279);
            }
            blockeQuer = (breite + blockBreite - 1) / blockBreite;
            blockeHoch = (hoehe + blockHoehe - 1) / blockHoehe;
            int noetig = blockeQuer * blockeHoch * (planar == 2 ? proben : 1);
            if (offsets == null || laengen == null || offsets.length < noetig || laengen.length < noetig)
                throw new IOException(datei + ": " + (offsets == null ? 0 : offsets.length) + " Blöcke statt " + noetig);
            // Lage
            double[] gk = t.get(34735) instanceof double[] d ? d : new double[0];
            int rasterTyp = 1, modell = 0, proj = 0, geog = 0;
            for (int k = 4; k + 3 < gk.length && k < 4 + 4 * (int) (gk.length > 3 ? gk[3] : 0); k += 4) {
                int id = (int) gk[k], ort = (int) gk[k + 1];
                if (ort != 0) continue;
                int v = (int) gk[k + 3];
                if (id == 1025) rasterTyp = v;
                else if (id == 1024) modell = v;
                else if (id == 3072) proj = v;
                else if (id == 2048) geog = v;
            }
            geografisch = modell == 2 || modell == 0 && proj == 0 && geog != 0;
            epsg = geografisch ? geog : proj;
            double ax, ay, sx, sy;
            if (t.get(34264) instanceof double[] m && m.length >= 8) {
                if (Math.abs(m[1]) > 0 || Math.abs(m[4]) > 0) throw new IOException(datei + ": gedrehte Lage nicht unterstützt");
                sx = m[0];
                sy = m[5];
                ax = m[3];
                ay = m[7];
            } else if (t.get(33550) instanceof double[] s && t.get(33922) instanceof double[] p && s.length >= 2 && p.length >= 6) {
                sx = s[0];
                sy = -s[1];
                ax = p[3] - p[0] * sx;
                ay = p[4] - p[1] * sy;
            } else {
                sx = 1;
                sy = -1;
                ax = 0;
                ay = 0;
            }
            if (rasterTyp == 2) {                                                          // PixelIsPoint: Anknüpfpunkt ist die Pixelmitte
                ax -= 0.5 * sx;
                ay -= 0.5 * sy;
            }
            x0 = ax;
            y0 = ay;
            dx = sx;
            dy = sy;
            double l = Double.NaN;
            if (t.get(42113) instanceof String s) {
                try {
                    l = Double.parseDouble(s.strip());
                } catch (NumberFormatException e) {
                    if (!s.strip().equalsIgnoreCase("nan")) throw new IOException(datei + ": Leerwert '" + s + "' unlesbar");
                }
            }
            leer = l;
        } catch (IOException | RuntimeException e) {
            kanal.close();
            throw e;
        }
    }

    /** Öffnet ein GeoTIFF; nur der Kopf wird gelesen. */
    public static GeoTiff oeffne(Path datei) throws IOException { return new GeoTiff(datei); }

    @Override public void close() throws IOException { kanal.close(); }

    /** Spalte (gebrochen, Pixelmitte = ganz) zum x der Lage; Zeile entsprechend (von oben). */
    public double spalte(double x) { return (x - x0) / dx - 0.5; }
    public double zeile(double y) { return (y - y0) / dy - 0.5; }
    /** x und y der Mitte eines Pixels. */
    public double x(int spalte) { return x0 + (spalte + 0.5) * dx; }
    public double y(int zeile) { return y0 + (zeile + 0.5) * dy; }

    /** Liest ein Fenster des ersten Bands: Zeilen von oben, NaN für den Leerwert und außerhalb des Bilds. */
    public float[] lies(int x, int y, int w, int h) throws IOException { return lies(x, y, w, h, 0); }

    /** Liest ein Fenster eines Bands. */
    public float[] lies(int x, int y, int w, int h, int band) throws IOException {
        if (band < 0 || band >= proben) throw new IllegalArgumentException("Band " + band + " von " + proben);
        float[] aus = new float[w * h];
        java.util.Arrays.fill(aus, Float.NaN);
        if (bits == 1) {
            einBit(x, y, w, h, aus);
            return aus;
        }
        int bx0 = Math.max(0, Math.floorDiv(x, blockBreite)), bx1 = Math.min(blockeQuer - 1, Math.floorDiv(x + w - 1, blockBreite));
        int by0 = Math.max(0, Math.floorDiv(y, blockHoehe)), by1 = Math.min(blockeHoch - 1, Math.floorDiv(y + h - 1, blockHoehe));
        for (int by = by0; by <= by1; by++)
            for (int bx = bx0; bx <= bx1; bx++) {
                int nr = by * blockeQuer + bx + (planar == 2 ? band * blockeQuer * blockeHoch : 0);
                int zeilenImBlock = gekachelt ? blockHoehe : Math.min(blockHoehe, hoehe - by * blockHoehe);
                int probenJeZeile = blockBreite * (planar == 2 ? 1 : proben);
                double[] werte = block(nr, zeilenImBlock, probenJeZeile);
                int schritt = planar == 2 ? 1 : proben, versatz = planar == 2 ? 0 : band;
                for (int r = 0; r < zeilenImBlock; r++) {
                    int gy = by * blockHoehe + r;
                    if (gy < y || gy >= y + h || gy >= hoehe) continue;
                    for (int c = 0; c < blockBreite; c++) {
                        int gx = bx * blockBreite + c;
                        if (gx < x || gx >= x + w || gx >= breite) continue;
                        double v = werte[r * probenJeZeile + c * schritt + versatz];
                        aus[(gy - y) * w + (gx - x)] = v == leer ? Float.NaN : (float) v;
                    }
                }
            }
        return aus;
    }

    /** Fenster eines Bilds mit einem Bit je Pixel; der zuletzt entpackte Block wird behalten. */
    private synchronized void einBit(int x, int y, int w, int h, float[] aus) throws IOException {
        int bx0 = Math.max(0, Math.floorDiv(x, blockBreite)), bx1 = Math.min(blockeQuer - 1, Math.floorDiv(x + w - 1, blockBreite));
        int by0 = Math.max(0, Math.floorDiv(y, blockHoehe)), by1 = Math.min(blockeHoch - 1, Math.floorDiv(y + h - 1, blockHoehe));
        for (int by = by0; by <= by1; by++)
            for (int bx = bx0; bx <= bx1; bx++) {
                int nr = by * blockeQuer + bx;
                int zeilen = gekachelt ? blockHoehe : Math.min(blockHoehe, hoehe - by * blockHoehe);
                byte[] b = bitBlock(nr, zeilen);
                int r0 = Math.max(y, by * blockHoehe), r1 = Math.min(Math.min(y + h, hoehe), by * blockHoehe + zeilen);
                int c0 = Math.max(x, bx * blockBreite), c1 = Math.min(Math.min(x + w, breite), (bx + 1) * blockBreite);
                for (int gy = r0; gy < r1; gy++) {
                    int q = (gy - by * blockHoehe) * blockBreite - bx * blockBreite, z = (gy - y) * w - x;
                    for (int gx = c0; gx < c1; gx++) aus[z + gx] = b[q + gx];
                }
            }
    }

    /** Ein Block mit einem Bit je Pixel als ein Byte je Pixel (0 oder 1). */
    private byte[] bitBlock(int nr, int zeilen) throws IOException {
        if (nr == letzterBlock) return letzteBits;
        byte[] aus = new byte[blockBreite * zeilen];
        long off = offsets[nr], len = laengen[nr];
        if (len > 0 && off > 0) {
            ByteBuffer bb = lies(off, (int) len);
            byte[] gepackt = new byte[(int) len];
            bb.get(0, gepackt);
            if (kompression == 4) Fax.gruppe4(gepackt, blockBreite, zeilen, bitfolge == 2, aus, 0);
            else {
                int jeZeile = (blockBreite + 7) / 8, soll = jeZeile * zeilen;
                byte[] roh = switch (kompression) {
                    case 1 -> gepackt;
                    case 5 -> lzw(gepackt, soll);
                    case 8, 32946 -> deflate(gepackt, soll);
                    case 32773 -> packBits(gepackt, soll);
                    default -> throw new IOException("Kompression " + kompression);
                };
                for (int r = 0; r < zeilen; r++)
                    for (int c = 0; c < blockBreite; c++) {
                        int k = r * jeZeile + (c >> 3);
                        if (k >= roh.length) break;
                        int s = bitfolge == 2 ? c & 7 : 7 - (c & 7);
                        aus[r * blockBreite + c] = (byte) ((roh[k] >> s) & 1);
                    }
            }
        }
        letzterBlock = nr;
        letzteBits = aus;
        return aus;
    }

    /** Ein Block entpackt, Prädiktor zurückgerechnet, als Zahlen. */
    private double[] block(int nr, int zeilen, int probenJeZeile) throws IOException {
        int bytesJeProbe = bits / 8, bytesJeZeile = probenJeZeile * bytesJeProbe, soll = bytesJeZeile * zeilen;
        long off = offsets[nr], len = laengen[nr];
        byte[] roh;
        if (len == 0 || off == 0) roh = new byte[soll];                                    // leerer Block (sparse): Nullen
        else {
            ByteBuffer bb = lies(off, (int) len);
            byte[] gepackt = new byte[(int) len];
            bb.get(0, gepackt);
            roh = switch (kompression) {
                case 1 -> gepackt;
                case 5 -> lzw(gepackt, soll);
                case 8, 32946 -> deflate(gepackt, soll);
                case 32773 -> packBits(gepackt, soll);
                default -> throw new IOException("Kompression " + kompression);
            };
            if (roh.length < soll) {                                                       // letzter Streifen oder kurze Daten
                byte[] r = new byte[soll];
                System.arraycopy(roh, 0, r, 0, roh.length);
                roh = r;
            }
        }
        double[] v = new double[probenJeZeile * zeilen];
        int schritt = planar == 2 ? 1 : proben;
        if (praediktor == 3) {
            byte[] zeile = new byte[bytesJeZeile];
            for (int r = 0; r < zeilen; r++) {
                System.arraycopy(roh, r * bytesJeZeile, zeile, 0, bytesJeZeile);
                for (int k = schritt; k < bytesJeZeile; k++) zeile[k] += zeile[k - schritt];
                for (int i = 0; i < probenJeZeile; i++) {
                    long b = 0;
                    for (int k = 0; k < bytesJeProbe; k++) b = (b << 8) | (zeile[k * probenJeZeile + i] & 0xff);   // Ebenen: höchstes Byte zuerst
                    v[r * probenJeZeile + i] = bytesJeProbe == 4 ? Float.intBitsToFloat((int) b) : Double.longBitsToDouble(b);
                }
            }
            return v;
        }
        ByteBuffer bb = ByteBuffer.wrap(roh).order(ordnung);
        for (int i = 0; i < v.length; i++) v[i] = probe(bb, i * bytesJeProbe);
        if (praediktor == 2) {
            for (int r = 0; r < zeilen; r++)
                for (int i = schritt; i < probenJeZeile; i++) {
                    int k = r * probenJeZeile + i;
                    v[k] = umlauf(v[k] + v[k - schritt]);
                }
        }
        return v;
    }

    private double probe(ByteBuffer bb, int p) {
        if (format == 3) return bits == 32 ? bb.getFloat(p) : bb.getDouble(p);
        boolean mit = format == 2;
        return switch (bits) {
            case 8 -> mit ? bb.get(p) : bb.get(p) & 0xff;
            case 16 -> mit ? bb.getShort(p) : bb.getShort(p) & 0xffff;
            case 32 -> mit ? bb.getInt(p) : bb.getInt(p) & 0xffffffffL;
            default -> bb.getLong(p);
        };
    }

    /** Ganze Zahl nach Summe wieder in ihren Bereich (Prädiktor 2 rechnet modulo 2^bits). */
    private double umlauf(double v) {
        if (bits == 64) return v;
        double m = Math.pow(2, bits);
        double u = ((v % m) + m) % m;
        return format == 2 && u >= m / 2 ? u - m : u;
    }

    private ByteBuffer lies(long pos, int n) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(n);
        while (bb.hasRemaining()) {
            int k = kanal.read(bb, pos + bb.position());
            if (k < 0) throw new IOException(datei + ": unerwartetes Ende bei " + (pos + bb.position()));
        }
        return bb.order(ordnung == null ? ByteOrder.BIG_ENDIAN : ordnung);
    }

    // ------------------------------------------------------------------ Kopf

    private Map<Integer, Object> tags(long ifd) throws IOException {
        int kopf = bigTiff ? 8 : 2, eintrag = bigTiff ? 20 : 12, inline = bigTiff ? 8 : 4;
        ByteBuffer z = lies(ifd, kopf);
        long n = bigTiff ? z.getLong(0) : z.getShort(0) & 0xffff;
        ByteBuffer e = lies(ifd + kopf, (int) (n * eintrag));
        Map<Integer, Object> t = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int o = i * eintrag, tag = e.getShort(o) & 0xffff, typ = e.getShort(o + 2) & 0xffff;
            long anzahl = bigTiff ? e.getLong(o + 4) : e.getInt(o + 4) & 0xffffffffL;
            int groesse = switch (typ) {
                case 1, 2, 6, 7 -> 1;
                case 3, 8 -> 2;
                case 4, 9, 11, 13 -> 4;
                case 5, 10, 12, 16, 17, 18 -> 8;
                default -> 0;
            };
            if (groesse == 0 || anzahl > Integer.MAX_VALUE / 8) continue;
            int bytes = (int) (anzahl * groesse);
            ByteBuffer w;
            if (bytes <= inline) {
                byte[] b = new byte[bytes];
                e.get(o + (bigTiff ? 12 : 8), b);
                w = ByteBuffer.wrap(b).order(ordnung);
            } else {
                long pos = bigTiff ? e.getLong(o + 12) : e.getInt(o + 8) & 0xffffffffL;
                w = lies(pos, bytes);
            }
            if (typ == 2) {
                byte[] b = new byte[bytes];
                w.get(0, b);
                String s = new String(b, StandardCharsets.ISO_8859_1);
                int k = s.indexOf('\0');
                t.put(tag, k >= 0 ? s.substring(0, k) : s);
                continue;
            }
            double[] d = new double[(int) anzahl];
            for (int k = 0; k < anzahl; k++) {
                int p = k * groesse;
                d[k] = switch (typ) {
                    case 1, 7 -> w.get(p) & 0xff;
                    case 6 -> w.get(p);
                    case 3 -> w.getShort(p) & 0xffff;
                    case 8 -> w.getShort(p);
                    case 4, 13 -> w.getInt(p) & 0xffffffffL;
                    case 9 -> w.getInt(p);
                    case 5 -> (w.getInt(p) & 0xffffffffL) / (double) (w.getInt(p + 4) & 0xffffffffL);
                    case 10 -> w.getInt(p) / (double) w.getInt(p + 4);
                    case 11 -> w.getFloat(p);
                    case 12 -> w.getDouble(p);
                    default -> w.getLong(p);
                };
            }
            t.put(tag, d);
        }
        return t;
    }

    private static double zahl(Map<Integer, Object> t, int tag, double vorgabe) {
        return t.get(tag) instanceof double[] d && d.length > 0 ? d[0] : vorgabe;
    }

    private static long[] lang(Map<Integer, Object> t, int tag) {
        if (!(t.get(tag) instanceof double[] d)) return null;
        long[] l = new long[d.length];
        for (int i = 0; i < d.length; i++) l[i] = (long) d[i];
        return l;
    }

    // ------------------------------------------------------------------ Entpacken

    static byte[] deflate(byte[] in, int soll) throws IOException {
        Inflater inf = new Inflater();
        try {
            inf.setInput(in);
            byte[] out = new byte[Math.max(soll, 1)];
            int n = 0;
            while (!inf.finished() && n < out.length) {
                int k = inf.inflate(out, n, out.length - n);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                n += k;
            }
            return n == out.length ? out : java.util.Arrays.copyOf(out, n);
        } catch (DataFormatException e) {
            throw new IOException("Deflate: " + e.getMessage(), e);
        } finally {
            inf.end();
        }
    }

    static byte[] packBits(byte[] in, int soll) {
        byte[] out = new byte[soll];
        int i = 0, n = 0;
        while (i < in.length && n < soll) {
            int k = in[i++];
            if (k >= 0) {
                int m = Math.min(k + 1, Math.min(soll - n, in.length - i));
                System.arraycopy(in, i, out, n, m);
                i += k + 1;
                n += m;
            } else if (k != -128 && i < in.length) {
                byte b = in[i++];
                for (int r = 0; r < 1 - k && n < soll; r++) out[n++] = b;
            }
        }
        return out;
    }

    /** TIFF-LZW (Codes ab 9 Bit, höchstes Bit zuerst, Breite wächst einen Code früher). */
    static byte[] lzw(byte[] in, int soll) throws IOException {
        byte[] out = new byte[soll];
        int n = 0;
        byte[][] tab = new byte[4096][];
        for (int i = 0; i < 256; i++) tab[i] = new byte[] {(byte) i};
        int naechster = 258, breite = 9;
        long puffer = 0;
        int imPuffer = 0, pos = 0;
        byte[] alt = null;
        while (true) {
            while (imPuffer < breite && pos < in.length) {
                puffer = (puffer << 8) | (in[pos++] & 0xff);
                imPuffer += 8;
            }
            if (imPuffer < breite) break;
            int code = (int) ((puffer >>> (imPuffer - breite)) & ((1 << breite) - 1));
            imPuffer -= breite;
            if (code == 257) break;                                                         // EOI
            if (code == 256) {                                                              // Clear
                naechster = 258;
                breite = 9;
                alt = null;
                continue;
            }
            byte[] eintrag;
            if (code < naechster && tab[code] != null) eintrag = tab[code];
            else if (code == naechster && alt != null) {
                eintrag = java.util.Arrays.copyOf(alt, alt.length + 1);
                eintrag[alt.length] = alt[0];
            } else throw new IOException("LZW: Code " + code + " ohne Eintrag");
            int m = Math.min(eintrag.length, soll - n);
            System.arraycopy(eintrag, 0, out, n, m);
            n += m;
            if (alt != null && naechster < 4096) {
                byte[] neu = java.util.Arrays.copyOf(alt, alt.length + 1);
                neu[alt.length] = eintrag[0];
                tab[naechster++] = neu;
            }
            alt = eintrag;
            if (naechster + 1 >= (1 << breite) && breite < 12) breite++;
            if (n >= soll) break;
        }
        return n == soll ? out : java.util.Arrays.copyOf(out, n);
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "%s: %d × %d, %d × %d Bit (Format %d), %s, Kompression %d, Prädiktor %d, %s, EPSG %d, Ecke %.4f / %.4f, Schritt %.6g / %.6g, leer %s",
                datei.getFileName(), breite, hoehe, proben, bits, format, gekachelt ? "Kacheln " + blockBreite + " × " + blockHoehe : "Streifen zu " + blockHoehe + " Zeilen",
                kompression, praediktor, ordnung == ByteOrder.BIG_ENDIAN ? "MM" : "II", epsg, x0, y0, dx, dy, Double.isNaN(leer) ? "–" : String.valueOf(leer));
    }
}
