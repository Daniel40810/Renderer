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
import java.util.Collections;
import java.util.List;

/**
 * Die Gebäude eines Gebiets als Körper (seit P12a, Werkbuch HalligHooge Teil A, 12.13; F125 A, F126 A): Dreiecke mit
 * Normale, Lage auf der Fläche (für Fenster, Backstein und Halme) und Art, dazu je Gebäude Material und Höhen. Ohne
 * Grafik: Das Bild ({@code render.bau}) und später die Referenz ({@code render.reference}, P12c) lesen dieselbe Ablage.
 * <p>
 * <b>Raum:</b> x = Ost − e0, y = Höhe (m NHN), z = n0 − Nord – wie die Clipmap des Geländes, mit (e0, n0) aus dem Kopf.
 * In float auf 1 mm genau, solange das Gebiet kleiner als rund 16 km ist.
 * <p>
 * <b>Scheitel</b> (12 float, 48 Byte, für die Karte ohne Umbau als std430 {@code vec4[3]}): x, y, z, Art | nx, ny, nz,
 * Gebäude | u, v, w, 0. Art: {@link #WAND}, {@link #DACH}. Wand: u längs der Wand ab ihrer Mitte (m), v Höhe über dem
 * Boden des Gebäudes (m), w Länge der Wand (m). Dach: u längs der Traufe, v die Falllinie hinab (m), w Länge der Traufe.
 * <p>
 * <b>Ablage</b> {@code bau/gebaeude.bin} (little endian): „HHBAU001“, int Version, double e0, n0, int Gebäude, int
 * Scheitel; je Gebäude int erster Scheitel, int Scheitel, float Boden, Traufe, First (m NHN), int Dachart, float Dach R G B
 * (Albedo linear), float Wand R G B, float DOP R G B (gemessen, linear; NaN ohne), float Grundfläche (m²), UTF-8 Kennung
 * und Name (je int Länge und Bytes); danach die Scheitel.
 */
public final class Bauten {
    public static final String DATEI = "gebaeude.bin";
    static final byte[] MAGIE = "HHBAU001".getBytes(StandardCharsets.US_ASCII);
    public static final int VERSION = 1, FLOATS = 12;
    /** Art eines Dreiecks. Seit P12b dazu die Körper der Gestalt (F133 A): Holz (Pfähle, Stege), Stein (Buhnen), Beton (Anleger), Buschwerk (Lahnungen). */
    public static final int WAND = 0, DACH = 1, HOLZ = 2, STEIN = 3, BETON = 4, BUSCH = 5;
    /** Dachart (F126 A, Daniel 19.11.: „grundsätzlich rote Backsteinbauten mit Reetdach“). */
    public static final int REET = 0, ZIEGEL = 1, FLACH = 2;
    public static final String[] DACHART = {"Reet", "Ziegel", "flach"};

    /** Ein Gebäude: Höhen in m NHN, Albedo linear. */
    public static final class Gebaeude {
        public int erster, scheitel;
        public float boden, traufe, first;
        public int dach;
        public float[] dachAlbedo = new float[3], wandAlbedo = new float[3], dop = {Float.NaN, Float.NaN, Float.NaN};
        public float flaeche;
        public String kennung = "", name = "";
        /** Hülle im Raum (x0, z0, x1, z1), aus den Scheiteln. */
        public final float[] huelle = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    }

    public final double e0, n0;
    public final List<Gebaeude> gebaeude;
    /** Alle Scheitel, je {@link #FLOATS}. */
    public final float[] scheitel;

    public Bauten(double e0, double n0, List<Gebaeude> gebaeude, float[] scheitel) {
        if (scheitel.length % FLOATS != 0) throw new IllegalArgumentException("Bauten: " + scheitel.length + " Floats, kein Vielfaches von " + FLOATS);
        if (scheitel.length / FLOATS % 3 != 0) throw new IllegalArgumentException("Bauten: " + scheitel.length / FLOATS + " Scheitel, keine ganzen Dreiecke");
        this.e0 = e0;
        this.n0 = n0;
        this.gebaeude = Collections.unmodifiableList(new ArrayList<>(gebaeude));
        this.scheitel = scheitel;
        for (Gebaeude b : gebaeude)
            for (int i = b.erster; i < b.erster + b.scheitel; i++) {
                float x = scheitel[i * FLOATS], z = scheitel[i * FLOATS + 2];
                b.huelle[0] = Math.min(b.huelle[0], x);
                b.huelle[1] = Math.min(b.huelle[1], z);
                b.huelle[2] = Math.max(b.huelle[2], x);
                b.huelle[3] = Math.max(b.huelle[3], z);
            }
    }

    public int scheitelZahl() { return scheitel.length / FLOATS; }

    /** Zwei Ablagen hintereinander (seit P12b: Häuser und Körper der Gestalt); Ursprung von a, b muss ihn teilen. */
    public static Bauten zusammen(Bauten a, Bauten b) {
        if (b == null) return a;
        if (a == null) return b;
        if (a.e0 != b.e0 || a.n0 != b.n0) throw new IllegalArgumentException("Bauten: verschiedene Ursprünge");
        List<Gebaeude> g = new ArrayList<>(a.gebaeude);
        int na = a.gebaeude.size(), sa = a.scheitelZahl();
        for (Gebaeude x : b.gebaeude) {
            Gebaeude y = new Gebaeude();
            y.erster = x.erster + sa; y.scheitel = x.scheitel; y.boden = x.boden; y.traufe = x.traufe; y.first = x.first; y.dach = x.dach;
            y.dachAlbedo = x.dachAlbedo; y.wandAlbedo = x.wandAlbedo; y.dop = x.dop; y.flaeche = x.flaeche; y.kennung = x.kennung; y.name = x.name;
            g.add(y);
        }
        float[] s = java.util.Arrays.copyOf(a.scheitel, a.scheitel.length + b.scheitel.length);
        System.arraycopy(b.scheitel, 0, s, a.scheitel.length, b.scheitel.length);
        for (int i = sa; i < s.length / FLOATS; i++) s[i * FLOATS + 7] += na;     // Nummer des Gebäudes
        return new Bauten(a.e0, a.n0, g, s);
    }

    public int dreiecke() { return scheitelZahl() / 3; }

    /** Hülle aller Gebäude im Raum: x0, y0 (m NHN), z0, x1, y1, z1. */
    public double[] huelle() {
        double[] h = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < scheitelZahl(); i++)
            for (int k = 0; k < 3; k++) {
                h[k] = Math.min(h[k], scheitel[i * FLOATS + k]);
                h[3 + k] = Math.max(h[3 + k], scheitel[i * FLOATS + k]);
            }
        return h;
    }

    /** Liest {@code ordner/gebaeude.bin}; null, wenn es die Datei nicht gibt. */
    public static Bauten lies(File ordner) throws IOException { return lies(ordner, DATEI); }

    /** Seit P12b: die Körper der Gestalt ({@code bau/koerper.bin}, Schritt gestalt). */
    public static final String KOERPER = "koerper.bin";

    /** Liest {@code ordner/datei}; null, wenn es die Datei nicht gibt. */
    public static Bauten lies(File ordner, String datei) throws IOException {
        File f = new File(ordner, datei);
        if (!f.isFile()) return null;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(f.toPath()), 1 << 20)) {
            return lies(in);
        }
    }

    public static Bauten lies(InputStream roh) throws IOException {
        DataInputStream in = new DataInputStream(roh);
        byte[] m = new byte[8];
        in.readFully(m);
        if (!java.util.Arrays.equals(m, MAGIE)) throw new IOException("Bauten: keine Ablage der Gebäude (Kennung " + new String(m, StandardCharsets.US_ASCII) + ")");
        ByteBuffer k = lese(in, 4 + 16 + 8);
        int v = k.getInt();
        if (v != VERSION) throw new IOException("Bauten: Version " + v + ", erwartet " + VERSION);
        double e0 = k.getDouble(), n0 = k.getDouble();
        int ng = k.getInt(), ns = k.getInt();
        List<Gebaeude> l = new ArrayList<>(ng);
        for (int i = 0; i < ng; i++) {
            ByteBuffer b = lese(in, 4 * 18);
            Gebaeude g = new Gebaeude();
            g.erster = b.getInt();
            g.scheitel = b.getInt();
            g.boden = b.getFloat();
            g.traufe = b.getFloat();
            g.first = b.getFloat();
            g.dach = b.getInt();
            for (int c = 0; c < 3; c++) g.dachAlbedo[c] = b.getFloat();
            for (int c = 0; c < 3; c++) g.wandAlbedo[c] = b.getFloat();
            for (int c = 0; c < 3; c++) g.dop[c] = b.getFloat();
            g.flaeche = b.getFloat();
            g.kennung = text(in);
            g.name = text(in);
            l.add(g);
        }
        ByteBuffer s = lese(in, 4L * FLOATS * ns);
        float[] sch = new float[FLOATS * ns];
        s.asFloatBuffer().get(sch);
        return new Bauten(e0, n0, l, sch);
    }

    /** Schreibt {@code ordner/gebaeude.bin}. */
    public void schreibe(File ordner) throws IOException {
        Files.createDirectories(ordner.toPath());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(new File(ordner, DATEI).toPath()), 1 << 20)) {
            schreibe(out);
        }
    }

    public void schreibe(OutputStream out) throws IOException {
        out.write(MAGIE);
        ByteBuffer k = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        k.putInt(VERSION).putDouble(e0).putDouble(n0).putInt(gebaeude.size()).putInt(scheitelZahl());
        out.write(k.array());
        for (Gebaeude g : gebaeude) {
            ByteBuffer b = ByteBuffer.allocate(4 * 18).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(g.erster).putInt(g.scheitel).putFloat(g.boden).putFloat(g.traufe).putFloat(g.first).putInt(g.dach);
            for (int c = 0; c < 3; c++) b.putFloat(g.dachAlbedo[c]);
            for (int c = 0; c < 3; c++) b.putFloat(g.wandAlbedo[c]);
            for (int c = 0; c < 3; c++) b.putFloat(g.dop[c]);
            b.putFloat(g.flaeche);
            out.write(b.array());
            text(out, g.kennung);
            text(out, g.name);
        }
        ByteBuffer s = ByteBuffer.allocate(4 * scheitel.length).order(ByteOrder.LITTLE_ENDIAN);
        s.asFloatBuffer().put(scheitel);
        out.write(s.array());
    }

    private static ByteBuffer lese(DataInputStream in, long n) throws IOException {
        if (n > Integer.MAX_VALUE - 16) throw new IOException("Bauten: " + n + " Byte am Stück");
        byte[] a = new byte[(int) n];
        in.readFully(a);
        return ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static String text(DataInputStream in) throws IOException {
        int n = lese(in, 4).getInt();
        if (n < 0 || n > 1 << 16) throw new IOException("Bauten: Text der Länge " + n);
        byte[] a = new byte[n];
        in.readFully(a);
        return new String(a, StandardCharsets.UTF_8);
    }

    private static void text(OutputStream out, String s) throws IOException {
        byte[] a = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(a.length).array());
        out.write(a);
    }

    // ------------------------------------------------------------------ Bauen aus Flächen

    /**
     * Baut die Scheitel eines Gebäudes aus ebenen Flächen (für die Aufbereitung und die Prüfungen). Jede Fläche ist ein
     * Ring im Raum (x, y, z je Punkt, ohne Wiederholung des ersten), mit Art; Wände und Dächer werden in ihrer Ebene in
     * Dreiecke zerlegt ({@link Zerlegung}) und mit u, v, w nach der Art versehen. Die Normale zeigt nach außen, wenn der
     * Ring von außen gesehen gegen den Uhrzeigersinn läuft (CityGML); {@code aussen} (x, z) dreht sie sonst um, wenn
     * sie auf die Mitte des Grundrisses zeigt.
     */
    public static final class Bauer {
        final List<float[]> s = new ArrayList<>();
        final List<Gebaeude> g = new ArrayList<>();
        Gebaeude jetzt;
        public int flaechen, verworfen;

        public Gebaeude neu() {
            jetzt = new Gebaeude();
            jetzt.erster = s.size();
            g.add(jetzt);
            return jetzt;
        }

        /** Eine Fläche des Gebäudes in Arbeit; mx, mz: Mitte des Grundrisses (für die Richtung der Normale). */
        public void flaeche(int art, double[] ring, double mx, double mz) {
            flaechen++;
            int n = ring.length / 3;
            if (n < 3) { verworfen++; return; }
            double[] nn = Zerlegung.normale(ring);
            if (nn == null) { verworfen++; return; }
            // Wände: Normale waagerecht nach außen; Dächer: nach oben
            double cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < n; i++) { cx += ring[3 * i]; cy += ring[3 * i + 1]; cz += ring[3 * i + 2]; }
            cx /= n; cy /= n; cz /= n;
            boolean dachartig = art == DACH || (art >= HOLZ && Math.abs(nn[1]) > 0.7);
            boolean drehen = dachartig ? nn[1] < 0 : (nn[0] * (cx - mx) + nn[2] * (cz - mz)) < 0;
            if (drehen) for (int k = 0; k < 3; k++) nn[k] = -nn[k];
            int[] tri = Zerlegung.dreiecke(ring, nn);
            if (tri == null) { verworfen++; return; }
            // Achsen auf der Fläche
            double[] t;                                   // waagerecht in der Fläche
            double h = Math.hypot(nn[0], nn[2]);
            if (h < 1e-6) t = new double[] {1, 0, 0};
            else t = new double[] {-nn[2] / h, 0, nn[0] / h};
            double[] f = {nn[1] * t[2] - nn[2] * t[1], nn[2] * t[0] - nn[0] * t[2], nn[0] * t[1] - nn[1] * t[0]};   // n × t: in der Fläche, quer zu t
            if (f[1] > 0) for (int k = 0; k < 3; k++) f[k] = -f[k];                                                    // die Falllinie hinab
            double umin = Double.MAX_VALUE, umax = -Double.MAX_VALUE, vmin = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double u = ring[3 * i] * t[0] + ring[3 * i + 2] * t[2], v = ring[3 * i] * f[0] + ring[3 * i + 1] * f[1] + ring[3 * i + 2] * f[2];
                umin = Math.min(umin, u);
                umax = Math.max(umax, u);
                vmin = Math.min(vmin, v);
            }
            double um = 0.5 * (umin + umax), w = umax - umin;
            for (int i : tri) {
                double x = ring[3 * i], y = ring[3 * i + 1], z = ring[3 * i + 2];
                double u = x * t[0] + z * t[2] - um;
                double v = !dachartig ? y - jetzt.boden : x * f[0] + y * f[1] + z * f[2] - vmin;
                s.add(new float[] {(float) x, (float) y, (float) z, art, (float) nn[0], (float) nn[1], (float) nn[2], g.size() - 1, (float) u, (float) v, (float) w, 0});
            }
        }

        /**
         * Ein Zwerchgiebel (seit P12a, F129 A) über der Traufe des Gebäudes in Arbeit [G]: ein Giebel aus Backstein in der
         * Ebene der Wand, Breite b, mit seinem eigenen Satteldach (Neigung {@code neigung} Grad), der die Wand um h über
         * die Traufe hebt; seine Wangen und sein Dach sind Reet und laufen nach hinten in das Hauptdach (Neigung
         * {@code dachNeigung}). (mx, mz): Mitte der Traufe im Raum; (tx, tz): Richtung der Traufe; (ox, oz): waagerecht
         * nach außen (die Falllinie des Hauptdachs hinab); traufe: Höhe der Traufe (m NHN). Gibt den First (m NHN).
         */
        public double zwerchgiebel(double mx, double mz, double tx, double tz, double ox, double oz, double traufe, double b, double h, double neigung,
                                   double dachNeigung, double hausX, double hausZ) {
            double tr = traufe, oben = tr + h, first = oben + 0.5 * b * Math.tan(Math.toRadians(neigung));
            double tanD = Math.tan(Math.toRadians(dachNeigung));
            double d1 = h / tanD, dr = (first - tr) / tanD;          // Tiefe, in der Wange und First das Hauptdach treffen
            java.util.function.BiFunction<Double, Double, double[]> p = (a, d) -> new double[] {mx + tx * a - ox * d, mz + tz * a - oz * d};
            double[] l0 = p.apply(-0.5 * b, 0.0), r0 = p.apply(0.5 * b, 0.0), m0 = p.apply(0.0, 0.0);
            double[] l1 = p.apply(-0.5 * b, d1), r1 = p.apply(0.5 * b, d1), m1 = p.apply(0.0, dr);
            // Giebelwand über der Traufe (Backstein)
            flaeche(WAND, new double[] {l0[0], tr, l0[1], r0[0], tr, r0[1], r0[0], oben, r0[1], m0[0], first, m0[1], l0[0], oben, l0[1]}, hausX, hausZ);
            // Wangen (Reet)
            flaeche(DACH, new double[] {l0[0], tr, l0[1], l0[0], oben, l0[1], l1[0], oben, l1[1]}, hausX, hausZ);
            flaeche(DACH, new double[] {r0[0], tr, r0[1], r1[0], oben, r1[1], r0[0], oben, r0[1]}, hausX, hausZ);
            // Dach des Zwerchgiebels (Reet)
            flaeche(DACH, new double[] {l0[0], oben, l0[1], m0[0], first, m0[1], m1[0], first, m1[1], l1[0], oben, l1[1]}, hausX, hausZ);
            flaeche(DACH, new double[] {r0[0], oben, r0[1], r1[0], oben, r1[1], m1[0], first, m1[1], m0[0], first, m0[1]}, hausX, hausZ);
            return first;
        }

        /**
         * Ein Quader (seit P12b): Grundriss als Rechteck um (x, z) mit halben Seiten a längs (dx, dz) und b quer dazu,
         * von y0 bis y1; vier Seiten und Deckel, ohne Boden. Für Pfähle, Stege, Anleger.
         */
        public void quader(int art, double x, double z, double dx, double dz, double a, double b, double y0, double y1) {
            double l = Math.hypot(dx, dz);
            double ux = dx / l, uz = dz / l, qx = -uz, qz = ux;
            double[][] e = {{x - ux * a - qx * b, z - uz * a - qz * b}, {x + ux * a - qx * b, z + uz * a - qz * b}, {x + ux * a + qx * b, z + uz * a + qz * b},
                    {x - ux * a + qx * b, z - uz * a + qz * b}};
            for (int i = 0; i < 4; i++) {
                double[] p = e[i], q = e[(i + 1) % 4];
                flaeche(art, new double[] {p[0], y0, p[1], q[0], y0, q[1], q[0], y1, q[1], p[0], y1, p[1]}, x, z);
            }
            flaeche(art, new double[] {e[0][0], y1, e[0][1], e[1][0], y1, e[1][1], e[2][0], y1, e[2][1], e[3][0], y1, e[3][1]}, x, z);
        }

        /**
         * Ein Strang (seit P12b): ein Querschnitt längs einer Linie, je Punkt (x, z, Boden y). Querschnitt als Trapez: unten
         * Breite bu, oben Breite bo, Höhe h über dem Boden. Für Steinbuhnen und Buschwerk zwischen den Pfahlreihen.
         */
        public void strang(int art, double[] xs, double[] zs, double[] ys, double bu, double bo, double h) {
            for (int i = 0; i + 1 < xs.length; i++) {
                double dx = xs[i + 1] - xs[i], dz = zs[i + 1] - zs[i], l = Math.hypot(dx, dz);
                if (l < 1e-6) continue;
                double qx = -dz / l, qz = dx / l, mx = 0.5 * (xs[i] + xs[i + 1]), mz = 0.5 * (zs[i] + zs[i + 1]);
                double[] a = {xs[i], zs[i], ys[i]}, c = {xs[i + 1], zs[i + 1], ys[i + 1]};
                for (int s = -1; s <= 1; s += 2) {          // Flanken
                    flaeche(art, new double[] {a[0] + s * qx * bu / 2, a[2], a[1] + s * qz * bu / 2, c[0] + s * qx * bu / 2, c[2], c[1] + s * qz * bu / 2,
                            c[0] + s * qx * bo / 2, c[2] + h, c[1] + s * qz * bo / 2, a[0] + s * qx * bo / 2, a[2] + h, a[1] + s * qz * bo / 2}, mx, mz);
                }
                flaeche(art, new double[] {a[0] - qx * bo / 2, a[2] + h, a[1] - qz * bo / 2, c[0] - qx * bo / 2, c[2] + h, c[1] - qz * bo / 2,
                        c[0] + qx * bo / 2, c[2] + h, c[1] + qz * bo / 2, a[0] + qx * bo / 2, a[2] + h, a[1] + qz * bo / 2}, mx, mz);
            }
        }

        /** Schließt das Gebäude in Arbeit ab. */
        public void fertig() {
            jetzt.scheitel = s.size() - jetzt.erster;
            if (jetzt.scheitel == 0) {
                g.remove(g.size() - 1);
            }
            jetzt = null;
        }

        public Bauten bauten(double e0, double n0) {
            float[] a = new float[s.size() * FLOATS];
            for (int i = 0; i < s.size(); i++) System.arraycopy(s.get(i), 0, a, i * FLOATS, FLOATS);
            return new Bauten(e0, n0, g, a);
        }

        public int scheitel() { return s.size(); }
    }
}
