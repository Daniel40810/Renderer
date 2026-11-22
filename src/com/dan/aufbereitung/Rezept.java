package com.dan.aufbereitung;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Rezept eines Gebiets (Werkbuch HalligHooge Teil A, 10.4, Schritt 8): eine Textdatei mit Zeilen
 * {@code schlüssel = wert}; {@code #} beginnt einen Kommentar. Pfade sind relativ zu {@code daten}, wenn sie nicht
 * absolut sind; {@code datei.zip!eintrag} nennt einen Eintrag in einem ZIP. Zahlen mit Punkt; mehrere Zahlen durch
 * Leerzeichen getrennt.
 */
public final class Rezept {
    public final Path datei;
    private final Map<String, String> werte = new LinkedHashMap<>();

    private Rezept(Path datei) { this.datei = datei; }

    public static Rezept lies(Path datei) throws IOException {
        Rezept r = new Rezept(datei);
        int nr = 0;
        for (String z : Files.readAllLines(datei, StandardCharsets.UTF_8)) {
            nr++;
            int k = z.indexOf('#');
            String s = (k >= 0 ? z.substring(0, k) : z).strip();
            if (s.isEmpty()) continue;
            int e = s.indexOf('=');
            if (e <= 0) throw new IOException(datei + ", Zeile " + nr + ": kein 'schlüssel = wert'");
            r.werte.put(s.substring(0, e).strip(), s.substring(e + 1).strip());
        }
        return r;
    }

    /** Setzt einen Wert (etwa aus der Befehlszeile). */
    public void setze(String schluessel, String wert) { werte.put(schluessel, wert); }

    public boolean hat(String schluessel) { return werte.containsKey(schluessel); }

    public String text(String schluessel) {
        String v = werte.get(schluessel);
        if (v == null || v.isEmpty()) throw new IllegalArgumentException(datei + ": '" + schluessel + "' fehlt");
        return v;
    }

    public String text(String schluessel, String vorgabe) { return werte.getOrDefault(schluessel, vorgabe); }

    public double zahl(String schluessel) { return Double.parseDouble(text(schluessel)); }

    public double[] zahlen(String schluessel, int anzahl) {
        String[] t = text(schluessel).split("[\\s,;]+");
        if (t.length != anzahl) throw new IllegalArgumentException(datei + ": '" + schluessel + "' braucht " + anzahl + " Zahlen, nicht " + t.length);
        double[] d = new double[anzahl];
        for (int i = 0; i < anzahl; i++) d[i] = Double.parseDouble(t[i]);
        return d;
    }

    public List<Integer> ganze(String schluessel) {
        List<Integer> l = new ArrayList<>();
        if (!hat(schluessel)) return l;
        for (String t : text(schluessel).split("[\\s,;]+")) if (!t.isEmpty()) l.add(Integer.parseInt(t));
        return l;
    }

    /** Alle Werte, in der Folge der Datei (für das Protokoll). */
    public Map<String, String> alle() { return java.util.Collections.unmodifiableMap(werte); }
}
