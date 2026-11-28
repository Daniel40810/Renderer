package com.dan.core.gitter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Wetter auf einem {@link Gradraster} in festem Zeitschritt (Werkbuch HalligHooge Teil A, 7.2 und 12.6, P5b): ERA5
 * ausgeschnitten auf G0 und etwas darüber hinaus, stündlich, mit den Größen {@code u10}, {@code v10} (m/s, Wind in 10 m,
 * nach Osten und Norden) und {@code msl} (Pa, Luftdruck auf Meereshöhe), seit P5e (F34) wahlweise dazu {@code iews},
 * {@code inss} (N/m², Schubspannung an der Oberfläche nach Osten und Norden) und {@code charnock} (–). Zwischen zwei
 * Zeiten linear, im Ort bilinear.
 * <p>
 * Abgelegt im Gebiet unter {@code antrieb/}: {@code era5.txt} (Text, UTF-8: {@code raster …} nach
 * {@link Gradraster#zeile()}, {@code zeit <Start, ISO 8601 UTC> <Schritt s> <Anzahl>}, {@code groessen <Namen>},
 * {@code werte <Datei>}; andere Zeilen für Menschen) und die Werte als float32 Little Endian, je Zeit je Größe alle Punkte
 * (j · nx + i). Gelesen wird über eine Abbildung der Datei (kein Platz im Speicher von Java).
 * <p>
 * <b>Zeiträume (P13a):</b> Ein weiteres Ereignis (etwa die Sturmflut Xaver im Dezember 2013) liegt neben dem Monat in
 * {@code antrieb/} in einem Unterordner {@code antrieb/<Zeitraum>/} (Vorgabe der Aufbereitung: {@code JJJJMM}) mit
 * denselben Dateien. {@link #lies(File, String, Instant)} nimmt den Ordner, dessen Zeiten den gefragten Zeitpunkt
 * enthalten, zuerst {@code antrieb/} selbst.
 */
public final class Wetterfeld {
    public static final String ORDNER = "antrieb", TEXT = "era5.txt", DATEN = "era5.f32";
    /** Name des Seegangs aus ERA5 im selben Ordner (P8b): {@code seegang.txt}, {@code seegang.f32}. */
    public static final String SEEGANG = "seegang";
    /**
     * Name von Luft, Strahlung und Wassertemperatur aus ERA5 im selben Ordner (P10b): {@code luft.txt}, {@code luft.f32},
     * auf einem eigenen Raster (Deutsche Bucht). Größen: {@code t2}, {@code d2}, {@code sst} (K), {@code ssrd},
     * {@code strd}, {@code sshf}, {@code slhf} (W/m², Mittel der Stunde vor der Zeit, nach unten positiv), {@code tcc}
     * (0–1), {@code tp} (m je Stunde).
     */
    public static final String LUFT = "luft";
    /**
     * Name der Böe aus ERA5 im selben Ordner (Seegang S8b, SF156 A): {@code boee.txt}, {@code boee.f32}, Raster der
     * Deutschen Bucht. Größe: {@code fg10} (m/s, stärkste Böe in 10 m in der Stunde vor der Zeit).
     */
    public static final String BOEE = "boee";

    public final Gradraster raster;
    public final Instant start;
    public final int schritt, zeiten;
    public final List<String> groessen;
    private final FloatBuffer werte;

    private Wetterfeld(Gradraster raster, Instant start, int schritt, int zeiten, List<String> groessen, FloatBuffer werte) {
        this.raster = raster;
        this.start = start;
        this.schritt = schritt;
        this.zeiten = zeiten;
        this.groessen = List.copyOf(groessen);
        this.werte = werte;
    }

    /** Index der Größe oder −1. */
    public int groesse(String name) { return groessen.indexOf(name); }

    public Instant zeit(int k) { return start.plusSeconds((long) k * schritt); }

    public Instant ende() { return zeit(zeiten - 1); }

    /** Ein Feld (alle Punkte) einer Größe zu einer Zeit, kopiert. */
    public float[] feld(int zeit, int groesse) {
        float[] f = new float[raster.punkte()];
        werte.get((int) versatz(zeit, groesse), f);
        return f;
    }

    private long versatz(int zeit, int groesse) { return ((long) zeit * groessen.size() + groesse) * raster.punkte(); }

    /** Wert einer Größe am Ort (Gewichte aus {@link Gradraster#gewichte}) zur Zeit t; außerhalb der Zeiten NaN. */
    public double wert(int groesse, Instant t, Gradraster.Gewichte g) {
        double s = (t.getEpochSecond() - start.getEpochSecond() + t.getNano() * 1e-9) / schritt;
        if (s < -1e-9 || s > zeiten - 1 + 1e-9) return Double.NaN;
        int k = Math.min(zeiten - 2, Math.max(0, (int) Math.floor(s)));
        if (zeiten == 1) return punkt(0, groesse, g);
        double w = Math.max(0, Math.min(1, s - k));
        return (1 - w) * punkt(k, groesse, g) + w * punkt(k + 1, groesse, g);
    }

    private double punkt(int zeit, int groesse, Gradraster.Gewichte g) {
        long o = versatz(zeit, groesse);
        double s = 0;
        for (int k = 0; k < g.index().length; k++) s += g.gewicht()[k] * werte.get((int) (o + g.index()[k]));
        return s;
    }

    // ------------------------------------------------------------------ Ablage

    /** Liefert das Feld einer Größe zu einer Zeit beim Schreiben. */
    public interface Quelle {
        float[] feld(int zeit, int groesse) throws IOException;
    }

    /** Schreibt {@code antrieb/era5.txt} und {@code antrieb/era5.f32}; {@code kopf}: weitere Zeilen (Quellen). */
    public static void schreibe(File gebiet, Gradraster raster, Instant start, int schritt, int zeiten, List<String> groessen, Quelle q, String kopf) throws IOException {
        schreibe(gebiet, "era5", raster, start, schritt, zeiten, groessen, q, kopf);
    }

    /** Wie oben unter einem Namen ({@code era5}, {@link #SEEGANG}): {@code antrieb/<name>.txt} und {@code .f32}. */
    public static void schreibe(File gebiet, String name, Gradraster raster, Instant start, int schritt, int zeiten, List<String> groessen, Quelle q, String kopf) throws IOException {
        schreibeIn(gebiet, null, name, raster, start, schritt, zeiten, groessen, q, kopf);
    }

    /** Ordner eines Zeitraums relativ zum Gebiet (P13a): {@code antrieb} für {@code null}, sonst {@code antrieb/<zeitraum>}. */
    public static String ordner(String zeitraum) {
        if (zeitraum == null || zeitraum.isBlank()) return ORDNER;
        if (!zeitraum.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Zeitraum " + zeitraum + ": nur Buchstaben, Ziffern, _ und -");
        return ORDNER + File.separator + zeitraum;
    }

    /** Wie {@link #schreibe(File, String, Gradraster, Instant, int, int, List, Quelle, String)} in den Ordner eines Zeitraums ({@link #ordner}). */
    public static void schreibeIn(File gebiet, String zeitraum, String name, Gradraster raster, Instant start, int schritt, int zeiten, List<String> groessen, Quelle q, String kopf) throws IOException {
        File o = new File(gebiet, ordner(zeitraum));
        Files.createDirectories(o.toPath());
        String DATEN = name + ".f32", TEXT = name + ".txt";
        File d = new File(o, DATEN), dt = new File(o, DATEN + ".neu");
        ByteBuffer b = ByteBuffer.allocate(4 * raster.punkte()).order(ByteOrder.LITTLE_ENDIAN);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dt), 1 << 20)) {
            for (int z = 0; z < zeiten; z++)
                for (int k = 0; k < groessen.size(); k++) {
                    float[] f = q.feld(z, k);
                    if (f.length != raster.punkte()) throw new IOException("Wetterfeld: " + f.length + " Werte statt " + raster.punkte());
                    b.clear();
                    for (float x : f) b.putFloat(x);
                    out.write(b.array(), 0, b.position());
                }
        }
        Files.move(dt.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING);
        StringBuilder s = new StringBuilder();
        s.append("# Wetter auf einem Gradraster (Werkbuch HalligHooge Teil A, 12.6, P5b), Format in com.dan.core.gitter.Wetterfeld\n");
        if (name.equals(SEEGANG)) s.append("# Seegang aus ERA5 (P8b): Höhen in m, Perioden in s, Richtungen in Grad (woher, von Nord im Uhrzeigersinn), Richtungsbreiten ohne Einheit; über Land aus den Nachbarn über See\n");
        else if (name.equals(BOEE)) s.append("# Böe aus ERA5 (Seegang S8b): fg10 in m/s, stärkste Böe in 10 m in der Stunde vor der Zeit\n");
        else if (name.equals(LUFT)) s.append("# Luft aus ERA5 (P10b): t2, d2, sst in K; ssrd, strd, sshf, slhf in W/m² (Mittel der Stunde vor der Zeit, nach unten positiv); tcc 0–1; tp in m je Stunde; sst über Land aus den Nachbarn über See\n");
        else s.append("# u10, v10 in m/s (nach Osten, Norden), msl in Pa; iews, inss in N/m² (nach Osten, Norden), charnock ohne Einheit (wo vorhanden);\n");
        s.append("# float32 Little Endian, je Zeit je Größe alle Punkte (j nx + i)\n");
        if (kopf != null && !kopf.isBlank()) s.append(kopf.strip()).append('\n');
        s.append(raster.zeile()).append('\n');
        s.append("zeit ").append(start).append(' ').append(schritt).append(' ').append(zeiten).append('\n');
        s.append("groessen ").append(String.join(" ", groessen)).append('\n');
        s.append("werte ").append(DATEN).append('\n');
        File t = new File(o, TEXT), tt = new File(o, TEXT + ".neu");
        Files.writeString(tt.toPath(), s, StandardCharsets.UTF_8);
        Files.move(tt.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Liegt ein Wetterfeld im Gebiet? */
    public static boolean da(File gebiet) { return da(gebiet, "era5"); }

    /** Liegt das Wetterfeld dieses Namens im Gebiet? */
    public static boolean da(File gebiet, String name) { return new File(new File(gebiet, ORDNER), name + ".txt").isFile(); }

    /** Liest {@code antrieb/era5.txt} und bildet die Werte ab. */
    public static Wetterfeld lies(File gebiet) throws IOException { return lies(gebiet, "era5"); }

    /** Liest {@code antrieb/<name>.txt} und bildet die Werte ab. */
    public static Wetterfeld lies(File gebiet, String name) throws IOException { return liesAus(new File(gebiet, ORDNER), name); }

    /**
     * Liest das Wetterfeld dieses Namens, dessen Zeiten {@code zeit} enthalten (P13a): zuerst {@code antrieb/}, dann die
     * Unterordner der Zeiträume nach Namen. Keiner passt: {@link IOException} mit den vorhandenen Zeiten.
     */
    public static Wetterfeld lies(File gebiet, String name, Instant zeit) throws IOException {
        File o = ordnerFuer(gebiet, name, zeit);
        if (o == null) throw new IOException("Wetterfeld " + name + ": keine Zeiten um " + zeit + " im Gebiet (" + zeiten(gebiet, name)
                + ") – Aufbereitung mit --schritte antrieb,seegang --antrieb.zeitraum JJJJMM");
        return liesAus(o, name);
    }

    /** Liegt ein Wetterfeld dieses Namens mit {@code zeit} im Gebiet (P13a)? */
    public static boolean da(File gebiet, String name, Instant zeit) { return ordnerFuer(gebiet, name, zeit) != null; }

    /** Der Ordner ({@code antrieb} oder ein Zeitraum darin), dessen {@code <name>.txt} die Zeit enthält; sonst null. */
    public static File ordnerFuer(File gebiet, String name, Instant zeit) {
        for (File o : kandidaten(gebiet)) {
            Instant[] z = zeitenIn(o, name);
            if (z != null && !zeit.isBefore(z[0]) && !zeit.isAfter(z[1])) return o;
        }
        return null;
    }

    /** Die vorhandenen Zeiten des Namens, je Ordner „Ordner: Start bis Ende“, für Meldungen. */
    public static String zeiten(File gebiet, String name) {
        List<String> t = new ArrayList<>();
        for (File o : kandidaten(gebiet)) {
            Instant[] z = zeitenIn(o, name);
            if (z != null) t.add(gebiet.toPath().relativize(o.toPath()) + ": " + z[0] + " bis " + z[1]);
        }
        return t.isEmpty() ? "keine" : String.join("; ", t);
    }

    private static List<File> kandidaten(File gebiet) {
        File o = new File(gebiet, ORDNER);
        List<File> k = new ArrayList<>();
        k.add(o);
        File[] u = o.listFiles(File::isDirectory);
        if (u != null) {
            java.util.Arrays.sort(u);
            k.addAll(List.of(u));
        }
        return k;
    }

    /** Start und Ende aus {@code <name>.txt} im Ordner, ohne die Werte zu lesen; null, wenn es fehlt oder unlesbar ist. */
    private static Instant[] zeitenIn(File o, String name) {
        File t = new File(o, name + ".txt");
        if (!t.isFile()) return null;
        try {
            for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8)) {
                String[] w = z.strip().split("\\s+");
                if (w.length >= 4 && w[0].equals("zeit")) {
                    Instant a = Instant.parse(w[1]);
                    return new Instant[] {a, a.plusSeconds((long) Integer.parseInt(w[2]) * (Integer.parseInt(w[3]) - 1))};
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Wetterfeld liesAus(File o, String name) throws IOException {
        String TEXT = name + ".txt", DATEN = name + ".f32";
        Gradraster r = null;
        Instant start = null;
        int schritt = 0, zeiten = 0;
        List<String> gr = new ArrayList<>();
        String datei = DATEN;
        for (String z : Files.readAllLines(new File(o, TEXT).toPath(), StandardCharsets.UTF_8)) {
            String s = z.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            switch (t[0]) {
                case "raster" -> r = Gradraster.aus(s);
                case "zeit" -> {
                    start = Instant.parse(t[1]);
                    schritt = Integer.parseInt(t[2]);
                    zeiten = Integer.parseInt(t[3]);
                }
                case "groessen" -> gr.addAll(List.of(t).subList(1, t.length));
                case "werte" -> datei = t[1];
                default -> { }
            }
        }
        if (r == null || start == null || gr.isEmpty() || schritt <= 0 || zeiten <= 0) throw new IOException(new File(o, TEXT) + ": raster, zeit oder groessen fehlt");
        long n = (long) zeiten * gr.size() * r.punkte();
        if (4 * n > Integer.MAX_VALUE) throw new IOException(TEXT + ": " + 4 * n + " Byte, mehr als eine Abbildung fasst (2 GB); den Zeitraum teilen");
        try (RandomAccessFile f = new RandomAccessFile(new File(o, datei), "r"); FileChannel c = f.getChannel()) {
            if (c.size() != 4 * n) throw new IOException(datei + ": " + c.size() + " Byte statt " + 4 * n);
            FloatBuffer w = c.map(FileChannel.MapMode.READ_ONLY, 0, 4 * n).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            return new Wetterfeld(r, start, schritt, zeiten, gr, w);
        }
    }
}
