package com.dan.aufbereitung;

import java.io.IOException;

/**
 * CCITT Gruppe 4 (ITU-T T.6, TIFF-Kompression 4) für Bilder mit einem Bit je Pixel, wie die Folien der DTK5
 * (Werkbuch HalligHooge Teil A, 10.3). Jede Zeile ist gegen die Zeile darüber kodiert (Pass, Horizontal mit zwei
 * Lauflängen nach T.4, Vertikal um −3 … +3); die erste Zeile eines Streifens gegen eine weiße.
 * <p>
 * Ergebnis: ein Byte je Pixel, 1 für einen schwarz kodierten Lauf, 0 für weiß. Was schwarz im Bild bedeutet, sagt
 * PhotometricInterpretation (bei 0, MinIsWhite, ist 1 schwarz). Nicht unterstützt: der unkomprimierte Modus (T6Options
 * Bit 1) und Gruppe 3.
 */
final class Fax {
    private Fax() { }

    // Lauflängen nach T.4, Tabellen 2 und 3: Endcodes 0–63, Aufbaucodes 64–1728 (Schritt 64), erweitert 1792–2560
    private static final String[] WEISS_ENDE = {"00110101", "000111", "0111", "1000", "1011", "1100", "1110", "1111", "10011", "10100", "00111", "01000",
            "001000", "000011", "110100", "110101", "101010", "101011", "0100111", "0001100", "0001000", "0010111", "0000011", "0000100", "0101000",
            "0101011", "0010011", "0100100", "0011000", "00000010", "00000011", "00011010", "00011011", "00010010", "00010011", "00010100", "00010101",
            "00010110", "00010111", "00101000", "00101001", "00101010", "00101011", "00101100", "00101101", "00000100", "00000101", "00001010",
            "00001011", "01010010", "01010011", "01010100", "01010101", "00100100", "00100101", "01011000", "01011001", "01011010", "01011011",
            "01001010", "01001011", "00110010", "00110011", "00110100"};
    private static final String[] WEISS_AUFBAU = {"11011", "10010", "010111", "0110111", "00110110", "00110111", "01100100", "01100101", "01101000",
            "01100111", "011001100", "011001101", "011010010", "011010011", "011010100", "011010101", "011010110", "011010111", "011011000",
            "011011001", "011011010", "011011011", "010011000", "010011001", "010011010", "011000", "010011011"};
    private static final String[] SCHWARZ_ENDE = {"0000110111", "010", "11", "10", "011", "0011", "0010", "00011", "000101", "000100", "0000100",
            "0000101", "0000111", "00000100", "00000111", "000011000", "0000010111", "0000011000", "0000001000", "00001100111", "00001101000",
            "00001101100", "00000110111", "00000101000", "00000010111", "00000011000", "000011001010", "000011001011", "000011001100",
            "000011001101", "000001101000", "000001101001", "000001101010", "000001101011", "000011010010", "000011010011", "000011010100",
            "000011010101", "000011010110", "000011010111", "000001101100", "000001101101", "000011011010", "000011011011", "000001010100",
            "000001010101", "000001010110", "000001010111", "000001100100", "000001100101", "000001010010", "000001010011", "000000100100",
            "000000110111", "000000111000", "000000100111", "000000101000", "000001011000", "000001011001", "000000101011", "000000101100",
            "000001011010", "000001100110", "000001100111"};
    private static final String[] SCHWARZ_AUFBAU = {"0000001111", "000011001000", "000011001001", "000001011011", "000000110011", "000000110100",
            "000000110101", "0000001101100", "0000001101101", "0000001001010", "0000001001011", "0000001001100", "0000001001101", "0000001110010",
            "0000001110011", "0000001110100", "0000001110101", "0000001110110", "0000001110111", "0000001010010", "0000001010011", "0000001010100",
            "0000001010101", "0000001011010", "0000001011011", "0000001100100", "0000001100101"};
    private static final String[] ERWEITERT = {"00000001000", "00000001100", "00000001101", "000000010010", "000000010011", "000000010100",
            "000000010101", "000000010110", "000000010111", "000000011100", "000000011101", "000000011110", "000000011111"};

    /** Arten der Kodierung einer Zeile (T.6, Tabelle 1). */
    private static final int PASS = 10, HORIZONTAL = 11, ERWEITERUNG = 12, ZEILENENDE = 13;     // vertikal: 0..6 für −3..+3
    private static final Baum WEISS = laeufe(WEISS_ENDE, WEISS_AUFBAU), SCHWARZ = laeufe(SCHWARZ_ENDE, SCHWARZ_AUFBAU), ART = arten();

    /** Ein Binärbaum über die Codes; jedes Blatt trägt seinen Wert. */
    private static final class Baum {
        int[] kind = new int[2 * 512];
        int[] wert = new int[512];
        int knoten = 1;

        Baum() { java.util.Arrays.fill(wert, -1); }

        void setze(String code, int w) {
            int k = 0;
            for (int i = 0; i < code.length(); i++) {
                if (wert[k] >= 0) throw new IllegalStateException("Fax: Code " + code + " beginnt mit einem anderen");
                int b = code.charAt(i) - '0', o = 2 * k + b;
                if (kind[o] == 0) {
                    if (knoten == wert.length) {
                        kind = java.util.Arrays.copyOf(kind, 4 * knoten);
                        int alt = wert.length;
                        wert = java.util.Arrays.copyOf(wert, 2 * knoten);
                        java.util.Arrays.fill(wert, alt, wert.length, -1);
                    }
                    kind[o] = knoten++;
                }
                k = kind[o];
            }
            if (wert[k] >= 0 || kind[2 * k] != 0 || kind[2 * k + 1] != 0) throw new IllegalStateException("Fax: Code " + code + " doppelt");
            wert[k] = w;
        }
    }

    private static Baum laeufe(String[] ende, String[] aufbau) {
        if (ende.length != 64 || aufbau.length != 27 || ERWEITERT.length != 13) throw new IllegalStateException("Fax: Tabellen unvollständig");
        Baum b = new Baum();
        for (int i = 0; i < 64; i++) b.setze(ende[i], i);
        for (int i = 0; i < 27; i++) b.setze(aufbau[i], 64 * (i + 1));
        for (int i = 0; i < 13; i++) b.setze(ERWEITERT[i], 1792 + 64 * i);
        return b;
    }

    private static Baum arten() {
        Baum b = new Baum();
        b.setze("1", 3);
        b.setze("011", 4);
        b.setze("010", 2);
        b.setze("000011", 5);
        b.setze("000010", 1);
        b.setze("0000011", 6);
        b.setze("0000010", 0);
        b.setze("001", HORIZONTAL);
        b.setze("0001", PASS);
        b.setze("0000001", ERWEITERUNG);
        b.setze("000000000001", ZEILENENDE);
        return b;
    }

    /** Liest Bits von vorn, höchstes Bit eines Bytes zuerst (FillOrder 1) oder niedrigstes zuerst (FillOrder 2). */
    private static final class Bits {
        final byte[] d;
        final boolean umgekehrt;
        long pos;

        Bits(byte[] d, boolean umgekehrt) {
            this.d = d;
            this.umgekehrt = umgekehrt;
        }

        int bit() throws IOException {
            int i = (int) (pos >>> 3);
            if (i >= d.length) throw new IOException("Fax: Daten zu kurz");
            int s = (int) (pos++ & 7);
            return umgekehrt ? (d[i] >> s) & 1 : (d[i] >> (7 - s)) & 1;
        }

        int code(Baum b) throws IOException {
            int k = 0;
            while (true) {
                k = b.kind[2 * k + bit()];
                if (k == 0) throw new IOException("Fax: ungültiger Code bei Bit " + pos);
                if (b.wert[k] >= 0) return b.wert[k];
            }
        }

        int lauf(int farbe) throws IOException {
            int summe = 0;
            while (true) {
                int w = code(farbe == 0 ? WEISS : SCHWARZ);
                summe += w;
                if (w < 64) return summe;
            }
        }
    }

    /**
     * Entpackt einen Streifen mit {@code zeilen} Zeilen zu je {@code breite} Pixeln nach {@code aus} ab
     * {@code versatz}: ein Byte je Pixel, 1 = schwarz kodiert. {@code aus} muss dort mit 0 gefüllt sein.
     */
    static void gruppe4(byte[] daten, int breite, int zeilen, boolean fillOrder2, byte[] aus, int versatz) throws IOException {
        Bits b = new Bits(daten, fillOrder2);
        int[] ref = new int[breite + 4], neu = new int[breite + 4];
        ref[0] = ref[1] = ref[2] = breite;                                                // die weiße Zeile über dem Streifen
        for (int z = 0; z < zeilen; z++) {
            int o = versatz + z * breite, a0 = -1, farbe = 0, n = 0, k = 0;
            while (a0 < breite) {
                int art = b.code(ART);
                if (art == ZEILENENDE) throw new IOException("Fax: Ende der Daten in Zeile " + z + " von " + zeilen);
                if (art == ERWEITERUNG) throw new IOException("Fax: Erweiterung (unkomprimierter Modus) in Zeile " + z + " nicht unterstützt");
                while (k > 0 && ref[k - 1] > a0) k--;
                while (ref[k] <= a0) k++;
                int kk = (k & 1) == farbe ? k : k + 1;                                      // b1: erster Wechsel rechts von a0 zur Gegenfarbe
                int b1 = ref[kk], b2 = ref[kk + 1], start = Math.max(a0, 0);
                if (art == PASS) {
                    if (farbe == 1) fuelle(aus, o, start, b2);
                    a0 = b2;
                } else if (art == HORIZONTAL) {
                    int a1 = Math.min(breite, start + b.lauf(farbe)), a2 = Math.min(breite, a1 + b.lauf(1 - farbe));
                    if (farbe == 1) fuelle(aus, o, start, a1);
                    else fuelle(aus, o, a1, a2);
                    neu[n++] = a1;
                    neu[n++] = a2;
                    a0 = a2;
                } else {
                    int a1 = b1 + art - 3;
                    if (a1 < start || a1 > breite) throw new IOException("Fax: vertikaler Schritt außerhalb der Zeile " + z);
                    if (farbe == 1) fuelle(aus, o, start, a1);
                    neu[n++] = a1;
                    a0 = a1;
                    farbe ^= 1;
                }
                if (n > breite) throw new IOException("Fax: zu viele Wechsel in Zeile " + z);
            }
            neu[n] = neu[n + 1] = neu[n + 2] = breite;
            int[] t = ref;
            ref = neu;
            neu = t;
        }
    }

    private static void fuelle(byte[] aus, int o, int von, int bis) {
        for (int i = von; i < bis; i++) aus[o + i] = 1;
    }
}
