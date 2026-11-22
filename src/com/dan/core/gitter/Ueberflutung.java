package com.dan.core.gitter;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Statistik der Überflutung je Zelle eines Gitters (P13b, Werkbuch HalligHooge Teil A, 12.14; F151 A): aus Wasserstand
 * und Tiefe je Kopplungsintervall der Scheitel jeder Tide (Fenster einer M2-Periode ab {@link #ab}), daraus
 * <ul>
 * <li><b>MThw</b>: Mittel der Scheitel, wo die Zelle in wenigstens {@link #mindestAnteil} der Tiden nass war; sonst
 * gefüllt aus den nächsten Zellen mit Wert (Ring um Ring, Mittel der Nachbarn) [G]: Das MThw ist eine Eigenschaft der
 * Wasserfläche und ändert sich über wenige hundert Meter kaum.</li>
 * <li><b>MSpThw, MNpThw</b>: 90- und 10-%-Wert der Scheitel (Spring- und Nippzeit) [G].</li>
 * <li><b>Dauer</b>: Anteil der Zeit, in der die Zelle nass ist; <b>Anteil</b>: Anteil der Tiden, in denen sie nass wird.</li>
 * </ul>
 * Kennt keine Bus und keine Grafik: Ein Modul (der Lauf) gibt die Stände.
 */
public final class Ueberflutung {
    public static final String VERSION = "0.1";
    /** Länge einer Tide (s): M2. */
    public static final double TIDE = 44714.16;
    public static final String TEXT = "ueberflutung.txt", DATEN = "ueberflutung.f32";
    public static final List<String> GROESSEN = List.of("mthw", "mspthw", "mnpthw", "dauer", "anteil", "gefuellt");

    public final Gitter gitter;
    public final double ab;
    /** Nass ab dieser Tiefe (m) [G]. */
    public double hNass = 0.05;
    /** Anteil der Tiden, in denen eine Zelle nass sein muss, damit ihr MThw zählt [G]. */
    public double mindestAnteil = 0.9;

    private final int nc;
    private final List<float[]> scheitel = new ArrayList<>();
    private float[] laufend;
    private int fenster = -1;
    private final double[] nassZeit;
    private double zeit;

    // Ergebnis
    public float[] mthw, mspthw, mnpthw, dauer, anteil;
    /** 1: MThw aus den Nachbarn gefüllt. */
    public float[] gefuellt;
    public int tiden;

    public Ueberflutung(Gitter gitter, double ab) {
        this.gitter = gitter;
        this.ab = ab;
        nc = gitter.zellen();
        nassZeit = new double[nc];
    }

    /** Ein Stand zur Zeit t (s) über die Dauer dt (s): Wasserstand (m NHN) und Tiefe (m) je Zelle. */
    public void stand(double t, double dt, float[] eta, float[] h) {
        if (t < ab) return;
        int w = (int) Math.floor((t - ab) / TIDE);
        if (w != fenster) {
            if (laufend != null) scheitel.add(laufend);
            laufend = new float[nc];
            Arrays.fill(laufend, Float.NaN);
            fenster = w;
        }
        for (int c = 0; c < nc; c++) {
            if (!(h[c] >= hNass)) continue;
            nassZeit[c] += dt;
            if (Float.isNaN(laufend[c]) || eta[c] > laufend[c]) laufend[c] = eta[c];
        }
        zeit += dt;
    }

    /** Schließt ab (die letzte Tide zählt nur, wenn sie voll ist) und rechnet die Größen. */
    public void abschliessen(double tEnde) {
        if (laufend != null && tEnde - ab >= (fenster + 1) * TIDE - 1) scheitel.add(laufend);
        laufend = null;
        tiden = scheitel.size();
        mthw = new float[nc]; mspthw = new float[nc]; mnpthw = new float[nc]; dauer = new float[nc]; anteil = new float[nc]; gefuellt = new float[nc];
        float[] werte = new float[tiden];
        for (int c = 0; c < nc; c++) {
            int k = 0;
            for (float[] s : scheitel) if (!Float.isNaN(s[c])) werte[k++] = s[c];
            anteil[c] = tiden == 0 ? 0 : (float) k / tiden;
            dauer[c] = zeit > 0 ? (float) (nassZeit[c] / zeit) : 0;
            if (tiden > 0 && k >= mindestAnteil * tiden) {
                double sum = 0;
                for (int q = 0; q < k; q++) sum += werte[q];
                float[] w = Arrays.copyOf(werte, k);
                Arrays.sort(w);
                mthw[c] = (float) (sum / k);
                mspthw[c] = w[Math.min(k - 1, (int) Math.floor(0.9 * (k - 1) + 0.5))];
                mnpthw[c] = w[(int) Math.floor(0.1 * (k - 1) + 0.5)];
            } else { mthw[c] = mspthw[c] = mnpthw[c] = Float.NaN; }
        }
        fuellen(mthw, mspthw, mnpthw);
    }

    /** Füllt NaN Ring um Ring mit dem Mittel der Nachbarn mit Wert (vier Nachbarn). */
    private void fuellen(float[]... f) {
        int nx = gitter.nx, ny = gitter.ny;
        boolean[] hat = new boolean[nc];
        ArrayDeque<Integer> rand = new ArrayDeque<>();
        for (int c = 0; c < nc; c++) hat[c] = !Float.isNaN(f[0][c]);
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int c = 0; c < nc; c++) if (!hat[c]) {
            int i = c % nx, j = c / nx;
            for (int[] d : nb) {
                int a = i + d[0], b = j + d[1];
                if (a >= 0 && b >= 0 && a < nx && b < ny && hat[a + nx * b]) { rand.add(c); break; }
            }
        }
        boolean[] drin = new boolean[nc];
        for (int c : rand) drin[c] = true;
        while (!rand.isEmpty()) {
            List<Integer> ring = new ArrayList<>(rand);
            rand.clear();
            float[][] neu = new float[f.length][ring.size()];
            for (int r = 0; r < ring.size(); r++) {
                int c = ring.get(r), i = c % nx, j = c / nx, k = 0;
                double[] s = new double[f.length];
                for (int[] d : nb) {
                    int a = i + d[0], b = j + d[1];
                    if (a < 0 || b < 0 || a >= nx || b >= ny || !hat[a + nx * b]) continue;
                    for (int q = 0; q < f.length; q++) s[q] += f[q][a + nx * b];
                    k++;
                }
                for (int q = 0; q < f.length; q++) neu[q][r] = (float) (s[q] / k);
            }
            for (int r = 0; r < ring.size(); r++) {
                int c = ring.get(r);
                for (int q = 0; q < f.length; q++) f[q][c] = neu[q][r];
                hat[c] = true;
                gefuellt[c] = 1;
            }
            for (int c : ring) {
                int i = c % nx, j = c / nx;
                for (int[] d : nb) {
                    int a = i + d[0], b = j + d[1];
                    if (a < 0 || b < 0 || a >= nx || b >= ny) continue;
                    int e = a + nx * b;
                    if (!hat[e] && !drin[e]) { drin[e] = true; rand.add(e); }
                }
            }
        }
    }

    /** Das Feld einer Größe aus {@link #GROESSEN}. */
    public float[] feld(String name) {
        return switch (name) { case "mthw" -> mthw; case "mspthw" -> mspthw; case "mnpthw" -> mnpthw; case "dauer" -> dauer; case "anteil" -> anteil; case "gefuellt" -> gefuellt; default -> null; };
    }

    /** Bilinear auf einen Ort in UTM (m); außerhalb der nächste Rand. */
    public double wert(float[] f, double ost, double nord) {
        double x = (ost - gitter.x0) / gitter.dx - 0.5, y = (nord - gitter.y0) / gitter.dy - 0.5;
        x = Math.max(0, Math.min(gitter.nx - 1, x));
        y = Math.max(0, Math.min(gitter.ny - 1, y));
        int i = Math.min(gitter.nx - 2, (int) Math.floor(x)), j = Math.min(gitter.ny - 2, (int) Math.floor(y));
        double fx = x - i, fy = y - j;
        int c = i + gitter.nx * j;
        return (1 - fy) * ((1 - fx) * f[c] + fx * f[c + 1]) + fy * ((1 - fx) * f[c + gitter.nx] + fx * f[c + gitter.nx + 1]);
    }

    /** Schreibt {@code ordner/ueberflutung.txt} und {@code .f32}; {@code kopf}: weitere Zeilen (Kommentare). */
    public void schreibe(File ordner, String kopf) throws IOException {
        Files.createDirectories(ordner.toPath());
        StringBuilder b = new StringBuilder();
        b.append("# Statistik der Überflutung (Werkbuch HalligHooge Teil A, 12.14, P13b; core.gitter.Ueberflutung ").append(VERSION).append(")\n");
        if (kopf != null) for (String z : kopf.split("\n")) b.append("# ").append(z).append('\n');
        b.append("# mthw, mspthw, mnpthw in m NHN; dauer, anteil 0–1; gefuellt 0/1; float32 Little Endian, je Größe alle Zellen (i + nx j)\n");
        b.append(String.format(Locale.ROOT, "gitter %s %.3f %.3f %.3f %.3f %d %d%n", gitter.name, gitter.x0, gitter.y0, gitter.dx, gitter.dy, gitter.nx, gitter.ny));
        b.append(String.format(Locale.ROOT, "zone %d%n", gitter.zone));
        b.append(String.format(Locale.ROOT, "tiden %d%nab %.1f%n", tiden, ab));
        b.append("groessen ").append(String.join(" ", GROESSEN)).append('\n');
        b.append("werte ").append(DATEN).append('\n');
        Files.writeString(new File(ordner, TEXT).toPath(), b.toString(), StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(4 * nc).order(ByteOrder.LITTLE_ENDIAN);
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(ordner, DATEN).toPath())))) {
            for (String g : GROESSEN) {
                bb.clear();
                for (float v : feld(g)) bb.putFloat(v);
                o.write(bb.array());
            }
        }
    }

    /** Liest, was {@link #schreibe} abgelegt hat; null, wenn es fehlt. */
    public static Ueberflutung lies(File ordner) throws IOException {
        File t = new File(ordner, TEXT);
        if (!t.isFile()) return null;
        Gitter g = null;
        String gname = null;
        double[] p = null;
        int zone = 32, tiden = 0;
        double ab = 0;
        for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8)) {
            String[] w = z.strip().split("\\s+");
            if (w.length == 0 || w[0].startsWith("#")) continue;
            switch (w[0]) {
                case "gitter" -> { gname = w[1]; p = new double[] {Double.parseDouble(w[2]), Double.parseDouble(w[3]), Double.parseDouble(w[4]), Double.parseDouble(w[5]), Double.parseDouble(w[6]), Double.parseDouble(w[7])}; }
                case "zone" -> zone = Integer.parseInt(w[1]);
                case "tiden" -> tiden = Integer.parseInt(w[1]);
                case "ab" -> ab = Double.parseDouble(w[1]);
                default -> { }
            }
        }
        if (p == null) throw new IOException(t + ": gitter fehlt");
        g = Gitter.utm(gname, zone, p[0], p[1], p[2], (int) p[4], (int) p[5]);
        Ueberflutung u = new Ueberflutung(g, ab);
        u.tiden = tiden;
        int nc = g.zellen();
        float[][] f = new float[GROESSEN.size()][nc];
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(new File(ordner, DATEN).toPath())))) {
            byte[] buf = new byte[4 * nc];
            for (float[] x : f) {
                in.readFully(buf);
                ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
                for (int c = 0; c < nc; c++) x[c] = bb.getFloat();
            }
        }
        u.mthw = f[0]; u.mspthw = f[1]; u.mnpthw = f[2]; u.dauer = f[3]; u.anteil = f[4]; u.gefuellt = f[5];
        return u;
    }
}
