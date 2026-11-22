package com.dan.core.pruef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Das Attest einer Modulversion (Werkbuch HalligHooge Teil A, 5.9): je Prüfstein die Größe, der Wert, der Grenzwert
 * und das Urteil, dazu Messwerte ohne Grenze und der Befund. Die Grenzwerte stehen fest, bevor gemessen wird; ein
 * Grenzwert mit [G] ist gesetzt, nicht hergeleitet. Abgelegt als Text ({@link #schreibe}), später in Oracle.
 * <p>
 * Stufen: A analytisch, B Labor und Literatur, C Feld, D Bild.
 */
public final class Attest {
    public enum Urteil { OK, FEHLT, INFO }

    public record Zeile(String teil, String groesse, String wert, String grenze, Urteil urteil) { }

    public final String modul, version;
    public final List<Zeile> zeilen = new ArrayList<>();
    public final List<String> befund = new ArrayList<>();
    /** Ohne Ausgabe auf der Konsole. */
    public boolean still;
    private String teil = "";

    public Attest(String modul, String version) {
        this.modul = modul;
        this.version = version;
    }

    /** Beginnt einen Teil; die folgenden Zeilen gehören dazu. */
    public void teil(String t) {
        teil = t;
        if (!still) {
            System.out.println();
            System.out.println(t);
        }
    }

    /** Ein Prüfstein: erfüllt oder nicht. */
    public boolean pruefe(boolean erfuellt, String groesse, String wert, String grenze) {
        zeilen.add(new Zeile(teil, groesse, wert, grenze, erfuellt ? Urteil.OK : Urteil.FEHLT));
        if (!still) System.out.println("  " + (erfuellt ? "ok   " : "FEHLT") + "  " + groesse + ": " + wert + (grenze.isEmpty() ? "" : "  [" + grenze + "]"));
        return erfuellt;
    }

    /** Ein Prüfstein mit Zahl: erfüllt, wenn der Betrag des Werts höchstens die Grenze ist (NaN ist nicht erfüllt). */
    public boolean hoechstens(String groesse, double wert, double grenze, String einheit) {
        return pruefe(Math.abs(wert) <= grenze, groesse, f("%.3g %s", wert, einheit).trim(), f("≤ %.3g %s", grenze, einheit).trim());
    }

    /** Ein Messwert ohne Grenze. */
    public void info(String groesse, String wert) {
        zeilen.add(new Zeile(teil, groesse, wert, "", Urteil.INFO));
        if (!still) System.out.println("         " + groesse + ": " + wert);
    }

    /** Ein Satz zum Befund. */
    public void befund(String t) {
        befund.add(teil.isEmpty() ? t : teil + ": " + t);
        if (!still) System.out.println("  » " + t);
    }

    /** Ein Abbruch zählt als Verstoß. */
    public void fehler(Throwable e) {
        String m = e.getClass().getSimpleName() + ": " + e.getMessage();
        zeilen.add(new Zeile(teil, "Ablauf", m.replace('\n', ' '), "ohne Fehler", Urteil.FEHLT));
        if (!still) {
            System.out.println("  FEHLT  Ablauf: " + m);
            e.printStackTrace(System.out);
        }
    }

    public int verstoesse() {
        int n = 0;
        for (Zeile z : zeilen) if (z.urteil == Urteil.FEHLT) n++;
        return n;
    }

    public int pruefsteine() {
        int n = 0;
        for (Zeile z : zeilen) if (z.urteil != Urteil.INFO) n++;
        return n;
    }

    /** „Alles in Ordnung.“ oder die Zahl der Verstöße. */
    public String urteil() {
        int v = verstoesse();
        return v == 0 ? "Alles in Ordnung." : v == 1 ? "1 Verstoß." : v + " Verstöße.";
    }

    /** Das Attest als Markdown. */
    public String text(String titel) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(titel).append("\n\n");
        b.append("Attest für `").append(modul).append("`, Version ").append(version).append(". Stand ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append(", ").append(System.getProperty("java.vm.name")).append(' ')
                .append(System.getProperty("java.version")).append(", ").append(Runtime.getRuntime().availableProcessors()).append(" Kerne.\n\n");
        b.append(pruefsteine()).append(" Prüfsteine, ").append(verstoesse()).append(" Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.\n\n");
        b.append("| Teil | Größe | Wert | Grenze | Urteil |\n|---|---|---|---|---|\n");
        for (Zeile z : zeilen)
            b.append("| ").append(z.teil).append(" | ").append(z.groesse.replace("|", "/")).append(" | ").append(z.wert.replace("|", "/")).append(" | ").append(z.grenze.replace("|", "/"))
                    .append(" | ").append(switch (z.urteil) { case OK -> "ok"; case FEHLT -> "**fehlt**"; case INFO -> "–"; }).append(" |\n");
        if (!befund.isEmpty()) {
            b.append("\n## Befund\n\n");
            for (String t : befund) b.append("- ").append(t).append('\n');
        }
        return b.append('\n').append(urteil()).append('\n').toString();
    }

    /** Schreibt das Attest als Markdown; gibt den Pfad zurück oder null, wenn es nicht ging. */
    public Path schreibe(Path datei, String titel) {
        try {
            if (datei.getParent() != null) Files.createDirectories(datei.getParent());
            Files.writeString(datei, text(titel), StandardCharsets.UTF_8);
            return datei.toAbsolutePath();
        } catch (IOException e) {
            if (!still) System.out.println("  (Attest nicht geschrieben: " + e.getMessage() + ")");
            return null;
        }
    }

    /** Zahlen mit Komma, wie im Werkbuch. */
    public static String f(String format, Object... werte) { return String.format(Locale.GERMAN, format, werte); }
}
