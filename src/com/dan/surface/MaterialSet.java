package com.dan.surface;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Die gebackenen Materialien eines Ordners (etwa {@code daten/texturen}), in der Reihenfolge einer Materialliste: jede
 * vorhandene wird eine Schicht im Texturfeld der Grafikseite. Ohne Grafikkarte nutzbar (Mittelwerte, Budget,
 * Beschreibung).
 */
public final class MaterialSet {
    /** Eine Schicht: Material, Ordner, Kantenlänge, Mittel der Farbe (linear) und der Rauheit. */
    public record Layer(int index, Material material, File dir, int size, double[] meanColor, double meanRough) { }

    public final File dir;
    private final List<Layer> layers = new ArrayList<>();
    private final List<String> missing = new ArrayList<>();

    /** Liest die Ordner dir/&lt;id&gt; mit {@code material.txt} in der Reihenfolge von list; fehlende werden vermerkt. */
    public MaterialSet(File dir, List<Material> list) throws IOException {
        this.dir = dir;
        for (Material m : list) {
            File d = new File(dir, m.id), t = new File(d, "material.txt");
            if (!t.isFile() || !new File(d, "a.png").isFile()) { missing.add(m.id); continue; }
            Properties p = new Properties();
            try (var r = Files.newBufferedReader(t.toPath(), StandardCharsets.UTF_8)) { p.load(r); }
            String[] c = p.getProperty("farbe_mittel", "0.2 0.2 0.2").trim().split("\\s+");
            layers.add(new Layer(layers.size(), m, d, Integer.parseInt(p.getProperty("kante", "0").trim()),
                    new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])},
                    Double.parseDouble(p.getProperty("rauheit_mittel", "0.8").trim())));
        }
    }

    public List<Layer> layers() { return layers; }
    public List<String> missing() { return missing; }

    /** Schicht des Materials id oder −1. */
    public int layer(String id) {
        for (Layer l : layers) if (l.material.id.equals(id)) return l.index;
        return -1;
    }

    /**
     * Speicher auf der Grafikkarte (Byte) bei Kantenlänge edge mit Mip-Stufen: Farbe+Höhe BC7 (1 Byte je Bildpunkt),
     * Normale BC5 (1), Rauheit+Verdeckung BC5 (1); ungepackt RGBA8 + RG8 + RG8 (8).
     */
    public long vram(int edge, boolean compressed) {
        long px = (long) edge * edge * layers.size();
        return (long) (px * (compressed ? 3 : 8) * 4.0 / 3.0);
    }

    /** „12 von 14 Materialien gebacken (2048²), fehlen: seetang, kutter_tau“ */
    public String describe() {
        int e = layers.isEmpty() ? 0 : layers.get(0).size;
        return String.format(Locale.GERMAN, "%d von %d Materialien gebacken (%d²)%s", layers.size(), layers.size() + missing.size(), e,
                missing.isEmpty() ? "" : ", fehlen: " + String.join(", ", missing));
    }
}
