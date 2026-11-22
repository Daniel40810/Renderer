package com.dan.core.gpu;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

/**
 * Ein Betriebsmittel auf der Karte, das Durchgänge lesen und schreiben: ein {@link Puffer} oder ein {@link Bild}.
 * Es trägt den Stand seines letzten Zugriffs; daraus rechnet der {@link Ablauf} die Sperren.
 */
public interface Mittel {
    /**
     * Der letzte Zugriff auf ein Betriebsmittel, je Warteschlange für sich (Sperren gelten nur innerhalb einer
     * Warteschlange; zwischen zweien trennt die {@link Zeitleiste}). Die Anordnung eines Bilds gilt für alle.
     */
    final class Stand {
        static final class Seite {
            long stufen, zugriffe;
            /**
             * Stufen, für die der letzte Schreibzugriff oder Wechsel der Anordnung sichtbar ist (die Zielstufen der
             * letzten Sperre, dazu nachgezogene Leser). Ein Leser in einer anderen Stufe braucht eine Sperre, die an
             * diese anschließt (Nachlauf P6, Prüfstein 9).
             */
            long gedeckt;
            boolean benutzt, schreibend;
        }

        final Seite[] seiten = {new Seite(), new Seite(), new Seite()};
        int anordnung = VK_IMAGE_LAYOUT_UNDEFINED;
    }

    /** Der Stand des letzten Zugriffs; nur der {@link Ablauf} ändert ihn. */
    Stand stand();

    /** Name für Meldungen. */
    String name();
}
