package com.dan.gertsch.demo;

import com.dan.gertsch.Dickicht;
import com.dan.gertsch.Druck;
import com.dan.gertsch.Kamera;
import com.dan.gertsch.Lab;
import com.dan.gertsch.Licht;
import com.dan.gertsch.Messung;
import com.dan.gertsch.Stich;
import com.dan.gertsch.Vorlage;
import com.dan.gertsch.Vorlagen;
import com.dan.gertsch.gl.GertschGl;
import com.dan.gertsch.gl.StichGl;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.util.Locale;
import java.util.SplittableRandom;
import java.util.function.Consumer;

/**
 * Prüfung von com.dan.gertsch.gl auf der Grafikkarte (OpenGL 4.3, ohne Fenster). Endet mit „Alles in Ordnung.“ oder
 * mit der Zahl der Verstöße (Rückgabewert 1):
 * <ul>
 * <li>Prozessor und Grafikkarte bauen aus dem seed dieselben Blätter ({@code pruefen.comp} gegen
 * {@link Dickicht.Zweig#blatt}).</li>
 * <li>Die Listen der Auswahl laufen in keiner Qualität über.</li>
 * <li>Die Hysterese senkt im Wind die Wechsel der Bänder.</li>
 * <li>Abnahme: ein Standbild in HIGH, gemessen gegen „Sommer“ (weitere Toleranzen als auf dem Prozessor, siehe README).</li>
 * <li>Phase 4: Holzschnitt ({@link StichGl}) gleich dem Prozessor ({@link Stich}); Trägheit und Hysterese senken im Wind
 * die Wechsel der Punkte; Abnahme des Drucks gegen den Holzschnitt.</li>
 * </ul>
 */
public final class GertschGlCheck implements GLEventListener {
    private static int bad;
    final GertschGl gg = new GertschGl();
    final StichGl sg = new StichGl();
    /** Je Bild auch den Holzschnitt des Lichts drucken (Phase 4). */
    boolean mitStich;
    final Kamera kamera = new Kamera();
    Dickicht dickicht;
    Licht licht;
    boolean geladen;
    double zeit;
    Consumer<GL4> nachher;

    @Override public void init(GLAutoDrawable d) {
        gg.init(d.getGL().getGL4());
        sg.init(d.getGL().getGL4());
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!geladen) { gg.laden(gl, dickicht, licht); geladen = true; }
        gg.zeichnen(gl, kamera, zeit, gl.getDefaultDrawFramebuffer(), 0, 0, 64, 64);
        gg.statistik(gl);
        if (mitStich) {
            sg.drucken(gl, gg.helligkeitTextur(), gg.bildBreite(), gg.bildHoehe(), StichGl.Eingang.HELLIGKEIT, Druck.BREITE, zeit);
            sg.statistik(gl);
        }
        if (nachher != null) { nachher.accept(gl); nachher = null; }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) {
        sg.dispose(d.getGL().getGL4());
        gg.dispose(d.getGL().getGL4());
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.GERMAN);
        GertschGlCheck c = new GertschGlCheck();
        c.dickicht = Dickicht.sommer().bauen();
        c.licht = new Licht().vorbereiten(c.dickicht);
        GLProfile p = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(p);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.addGLEventListener(c);

        // 1 dieselben Blätter auf Prozessor und Grafikkarte
        int n = 4000;
        int[] zw = new int[n], bl = new int[n];
        SplittableRandom r = new SplittableRandom(5);
        for (int k = 0; k < n; k++) {
            zw[k] = k < 50 ? c.dickicht.zweige.size() - 1 - k : r.nextInt(c.dickicht.zweige.size());   // die letzten sind Streu
            bl[k] = r.nextInt(c.dickicht.zweige.get(zw[k]).blaetter);
        }
        float[][] gpu = new float[1][];
        c.gg.quality = GertschGl.Quality.LOW;
        c.nachher = gl -> gpu[0] = c.gg.blaetterPruefen(gl, zw, bl);
        dr.display();
        double dp = 0, dn = 0, dl = 0;
        int zufallGleich = 0;
        Dickicht.Blatt b = new Dickicht.Blatt();
        for (int k = 0; k < n; k++) {
            c.dickicht.zweige.get(zw[k]).blatt(bl[k], b);
            float[] g = gpu[0];
            int o = 12 * k;
            dp = Math.max(dp, Math.max(Math.abs(g[o] - b.x), Math.max(Math.abs(g[o + 1] - b.y), Math.abs(g[o + 2] - b.z))));
            dn = Math.max(dn, 1 - (g[o + 3] * b.nx + g[o + 4] * b.ny + g[o + 5] * b.nz));
            dn = Math.max(dn, 1 - (g[o + 6] * b.ax + g[o + 7] * b.ay + g[o + 8] * b.az));
            dl = Math.max(dl, Math.abs(g[o + 9] - b.laenge) / b.laenge);
            if (g[o + 11] == b.zufall) zufallGleich++;
        }
        check(dp < 1e-4 && dn < 1e-5 && dl < 1e-5 && zufallGleich == n,
                "Grafikkarte und Prozessor bauen dieselben Blätter: Lage höchstens %.1g m daneben, Richtung 1 − cos %.1g, Länge %.1g, Zufall %d von %d gleich",
                dp, dn, dl, zufallGleich, n);

        // 2 Listen laufen nicht über
        for (GertschGl.Quality q : GertschGl.Quality.values()) {
            c.gg.quality = q;
            c.gg.wind.staerke = 0;
            for (int i = 0; i < 3; i++) dr.display();
            check(c.gg.gewollt == c.gg.instanzen && c.gg.schattenGewollt == c.gg.schattenInstanzen && c.gg.instanzen > 0,
                    "%s: %,d Flecken, %,d Werfer im Schatten, keine Liste zu klein", q, c.gg.instanzen, c.gg.schattenInstanzen);
        }

        // 3 Hysterese im Wind
        c.gg.quality = GertschGl.Quality.LOW;
        c.gg.wind.staerke = 6;
        double[] flimmern = new double[2];
        double[] h = {0, 0.3};
        for (int j = 0; j < 2; j++) {
            c.gg.hysterese = h[j];
            double s = 0;
            int m = 0;
            for (int i = 0; i < 40; i++) {
                c.zeit = 20 + i / 24.0;
                dr.display();
                if (i >= 8) { s += c.gg.flimmern(); m++; }
            }
            flimmern[j] = s / m;
        }
        check(flimmern[0] > 0.005 && flimmern[1] < 0.5 * flimmern[0],
                "Hysterese bei 6 m/s: %.2f %% der Flecken wechseln je Bild das Band ohne, %.2f %% mit 0,3 Blenden", 100 * flimmern[0], 100 * flimmern[1]);

        // 4 Abnahme gegen „Sommer“: Standbild in HIGH ohne Wind
        c.gg.quality = GertschGl.Quality.HIGH;
        c.gg.wind.staerke = 0;
        c.gg.hysterese = 0.3;
        c.zeit = 0;
        java.awt.image.BufferedImage[] bild = new java.awt.image.BufferedImage[1];
        for (int i = 0; i < 6; i++) dr.display();
        c.nachher = gl -> bild[0] = c.gg.lesen(gl);
        dr.display();
        Vorlage ist = new Messung().messen(bild[0]), soll = Vorlagen.sommer();
        double rel = ist.hellD50 / soll.hellD50, reld = ist.dunkelD50 / soll.dunkelD50, haupt = Math.min(ist.hauptrichtung, 180 - ist.hauptrichtung);
        check(Math.abs(ist.l50 - soll.l50) < 5 && Math.abs(ist.grund - soll.grund) < 0.08,
                "Sommer (OpenGL), Helligkeit: Median %.1f (Soll %.1f ± 5), Grund %.1f %% (Soll %.1f %% ± 8)", ist.l50, soll.l50, 100 * ist.grund, 100 * soll.grund);
        check(rel > 0.7 && rel < 1.35 && reld > 0.7 && reld < 1.4,
                "Sommer (OpenGL), Flecken: hell %.2f %% (%+.0f %%), dunkel %.2f %% (%+.0f %%)", 100 * ist.hellD50, 100 * (rel - 1), 100 * ist.dunkelD50, 100 * (reld - 1));
        check(haupt < 25 && ist.akzent > 0.001 && ist.akzent < 0.015,
                "Sommer (OpenGL), Richtung waagerecht (%.0f°, %.0f %% senkrecht), Akzente %.2f %%", ist.hauptrichtung, 100 * ist.senkrecht, 100 * ist.akzent);


        // 5 Holzschnitt: Grafikkarte und Prozessor drucken dasselbe (ohne Hysterese, mit derselben Tabelle des Tons)
        c.gg.quality = GertschGl.Quality.MEDIUM;
        c.gg.wind.staerke = 0;
        c.zeit = 0;
        java.awt.image.BufferedImage[] quelle = new java.awt.image.BufferedImage[1], gpuDruck = new java.awt.image.BufferedImage[1];
        float[][] tonKurve = new float[1][];
        int[] gpuGestochen = new int[1];
        c.nachher = gl -> {
            quelle[0] = c.gg.lesen(gl);
            int tex = StichGl.textur(gl, quelle[0]);
            c.sg.hysterese = 0;
            c.sg.neu();
            c.sg.drucken(gl, tex, quelle[0].getWidth(), quelle[0].getHeight(), StichGl.Eingang.FARBE, Druck.BREITE);
            c.sg.statistik(gl);
            gpuGestochen[0] = c.sg.gestochen;
            gpuDruck[0] = c.sg.lesen(gl);
            tonKurve[0] = c.sg.tonKurve();
            gl.glDeleteTextures(1, new int[]{tex}, 0);
        };
        dr.display();
        Stich.Ergebnis cpu = new Stich().stechen(Stich.helligkeit(quelle[0]), quelle[0].getWidth(), quelle[0].getHeight(), Druck.BREITE, tonKurve[0]);
        int nahBp = 0, bp = cpu.breite * cpu.hoehe;
        double dL = 0;
        for (int y = 0; y < cpu.hoehe; y++)
            for (int x = 0; x < cpu.breite; x++) {
                double[] a = Lab.vonRgb(cpu.bild.getRGB(x, y)), g = Lab.vonRgb(gpuDruck[0].getRGB(x, y));
                if (Lab.deltaE(a, g) < 3) nahBp++;
                dL += Math.abs(a[0] - g[0]);
            }
        check(Math.abs(gpuGestochen[0] - cpu.gestochen) <= Math.max(5, cpu.gestochen / 2000) && nahBp >= 0.999 * bp && dL / bp < 0.1,
                "Holzschnitt: Grafikkarte und Prozessor stechen dieselben Punkte (%,d und %,d), %.3f %% der Bildpunkte innerhalb ΔE 3, |ΔL*| im Mittel %.3f",
                gpuGestochen[0], cpu.gestochen, 100.0 * nahBp / bp, dL / bp);

        // 6 Holzschnitt im Wind: Trägheit und Hysterese halten die Punkte ruhig
        c.gg.quality = GertschGl.Quality.LOW;
        c.gg.wind.staerke = 6;
        c.mitStich = true;
        double[] wechsel = new double[2];
        double[][] ruhe = {{0, 0}, {0.3, 0.15}};
        for (int j = 0; j < 2; j++) {
            c.sg.hysterese = ruhe[j][0];
            c.sg.traegheit = ruhe[j][1];
            c.sg.neu();
            double s = 0;
            int m = 0;
            for (int i = 0; i < 40; i++) {
                c.zeit = 20 + i / 24.0;
                dr.display();
                if (i >= 8) { s += c.sg.flimmern(); m++; }
            }
            wechsel[j] = s / m;
        }
        check(wechsel[0] > 0.02 && wechsel[1] < wechsel[0] / 3,
                "Holzschnitt bei 6 m/s: %.1f %% der gestochenen Punkte wechseln je Bild ohne, %.1f %% mit Trägheit 0,15 s und Hysterese 0,3 Blenden",
                100 * wechsel[0], 100 * wechsel[1]);

        // 7 Abnahme gegen den Holzschnitt: das Licht des Dickichts (HIGH, ohne Wind) als Holzschnitt auf der Grafikkarte
        c.gg.quality = GertschGl.Quality.HIGH;
        c.gg.wind.staerke = 0;
        c.sg.hysterese = 0.3;
        c.sg.traegheit = 0.15;
        c.sg.neu();
        c.zeit = 0;
        for (int i = 0; i < 6; i++) dr.display();
        c.nachher = gl -> gpuDruck[0] = c.sg.lesen(gl);
        dr.display();
        c.mitStich = false;
        Druck dg = Druck.messen(gpuDruck[0]);
        GertschCheck.abnahmeDruck("Holzschnitt (OpenGL)", dg, Vorlagen.druck(), 0.15, GertschGlCheck::check);

        dr.destroy();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what, Object... args) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + String.format(Locale.GERMAN, what, args));
    }
}
