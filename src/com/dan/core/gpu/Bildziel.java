package com.dan.core.gpu;

import com.dan.core.raum.Blick;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wohin ein Bild gezeichnet wird (seit P6a, Werkbuch HalligHooge Teil A, 8.1 und 12.7): das HDR-Bild (RGBA16F,
 * Leuchtdichte in cd/m² mal Vorbelichtung), die Tiefe (D32F, umgekehrt: 0 = unendlich fern), der Blick, die Szenenzeit
 * und die Vorbelichtung. Es steht in core.gpu, weil jedes Bildmodul hineinzeichnet und die Module einander nicht
 * kennen (Schichtenvertrag, 4); render.frame legt es an und gibt es an die anderen weiter.
 * <p>
 * {@link #geteilt} trägt Bilder, die ein Modul für andere ablegt (der Himmel seine Tabellen für die Spiegelung im
 * Wasser und die Luftperspektive im Gelände), unter Namen, die im Werkbuch stehen. Wie beim Bus gilt: ein Schreiber,
 * beliebig viele Leser, und der Schreiber legt vor den Lesern ab ({@link Bildteil#vorbereiten}).
 */
public final class Bildziel {
    public final Bild hdr, tiefe;
    public final int breite, hoehe;
    public final Map<String, Bild> geteilt = new LinkedHashMap<>();
    /** Puffer, die ein Modul für andere ablegt (seit P6c: die Werte des Himmels für die Luftperspektive), Regeln wie {@link #geteilt}. */
    public final Map<String, Puffer> puffer = new LinkedHashMap<>();
    /** Zahlen, die ein Modul für andere auf dem Prozessor ablegt (seit P6c: Sonne und Mond), Regeln wie {@link #geteilt}. */
    public final Map<String, double[]> werte = new LinkedHashMap<>();
    /** Je Bild gesetzt: Blick, Szenenzeit (UTC), Vorbelichtung (das HDR-Bild trägt L · Vorbelichtung). */
    public Blick blick;
    public Instant zeit;
    public double vorbelichtung = 1;

    public Bildziel(Bild hdr, Bild tiefe) {
        if (hdr.breite != tiefe.breite || hdr.hoehe != tiefe.hoehe) throw new IllegalArgumentException("Bildziel: HDR " + hdr + " und Tiefe " + tiefe + " verschieden groß");
        this.hdr = hdr;
        this.tiefe = tiefe;
        this.breite = hdr.breite;
        this.hoehe = hdr.hoehe;
    }

    /** Ein geteiltes Bild; Fehler, wenn es keiner abgelegt hat. */
    public Bild geteilt(String name) {
        Bild b = geteilt.get(name);
        if (b == null) throw new IllegalStateException("Bildziel: niemand legt „" + name + "“ ab (abgelegt sind " + geteilt.keySet() + ")");
        return b;
    }

    /** Ein geteilter Puffer; Fehler, wenn ihn keiner abgelegt hat. */
    public Puffer puffer(String name) {
        Puffer p = puffer.get(name);
        if (p == null) throw new IllegalStateException("Bildziel: niemand legt den Puffer „" + name + "“ ab (abgelegt sind " + puffer.keySet() + ")");
        return p;
    }

    /** Geteilte Zahlen; Fehler, wenn sie keiner abgelegt hat. */
    public double[] werte(String name) {
        double[] w = werte.get(name);
        if (w == null) throw new IllegalStateException("Bildziel: niemand legt die Werte „" + name + "“ ab (abgelegt sind " + werte.keySet() + ")");
        return w;
    }
}
