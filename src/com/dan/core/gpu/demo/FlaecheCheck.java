package com.dan.core.gpu.demo;

import com.dan.core.Core;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Flaeche;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der {@link Flaeche} in einem AWT-Fenster (P6a, Werkbuch HalligHooge Teil A, 12.7): Fläche aus dem Canvas,
 * Bilder zeigen, Größe ändern (Neuaufbau der Swapchain). Unter Linux mit Xvfb und lavapipe prüfbar, unter Windows auf
 * der 680M. Jedes Bild wird mit einer anderen Farbe gelöscht (ohne Zeichner); gemessen werden Bilder je Sekunde.
 */
public final class FlaecheCheck {
    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        int bilder = 240;
        Attest A = new Attest("com.dan.core.gpu", Core.VERSION);
        System.out.println("FlaecheCheck – com.dan.core.gpu " + Core.VERSION + " (Werkbuch HalligHooge Teil A, P6a)");
        A.teil("Fläche im Fenster");
        Frame[] fenster = new Frame[1];
        Canvas canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(640, 360));
        EventQueue.invokeAndWait(() -> {
            fenster[0] = new Frame("FlaecheCheck");
            fenster[0].add(canvas);
            fenster[0].pack();
            fenster[0].setVisible(true);
        });
        Thread.sleep(500);
        Geraet.Optionen o = new Geraet.Optionen();
        o.flaeche = true;
        o.strahlen = false;
        try (Geraet g = new Geraet(o); Flaeche fl = new Flaeche(g, canvas, true)) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            int format = fl.format();
            A.pruefe(format != 0, "Fläche aus dem Fenster-Handle des Canvas (JAWT), Swapchain", f("%d × %d, Format %d, %s", fl.breite(), fl.hoehe(), format, fl.modus()), "angelegt");
            long t0 = System.nanoTime();
            int gezeigt = 0, leer = 0;
            for (int i = 0; i < bilder; i++) {
                if (i == bilders(bilder)) {
                    EventQueue.invokeAndWait(() -> fenster[0].setSize(fenster[0].getWidth() + 120, fenster[0].getHeight() + 60));
                    Thread.sleep(200);
                    fl.neuBauen();
                }
                float t = (float) i / bilder;
                boolean ok = fl.bild(null, 0, (c, ziel) -> new Ablauf(g, g.grafik)
                        .zeichne("löschen", new Bild[] {ziel}, new float[][] {{t, 0.4f, 1 - t, 1}}, null, null, null, x -> { }).aufnehmen(c, null));
                if (ok) gezeigt++; else leer++;
            }
            fl.leiste().warte(fl.leiste().vergeben(), Long.MAX_VALUE);
            double s = (System.nanoTime() - t0) / 1e9;
            A.pruefe(gezeigt + leer == bilder && gezeigt >= bilder - 2, "Bilder gezeigt", f("%d gezeigt, %d ohne Bild (Neuaufbau), %.1f Bilder/s (%s)", gezeigt, leer, gezeigt / s, fl.modus()),
                    "alle bis auf den Neuaufbau");
            A.pruefe(fl.neuGebaut() >= 1, "Größe geändert: Swapchain neu gebaut", f("%d× neu, jetzt %d × %d", fl.neuGebaut(), fl.breite(), fl.hoehe()), "≥ 1");
            A.pruefe(g.validierungsFehler == 0, "Keine Meldung der Validierung", g.validierung ? g.validierungsFehler + " Fehler" : "nicht eingeschaltet", "0");
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            EventQueue.invokeAndWait(() -> fenster[0].dispose());
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P6_Flaeche.md"), "P6a – FlaecheCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    private static int bilders(int n) { return n / 2; }
}
