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
 * Ein kleiner JSON-Leser (RFC 8259) für die Antworten von Overpass (Werkbuch HalligHooge Teil A, 10.4, Schritt 1).
 * Objekte werden zu {@code Map<String, Object>} (Reihenfolge wie in der Datei), Felder zu {@code List<Object>}, Zahlen
 * zu {@code Double} oder, ganz und ohne Exponent, zu {@code Long}, dazu {@code String}, {@code Boolean} und
 * {@code null}. Nur {@code java.*}.
 */
public final class Json {
    private final String s;
    private int p;

    private Json(String s) { this.s = s; }

    public static Object lies(Path datei) throws IOException { return text(Files.readString(datei, StandardCharsets.UTF_8)); }

    public static Object text(String s) throws IOException {
        Json j = new Json(s);
        j.leer();
        if (j.p < s.length() && s.charAt(j.p) == '﻿') j.p++;
        Object o = j.wert();
        j.leer();
        if (j.p != s.length()) throw j.fehler("Text nach dem Ende");
        return o;
    }

    private IOException fehler(String was) {
        int zeile = 1;
        for (int i = 0; i < Math.min(p, s.length()); i++) if (s.charAt(i) == '\n') zeile++;
        return new IOException("JSON: " + was + " in Zeile " + zeile + " (Zeichen " + p + ")");
    }

    private void leer() {
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') p++;
            else break;
        }
    }

    private Object wert() throws IOException {
        if (p >= s.length()) throw fehler("Ende mitten im Wert");
        char c = s.charAt(p);
        switch (c) {
            case '{': return objekt();
            case '[': return feld();
            case '"': return zeichenkette();
            case 't': wort("true"); return Boolean.TRUE;
            case 'f': wort("false"); return Boolean.FALSE;
            case 'n': wort("null"); return null;
            default:
                if (c == '-' || c >= '0' && c <= '9') return zahl();
                throw fehler("unerwartetes Zeichen '" + c + "'");
        }
    }

    private void wort(String w) throws IOException {
        if (!s.startsWith(w, p)) throw fehler("erwartet " + w);
        p += w.length();
    }

    private Map<String, Object> objekt() throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        p++;
        leer();
        if (p < s.length() && s.charAt(p) == '}') { p++; return m; }
        while (true) {
            leer();
            if (p >= s.length() || s.charAt(p) != '"') throw fehler("Schlüssel erwartet");
            String k = zeichenkette();
            leer();
            if (p >= s.length() || s.charAt(p) != ':') throw fehler("':' erwartet");
            p++;
            leer();
            m.put(k, wert());
            leer();
            if (p >= s.length()) throw fehler("Ende im Objekt");
            char c = s.charAt(p++);
            if (c == '}') return m;
            if (c != ',') throw fehler("',' oder '}' erwartet");
        }
    }

    private List<Object> feld() throws IOException {
        List<Object> l = new ArrayList<>();
        p++;
        leer();
        if (p < s.length() && s.charAt(p) == ']') { p++; return l; }
        while (true) {
            leer();
            l.add(wert());
            leer();
            if (p >= s.length()) throw fehler("Ende im Feld");
            char c = s.charAt(p++);
            if (c == ']') return l;
            if (c != ',') throw fehler("',' oder ']' erwartet");
        }
    }

    private String zeichenkette() throws IOException {
        p++;
        int a = p;
        while (p < s.length()) {                                                          // schnell: ohne Escape
            char c = s.charAt(p);
            if (c == '"') return s.substring(a, p++);
            if (c == '\\' || c < 0x20) break;
            p++;
        }
        StringBuilder b = new StringBuilder(s.substring(a, p));
        while (p < s.length()) {
            char c = s.charAt(p++);
            if (c == '"') return b.toString();
            if (c < 0x20) throw fehler("Steuerzeichen in Zeichenkette");
            if (c != '\\') { b.append(c); continue; }
            if (p >= s.length()) break;
            char e = s.charAt(p++);
            switch (e) {
                case '"', '\\', '/' -> b.append(e);
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (p + 4 > s.length()) throw fehler("\\u unvollständig");
                    try {
                        b.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                    } catch (NumberFormatException x) {
                        throw fehler("\\u" + s.substring(p, p + 4));
                    }
                    p += 4;
                }
                default -> throw fehler("unbekanntes Escape \\" + e);
            }
        }
        throw fehler("Zeichenkette ohne Ende");
    }

    private Object zahl() throws IOException {
        int a = p;
        if (s.charAt(p) == '-') p++;
        boolean ganz = true;
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c >= '0' && c <= '9') p++;
            else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { ganz = false; p++; }
            else break;
        }
        String t = s.substring(a, p);
        try {
            if (ganz && t.length() < 19) return Long.parseLong(t);
            return Double.parseDouble(t);
        } catch (NumberFormatException x) {
            throw fehler("Zahl '" + t + "'");
        }
    }

    // ---- Zugriff

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objekt(Object o) { return o instanceof Map ? (Map<String, Object>) o : Map.of(); }

    @SuppressWarnings("unchecked")
    public static List<Object> feld(Object o) { return o instanceof List ? (List<Object>) o : List.of(); }

    public static double zahl(Object o) { return o instanceof Number n ? n.doubleValue() : Double.NaN; }
}
