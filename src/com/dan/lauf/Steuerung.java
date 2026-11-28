package com.dan.lauf;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Blick;

import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Instant;

/**
 * Die Kamera mit Maus und Tasten (Werkbuch HalligHooge Teil B, B.3; aus {@code lauf.demo.BildDemo} herausgezogen):
 * Ziehen mit der Maus dreht und neigt, das Rad ändert den Bildwinkel; W A S D bewegen (mit Umschalt zehnmal
 * schneller), Q und E heben und senken. Die Geschwindigkeit wächst mit der Höhe über dem Boden. Seit S3c-5 (SF29 A)
 * bleibt das Auge über dem Wasser ({@link #wasser}).
 * <p>
 * Die Ereignisse kommen im Faden der Oberfläche, {@link #bewegen} ruft die Bildschleife; beides ist gesperrt.
 */
public final class Steuerung {
    private final Blick blick = new Blick();
    private double vor, seit, hoch, dreh, neig, schnell = 1, boden;
    /**
     * Seit S3c-5 (SF29 A): Wasser am Ort (Ost, Nord → {Spiegel m NHN oder NaN, Hs m}), von der Bühne gesetzt; null: ohne.
     * Das Auge bleibt mindestens {@link #ueberWasser} m über Spiegel plus Hs/2.
     */
    public volatile java.util.function.BiFunction<Double, Double, double[]> wasser;
    /** Mindesthöhe über dem Wasser (m), aus der zuletzt gesetzten Ansicht. */
    private double ueberWasser = Ansicht.UEBER_WASSER;
    private int mx, my;

    /** Hängt Maus und Tasten an eine Komponente (die Leinwand). */
    public void anbringen(Component c) {
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                mx = e.getX();
                my = e.getY();
                c.requestFocus();
            }

            @Override public void mouseDragged(MouseEvent e) {
                synchronized (Steuerung.this) {
                    double s = blick.bildwinkel / Math.max(1, c.getHeight());
                    dreh -= (e.getX() - mx) * s;
                    neig += (e.getY() - my) * s;
                }
                mx = e.getX();
                my = e.getY();
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                synchronized (Steuerung.this) {
                    blick.bildwinkel = Math.max(5, Math.min(100, blick.bildwinkel * Math.pow(1.1, e.getWheelRotation())));
                }
            }
        };
        c.addMouseListener(m);
        c.addMouseMotionListener(m);
        c.addMouseWheelListener(m);
        c.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { taste(e, 1); }

            @Override public void keyReleased(KeyEvent e) { taste(e, 0); }
        });
    }

    private synchronized void taste(KeyEvent e, int an) {
        schnell = e.isShiftDown() ? 10 : 1;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W -> vor = an;
            case KeyEvent.VK_S -> vor = -an;
            case KeyEvent.VK_D -> seit = an;
            case KeyEvent.VK_A -> seit = -an;
            case KeyEvent.VK_Q -> hoch = an;
            case KeyEvent.VK_E -> hoch = -an;
            default -> { }
        }
    }

    /** Je Bild: wendet Maus und Tasten seit dem vorigen Bild an; das Auge bleibt mindestens 1 m über dem Boden. */
    public synchronized void bewegen(double dt, Gelaende gel) {
        blick.richtung = (blick.richtung + dreh + 360) % 360;
        blick.neigung = Math.max(-89, Math.min(89, blick.neigung + neig));
        dreh = neig = 0;
        double v = Math.max(5, blick.hoehe - boden) * dt * schnell;
        double a = Math.toRadians(blick.richtung);
        blick.ost += v * (vor * Math.sin(a) + seit * Math.cos(a));
        blick.nord += v * (vor * Math.cos(a) - seit * Math.sin(a));
        boden = gel != null ? gel.hoehe(blick.ost, blick.nord, 1) : 0;
        blick.hoehe = Math.max(boden + 1.0, blick.hoehe + hoch * v);
        ueberWasser();
    }

    /** Seit S3c-5 (SF29 A): hebt das Auge über das Wasser am Ort, wenn die Bühne es kennt. */
    private void ueberWasser() {
        java.util.function.BiFunction<Double, Double, double[]> w = wasser;
        if (w == null) return;
        double[] e = w.apply(blick.ost, blick.nord);
        if (e != null) Ansicht.hebe(blick, e[0], e[1], ueberWasser);
    }

    /** Setzt den Blick auf eine Ansicht (siehe {@link Ansicht#setze}). */
    public synchronized void setze(Ansicht a, Gelaende gel, Gitter utm, Instant zeit) {
        a.setze(blick, gel, utm, zeit);
        boden = gel != null ? gel.hoehe(blick.ost, blick.nord, 1) : 0;
        dreh = neig = 0;
        ueberWasser = a.ueberWasser;
        ueberWasser();
    }

    /** Setzt den Blick frei. */
    public synchronized void setze(double ost, double nord, double hoehe, double richtung, double neigung, double bildwinkel) {
        blick.ost = ost;
        blick.nord = nord;
        blick.hoehe = hoehe;
        blick.richtung = richtung;
        blick.neigung = neigung;
        blick.bildwinkel = bildwinkel;
    }

    /** Der Blick dieses Augenblicks, als Kopie. */
    public synchronized Blick blick() { return blick.kopie(); }
}
