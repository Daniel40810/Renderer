package com.dan.lauf;

import com.dan.core.raum.Blick;

/**
 * Eine Quelle für den Blick (seit W3, Werkbuch HalligHooge 116, F192 A): Die Bildschleife der {@link Buehne} fragt sie
 * in jedem Bild, nach der Szenenzeit und vor dem Zeichnen. So fährt eine Kamera glatt, ohne den Takt der Oberfläche,
 * und ein Video (W4) nutzt dieselbe Quelle Bild für Bild.
 * <p>
 * Gerufen im Faden der Bildschleife. Was die Quelle dort tut, muss schnell sein; Wünsche an die Bühne (Szenario,
 * Ansicht, Zeit) gelten ab dem nächsten Bild.
 */
public interface Kamera {
    /**
     * Ein Bild weiter: {@code dt} echte Sekunden seit dem vorigen Bild, {@code szenenzeit} die Zeit dieses Bilds (s seit
     * dem Start der Quelle), {@code jetzt} der Blick von Maus und Tasten. Gibt den Blick dieses Bilds zurück oder null:
     * dann gilt {@code jetzt}. Die Bühne übernimmt einen Blick in die Steuerung; danach geht es mit der Maus ohne
     * Sprung weiter.
     */
    Blick weiter(double dt, double szenenzeit, Blick jetzt);
}
