package com.dan.core.szene;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Die Gehölze eines Gebiets (seit P12c, F136 A, F137 A): je Gehölz Lage, Boden, Höhe, Krone und Art, ohne Grafik.
 * Gemessen im Schritt {@code gehoelz} der Aufbereitung aus dem bDOM (Oberfläche, 20 cm) minus DGM1 und dem NDVI des
 * DOP20; die Art nach Regeln [G]. Ablage {@code bau/gehoelz.bin} (Magie "HHGEH001", Version 1, little endian):
 * <pre>
 * Magie (8), Version (int), e0, n0 (double), Zahl (int);
 * je Gehölz: x, z (float, Raum der Bauten: x = Ost − e0, z = n0 − Nord), Boden, Höhe (m NHN, m über dem Boden),
 *            8 Halbmesser der Krone (m, Richtungen Ost, Nordost, Nord, … gegen den Uhrzeigersinn), Art (int), Fläche (m²)
 * </pre>
 * Raum wie {@link Bauten}: dieselben e0, n0, damit Häuser, Körper und Gehölze zusammenpassen.
 */
public final class Gehoelz {
    public static final String DATEI = "gehoelz.bin";
    static final byte[] MAGIE = "HHGEH001".getBytes(StandardCharsets.US_ASCII);
    public static final int VERSION = 1, RICHTUNGEN = 8;

    /** Arten der Hallig (F137 A). */
    public static final int ESCHE = 0, SCHWARZERLE = 1, EBERESCHE = 2, SALWEIDE = 3, HOLUNDER = 4, HUNDSROSE = 5;
    public static final int ARTEN = 6;

    public static String name(int art) {
        return switch (art) {
            case ESCHE -> "Esche"; case SCHWARZERLE -> "Schwarzerle"; case EBERESCHE -> "Eberesche"; case SALWEIDE -> "Salweide";
            case HOLUNDER -> "Holunder"; case HUNDSROSE -> "Hundsrose";
            default -> "?";
        };
    }

    /** Ein Gehölz. {@code krone}: Halbmesser in 8 Richtungen (0 = Ost, dann je 45° gegen den Uhrzeigersinn, also nach Nord). */
    public static final class Pflanze {
        public float x, z, boden, hoehe, flaeche;
        public final float[] krone = new float[RICHTUNGEN];
        public int art;

        /** Größter Halbmesser der Krone. */
        public float kroneMax() {
            float m = 0;
            for (float r : krone) m = Math.max(m, r);
            return m;
        }

        /** Mittlerer Halbmesser der Krone. */
        public float kroneMittel() {
            float m = 0;
            for (float r : krone) m += r;
            return m / RICHTUNGEN;
        }

        /** Halbmesser der Krone in der Richtung a (rad, 0 = Ost, gegen den Uhrzeigersinn), linear zwischen den acht. */
        public double kroneIn(double a) {
            double t = a / (2 * Math.PI / RICHTUNGEN);
            t -= Math.floor(t / RICHTUNGEN) * RICHTUNGEN;
            int i = (int) Math.floor(t);
            double f = t - i;
            return krone[i % RICHTUNGEN] * (1 - f) + krone[(i + 1) % RICHTUNGEN] * f;
        }
    }

    public final double e0, n0;
    public final List<Pflanze> pflanzen;

    public Gehoelz(double e0, double n0, List<Pflanze> pflanzen) {
        this.e0 = e0;
        this.n0 = n0;
        this.pflanzen = Collections.unmodifiableList(new ArrayList<>(pflanzen));
    }

    /** Liest {@code ordner/gehoelz.bin}; null, wenn es fehlt. */
    public static Gehoelz lies(File ordner) throws IOException {
        File f = new File(ordner, DATEI);
        if (!f.isFile()) return null;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(f.toPath()))) {
            return lies(in);
        }
    }

    public static Gehoelz lies(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] m = new byte[8];
        d.readFully(m);
        if (!Arrays.equals(m, MAGIE)) throw new IOException("gehoelz.bin: keine Gehölze (Magie)");
        ByteBuffer k = ByteBuffer.wrap(d.readNBytes(4 + 8 + 8 + 4)).order(ByteOrder.LITTLE_ENDIAN);
        int v = k.getInt();
        if (v != VERSION) throw new IOException("gehoelz.bin: Version " + v + " statt " + VERSION);
        double e0 = k.getDouble(), n0 = k.getDouble();
        int n = k.getInt();
        int je = 4 * (4 + RICHTUNGEN + 2);
        ByteBuffer b = ByteBuffer.wrap(d.readNBytes(je * n)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.capacity() != je * n) throw new IOException("gehoelz.bin: zu kurz");
        List<Pflanze> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Pflanze p = new Pflanze();
            p.x = b.getFloat(); p.z = b.getFloat(); p.boden = b.getFloat(); p.hoehe = b.getFloat();
            for (int r = 0; r < RICHTUNGEN; r++) p.krone[r] = b.getFloat();
            p.art = b.getInt();
            p.flaeche = b.getFloat();
            l.add(p);
        }
        return new Gehoelz(e0, n0, l);
    }

    public void schreibe(OutputStream aus) throws IOException {
        int je = 4 * (4 + RICHTUNGEN + 2);
        ByteBuffer b = ByteBuffer.allocate(8 + 4 + 16 + 4 + je * pflanzen.size()).order(ByteOrder.LITTLE_ENDIAN);
        b.put(MAGIE).putInt(VERSION).putDouble(e0).putDouble(n0).putInt(pflanzen.size());
        for (Pflanze p : pflanzen) {
            b.putFloat(p.x).putFloat(p.z).putFloat(p.boden).putFloat(p.hoehe);
            for (float r : p.krone) b.putFloat(r);
            b.putInt(p.art).putFloat(p.flaeche);
        }
        aus.write(b.array());
        aus.flush();
    }

    public void schreibe(File ordner) throws IOException {
        Files.createDirectories(ordner.toPath());
        try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(new File(ordner, DATEI).toPath()))) {
            schreibe(o);
        }
    }

    /** Zahl je Art. */
    public int[] zahlJeArt() {
        int[] z = new int[ARTEN];
        for (Pflanze p : pflanzen) if (p.art >= 0 && p.art < ARTEN) z[p.art]++;
        return z;
    }
}
