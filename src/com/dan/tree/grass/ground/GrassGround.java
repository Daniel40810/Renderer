package com.dan.tree.grass.ground;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Bodentextur „Mat 025 grass“ (Rasen von oben, nahtlos), als Boden unter den Gras-Demos. Das Bild ist
 * 1263 × 842 Punkte groß; eine Kachel deckt {@link #TILE_X} × {@link #TILE_Z} Meter.
 */
public final class GrassGround {

    private GrassGround() {}

    public static final String RESOURCE = "mat025_grass.jpg";
    public static final float TILE_X = 1.8f, TILE_Z = 1.2f;

    private static BufferedImage image;

    /** Farbbild (sRGB), einmal geladen. */
    public static synchronized BufferedImage texture() {
        if (image == null) {
            try (InputStream in = GrassGround.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException("Ressource fehlt: " + RESOURCE);
                image = ImageIO.read(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return image;
    }
}
