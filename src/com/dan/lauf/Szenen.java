package com.dan.lauf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Szenendatei eines Projekts (Werkbuch HalligHooge Teil B, B.3; F71 A): Gebiet, Pegel, Ansichten und Szenarien als
 * Text, im Format des Rezepts der Aufbereitung – je Zeile {@code Schlüssel = Wert}, {@code #} beginnt einen Kommentar
 * (am Zeilenanfang oder nach einem Leerzeichen). Pfade ohne Laufwerk gelten relativ zum Ordner der Datei.
 * <pre>
 * gebiet                        = D:\Daten\HalligHooge\gebiet
 * pegel.name                    = Hooge
 * pegel.ort                     = 470410 6048090      # UTM Ost Nord
 * pegel.umkreis                 = 300                 # m: die tiefste Zelle im Umkreis gilt
 * pegel.marke                   = 2.01                # m NHN: Wasser darüber wird als Band gezeigt
 * pegel.marke.name              = Landunter
 *
 * ansicht.westspitze.name       = Deich an der Westspitze
 * ansicht.westspitze.ort        = 468290 6047690
 * ansicht.westspitze.hoehe      = 1.7 boden           # m über dem Boden; oder: 300 nhn
 * ansicht.westspitze.richtung   = 259                 # Grad von Nord über Ost; oder: sonne
 * ansicht.westspitze.neigung    = -2
 * ansicht.westspitze.bildwinkel = 55
 *
 * szenario.sturm.name           = Sturmflut, 25.10.2026
 * szenario.sturm.archiv         = D:\Daten\HalligHooge\_p6\archiv
 * szenario.sturm.beginn         = 2026-10-25T10:00:00Z   # Szenenzeit beim Öffnen (ohne: der erste Stand)
 * szenario.sturm.zeitraffer     = 300
 * szenario.sturm.ansicht        = westspitze
 *
 * szenario.live.name            = Live
 * szenario.live.start           = 2026-10-29T00:00:00Z   # ohne archiv: der Lauf rechnet neben dem Bild
 * szenario.live.stufen          = G0 | G1 | G2 G3
 * szenario.live.fenster3        = 600 850 500 400        # G3 als Fenster: i0 j0 ni nj
 * szenario.live.sediment        = an                     # nur live, seit P9c: der Lauf rechnet das Sediment mit
 * szenario.sediment.schwebstoff = 20                     # g/m³, wo kein Netz des Sediments die Trübung gibt (seit P9b; sonst die Vorgabe 30)
 * </pre>
 * Ansichten und Szenarien stehen in der Reihenfolge der Datei.
 */
public final class Szenen {
    public final File datei;
    private final Map<String, String> werte = new LinkedHashMap<>();
    private final List<Ansicht> ansichten = new ArrayList<>();
    private final List<Szenario> szenarien = new ArrayList<>();

    private Szenen(File datei) { this.datei = datei; }

    public static Szenen lies(File datei) throws IOException {
        Szenen s = new Szenen(datei.getAbsoluteFile());
        int nr = 0;
        for (String zeile : Files.readAllLines(datei.toPath(), StandardCharsets.UTF_8)) {
            nr++;
            String z = zeile.strip();
            if (!z.isEmpty() && z.charAt(0) == '\uFEFF') z = z.substring(1).strip();
            if (z.startsWith("#")) continue;
            for (int i = 1; i < z.length(); i++)
                if (z.charAt(i) == '#' && Character.isWhitespace(z.charAt(i - 1))) {
                    z = z.substring(0, i).strip();
                    break;
                }
            if (z.isEmpty()) continue;
            int p = z.indexOf('=');
            if (p <= 0) throw new IOException(datei + ", Zeile " + nr + ": „Schlüssel = Wert“ erwartet: " + zeile);
            s.werte.put(z.substring(0, p).strip(), z.substring(p + 1).strip());
        }
        // seit W5 (Werkbuch HalligHooge 126, F214 A): ${name} in Werten – aus -Dszenen.<name>, sonst aus dem Schlüssel <name>
        // der Datei (etwa daten = D:\Daten\HalligHooge und gebiet = ${daten}\gebiet)
        for (int runde = 0; runde < 4; runde++)
            for (Map.Entry<String, String> e : s.werte.entrySet()) e.setValue(s.einsetzen(e.getValue()));
        for (String k : s.namen("ansicht")) s.ansichten.add(s.baueAnsicht(k));
        for (String k : s.namen("szenario")) s.szenarien.add(s.baueSzenario(k));
        return s;
    }

    private String einsetzen(String v) {
        int a = v.indexOf("${");
        while (a >= 0) {
            int e = v.indexOf('}', a);
            if (e < 0) break;
            String name = v.substring(a + 2, e), w = System.getProperty("szenen." + name);
            if (w == null) w = werte.get(name);
            if (w == null || w.contains("${" + name + "}")) {
                a = v.indexOf("${", e);
                continue;
            }
            v = v.substring(0, a) + w + v.substring(e + 1);
            a = v.indexOf("${", a + w.length());
        }
        return v;
    }

    /** Die Namen hinter einem Vorsatz ({@code ansicht.<name>.…}), in der Reihenfolge der Datei. */
    private Set<String> namen(String vorsatz) {
        Set<String> n = new LinkedHashSet<>();
        for (String k : werte.keySet())
            if (k.startsWith(vorsatz + ".")) {
                int e = k.indexOf('.', vorsatz.length() + 1);
                if (e > 0) n.add(k.substring(vorsatz.length() + 1, e));
            }
        return n;
    }

    private Ansicht baueAnsicht(String k) throws IOException {
        String v = "ansicht." + k + ".";
        double[] ort = zahlen(v + "ort");
        if (ort == null || ort.length != 2) throw new IOException(datei + ": " + v + "ort = Ost Nord fehlt");
        String[] h = text(v + "hoehe", "1.7 boden").split("\\s+");
        boolean boden = h.length < 2 || h[1].equalsIgnoreCase("boden");
        if (h.length >= 2 && !boden && !h[1].equalsIgnoreCase("nhn")) throw new IOException(datei + ": " + v + "hoehe = Zahl und „boden“ oder „nhn“ erwartet");
        String r = text(v + "richtung", "0");
        double richtung = r.equalsIgnoreCase("sonne") ? Double.NaN : zahl(datei, v + "richtung", r);
        return new Ansicht(k, text(v + "name", k), ort[0], ort[1], zahl(datei, v + "hoehe", h[0]), boden, richtung, zahl(v + "neigung", -2), zahl(v + "bildwinkel", 55));
    }

    private Szenario baueSzenario(String k) throws IOException {
        String v = "szenario." + k + ".";
        File archiv = pfad(v + "archiv");
        String start = text(v + "start", null), beginn = text(v + "beginn", null);
        if (archiv == null && start == null) throw new IOException(datei + ": " + v + "archiv oder " + v + "start fehlt");
        double[] f = zahlen(v + "fenster3");
        int[] f3 = null;
        if (f != null) {
            if (f.length != 4) throw new IOException(datei + ": " + v + "fenster3 = i0 j0 ni nj erwartet");
            f3 = new int[] {(int) f[0], (int) f[1], (int) f[2], (int) f[3]};
        }
        String an = text(v + "ansicht", null);
        if (an != null && ansicht(an) == null) throw new IOException(datei + ": " + v + "ansicht = " + an + " – diese Ansicht gibt es nicht");
        Szenario sz = new Szenario(k, text(v + "name", k), archiv, start == null ? null : zeit(v + "start", start), text(v + "stufen", "G0 | G1 | G2 G3"), f3,
                beginn == null ? null : zeit(v + "beginn", beginn), zahl(v + "zeitraffer", archiv != null ? 300 : 100), an);
        sz.schwebstoff = zahl(v + "schwebstoff", Double.NaN);
        sz.sediment = List.of("an", "ja", "true").contains(text(v + "sediment", "aus").trim().toLowerCase(java.util.Locale.ROOT));
        return sz;
    }

    private Instant zeit(String schluessel, String wert) throws IOException {
        try {
            return Instant.parse(wert);
        } catch (RuntimeException e) {
            throw new IOException(datei + ": " + schluessel + " = " + wert + " – Zeit in UTC wie 2026-10-25T10:00:00Z erwartet");
        }
    }

    private static double zahl(File datei, String schluessel, String wert) throws IOException {
        try {
            return Double.parseDouble(wert.replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IOException(datei + ": " + schluessel + " = " + wert + " – Zahl erwartet");
        }
    }

    /** Der Wert zu einem Schlüssel, oder die Vorgabe. */
    public String text(String schluessel, String vorgabe) {
        String w = werte.get(schluessel);
        return w == null || w.isEmpty() ? vorgabe : w;
    }

    public double zahl(String schluessel, double vorgabe) throws IOException {
        String w = text(schluessel, null);
        return w == null ? vorgabe : zahl(datei, schluessel, w);
    }

    /** Mehrere Zahlen, durch Leerzeichen getrennt; null, wenn der Schlüssel fehlt. */
    public double[] zahlen(String schluessel) throws IOException {
        String w = text(schluessel, null);
        if (w == null) return null;
        String[] t = w.split("[\\s;]+");
        double[] r = new double[t.length];
        for (int i = 0; i < t.length; i++) r[i] = zahl(datei, schluessel, t[i]);
        return r;
    }

    /** Ein Pfad; ohne Laufwerk oder Wurzel relativ zum Ordner der Datei. Null, wenn der Schlüssel fehlt. */
    public File pfad(String schluessel) {
        String w = text(schluessel, null);
        if (w == null) return null;
        File f = new File(w);
        return f.isAbsolute() ? f : new File(datei.getParentFile(), w);
    }

    /** Der Ordner des Gebiets ({@code gebiet = …}). */
    public File gebiet() throws IOException {
        File g = pfad("gebiet");
        if (g == null) throw new IOException(datei + ": gebiet = Ordner fehlt");
        return g;
    }

    public List<Ansicht> ansichten() { return Collections.unmodifiableList(ansichten); }

    public List<Szenario> szenarien() { return Collections.unmodifiableList(szenarien); }

    /** Die Ansicht zu einem Schlüssel; null, wenn es sie nicht gibt. */
    public Ansicht ansicht(String schluessel) {
        for (Ansicht a : ansichten) if (a.schluessel.equals(schluessel)) return a;
        return null;
    }

    /** Das Szenario zu einem Schlüssel; null, wenn es das nicht gibt. */
    public Szenario szenario(String schluessel) {
        for (Szenario s : szenarien) if (s.schluessel.equals(schluessel)) return s;
        return null;
    }
}
