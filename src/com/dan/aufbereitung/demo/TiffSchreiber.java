package com.dan.aufbereitung.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;

/**
 * Schreibt GeoTIFFs in allen Arten, die {@code GeoTiff} lesen soll – nur für die Prüfung: klassisch oder BigTIFF,
 * beide Bytefolgen, Streifen oder Kacheln, ohne Kompression, LZW, Deflate oder PackBits, Prädiktor 1, 2 und 3.
 */
final class TiffSchreiber {
    ByteOrder ordnung = ByteOrder.LITTLE_ENDIAN;
    boolean bigTiff, kacheln, transformation, punkt;
    int kompression = 1, praediktor = 1, format = 3, bits = 32, proben = 1, block = 16;
    double x0 = 470000, y0 = 6048000, dx = 1, dy = -1;
    int epsg = 25832;
    String leer;

    /** werte: [zeile][spalte * proben + probe]. */
    void schreibe(Path datei, double[][] werte, int breite, int hoehe) throws IOException {
        int bw = kacheln ? block : breite, bh = kacheln ? block : Math.min(block, hoehe);
        int quer = (breite + bw - 1) / bw, hoch = (hoehe + bh - 1) / bh, b = bits / 8;
        List<byte[]> bloecke = new ArrayList<>();
        for (int by = 0; by < hoch; by++)
            for (int bx = 0; bx < quer; bx++) {
                int zeilen = kacheln ? bh : Math.min(bh, hoehe - by * bh);
                int jeZeile = bw * proben;
                ByteBuffer roh = ByteBuffer.allocate(zeilen * jeZeile * b).order(ordnung);
                for (int r = 0; r < zeilen; r++) {
                    double[] z = new double[jeZeile];
                    for (int c = 0; c < bw; c++)
                        for (int p = 0; p < proben; p++) {
                            int gy = by * bh + r, gx = bx * bw + c;
                            z[c * proben + p] = gy < hoehe && gx < breite ? werte[gy][gx * proben + p] : 0;
                        }
                    if (praediktor == 3) {
                        byte[] ebenen = new byte[jeZeile * b];
                        for (int i = 0; i < jeZeile; i++) {
                            long bitsW = b == 4 ? Float.floatToRawIntBits((float) z[i]) & 0xffffffffL : Double.doubleToRawLongBits(z[i]);
                            for (int k = 0; k < b; k++) ebenen[k * jeZeile + i] = (byte) (bitsW >>> (8 * (b - 1 - k)));
                        }
                        for (int k = ebenen.length - 1; k >= proben; k--) ebenen[k] -= ebenen[k - proben];
                        roh.put(ebenen);
                        continue;
                    }
                    long[] g = new long[jeZeile];
                    if (format != 3) for (int i = 0; i < jeZeile; i++) g[i] = (long) z[i];
                    if (praediktor == 2) for (int i = jeZeile - 1; i >= proben; i--) g[i] -= g[i - proben];
                    for (int i = 0; i < jeZeile; i++) {
                        if (format == 3) { if (b == 4) roh.putFloat((float) z[i]); else roh.putDouble(z[i]); }
                        else switch (b) {
                            case 1 -> roh.put((byte) g[i]);
                            case 2 -> roh.putShort((short) g[i]);
                            case 4 -> roh.putInt((int) g[i]);
                            default -> roh.putLong(g[i]);
                        }
                    }
                }
                byte[] r = roh.array();
                bloecke.add(switch (kompression) {
                    case 1 -> r;
                    case 5 -> lzw(r);
                    case 8 -> deflate(r);
                    case 32773 -> packBits(r);
                    default -> throw new IllegalArgumentException("Kompression " + kompression);
                });
            }
        // Tags
        TreeMap<Integer, Object[]> t = new TreeMap<>();                                      // tag -> {typ, werte}
        t.put(256, new Object[] {4, new long[] {breite}});
        t.put(257, new Object[] {4, new long[] {hoehe}});
        long[] bps = new long[proben];
        java.util.Arrays.fill(bps, bits);
        t.put(258, new Object[] {3, bps});
        t.put(259, new Object[] {3, new long[] {kompression}});
        t.put(262, new Object[] {3, new long[] {proben == 3 ? 2 : 1}});
        t.put(277, new Object[] {3, new long[] {proben}});
        t.put(284, new Object[] {3, new long[] {1}});
        if (praediktor != 1) t.put(317, new Object[] {3, new long[] {praediktor}});
        long[] sf = new long[proben];
        java.util.Arrays.fill(sf, format);
        t.put(339, new Object[] {3, sf});
        int offTag = kacheln ? 324 : 273, lenTag = kacheln ? 325 : 279;
        if (kacheln) {
            t.put(322, new Object[] {3, new long[] {bw}});
            t.put(323, new Object[] {3, new long[] {bh}});
        } else t.put(278, new Object[] {4, new long[] {bh}});
        long[] laengen = new long[bloecke.size()];
        for (int i = 0; i < laengen.length; i++) laengen[i] = bloecke.get(i).length;
        t.put(offTag, new Object[] {bigTiff ? 16 : 4, new long[bloecke.size()]});
        t.put(lenTag, new Object[] {bigTiff ? 16 : 4, laengen});
        double ax = punkt ? x0 + 0.5 * dx : x0, ay = punkt ? y0 + 0.5 * dy : y0;
        if (transformation) t.put(34264, new Object[] {12, new double[] {dx, 0, 0, ax, 0, dy, 0, ay, 0, 0, 0, 0, 0, 0, 0, 1}});
        else {
            t.put(33550, new Object[] {12, new double[] {dx, -dy, 0}});
            t.put(33922, new Object[] {12, new double[] {0, 0, 0, ax, ay, 0}});
        }
        boolean geo = epsg >= 4000 && epsg < 5000;
        t.put(34735, new Object[] {3, new long[] {1, 1, 0, 3, 1024, 0, 1, geo ? 2 : 1, 1025, 0, 1, punkt ? 2 : 1, geo ? 2048 : 3072, 0, 1, epsg}});
        if (leer != null) t.put(42113, new Object[] {2, (leer + "\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII)});
        // Ablage: Kopf, Blöcke, IFD, Zusatzwerte
        ByteArrayOutputStream daten = new ByteArrayOutputStream();
        int kopf = bigTiff ? 16 : 8;
        long[] offsets = (long[]) t.get(offTag)[1];
        for (int i = 0; i < bloecke.size(); i++) {
            offsets[i] = kopf + daten.size();
            daten.write(bloecke.get(i));
            if (daten.size() % 2 == 1) daten.write(0);
        }
        long ifd = kopf + daten.size();
        int eintrag = bigTiff ? 20 : 12, inline = bigTiff ? 8 : 4;
        long zusatz = ifd + (bigTiff ? 8 : 2) + (long) t.size() * eintrag + (bigTiff ? 8 : 4);
        ByteBuffer ib = ByteBuffer.allocate((int) (zusatz - ifd)).order(ordnung);
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        if (bigTiff) ib.putLong(t.size()); else ib.putShort((short) t.size());
        for (Map.Entry<Integer, Object[]> e : t.entrySet()) {
            int typ = (Integer) e.getValue()[0];
            byte[] w = werte(typ, e.getValue()[1]);
            int anzahl = typ == 2 ? w.length : w.length / groesse(typ);
            ib.putShort((short) (int) e.getKey()).putShort((short) typ);
            if (bigTiff) ib.putLong(anzahl); else ib.putInt(anzahl);
            if (w.length <= inline) {
                byte[] f = java.util.Arrays.copyOf(w, inline);
                ib.put(f);
            } else {
                long pos = zusatz + extra.size();
                if (bigTiff) ib.putLong(pos); else ib.putInt((int) pos);
                extra.write(w);
                if (extra.size() % 2 == 1) extra.write(0);
            }
        }
        if (bigTiff) ib.putLong(0); else ib.putInt(0);
        ByteBuffer k = ByteBuffer.allocate(kopf).order(ordnung);
        k.put((byte) (ordnung == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M')).put((byte) (ordnung == ByteOrder.LITTLE_ENDIAN ? 'I' : 'M'));
        if (bigTiff) k.putShort((short) 43).putShort((short) 8).putShort((short) 0).putLong(ifd);
        else k.putShort((short) 42).putInt((int) ifd);
        ByteArrayOutputStream alles = new ByteArrayOutputStream();
        alles.write(k.array());
        alles.write(daten.toByteArray());
        alles.write(ib.array());
        alles.write(extra.toByteArray());
        Files.write(datei, alles.toByteArray());
    }

    private static int groesse(int typ) {
        return switch (typ) { case 2 -> 1; case 3 -> 2; case 4 -> 4; default -> 8; };
    }

    private byte[] werte(int typ, Object v) {
        if (typ == 2) return (byte[]) v;
        if (v instanceof double[] d) {
            ByteBuffer b = ByteBuffer.allocate(d.length * 8).order(ordnung);
            for (double x : d) b.putDouble(x);
            return b.array();
        }
        long[] l = (long[]) v;
        ByteBuffer b = ByteBuffer.allocate(l.length * groesse(typ)).order(ordnung);
        for (long x : l) {
            if (typ == 3) b.putShort((short) x);
            else if (typ == 4) b.putInt((int) x);
            else b.putLong(x);
        }
        return b.array();
    }

    static byte[] deflate(byte[] in) {
        Deflater d = new Deflater();
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        while (!d.finished()) o.write(buf, 0, d.deflate(buf));
        d.end();
        return o.toByteArray();
    }

    static byte[] packBits(byte[] in) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int i = 0;
        while (i < in.length) {
            int run = 1;
            while (i + run < in.length && run < 128 && in[i + run] == in[i]) run++;
            if (run >= 3) {
                o.write(1 - run);
                o.write(in[i]);
                i += run;
            } else {
                int n = Math.min(128, in.length - i);
                int m = 0;
                while (m < n && !(i + m + 2 < in.length && in[i + m] == in[i + m + 1] && in[i + m] == in[i + m + 2])) m++;
                m = Math.max(1, m);
                o.write(m - 1);
                o.write(in, i, m);
                i += m;
            }
        }
        return o.toByteArray();
    }

    /** TIFF-LZW mit früher Umstellung der Codebreite. */
    static byte[] lzw(byte[] in) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        long[] puf = {0, 0};                                                              // Bits, Anzahl
        Map<Long, Integer> tab = new HashMap<>();
        int frei = 258, breite = 9;
        schreibe(o, puf, 256, breite);
        if (in.length == 0) {
            schreibe(o, puf, 257, breite);
            leere(o, puf);
            return o.toByteArray();
        }
        int w = in[0] & 0xff;
        for (int i = 1; i < in.length; i++) {
            int c = in[i] & 0xff;
            long key = ((long) w << 8) | c;
            Integer code = tab.get(key);
            if (code != null) { w = code; continue; }
            schreibe(o, puf, w, breite);
            tab.put(key, frei++);
            if (frei > (1 << breite) - 1 && breite < 12) breite++;
            if (frei >= 4093) {
                schreibe(o, puf, 256, breite);
                tab.clear();
                frei = 258;
                breite = 9;
            }
            w = c;
        }
        schreibe(o, puf, w, breite);
        frei++;
        if (frei > (1 << breite) - 1 && breite < 12) breite++;
        schreibe(o, puf, 257, breite);
        leere(o, puf);
        return o.toByteArray();
    }

    private static void schreibe(ByteArrayOutputStream o, long[] puf, int code, int breite) {
        puf[0] = (puf[0] << breite) | code;
        puf[1] += breite;
        while (puf[1] >= 8) {
            o.write((int) (puf[0] >>> (puf[1] - 8)) & 0xff);
            puf[1] -= 8;
        }
    }

    private static void leere(ByteArrayOutputStream o, long[] puf) {
        if (puf[1] > 0) o.write((int) (puf[0] << (8 - puf[1])) & 0xff);
        puf[1] = 0;
    }
}
