package com.dan.core.gpu.demo;

import com.dan.core.Core;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Feldtafel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Karte;
import com.dan.core.gpu.KartenFeld;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Shader;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.gpu.Zeitleiste;
import com.dan.core.pruef.Attest;
import com.dan.core.takt.Takt;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;

import static com.dan.core.pruef.Attest.f;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Prüfung von {@code com.dan.core.gpu} auf der Grafikkarte, ohne Fenster (Werkbuch HalligHooge Teil A, Phase P2). Endet
 * mit „Alles in Ordnung.“ oder mit der Zahl der Verstöße (Rückgabewert 1) und schreibt das Attest nach
 * {@code docs/status/P2_GpuCheck.md} (mit {@code --ohne-attest} nur auf die Konsole).
 * <p>
 * Die Grenzwerte standen fest, bevor gemessen wurde. Wo ein Kernel nur addiert, subtrahiert und multipliziert und
 * der Shader „precise“ sagt, verlangt Vulkan jede Operation für sich richtig gerundet; die Grenze ist dort null
 * float-Schritte gegen dieselbe Rechnung in Java. Was ein Gerät nicht kann (float16, Untergruppen, eigene
 * Rechen-Warteschlange), wird genannt und nicht geprüft.
 * <p>
 * Start: {@code java --enable-native-access=ALL-UNNAMED -cp … com.dan.core.gpu.demo.GpuCheck}; mit
 * {@code -Dcore.gpu.validierung=true} prüft die Validierungsschicht des Vulkan SDK mit.
 */
public final class GpuCheck {
    private static final Attest A = new Attest("com.dan.core.gpu", Core.VERSION);
    private static final String PFAD = "/com/dan/core/gpu/demo/";
    private static Geraet g;
    private static Konto konto;
    /** Fehler, die die Validierung in der Gegenprobe melden soll. */
    private static int gegenprobe;

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("GpuCheck – com.dan.core.gpu " + Core.VERSION + " (Werkbuch HalligHooge Teil A, P2, Zeichnen seit P6a)");
        A.teil("1 Gerät");
        try {
            g = new Geraet();
            konto = g.konto("GpuCheck", 512L << 20);
            geraet();
            teil("2 Speicher", GpuCheck::speicher);
            teil("3 Shader und Kernel", GpuCheck::kernel);
            teil("4 Ablauf und Sperren", GpuCheck::ablauf);
            teil("5 Zeitleiste und Fäden", GpuCheck::zeitleiste);
            teil("6 Zeiten", GpuCheck::zeiten);
            teil("7 Felder auf der Karte", GpuCheck::felder);
            teil("8 Durchsatz", GpuCheck::durchsatz);
            teil("9 Zeichnen (seit P6a)", GpuCheck::zeichnen);
            A.teil("10 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                int fehler = g.validierungsFehler - gegenprobe;
                String erste = "";                                   // nicht die Meldungen der Gegenprobe
                for (String m : g.meldungen) if (erste.isEmpty() && !m.toLowerCase().contains("hazard")) erste = m;
                if (erste.isEmpty() && !g.meldungen.isEmpty()) erste = g.meldungen.get(0);
                erste = "; die erste Meldung: " + erste.replace('\n', ' ');
                A.pruefe(fehler == 0, "Validierungsschicht " + g.schicht + (g.synchronPruefung ? " mit Prüfung der Synchronisation" : " ohne Prüfung der Synchronisation (erst ab 1.4.304)")
                        + ", über alle Teile", fehler + " Fehler" + (fehler > 0 ? erste : ""), "0 Fehler");
            }
            else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true, braucht das Vulkan SDK)");
        } catch (Throwable e) {
            A.fehler(e);
        } finally {
            if (g != null) g.close();
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P2_GpuCheck.md"), "P2 – GpuCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    private interface Teil { void pruefe() throws Exception; }

    private static void teil(String name, Teil t) {
        A.teil(name);
        try {
            t.pruefe();
        } catch (Throwable e) {
            A.fehler(e);
        }
        g.ruhe();
    }

    private static String ja(boolean b) { return b ? "ja" : "nein"; }

    // ------------------------------------------------------------------ 1 Gerät

    private static void geraet() {
        Geraet.Faehigkeiten k = g.kann;
        A.info("Gerät", g.name + ", Vulkan " + Geraet.version(g.apiVersion) + ", Treiber " + g.treiber + (k.software() ? " (rechnet auf dem Prozessor)" : ""));
        A.pruefe(g.apiVersion >= VK_API_VERSION_1_3, "Vulkan 1.3", Geraet.version(g.apiVersion), "≥ 1.3");
        A.pruefe(k.sync2() && k.zeitleiste() && k.deskriptorIndizes(), "Pflicht: Synchronisation 2, Zeitleisten-Semaphoren, Deskriptor-Indizes",
                ja(k.sync2()) + ", " + ja(k.zeitleiste()) + ", " + ja(k.deskriptorIndizes()), "alle ja");
        A.info("Warteschlangen", g.grafik + (g.rechnen != g.grafik ? ", " + g.rechnen + (k.eigeneRechenSchlange() ? " neben dem Bild" : "") : ", Rechnen auf derselben")
                + (g.uebertragung != g.grafik ? ", " + g.uebertragung : ", Übertragung auf derselben"));
        if (g.rechnen != g.grafik && !k.eigeneRechenSchlange()) A.befund("Prüfhilfe core.gpu.getrennt: eigene Schlangen für Rechnen und Übertragung auf derselben Warteschlange.");
        A.info("Weitere Fähigkeiten", "Strahlabfrage " + ja(k.strahlabfrage()) + ", float16 " + ja(k.float16()) + ", Untergruppen-Arithmetik " + ja(k.untergruppenArithmetik()) + " (Größe "
                + k.untergruppeMin() + "–" + k.untergruppeMax() + "), Zeitstempel " + ja(k.zeitstempel()) + ", Puffer-Adressen " + ja(k.adressen()) + ", dynamisches Rendern "
                + ja(k.dynamischesRendern()) + ", Speicherbudget " + ja(k.budget()));
        StringBuilder h = new StringBuilder();
        for (Geraet.Heap x : g.heaps())
            h.append(h.length() == 0 ? "" : "; ").append("Heap ").append(x.nummer()).append(": ").append(Geraet.mb(x.groesse())).append(x.geraetelokal() ? " gerätelokal" : " geteilt")
                    .append(x.vomProzessorSichtbar() ? ", vom Prozessor sichtbar" : "").append(", Budget ").append(Geraet.mb(x.budget()));
        A.info("Speicher", h + "; größte Zuteilung " + Geraet.mb(g.groessteZuteilung) + ", größter Speicherpuffer " + Geraet.mb(g.groessterPuffer));
        if (!k.strahlabfrage()) A.befund("Ohne Strahlabfrage geht die Referenz Stufe 2 (P11) auf diesem Gerät nicht.");
        if (!k.eigeneRechenSchlange()) A.befund("Ohne eigene Rechen-Warteschlange teilen sich Physik und Bild eine Warteschlange; geprüft ist dann nur dieser Weg.");
    }

    // ------------------------------------------------------------------ 2 Speicher

    private static void speicher() {
        int n = 1_000_000;
        float[] w = new float[n];
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < n; i++) w[i] = (float) (r.nextDouble() * 20 - 10);
        for (Puffer.Art art : new Puffer.Art[] {Puffer.Art.KARTE, Puffer.Art.GETEILT}) {
            try (Puffer p = Puffer.neu(g, konto, "probe", 4L * n, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, art)) {
                p.schreibe(w);
                float[] z = p.floats(n);
                int falsch = 0;
                for (int i = 0; i < n; i++) if (Float.floatToRawIntBits(z[i]) != Float.floatToRawIntBits(w[i])) falsch++;
                // ein Stück in der Mitte überschreiben und nur dieses lesen
                ByteBuffer st = org.lwjgl.system.MemoryUtil.memAlloc(4 * 1000);
                try {
                    for (int i = 0; i < 1000; i++) st.putFloat(4 * i, -i);
                    p.schreibe(st, 4L * 5000);
                    st.clear();
                    p.lies(st, 4L * 5500);
                    for (int i = 0; i < 500; i++) if (st.getFloat(4 * i) != -(i + 500)) falsch++;
                    for (int i = 500; i < 1000; i++) if (st.getFloat(4 * i) != w[6000 + i - 500]) falsch++;
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(st);
                }
                p.nullen();
                float[] leer = p.floats(n);
                for (float x : leer) if (x != 0) falsch++;
                A.pruefe(falsch == 0 && konto.belegt() == 4L * n, "Puffer " + art + ": 1 Mio. Werte hin und zurück, ein Stück mit Versatz, mit Nullen gefüllt", falsch + " falsche Werte",
                        "bitgleich");
            }
        }
        Konto klein = g.konto("GpuCheck klein", 1 << 20);
        String meldung = "";
        try (Puffer a = Puffer.speicher(g, klein, "a", 600 << 10)) {
            try (Puffer b = Puffer.speicher(g, klein, "b", 600 << 10)) {
                meldung = "zugeteilt: " + a + ", " + b;
            } catch (IllegalStateException e) {
                meldung = e.getMessage();
            }
            A.pruefe(meldung.contains("geht über die Grenze") && klein.belegt() == 600 << 10, "Speicherkonto: Zuteilung über die Grenze des Moduls", meldung, "abgelehnt, Konto unverändert");
        }
        A.pruefe(klein.belegt() == 0 && klein.spitze() == 600 << 10, "Speicherkonto nach der Freigabe", klein + ", Spitze " + Geraet.mb(klein.spitze()), "0 belegt");
        String gross = "";
        try {
            Puffer.speicher(g, null, "riesig", Math.min(g.groessteZuteilung, g.groessterPuffer) + 4096).close();
            gross = "zugeteilt";
        } catch (IllegalArgumentException e) {
            gross = e.getMessage();
        }
        A.pruefe(gross.contains("in Stücken führen"), "Puffer über der größten Zuteilung des Geräts", gross, "abgelehnt mit Hinweis");
    }

    // ------------------------------------------------------------------ 3 Shader und Kernel

    private static void kernel() {
        // Übersetzen: Include aus dem Klassenpfad, Fehlermeldung mit Datei, vorgebaut gleich frisch
        Shader.Spirv vor = Shader.laden(PFAD + "stufe.comp"), frisch = Shader.frisch(PFAD + "stufe.comp");
        if (vor.vorgebaut) A.pruefe(vor.code.equals(frisch.code), "Vorgebautes SPIR-V (Ant-Ziel spirv) gegen zur Laufzeit übersetzt, mit #include", vor.code.remaining() + " und "
                + frisch.code.remaining() + " Byte", "bytegleich");
        else A.info("SPIR-V", vor.woher + f(", %.0f ms (vorgebaut wird mit Clean and Build; Ant-Ziel spirv)", vor.ms));
        A.pruefe(frisch.code.remaining() > 400 && frisch.code.getInt(0) == 0x07230203, "GLSL nach SPIR-V mit #include \"/com/dan/…\" aus dem Klassenpfad", frisch.code.remaining() + " Byte, "
                + f("%.0f ms", frisch.ms), "gültiger Kopf");
        vor.frei();
        frisch.frei();
        // ein vorgebautes SPIR-V gilt nur, solange seine Quellen dieselben sind
        byte[] q1 = "void main() { }".getBytes(java.nio.charset.StandardCharsets.UTF_8), q2 = "float eins() { return 1.0; }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] liste = (Shader.sha256(q1) + " /a.comp\n" + Shader.sha256(q2) + " /b.glsl\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean gleich = Shader.aktuell(liste, p -> p.equals("/a.comp") ? q1 : p.equals("/b.glsl") ? q2 : null);
        boolean geaendert = Shader.aktuell(liste, p -> p.equals("/a.comp") ? q1 : "float eins() { return 2.0; }".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        boolean fehlt = Shader.aktuell(liste, p -> p.equals("/a.comp") ? q1 : null), ohne = Shader.aktuell(null, p -> q1);
        A.pruefe(gleich && !geaendert && !fehlt && !ohne, "Vorgebautes SPIR-V gilt nur mit unveränderten Quellen (Shader und Eingebundenes)",
                "unverändert " + ja(gleich) + ", Eingebundenes geändert " + ja(geaendert) + ", Quelle fehlt " + ja(fehlt) + ", ohne Liste " + ja(ohne), "ja, nein, nein, nein");
        String fehler = "";
        try {
            Shader.uebersetzen("#version 460\nlayout(local_size_x = 1) in;\nvoid main() { float x = nichtda; }\n", "kaputt.comp", Shader.art("kaputt.comp")).frei();
        } catch (IllegalStateException e) {
            fehler = e.getMessage();
        }
        A.pruefe(fehler.contains("kaputt.comp") && fehler.contains("nichtda"), "Fehler im Shader: Meldung nennt Datei und Stelle", fehler.replace('\n', ' ').trim(), "Datei und Name");
        Shader.Gl gl = Shader.ausGl("layout(local_size_x = 64) in;\nuniform int nx, nz;\nuniform float dt;\nvoid main() { float t = dt * float(nx + nz); }\n", true);
        try (MemoryStack s = stackPush()) {
            ByteBuffer p = gl.packen(s, "nz", 7, "dt", 0.25f, "gibtsnicht", 1);
            Shader.Spirv u = Shader.uebersetzen(gl.quelle, "bestand.comp", Shader.art("x.comp"));
            A.pruefe(gl.groesse == 12 && gl.werte.size() == 3 && p.getInt(4) == 7 && p.getFloat(8) == 0.25f && p.getInt(0) == 0 && u.code.remaining() > 100,
                    "Shader des Bestands (GLSL 430, lose Uniforms) nach Vulkan: Push-Konstanten in derselben Reihenfolge", gl.werte.size() + " Werte, " + gl.groesse + " Byte", "3 Werte, 12 Byte");
            u.frei();
        }

        // saxpy: y = a·x + y für n Werte, n kein Vielfaches der Gruppe; der Rest des Puffers bleibt
        int n = 1_000_003, lang = n + 61;
        float a = 0.37f;
        float[] x = new float[lang], y = new float[lang];
        SplittableRandom r = new SplittableRandom(2);
        for (int i = 0; i < lang; i++) {
            x[i] = (float) (r.nextDouble() * 8 + 1);
            y[i] = (float) (r.nextDouble() * 8 + 1);
        }
        try (Kernel k = Kernel.lade(g, PFAD + "saxpy.comp", 8, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
             Puffer px = Puffer.speicher(g, konto, "x", 4L * lang); Puffer py = Puffer.speicher(g, konto, "y", 4L * lang)) {
            px.schreibe(x);
            py.schreibe(y);
            try (MemoryStack s = stackPush()) {
                Ablauf ab = new Ablauf(g).rechne("saxpy", k, k.satz(px, py), s.malloc(8).putInt(0, n).putFloat(4, a), Kernel.gruppen(n, 64), 1, 1, new Mittel[] {px}, new Mittel[] {py});
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            }
            float[] z = py.floats(lang);
            double schritte = 0;
            int rest = 0;
            for (int i = 0; i < n; i++) schritte = Math.max(schritte, Math.abs(z[i] - (a * x[i] + y[i])) / Math.ulp(z[i]));
            for (int i = n; i < lang; i++) if (z[i] != y[i]) rest++;
            A.hoechstens("Kernel saxpy, 1 000 003 Werte, gegen Java: größte Abweichung in float-Schritten", schritte, 0, "");
            A.pruefe(rest == 0, "Werte hinter dem Ende (die letzte Gruppe ist nicht voll) bleiben unberührt", rest + " verändert", "0");
        }

        // float16: die Tabellen des Subgrids liegen als float16 vor. Lesen ist genau; beim Packen darf die Karte runden, wie sie will.
        int m16 = 4096;
        short[] tab = new short[m16];
        float[] roh = new float[m16];
        for (int i = 0; i < m16; i++) {
            roh[i] = (i - 2048) / 64f + (i % 3) * 1e-4f;
            tab[i] = Float.floatToFloat16(roh[i] * 0.37f);
        }
        try (Kernel k = Kernel.lade(g, PFAD + "halbwort.comp", 4, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Puffer pt = Puffer.speicher(g, konto, "tabelle", 2L * m16);
             Puffer pa = Puffer.speicher(g, konto, "aus", 4L * m16)) {
            pt.schreibe(tab);
            try (MemoryStack s = stackPush()) {
                Ablauf ab = new Ablauf(g).rechne("halbwort", k, k.satz(pt, pa), s.malloc(4).putInt(0, m16), m16 / 64, 1, 1, new Mittel[] {pt}, new Mittel[] {pa});
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            }
            float[] z = pa.floats(m16);
            int falsch = 0;
            for (int i = 0; i < m16; i++) if (z[i] != Float.float16ToFloat(tab[i])) falsch++;
            A.pruefe(falsch == 0, "Tabelle in float16, in Java gepackt, im Kernel über unpackHalf2x16 gelesen (geht auf jedem Gerät)", falsch + " von " + m16 + " Werten anders", "0");
        }
        if (g.kann.float16()) {
            try (Kernel k = Kernel.lade(g, PFAD + "halb.comp", 4, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
                 Puffer pt = Puffer.speicher(g, konto, "tabelle", 2L * m16); Puffer pa = Puffer.speicher(g, konto, "aus", 4L * m16); Puffer pe = Puffer.speicher(g, konto, "ein", 4L * m16);
                 Puffer pg = Puffer.speicher(g, konto, "gepackt", 2L * m16)) {
                pt.schreibe(tab);
                pe.schreibe(roh);
                try (MemoryStack s = stackPush()) {
                    Ablauf ab = new Ablauf(g).rechne("halb", k, k.satz(pt, pa, pe, pg), s.malloc(4).putInt(0, m16), m16 / 64, 1, 1, new Mittel[] {pt, pe}, new Mittel[] {pa, pg});
                    g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                }
                float[] z = pa.floats(m16);
                short[] gp = pg.shorts(m16);
                int falsch = 0, naechster = 0;
                double schritt = 0;
                for (int i = 0; i < m16; i++) {
                    if (z[i] != Float.float16ToFloat(tab[i])) falsch++;
                    if (gp[i] == Float.floatToFloat16(roh[i])) naechster++;
                    schritt = Math.max(schritt, Math.abs(Float.float16ToFloat(gp[i]) - roh[i]) / halbSchritt(roh[i]));
                }
                A.pruefe(falsch == 0, "Dieselbe Tabelle als float16_t im Puffer gelesen", falsch + " von " + m16 + " Werten anders", "0");
                A.hoechstens("Kernel packt float nach float16: Abweichung in Schritten von float16", schritt, 1, "");
                A.info("Rundung beim Packen", naechster + " von " + m16 + " Werten wie in Java (zum nächsten)" + (naechster == m16 ? "" : "; die Karte rundet anders – Tabellen packt der Prozessor"));
            }
        } else A.info("float16", "kann das Gerät nicht (Rechnen in float16 oder float16 im Puffer); Tabellen gehen über unpackHalf2x16");
        if (g.kann.untergruppenArithmetik()) {
            int m = 64 * 64;
            try (Kernel k = Kernel.lade(g, PFAD + "gruppe.comp", 0, null, Kernel.Bindung.PUFFER); Puffer p = Puffer.speicher(g, konto, "gruppe", 4L * m)) {
                p.nullen();
                Ablauf ab = new Ablauf(g).rechne("gruppe", k, k.satz(p), null, m / 64, 1, 1, new Mittel[0], new Mittel[] {p});
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                float[] z = p.floats(m);
                float kleinste = Float.MAX_VALUE, groesste = 0;
                for (float v : z) {
                    kleinste = Math.min(kleinste, v);
                    groesste = Math.max(groesste, v);
                }
                A.pruefe(kleinste >= 1 && groesste <= g.kann.untergruppeMax() && groesste >= Math.min(g.kann.untergruppeMin(), 64), "Kernel mit Untergruppen-Summe: tatsächliche Größe der Untergruppe",
                        f("%.0f bis %.0f", kleinste, groesste), "1 bis " + g.kann.untergruppeMax());
            }
        } else A.info("Untergruppen", "kann das Gerät nicht; nicht geprüft");

        // Feldtafel: Felder nach Nummer
        int m = 10_000;
        float[] fa = new float[m], fb = new float[m];
        for (int i = 0; i < m; i++) {
            fa[i] = i * 0.25f;
            fb[i] = 1000 - i;
        }
        try (Feldtafel t = new Feldtafel(g); Kernel k = Kernel.lade(g, PFAD + "tafel.comp", 16, t);
             Puffer pa = Puffer.speicher(g, konto, "a", 4L * m); Puffer pb = Puffer.speicher(g, konto, "b", 4L * m); Puffer pc = Puffer.speicher(g, konto, "c", 4L * m);
             Puffer pd = Puffer.speicher(g, konto, "d", 4L * m)) {
            pa.schreibe(fa);
            pb.schreibe(fb);
            int na = t.trage(pa), nb = t.trage(pb), nc = t.trage(pc);
            try (MemoryStack s = stackPush()) {
                Ablauf ab = new Ablauf(g).rechne("c = a + b", k, 0, s.malloc(16).putInt(0, m).putInt(4, na).putInt(8, nb).putInt(12, nc), Kernel.gruppen(m, 64), 1, 1, new Mittel[] {pa, pb},
                        new Mittel[] {pc});
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                // ein Platz wird geräumt und neu belegt, dann d = c + c über dieselbe Pipeline
                t.streiche(nb);
                int nd = t.trage(pd);
                Ablauf ab2 = new Ablauf(g).rechne("d = c + c", k, 0, s.malloc(16).putInt(0, m).putInt(4, nc).putInt(8, nc).putInt(12, nd), Kernel.gruppen(m, 64), 1, 1, new Mittel[] {pc},
                        new Mittel[] {pd});
                g.rechnen.einmal(c -> ab2.aufnehmen(c, null));
                float[] zc = pc.floats(m), zd = pd.floats(m);
                int falsch = 0;
                for (int i = 0; i < m; i++) if (zc[i] != fa[i] + fb[i] || zd[i] != 2 * zc[i]) falsch++;
                A.pruefe(falsch == 0 && nd == nb && t.trage(pa) == na && t.belegt() == 3, "Feldtafel: Kernel wählt Felder über Nummern; ein Platz geräumt und neu belegt", falsch
                        + " falsche Werte, Nummern " + na + ", " + nb + ", " + nc + ", " + nd, "0 falsch");
            }
        }

        // Bild: ein Kernel schreibt ein Speicherbild, gelesen wird es über eine Kopie
        try (Kernel k = Kernel.lade(g, PFAD + "bild.comp", 0, null, Kernel.Bindung.BILD, Kernel.Bindung.PUFFER); Bild b = new Bild(g, konto, "probe", 200, 150, Bild.R32F);
             Puffer q = Puffer.speicher(g, konto, "q", 16)) {
            q.schreibe(new float[] {0.5f, 0, 0, 0});
            Ablauf ab = new Ablauf(g).rechne("bild", k, k.satz(b, q), null, 25, 19, 1, new Mittel[] {q}, new Mittel[] {b});
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            float[] z = b.lies();
            int falsch = 0;
            for (int j = 0; j < 150; j++) for (int i = 0; i < 200; i++) if (z[i + 200 * j] != i + 1000f * j + 0.5f) falsch++;
            A.pruefe(falsch == 0, "Kernel schreibt ein Speicherbild (R32F, 200 × 150); der Ablauf wechselt die Anordnung zum Schreiben und zum Lesen", falsch + " falsche Bildpunkte", "0");
        }
    }

    /** Abstand benachbarter float16-Werte beim Betrag von v. */
    private static double halbSchritt(float v) {
        short h = Float.floatToFloat16(Math.abs(v));
        return Math.max(Float.float16ToFloat((short) (h + 1)) - Float.float16ToFloat(h), 6e-8);
    }

    // ------------------------------------------------------------------ 4 Ablauf und Sperren

    private static void ablauf() {
        // Sperren: nur wo nötig. A schreibt X; B liest X, schreibt Y; C liest X, Y, schreibt Z; D liest X, Y, schreibt W; E liest Z, schreibt X.
        int n = 4096;
        float[] eins = new float[n];
        java.util.Arrays.fill(eins, 1f);
        try (Kernel k = Kernel.lade(g, PFAD + "saxpy.comp", 8, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
             Puffer q = Puffer.speicher(g, konto, "Q", 4L * n); Puffer x = Puffer.speicher(g, konto, "X", 4L * n); Puffer y = Puffer.speicher(g, konto, "Y", 4L * n);
             Puffer z = Puffer.speicher(g, konto, "Z", 4L * n); Puffer w = Puffer.speicher(g, konto, "W", 4L * n)) {
            q.schreibe(eins);
            for (Puffer p : new Puffer[] {x, y, z, w}) p.nullen();
            try (MemoryStack s = stackPush()) {
                ByteBuffer p1 = s.malloc(8).putInt(0, n).putFloat(4, 1f), p2 = s.malloc(8).putInt(0, n).putFloat(4, 2f);
                Ablauf ab = new Ablauf(g)
                        .rechne("A", k, k.satz(q, x), p1, n / 64, 1, 1, new Mittel[] {q}, new Mittel[] {x})                     // X = 1
                        .rechne("B", k, k.satz(x, y), p2, n / 64, 1, 1, new Mittel[] {x}, new Mittel[] {y})                     // Y = 2
                        .rechne("C1", k, k.satz(x, z), p1, n / 64, 1, 1, new Mittel[] {x, y}, new Mittel[] {z})                  // Z = 1
                        .rechne("C2", k, k.satz(y, z), p1, n / 64, 1, 1, new Mittel[] {x, y}, new Mittel[] {z})                  // Z = 3
                        .rechne("D", k, k.satz(y, w), p2, n / 64, 1, 1, new Mittel[] {x, y}, new Mittel[] {w})                   // W = 4
                        .rechne("E", k, k.satz(z, x), p2, n / 64, 1, 1, new Mittel[] {z}, new Mittel[] {x});                     // X = 1 + 2·3 = 7
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                float[] fx = x.floats(n), fy = y.floats(n), fz = z.floats(n), fw = w.floats(n);
                int falsch = 0;
                for (int i = 0; i < n; i++) if (fx[i] != 7 || fy[i] != 2 || fz[i] != 3 || fw[i] != 4) falsch++;
                // Q: zuletzt übertragen (kein Stand) → erster Zugriff ohne Sperre. B: X. C1: Y. C2: Z (Schreiben nach Schreiben). D: nichts. E: Z und X.
                A.pruefe(ab.sperren() == 4 && ab.gesperrt() == 5 && falsch == 0, "Sechs Durchgänge über fünf Puffer: Sperren nur, wo auf Schreiben gelesen oder auf Lesen geschrieben wird",
                        ab.sperren() + " Sperrbefehle über " + ab.gesperrt() + " Puffer, " + falsch + " falsche Werte", "4 Sperrbefehle, 5 Puffer, 0 falsch");
            }
        }

        // Gegenprobe: dieselbe Folge ohne gemeldete Zugriffe hat keine Sperren; die Validierung muss das finden
        if (g.synchronPruefung) {
            int vorher = g.validierungsFehler;
            try (Kernel k = Kernel.lade(g, PFAD + "saxpy.comp", 8, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
                 Puffer q = Puffer.speicher(g, konto, "Q", 4L * n); Puffer x = Puffer.speicher(g, konto, "X", 4L * n); MemoryStack s = stackPush()) {
                ByteBuffer p1 = s.malloc(8).putInt(0, n).putFloat(4, 1f);
                long hin = k.satz(q, x), her = k.satz(x, q);
                Ablauf ohne = new Ablauf(g).durchgang("hin", new Ablauf.Zugriff[0], c -> k.aufnehmen(c, hin, p1, n / 64, 1, 1))
                        .durchgang("her", new Ablauf.Zugriff[0], c -> k.aufnehmen(c, her, p1, n / 64, 1, 1));
                g.rechnen.einmal(c -> ohne.aufnehmen(c, null));
            }
            gegenprobe = g.validierungsFehler - vorher;
            A.pruefe(gegenprobe >= 1, "Gegenprobe: zwei Durchgänge ohne gemeldete Zugriffe (also ohne Sperre) – die Validierung findet es", gegenprobe + " Fehler gemeldet", "≥ 1");
        }

        // 100 Schritte im Wechsel zwischen zwei Puffern, gegen Java
        int nx = 200, ny = 150, schritte = 100;
        float kk = 0.2f;
        float[] u = new float[nx * ny];
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < u.length; i++) u[i] = (float) (5 + 3 * Math.sin(i * 0.01) + r.nextDouble());
        float[] ja = u.clone(), jb = new float[u.length], nach1 = null;
        for (int st = 0; st < schritte; st++) {
            stufe(ja, jb, nx, ny, kk);
            float[] h = ja;
            ja = jb;
            jb = h;
            if (st == 0) nach1 = ja.clone();
        }
        try (Kernel k = Kernel.lade(g, PFAD + "stufe.comp", 12, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
             Puffer a = Puffer.speicher(g, konto, "A", 4L * u.length); Puffer b = Puffer.speicher(g, konto, "B", 4L * u.length)) {
            a.schreibe(u);
            try (MemoryStack s = stackPush()) {
                ByteBuffer p = s.malloc(12).putInt(0, nx).putInt(4, ny).putFloat(8, kk);
                Ablauf einer = new Ablauf(g).rechne("stufe", k, k.satz(a, b), p, Kernel.gruppen(nx * ny, 64), 1, 1, new Mittel[] {a}, new Mittel[] {b});
                g.rechnen.einmal(c -> einer.aufnehmen(c, null));
                float[] z1 = b.floats(u.length);
                double d1 = 0;
                for (int i = 0; i < u.length; i++) d1 = Math.max(d1, Math.abs(z1[i] - nach1[i]) / Math.ulp(nach1[i]));
                A.hoechstens("Ein Schritt eines Ausgleichs auf 200 × 150 Zellen gegen Java (Kernel gleich): float-Schritte", d1, 0, "");
                Ablauf ab = new Ablauf(g);
                for (int st = 1; st < schritte; st++) {
                    Puffer von = st % 2 == 1 ? b : a, zu = st % 2 == 1 ? a : b;
                    ab.rechne("stufe", k, k.satz(von, zu), p, Kernel.gruppen(nx * ny, 64), 1, 1, new Mittel[] {von}, new Mittel[] {zu});
                }
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                float[] z = (schritte % 2 == 1 ? b : a).floats(u.length);
                double d = 0;
                int nan = 0;
                for (int i = 0; i < u.length; i++) {
                    if (Float.isNaN(z[i])) nan++;
                    d = Math.max(d, Math.abs(z[i] - ja[i]) / Math.ulp(ja[i]));
                }
                A.pruefe(nan == 0 && ab.sperren() == 99 && ab.gesperrt() == 198, "100 Schritte im Wechsel zweier Puffer in einem Befehlspuffer", f("größte Abweichung %.0f float-Schritte, %d NaN, ", d, nan)
                        + ab.sperren() + " Sperrbefehle", "kein NaN; je Schritt eine Sperre über beide Puffer");
                A.hoechstens("100 Schritte gegen Java: float-Schritte (nur +, −, · mit „precise“)", d, 0, "");
            }
        }
    }

    /** Derselbe Schritt wie {@code stufe.comp}, in float und in derselben Reihenfolge. */
    private static void stufe(float[] u, float[] w, int nx, int ny, float k) {
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                float c = u[i + nx * j];
                float ost = u[Math.min(i + 1, nx - 1) + nx * j] + u[Math.max(i - 1, 0) + nx * j];
                float nord = u[i + nx * Math.min(j + 1, ny - 1)] + u[i + nx * Math.max(j - 1, 0)];
                float summe = ost + nord, vier = 4.0f * c, d = summe - vier, kd = k * d;
                w[i + nx * j] = c + kd;
            }
    }

    // ------------------------------------------------------------------ 5 Zeitleiste und Fäden

    private static void zeitleiste() throws Exception {
        int n = 1 << 16;
        float[] eins = new float[n];
        java.util.Arrays.fill(eins, 1f);
        try (Kernel k = Kernel.lade(g, PFAD + "saxpy.comp", 8, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Zeitleiste physik = new Zeitleiste(g); Zeitleiste bild = new Zeitleiste(g);
             Puffer q = Puffer.speicher(g, konto, "Q", 4L * n); Puffer x = Puffer.speicher(g, konto, "X", 4L * n); Puffer y = Puffer.speicher(g, konto, "Y", 4L * n)) {
            q.schreibe(eins);
            x.nullen();
            y.nullen();
            A.pruefe(physik.stand() == 0 && !physik.warte(1, 2_000_000), "Zeitleiste vor der ersten Arbeit", "Stand " + physik.stand() + ", Warten auf 1 läuft nach 2 ms ab", "Stand 0, Zeit abgelaufen");
            try (MemoryStack s = stackPush()) {
                ByteBuffer p3 = s.malloc(8).putInt(0, n).putFloat(4, 3f);
                // Physik: X = 3 auf der Rechen-Warteschlange. Bild: Y = X kopieren auf der Grafik-Warteschlange, wartet auf der Karte auf die Physik.
                Ablauf rechnen = new Ablauf(g).rechne("X = 3", k, k.satz(q, x), p3, n / 64, 1, 1, new Mittel[] {q}, new Mittel[] {x});
                Ablauf kopie = new Ablauf(g, g.grafik).durchgang("Y = X", new Ablauf.Zugriff[] {new Ablauf.Zugriff(x, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                        new Ablauf.Zugriff(y, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                    try (MemoryStack t = stackPush()) {
                        vkCmdCopyBuffer(c, x.handle, y.handle, VkBufferCopy.calloc(1, t).size(4L * n));
                    }
                });
                long wPhysik, wBild;
                boolean vorher = g.kann.eigeneRechenSchlange();
                if (vorher) {                                           // das Bild wird zuerst abgeschickt und wartet auf einen Wert, den es noch nicht gibt
                    VkCommandBuffer cb = g.grafik.befehle();
                    kopie.aufnehmen(cb, null);
                    wBild = g.grafik.schicke(cb, bild, physik, 1);
                    Thread.sleep(20);
                    boolean wartet = bild.stand() < wBild;
                    VkCommandBuffer cr = g.rechnen.befehle();
                    rechnen.aufnehmen(cr, null);
                    wPhysik = g.rechnen.schicke(cr, physik, null, 0);
                    A.pruefe(wartet, "Das Bild, zuerst abgeschickt, wartet auf der Karte auf die Physik", wartet ? "wartet" : "lief schon", "wartet");
                } else {
                    VkCommandBuffer cr = g.rechnen.befehle();
                    rechnen.aufnehmen(cr, null);
                    wPhysik = g.rechnen.schicke(cr, physik, null, 0);
                    VkCommandBuffer cb = g.grafik.befehle();
                    kopie.aufnehmen(cb, null);
                    wBild = g.grafik.schicke(cb, bild, physik, wPhysik);
                }
                boolean fertig = bild.warte(wBild, 10_000_000_000L);
                float[] fy = y.floats(n);
                int falsch = 0;
                for (float v : fy) if (v != 3) falsch++;
                A.pruefe(fertig && falsch == 0 && physik.stand() == wPhysik && wPhysik == 1 && physik.vergeben() == 1, "Physik rechnet, das Bild wartet über die Zeitleiste und liest den fertigen Stand"
                                + (vorher ? " (zwei Warteschlangen)" : " (eine Warteschlange)"), falsch + " falsche Werte, Zeitleiste der Physik bei " + physik.stand(), "0 falsch, Stand 1");
            }

            // eine Zeitleiste vergeht vor der Warteschlange: die arbeitet weiter, und die geschlossene Zeitleiste sagt es
            Zeitleiste kurz = new Zeitleiste(g);
            for (com.dan.core.gpu.Schlange q2 : new com.dan.core.gpu.Schlange[] {g.rechnen, g.grafik}) kurz.warte(q2.schicke(q2.befehle(), kurz, null, 0), 10_000_000_000L);
            kurz.close();
            String danach;
            try {
                g.rechnen.einmal(c -> { });
                g.grafik.einmal(c -> { });
                kurz.stand();
                danach = "die geschlossene Zeitleiste gab noch einen Stand";
            } catch (IllegalStateException e) {
                danach = e.getMessage();
            }
            A.pruefe(danach.contains("geschlossen"), "Eine Zeitleiste wird geschlossen, die Warteschlangen arbeiten weiter", danach, "Arbeit läuft; Zeitleiste meldet sich geschlossen");

            // zwei Fäden: die Physik schickt 200 Arbeiten ab, während das Bild überträgt und liest
            x.nullen();
            Throwable[] fehler = new Throwable[1];
            long[] letzter = new long[1];
            Thread t = new Thread(() -> {
                try (MemoryStack s = stackPush()) {
                    ByteBuffer p1 = s.malloc(8).putInt(0, n).putFloat(4, 1f);
                    Ablauf ab = new Ablauf(g).rechne("X += 1", k, k.satz(q, x), p1, n / 64, 1, 1, new Mittel[] {q}, new Mittel[] {x});
                    for (int i = 0; i < 200; i++) {
                        VkCommandBuffer c = g.rechnen.befehle();
                        ab.aufnehmen(c, null);
                        letzter[0] = g.rechnen.schicke(c, physik, null, 0);
                    }
                } catch (Throwable e) {
                    fehler[0] = e;
                }
            }, "physik");
            t.start();
            int falsch = 0, runden = 0;
            float[] muster = new float[4096];
            try (Puffer z = Puffer.speicher(g, konto, "Z", 4L * muster.length)) {
                while (t.isAlive() || runden < 20) {
                    for (int i = 0; i < muster.length; i++) muster[i] = runden + i;
                    z.schreibe(muster);
                    float[] zurueck = z.floats(muster.length);
                    for (int i = 0; i < muster.length; i++) if (zurueck[i] != muster[i]) falsch++;
                    runden++;
                }
            }
            t.join();
            physik.warte(letzter[0], 30_000_000_000L);
            float[] fx = x.floats(n);
            for (float v : fx) if (v != 200) falsch++;
            A.pruefe(fehler[0] == null && falsch == 0 && physik.stand() == 201, "Zwei Fäden: 200 Arbeiten der Physik ohne Warten, daneben " + runden + " Übertragungen des Bilds",
                    (fehler[0] == null ? "" : fehler[0] + "; ") + falsch + " falsche Werte, Zeitleiste bei " + physik.stand(), "0 falsch, Stand 201");
        }
    }

    // ------------------------------------------------------------------ 6 Zeiten

    private static void zeiten() {
        int nx = 512, ny = 512;
        try (Kernel k = Kernel.lade(g, PFAD + "stufe.comp", 12, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 16);
             Puffer a = Puffer.speicher(g, konto, "A", 4L * nx * ny); Puffer b = Puffer.speicher(g, konto, "B", 4L * nx * ny)) {
            if (!uhr.verfuegbar) {
                A.info("Zeitstempel", "hat die Rechen-Warteschlange nicht; nicht geprüft");
                return;
            }
            a.nullen();
            try (MemoryStack s = stackPush()) {
                ByteBuffer p = s.malloc(12).putInt(0, nx).putInt(4, ny).putFloat(8, 0.2f);
                Ablauf ab = new Ablauf(g);
                for (int i = 0; i < 6; i++) ab.rechne(i < 2 ? "hin" : i < 4 ? "her" : "noch", k, i % 2 == 0 ? k.satz(a, b) : k.satz(b, a), p, Kernel.gruppen(nx * ny, 64), 1, 1,
                        new Mittel[] {i % 2 == 0 ? a : b}, new Mittel[] {i % 2 == 0 ? b : a});
                long wand = g.rechnen.einmal(c -> {
                    uhr.runde(c);
                    uhr.start(c, "gesamt");
                    ab.aufnehmen(c, uhr);
                    uhr.stopp(c, "gesamt");
                });
                int gelesen = uhr.sammle(true);
                double teile = uhr.letzte("hin") + uhr.letzte("her") + uhr.letzte("noch"), gesamt = uhr.letzte("gesamt");
                A.pruefe(gelesen == 1 && uhr.abschnitte().size() == 4 && uhr.letzte("hin") > 0 && uhr.letzte("her") > 0 && uhr.letzte("noch") > 0 && teile <= gesamt * 1.001 + 0.05
                                && gesamt <= wand * 1e-6 + 0.05, "Zeitstempel je Abschnitt: drei Abschnitte (je zwei Durchgänge) in einem gemessenen Ganzen",
                        f("hin %.2f, her %.2f, noch %.2f, gesamt %.2f ms; Wand %.2f ms", uhr.letzte("hin"), uhr.letzte("her"), uhr.letzte("noch"), gesamt, wand * 1e-6),
                        "Teile ≤ Ganzes ≤ Wand");
                // sechs Runden ohne zu lesen: der Ring hält vier
                for (int i = 0; i < 6; i++)
                    g.rechnen.einmal(c -> {
                        uhr.runde(c);
                        uhr.start(c, "gesamt");
                        ab.aufnehmen(c, null);
                        uhr.stopp(c, "gesamt");
                    });
                int danach = uhr.sammle(false);
                A.pruefe(uhr.verloren() == 2 && danach == 4 && uhr.ms("gesamt") > 0, "Ring von vier Runden: sechs Runden ohne Lesen", uhr.verloren() + " verloren, " + danach + " gelesen", "2 verloren, 4 gelesen");
            }
        }
    }

    // ------------------------------------------------------------------ 7 Felder auf der Karte

    /** Schreibt {@code water.eta} auf der Karte: je Kopplungsschritt η ← η + 0,125 in jeder Zelle. */
    private static final class KartenHydro implements Modul {
        final Gitter gitter;
        final Karte karte;
        final Kernel saxpy;
        final Puffer eins;
        final Zeitleiste leiste;
        Feld alt;

        KartenHydro(Gitter gitter, Karte karte, Kernel saxpy, Puffer eins, Zeitleiste leiste) {
            this.gitter = gitter;
            this.karte = karte;
            this.saxpy = saxpy;
            this.eins = eins;
            this.leiste = leiste;
        }

        @Override public String name() { return "hydro"; }
        @Override public void anmelden(Anmeldung a) { a.kopplung(20).schreibt("water.eta", "m NHN", Ort.ZELLE, gitter, Feld.Zahl.FLOAT); }

        @Override public void beginn(double t0, Bus bus) {
            Feld feld = bus.schreibfeld(this, "water.eta");
            for (int i = 0; i < feld.laenge; i++) feld.f[i] = 1.5f + 0.001f * i;
            karte.an(feld);
            feld.hoch();
            alt = feld;
            bus.lege(this, "water.eta", t0);
        }

        @Override public void schritt(double t0, double t1, Bus bus) {
            Feld feld = bus.schreibfeld(this, "water.eta");
            KartenFeld neu = karte.an(feld), vor = (KartenFeld) alt.karte;
            try (MemoryStack s = stackPush()) {
                Ablauf ab = new Ablauf(g).durchgang("η übernehmen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(vor.puffer, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                        new Ablauf.Zugriff(neu.puffer, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                    try (MemoryStack t = stackPush()) {
                        vkCmdCopyBuffer(c, vor.puffer.handle, neu.puffer.handle, VkBufferCopy.calloc(1, t).size(4L * feld.laenge));
                    }
                }).rechne("η + 0,125", saxpy, saxpy.satz(eins, neu.puffer), s.malloc(8).putInt(0, feld.laenge).putFloat(4, 0.125f), Kernel.gruppen(feld.laenge, 64), 1, 1,
                        new Mittel[] {eins}, new Mittel[] {neu.puffer});
                VkCommandBuffer c = g.rechnen.befehle();
                ab.aufnehmen(c, null);
                neu.geschrieben(feld, leiste, g.rechnen.schicke(c, leiste, null, 0));
            }
            feld.herunter();                     // ausdrücklich: Leser auf dem Prozessor brauchen den Stand
            alt = feld;
            bus.lege(this, "water.eta", t1);
        }
    }

    private static void felder() {
        Katalog kat = Katalog.grundstufe();
        Gitter gi = Gitter.utm("P", 32, 468_000, 6_046_000, 20, 64, 48);
        try (Karte karte = new Karte(g, konto)) {
            Feld fl = new Feld(kat.verlange("water.h"), gi, Feld.Zahl.FLOAT), db = new Feld(kat.verlange("water.eta"), gi, Feld.Zahl.DOUBLE);
            for (int i = 0; i < fl.laenge; i++) {
                fl.f[i] = 0.1f * i;
                db.d[i] = Math.PI * i;
            }
            karte.an(fl);
            KartenFeld kd = karte.an(db, Puffer.Art.GETEILT);
            fl.hoch();
            db.hoch();
            boolean flaggen = !fl.prozessorNeuer && !fl.karteNeuer;
            float[] fa = fl.f.clone();
            double[] da = db.d.clone();
            fl.fuellen(-1);
            db.fuellen(-1);
            fl.herunter();
            db.herunter();
            int falsch = 0;
            for (int i = 0; i < fl.laenge; i++) if (fl.f[i] != fa[i] || db.d[i] != (double) (float) da[i]) falsch++;
            A.pruefe(falsch == 0 && flaggen && karte.an(db) == kd && karte.felder() == 2, "Feld hoch und herunter: float bitgleich, double über float; je Feld eine Seite auf der Karte",
                    falsch + " falsche Werte", "0");
        }
        // ein Modul rechnet water.eta auf der Karte; der Bus hält drei Stände und ein Schreibfeld, das Bild liest dazwischen
        int n = 64 * 48;
        float[] eins = new float[n];
        java.util.Arrays.fill(eins, 1f);
        try (Karte karte = new Karte(g, konto); Kernel k = Kernel.lade(g, PFAD + "saxpy.comp", 8, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Zeitleiste leiste = new Zeitleiste(g);
             Puffer pe = Puffer.speicher(g, konto, "eins", 4L * n)) {
            pe.schreibe(eins);
            Bus bus = new Bus(kat);
            KartenHydro hydro = new KartenHydro(gi, karte, k, pe, leiste);
            Modul bild = new Modul() {
                @Override public String name() { return "render.water"; }
                @Override public void anmelden(Anmeldung a) { a.ohneTakt().liest("water.eta", "m NHN", Ort.ZELLE, gi); }
                @Override public void beginn(double t0, Bus b) { }
                @Override public void schritt(double t0, double t1, Bus b) { }
            };
            bus.anmelden(hydro).anmelden(bild).verdrahten();
            Takt takt = new Takt(bus);
            takt.beginn(0);
            takt.bis(200);
            Feld ziel = bus.lesefeld(bild, "water.eta");
            float[] soll9 = new float[n], soll10 = new float[n];
            for (int i = 0; i < n; i++) {
                float v = 1.5f + 0.001f * i;
                for (int st = 1; st <= 10; st++) {
                    v = v + 0.125f * 1f;
                    if (st == 9) soll9[i] = v;
                }
                soll10[i] = v;
            }
            bus.lies(bild, "water.eta", 200, ziel);
            int falsch = 0;
            for (int i = 0; i < n; i++) if (ziel.f[i] != soll10[i]) falsch++;
            bus.lies(bild, "water.eta", 185, ziel);
            for (int i = 0; i < n; i++) if (ziel.f[i] != soll9[i] + 0.25f * (soll10[i] - soll9[i])) falsch++;
            A.pruefe(falsch == 0 && karte.felder() == Bus.STAENDE + 1 && leiste.stand() == 10, "Ein Modul rechnet water.eta auf der Karte (10 Kopplungsschritte); das Bild liest am Bus zwischen zwei Ständen",
                    falsch + " falsche Werte, " + karte.felder() + " Felder auf der Karte, Zeitleiste bei " + leiste.stand(), "0 falsch, 4 Felder, Stand 10");
        }
    }

    // ------------------------------------------------------------------ 8 Durchsatz

    private static void durchsatz() {
        int nx = g.kann.software() ? 256 : 1024, ny = nx, schritte = g.kann.software() ? 20 : 100;
        float[] u = new float[nx * ny];
        for (int i = 0; i < u.length; i++) u[i] = (float) (5 + Math.sin(i * 0.001));
        StringBuilder txt = new StringBuilder();
        try (Kernel k = Kernel.lade(g, PFAD + "stufe.comp", 12, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER); Stoppuhr uhr = new Stoppuhr(g, g.rechnen, 4)) {
            for (Puffer.Art art : new Puffer.Art[] {Puffer.Art.KARTE, Puffer.Art.GETEILT}) {
                try (Puffer a = Puffer.neu(g, konto, "A", 4L * u.length, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, art); Puffer b = Puffer.neu(g, konto, "B", 4L * u.length, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, art);
                     MemoryStack s = stackPush()) {
                    a.schreibe(u);
                    ByteBuffer p = s.malloc(12).putInt(0, nx).putInt(4, ny).putFloat(8, 0.2f);
                    Ablauf ab = new Ablauf(g);
                    for (int st = 0; st < schritte; st++) ab.rechne("stufe", k, st % 2 == 0 ? k.satz(a, b) : k.satz(b, a), p, Kernel.gruppen(nx * ny, 64), 1, 1, new Mittel[] {st % 2 == 0 ? a : b},
                            new Mittel[] {st % 2 == 0 ? b : a});
                    g.rechnen.einmal(c -> ab.aufnehmen(c, null));                                     // einmal vorweg: Pipeline warm
                    long wand = g.rechnen.einmal(c -> {
                        uhr.runde(c);
                        uhr.start(c, "lauf");
                        ab.aufnehmen(c, null);
                        uhr.stopp(c, "lauf");
                    });
                    uhr.sammle(true);
                    double ms = uhr.verfuegbar ? uhr.letzte("lauf") : wand * 1e-6, zs = (double) nx * ny * schritte;
                    txt.append(txt.length() == 0 ? "" : "; ").append(art).append(f(": %.2f ns je Zellschritt (%.2f·10⁸ Zellschritte/s)", ms * 1e6 / zs, zs / (ms * 1e-3) / 1e8));
                }
            }
        }
        A.info(f("Ausgleich mit fünf Nachbarn, %d² Zellen, %d Schritte in einem Befehlspuffer", nx, schritte), txt.toString());
        if (g.kann.software()) A.befund("Software-Gerät: Der Durchsatz sagt nichts über die Grafikkarte.");
    }

    private GpuCheck() { }

    // ------------------------------------------------------------------ 9 Zeichnen (P6a, Werkbuch 12.7, Prüfstein 1)

    /** Ein Dreieck im Bildraum (Bildpunkte, y nach unten) mit Tiefe je Ecke und Nummer. */
    private record Dreieck(double[] x, double[] y, double[] z, int nummer) { }

    /** Kantenfunktion im Bild (y nach unten). */
    private static double kante(double ax, double ay, double bx, double by, double px, double py) { return (bx - ax) * (py - ay) - (by - ay) * (px - ax); }

    private static void zeichnen() {
        int W = 96, H = 80, K = 6;
        java.util.Random r = new java.util.Random(6);
        // Netz über das ganze Bild: Ecken auf 1/16 Bildpunkt, innen verschoben, am Rand auf dem Rand
        double[][] ex = new double[K + 1][K + 1], ey = new double[K + 1][K + 1], ez = new double[K + 1][K + 1];
        for (int j = 0; j <= K; j++)
            for (int i = 0; i <= K; i++) {
                double x = (double) W * i / K, y = (double) H * j / K;
                if (i > 0 && i < K) x += (r.nextInt(97) - 48) / 16.0;
                if (j > 0 && j < K) y += (r.nextInt(97) - 48) / 16.0;
                ex[j][i] = Math.round(x * 16) / 16.0;
                ey[j][i] = Math.round(y * 16) / 16.0;
                ez[j][i] = (float) (0.05 + 0.4 * r.nextDouble());          // so, wie die Karte sie bekommt
            }
        List<Dreieck> d = new java.util.ArrayList<>();
        int nr = 1;
        for (int j = 0; j < K; j++)
            for (int i = 0; i < K; i++) {
                // beide Umlaufsinne kommen vor (die Pipeline lässt nichts weg)
                d.add(new Dreieck(new double[] {ex[j][i], ex[j][i + 1], ex[j + 1][i + 1]}, new double[] {ey[j][i], ey[j][i + 1], ey[j + 1][i + 1]},
                        new double[] {ez[j][i], ez[j][i + 1], ez[j + 1][i + 1]}, nr++));
                d.add(new Dreieck(new double[] {ex[j][i], ex[j + 1][i], ex[j + 1][i + 1]}, new double[] {ey[j][i], ey[j + 1][i], ey[j + 1][i + 1]},
                        new double[] {ez[j][i], ez[j + 1][i], ez[j + 1][i + 1]}, nr++));
            }
        int netz = d.size();
        // darüber Dreiecke mit größerer Tiefe (näher bei umgekehrter Tiefe), die sich auch gegenseitig verdecken
        for (int k = 0; k < 6; k++) {
            double[] x = new double[3], y = new double[3], z = new double[3];
            for (int e = 0; e < 3; e++) {
                x[e] = r.nextInt(W * 16 + 1) / 16.0;
                y[e] = r.nextInt(H * 16 + 1) / 16.0;
                z[e] = (float) (0.3 + 0.65 * r.nextDouble());
            }
            d.add(new Dreieck(x, y, z, nr++));
        }
        float[] sch = new float[d.size() * 12];
        for (int t = 0; t < d.size(); t++)
            for (int e = 0; e < 3; e++) {
                Dreieck q = d.get(t);
                sch[t * 12 + e * 4] = (float) (q.x[e] / W * 2 - 1);
                sch[t * 12 + e * 4 + 1] = (float) (q.y[e] / H * 2 - 1);
                sch[t * 12 + e * 4 + 2] = (float) q.z[e];
                sch[t * 12 + e * 4 + 3] = q.nummer;
            }
        com.dan.core.gpu.Zeichner.Art art = new com.dan.core.gpu.Zeichner.Art();
        art.farben = new int[] {Bild.R32F};
        try (com.dan.core.gpu.Zeichner z = com.dan.core.gpu.Zeichner.lade(g, PFAD + "raster.vert", PFAD + "raster.frag", 0, null, art, Kernel.Bindung.PUFFER);
             Puffer p = Puffer.speicher(g, konto, "Scheitel", 4L * sch.length);
             Bild farbe = new Bild(g, konto, "Nummer", W, H, Bild.R32F); Bild tiefe = new Bild(g, konto, "Tiefe", W, H, Bild.D32F)) {
            p.schreibe(sch);
            long satz = z.satz(p);
            Ablauf ab = new Ablauf(g, g.grafik).zeichne("Dreiecke", new Bild[] {farbe}, new float[][] {{0, 0, 0, 0}}, tiefe, 0f,
                    Ablauf.liest(Ablauf.ZEICHNEN, p), c -> z.zeichne(c, satz, null, 3 * d.size(), 1));
            g.grafik.einmal(c -> ab.aufnehmen(c, null));
            float[] nf = farbe.lies(), tf = tiefe.lies();
            // Rasterung in Java: Mitte des Bildpunkts, Tiefe affin im Bild, größte Tiefe gewinnt
            int luecken = 0, kantig = 0, anders = 0, verglichen = 0;
            double dz = 0;
            String erstes = "", wo = "";
            for (int py = 0; py < H; py++)
                for (int px = 0; px < W; px++) {
                    double sx = px + 0.5, sy = py + 0.5, best = -1, grad = 0;
                    int wer = 0;
                    boolean nahKante = false;
                    for (Dreieck q : d) {
                        double e0 = kante(q.x[1], q.y[1], q.x[2], q.y[2], sx, sy), e1 = kante(q.x[2], q.y[2], q.x[0], q.y[0], sx, sy), e2 = kante(q.x[0], q.y[0], q.x[1], q.y[1], sx, sy);
                        double fl = e0 + e1 + e2;
                        if (Math.abs(fl) < 1e-12) continue;
                        double l0 = e0 / fl, l1 = e1 / fl, l2 = e2 / fl;
                        double a0 = Math.abs(e0) / Math.hypot(q.x[2] - q.x[1], q.y[2] - q.y[1]), a1 = Math.abs(e1) / Math.hypot(q.x[0] - q.x[2], q.y[0] - q.y[2]),
                                a2 = Math.abs(e2) / Math.hypot(q.x[1] - q.x[0], q.y[1] - q.y[0]);
                        boolean innen = l0 >= 0 && l1 >= 0 && l2 >= 0;
                        // nah an einer Kante (oder Ecke) dieses Dreiecks: Rasterregeln der Geräte dürfen dort verschieden entscheiden
                        if ((a0 < 1.0 / 64 && l1 >= -1e-9 && l2 >= -1e-9) || (a1 < 1.0 / 64 && l0 >= -1e-9 && l2 >= -1e-9) || (a2 < 1.0 / 64 && l0 >= -1e-9 && l1 >= -1e-9))
                            nahKante = true;
                        if (!innen) continue;
                        double zt = l0 * q.z[0] + l1 * q.z[1] + l2 * q.z[2];
                        if (zt > best) {
                            best = zt;
                            wer = q.nummer;
                            double dzx = ((q.y[1] - q.y[2]) * q.z[0] + (q.y[2] - q.y[0]) * q.z[1] + (q.y[0] - q.y[1]) * q.z[2]) / fl;
                            double dzy = ((q.x[2] - q.x[1]) * q.z[0] + (q.x[0] - q.x[2]) * q.z[1] + (q.x[1] - q.x[0]) * q.z[2]) / fl;
                            grad = Math.hypot(dzx, dzy);
                        }
                    }
                    int i = py * W + px;
                    if (nf[i] == 0) luecken++;
                    if (nahKante) { kantig++; continue; }
                    verglichen++;
                    if ((int) nf[i] != wer) {
                        anders++;
                        if (erstes.isEmpty()) erstes = f("; erster bei (%d, %d): Karte %d, Java %d", px, py, (int) nf[i], wer);
                    } else if (Math.abs(tf[i] - best) > dz) {
                        dz = Math.abs(tf[i] - best);
                        wo = f(" bei (%d, %d), Dreieck %d, Gefälle der Tiefe %.3g je Bildpunkt", px, py, wer, grad);
                    }
                }
            A.info("Zeichner", f("%d Dreiecke (%d im Netz über das Bild, %d darüber), %d × %d, Ziele R32F und D32F, Tiefe umgekehrt (größer gewinnt, gelöscht mit 0)",
                    d.size(), netz, d.size() - netz, W, H));
            A.pruefe(anders == 0, "Prüfstein 1 (P6a): Abdeckung gegen eine Rasterung in Java (Mitte des Bildpunkts, umgekehrte Tiefe), Bildpunkte weiter als 1/64 von jeder Kante",
                    f("%d von %d anders%s; %d an Kanten nicht verglichen", anders, verglichen, erstes, kantig), "gleich");
            A.pruefe(dz <= 1e-6, "Prüfstein 1 (P6a): Tiefe gegen die Rasterung in Java (affin im Bild)", f("größte Abweichung %.2e%s", dz, wo), "≤ 1e-6 [G]");
            A.pruefe(luecken == 0, "Netz ohne Lücken: jeder Bildpunkt von einem Dreieck bedeckt, auch auf gemeinsamen Kanten", luecken + " Bildpunkte ohne Dreieck", "0");
        }
        // Textur: ein Bild hochladen, ein Kernel liest es über den Abtaster (Bildpunkt genau) in einen Puffer
        int n = 37 * 23;
        float[] w = new float[n];
        for (int i = 0; i < n; i++) w[i] = (float) Math.sin(i * 0.37) * 100;
        try (com.dan.core.gpu.Abtaster at = new com.dan.core.gpu.Abtaster(g, false, false); Bild t = new Bild(g, konto, "Textur", 37, 23, Bild.R32F);
             Kernel k = Kernel.lade(g, PFAD + "textur.comp", 8, null, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER); Puffer aus = Puffer.speicher(g, konto, "aus", 4L * n);
             MemoryStack s = stackPush()) {
            t.schreibe(w);
            ByteBuffer push = s.malloc(8).putInt(0, 37).putInt(4, 23);
            Ablauf ab = new Ablauf(g, g.grafik).rechne("lesen", k, k.satz(at.an(t), aus), push, Kernel.gruppen(n, 64), 1, 1,
                    Ablauf.und(Ablauf.textur(Ablauf.RECHNEN, t), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> ab.aufnehmen(c, null));
            float[] z = aus.floats(n);
            int falsch = 0;
            for (int i = 0; i < n; i++) if (z[i] != w[i]) falsch++;
            A.pruefe(falsch == 0, "Bild hochgeladen (R32F), als Textur über einen Abtaster gelesen (der Ablauf wechselt zur Anordnung für Texturen)", falsch + " von " + n + " anders", "0");
        }
    }
}
