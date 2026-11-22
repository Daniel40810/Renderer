package com.dan.lauf.demo;

import com.dan.core.astro.Gestirne;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.zeit.Uhr;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Lauf;
import com.dan.render.air.Himmel;
import com.dan.render.frame.Rahmen;
import com.dan.render.terrain.Gelaendebild;
import com.dan.core.szene.Spiegel;
import com.dan.render.water.Wasserbild;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung des Laufs mit Bild ohne Fenster (P6e, Werkbuch HalligHooge Teil A, 12.7, Feinplan P6e): die Bildmodule am Bus
 * und am Archiv, mit den Ständen von hydro.
 * <ul>
 * <li><b>Teil 1, Lauf mit Archiv:</b> {@code --stufen} (Vorgabe G0 | G1 | G2 G3, G3 als Fenster um Hooge) von
 * {@code --start} (Vorgabe 23.10.2026, 00:00 UTC, wie die Sturmflut in TideCheck, damit sich der Stau aufbaut) bis
 * {@code --ende} (26.10., 00:00) mit Anlauf; das Modul {@code archiv} legt ab {@code --ab} (25.10., 06:00) G2–G4 und den
 * Wind ab. Liegt das Archiv
 * schon, wird es nur gelesen ({@code --neu} rechnet neu).</li>
 * <li><b>Teil 2, aus dem Archiv:</b> Prüfstein 5 mit den Ständen von hydro (benetzter Anteil im Bild gegen die Pixel des
 * Stapels unter η der Zelle, F46 A; die Tabelle von hydro zur Auskunft; im Fenster O 470–472 km, N 6 045–6 047 km; kein Wasser in trockenen Zellen, auch nicht an einer
 * Deichkante), Prüfstein 6 (η_Bild zwischen zwei Ständen gegen die lineare Interpolation; Wechsel des Intervalls),
 * Prüfstein 3 in den vier Ansichten und Prüfstein 10 ({@code P6_Bild.png}).</li>
 * <li><b>Teil 3, live:</b> der Lauf auf der Karte mit dem Bild am Bus ({@code --breite} × {@code --hoehe}, Vorgabe
 * 1920 × 1080, {@code --bilder} Bilder nach {@link #ANLAUF} zum Anlaufen; das Gelände für das feste Auge ist vorher ganz
 * geladen, und jedes gemessene Bild muss es zeigen): Bilder/s, Zeit je Durchgang, Zeitraffer der Physik (Prüfstein 8;
 * auf llvmpipe nur zur Auskunft) und Prüfstein 9 mit {@code -Dcore.gpu.validierung=true}.</li>
 * </ul>
 * <pre>
 * java -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.BildCheck --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p6\archiv
 * </pre>
 * Mit {@code -Dcore.gpu.validierung=true} gilt Prüfstein 8 nur zur Auskunft (auch wenn die Schicht fehlt; dann ist
 * Prüfstein 9 nicht geprüft), und das Attest heißt {@code P6_Bild_Validierung.md}; die Zeiten misst ein Lauf ohne
 * Validierung. Die Ansichten werden belichtet wie ein Standbild: jede Messung gilt sofort, zwölf Bilder lang.
 * Weitere: {@code --nur 1,2,3}, {@code --fenster3 i0,j0,ni,nj} oder {@code -} (Vorgabe 600,850,500,400; in einem
 * Auszug von {@link LaufAuszug} keins), {@code --prozessor}, {@code --anlauf h}, {@code --ansicht-breite} und
 * {@code --ansicht-hoehe} (Teil 2, Vorgabe 960 × 540), {@code --ziel} (Zeitraffer in Teil 3, Vorgabe 200).
 */
public final class BildCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    /** Auge auf der Hanswarft (UTM 32), wie TerrainCheck; Prüfstein 8 misst von hier. */
    static final double OST = 470_900, NORD = 6_046_615;
    /**
     * Augen der Ansichten (Prüfstein 10, Sichtprüfung nach der Abnahme P6, 12.7): auf der Krone des Sommerdeichs, 1,7 m
     * darüber. Von der Hanswarft verdeckt ihr eigener Rand (5,98 m NHN, 48 m vor dem Auge auf 6,09 m) alles unter
     * −2,3 mrad; das Watt bliebe ein Streifen unter einem Bildpunkt. Westspitze: Krone 2,99 m, Blick 259° zum Japsand;
     * Südwestdeich: Krone 2,80 m, Blick zur Sonne über das Watt Richtung Norderoogsand.
     */
    static final double WEST_OST = 468_290, WEST_NORD = 6_047_690, WEST_RICHTUNG = 259;
    static final double SW_OST = 470_200, SW_NORD = 6_046_370;
    /** Fenster der Wasserlinie wie WaterCheck: O 470–472 km, N 6 045–6 047 km. */
    static final double F_OST = 470_000, F_NORD = 6_045_000;
    static final int F_ZELLEN = 100;
    /** Bilder zum Anlaufen vor der Messung der Zeiten (Teil 3). */
    static final int ANLAUF = 10;
    /** Pegel Hooge (Anleger) für die Wahl der Zeiten, wie in der Vorgabe; gilt die tiefste Zelle von G3 im Umkreis von 300 m [G]. */
    static double P_OST = 470_410, P_NORD = 6_048_090;
    static final double UMKREIS_PEGEL = 300;
    static final double LON = 8.55, LAT = 54.57;

    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", archiv = null, stufen = "G0 | G1 | G2 G3", fenster = null, nur = "1,2,3";
    /** Der Lauf beginnt wie die Sturmflut in TideCheck (P5d) am 23.10., damit sich der Stau aufbaut; abgelegt wird ab dem 25.10., 06:00 UTC. */
    static Instant start = Instant.parse("2026-10-23T00:00:00Z"), ab = Instant.parse("2026-10-25T06:00:00Z"), ende = Instant.parse("2026-10-26T00:00:00Z");
    /**
     * Der ruhige Tag (F48 A, 12.7 Nachlauf P6): Hochdruck 1 029–1 034 hPa, Wind an Hooge im Tagesmittel 2,4 m/s, kurz
     * nach Vollmond. Gerechnet ab dem 28.10. (zwei Tage, damit der Stau des Sturms abklingt), abgelegt der 30.10.
     */
    static Instant startRuhig = Instant.parse("2026-10-28T00:00:00Z"), abRuhig = Instant.parse("2026-10-30T00:00:00Z"), endeRuhig = Instant.parse("2026-10-31T00:00:00Z");
    static String archivRuhig = null;
    static boolean neu, prozessor;
    static double anlauf = 3, ziel = 200;
    static int breite = 1920, hoehe = 1080, bilder = 300, aBreite = 960, aHoehe = 540;

    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        boolean validierung = Boolean.getBoolean("core.gpu.validierung");
        for (int i = 0; i < args.length; i++) {
            String a = args[i], v = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--gebiet" -> gebiet = v;
                case "--archiv" -> archiv = v;
                case "--archiv-ruhig" -> archivRuhig = v;
                case "--stufen" -> stufen = v;
                case "--fenster3" -> fenster = v;
                case "--start" -> start = Instant.parse(v);
                case "--ende" -> ende = Instant.parse(v);
                case "--ab" -> ab = Instant.parse(v);
                case "--nur" -> nur = v;
                case "--anlauf" -> anlauf = Double.parseDouble(v);
                case "--breite" -> breite = Integer.parseInt(v);
                case "--hoehe" -> hoehe = Integer.parseInt(v);
                case "--bilder" -> bilder = Integer.parseInt(v);
                case "--ansicht-breite" -> aBreite = Integer.parseInt(v);
                case "--ansicht-hoehe" -> aHoehe = Integer.parseInt(v);
                case "--ziel" -> ziel = Double.parseDouble(v);
                case "--neu" -> neu = true;
                case "--prozessor" -> prozessor = true;
                default -> { }
            }
        }
        File gb = new File(gebiet);
        File ar = archiv != null ? new File(archiv) : new File(gb.getAbsoluteFile().getParentFile(), "_p6" + File.separator + "archiv");
        File arR = archivRuhig != null ? (archivRuhig.equals("-") ? null : new File(archivRuhig)) : new File(ar.getAbsoluteFile().getParentFile(), "archiv_ruhig");
        System.out.println("BildCheck – com.dan.lauf " + Lauf.VERSION + " (Werkbuch HalligHooge Teil A, P6e, F48 A)");
        A.info("Gebiet", gb.getPath() + "; Archiv " + ar.getPath() + (arR != null ? "; Archiv des ruhigen Tags " + arR.getPath() : "; ohne Archiv des ruhigen Tags"));
        try {
            Gebiet ge = Gebiet.lies(gb);
            int[] f3 = fenster3(gb);
            if (nur.contains("1")) {
                teil1(gb, ar, f3, "Sturm", start, ab, ende);
                if (arR != null) teil1(gb, arR, f3, "ruhiger Tag", startRuhig, abRuhig, endeRuhig);
            }
            if (nur.contains("2") || nur.contains("3")) {
                try (Geraet g = new Geraet()) {
                    A.info("Gerät", g.name + " (" + g.treiber + ")");
                    Konto konto = g.konto("BildCheck", 3L << 30);
                    if (nur.contains("2")) teil2(g, konto, gb, ge, ar, arR, f3);
                    if (nur.contains("3")) teil3(g, konto, gb, ge, f3);
                    A.teil("Aufräumen");
                    A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
                    if (g.validierung) {
                        String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                        A.pruefe(g.validierungsFehler == 0, "Prüfstein 9: Validierungsschicht " + g.schicht + " über den Lauf mit Bild", g.validierungsFehler + " Fehler" + erste, "0 Fehler");
                    } else A.info("Prüfstein 9", validierung ? "nicht geprüft: Validierung verlangt, aber VK_LAYER_KHRONOS_validation fehlt (Vulkan SDK installieren)"
                            : "Validierung nicht eingeschaltet (-Dcore.gpu.validierung=true)");
                }
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", validierung ? "P6_Bild_Validierung.md" : "P6_Bild.md"), "P6e – BildCheck" + (validierung ? " mit Validierung" : ""));
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Das Fenster von G3: aus {@code --fenster3}; in einem Auszug ({@code auszug} in gebiet.txt) keins; sonst um Hooge. */
    static int[] fenster3(File gb) throws Exception {
        if (fenster != null) {
            if (fenster.equals("-")) return null;
            String[] t = fenster.split(",");
            return new int[] {Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()), Integer.parseInt(t[2].trim()), Integer.parseInt(t[3].trim())};
        }
        for (String z : Files.readAllLines(new File(gb, "gebiet.txt").toPath())) if (z.startsWith("auszug\t")) return null;
        return new int[] {600, 850, 500, 400};
    }

    static Lauf.Einstellung einstellung(File gb, int[] f3) { return einstellung(gb, f3, start); }

    static Lauf.Einstellung einstellung(File gb, int[] f3, Instant start) {
        Lauf.Einstellung e = new Lauf.Einstellung();
        e.gebiet = gb;
        e.start = start;
        e.stufen(stufen);
        e.karte = !prozessor;
        e.fenster3 = f3;
        e.anlauf = anlauf * 3600;
        return e;
    }

    // ------------------------------------------------------------------ Teil 1

    static void teil1(File gb, File ar, int[] f3, String wer, Instant start, Instant ab, Instant ende) throws Exception {
        A.teil("1 Lauf mit Archiv, " + wer);
        double dauer = (ende.toEpochMilli() - start.toEpochMilli()) / 1000.0;
        boolean liegt = new File(ar, "G3" + File.separator + "lauf.txt").isFile();
        if (liegt && !neu) {
            try (Archivquelle q = new Archivquelle(ar.toPath())) {
                A.info("Archiv liegt schon (--neu rechnet neu)", f("%s: %d Stände, %s bis %s, %.1f MB je Stand; %s", ar, q.staende(), q.start, q.start.plusSeconds((long) q.zeiten()[q.staende() - 1]),
                        q.bytesJeStand() / 1e6, q.kopf.antrieb));
            }
            return;
        }
        if (liegt) throw new IllegalStateException(ar + ": dort liegt ein Archiv; bitte erst wegräumen");
        Lauf.Einstellung e = einstellung(gb, f3, start);
        e.archiv = ar;
        e.archivAb = Math.max(0, (ab.toEpochMilli() - start.toEpochMilli()) / 1000.0);
        e.name = "P6e BildCheck " + start;
        long n0 = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf", l + f("; Aufbau %.1f s", l.aufbau));
            A.info("Archiv", f("%s; Gitter %s, dazu der Wind; abgelegt ab %s", ar, namen(l.archiv.gitter()), l.zeitpunkt(e.archivAb)));
            if (!l.hinweise.isEmpty()) A.info("Hinweise beim Verdrahten", String.join("; ", l.hinweise));
            long n1 = System.nanoTime();
            for (double t = Math.min(3600, dauer); ; t = Math.min(t + 3600, dauer)) {
                l.bis(t);
                System.out.printf("  %s: %.0f s gerechnet%n", l.zeitpunkt(l.zeit()), (System.nanoTime() - n1) / 1e9);
                if (t >= dauer) break;
            }
            double s = (System.nanoTime() - n1) / 1e9;
            long soll = Math.round((dauer - e.archivAb) / e.kopplung) + 1;
            A.pruefe(l.archiv.staende == soll && l.bus.ausserhalb() == 0, "Lauf mit Archiv (" + wer + "): Stände abgelegt; außerhalb gelesen",
                    f("%d Stände (%.0f MB, Ablegen %.1f s); %d", l.archiv.staende, l.archiv.bytes / 1e6, l.archiv.sekunden, l.bus.ausserhalb()), soll + "; 0");
            A.info("Zeit", f("%.1f h Modellzeit in %.1f min, Zeitraffer %.1f (ohne Bild)", dauer / 3600, s / 60, dauer / s));
            for (int st = 1; st < e.stufen.size(); st++) A.info("Bilanz der Stufe " + st, f("%.2e", l.bilanz(st)));
        }
        A.info("Teil 1 gesamt (" + wer + ")", f("%.1f min", (System.nanoTime() - n0) / 6e10));
    }

    static String namen(List<Gitter> g) {
        List<String> n = new ArrayList<>();
        for (Gitter x : g) n.add(x.name + " " + x.nx + " × " + x.ny);
        return String.join(", ", n);
    }

    // ------------------------------------------------------------------ Teil 2

    /** Die Bildteile einer Prüfung. */
    static final class Szene implements AutoCloseable {
        final Rahmen rahmen;
        final Himmel himmel;
        final Gelaendebild gelaende;
        final Wasserbild wasser;
        /** Seit P10b: die Kimmung als letzter Teil; ohne Luft in der Quelle lässt sie das Bild, wie es ist. */
        final com.dan.render.air.Kimmung kimmung;
        final com.dan.lauf.Bild bild;
        final Bild aus;
        final Geraet g;

        Szene(Geraet g, Konto k, File gb, Gebiet ge, com.dan.lauf.Bild.Quelle q, int w, int h) throws Exception {
            this.g = g;
            rahmen = new Rahmen(g, k, w, h);
            himmel = new Himmel(g, k);
            gelaende = Gelaendebild.aus(g, k, gb);
            gelaende.flugZeit = 0;                     // seit P10c: Die Streifen des Sandfegens stehen, damit die Bilder der Prüfung wiederholbar sind
            wasser = new Wasserbild(g, k);
            kimmung = new com.dan.render.air.Kimmung(g, k);
            aus = new Bild(g, k, "Ausgabe", w, h, Bild.RGBA8S);
            bild = new com.dan.lauf.Bild(q, ge, wasser, gelaende);
            bild.kimmung = kimmung;
            bild.himmel = himmel;                     // seit P10d: Albedo der Luft aus dem Anteil des Wassers
        }

        /** Ein Bild: Stände zur Zeit t, dann zeichnen und warten. */
        void zeichne(Blick b, Instant t0, double t, Stoppuhr uhr) {
            bild.zu(t, b.ost, b.nord);
            bild.blick(b, (double) aus.breite / aus.hoehe);
            Ablauf a = new Ablauf(g, g.grafik);
            rahmen.bild(a, List.of(gelaende, wasser, himmel, kimmung), b, t0.plusMillis(Math.round(t * 1000)), aus, 0.5);
            g.grafik.einmal(c -> {
                if (uhr != null) uhr.runde(c);
                a.aufnehmen(c, uhr);
            });
        }

        @Override public void close() {
            aus.close();
            kimmung.close();
            wasser.close();
            gelaende.close();
            himmel.close();
            rahmen.close();
        }
    }

    /** Die gewählten Zeiten eines Archivs: Pegel Hooge, Niedrig- und Hochwasser nach dem Anlauf (bei Tag bevorzugt), Landunter, Abendsonne. */
    record Zeiten(double[] z, Instant t0, int k3, Gitter g3, Subgrid.Packung p3, double[] pegel, int cp, int iNW, int iHW, int iLU, int iAb, double nachAnlauf, String ersatz) {
        String text() {
            return f("Niedrigwasser %s (%+.2f m); Hochwasser %s (%+.2f m); %sAbendsonne %s (Sonne %.1f°, Pegel %+.2f m)%s",
                    zeit(t0, z[iNW]), pegel[iNW], zeit(t0, z[iHW]), pegel[iHW], iLU == iHW ? "" : "Landunter " + zeit(t0, z[iLU]) + f(" (%+.2f m); ", pegel[iLU]), zeit(t0, z[iAb]),
                    Gestirne.bei(t0.plusMillis(Math.round(z[iAb] * 1000))).sonne(LON, LAT).hoeheGrad(), pegel[iAb], z[iAb] < nachAnlauf ? " – noch im Anlauf" : "") + ersatz;
        }
        String ort() { return f("Pegel Hooge: Zelle %d, %d von G3, Grund %.2f m NHN", cp % g3.nx, cp / g3.nx, grund(p3, cp)); }
        /** Halbe Tide zwischen Niedrig- und Hochwasser. */
        int halb() { return (iNW + iHW) / 2; }
    }

    static Zeiten zeiten(Archivquelle q, Gebiet ge) throws Exception {
        double[] z = q.zeiten();
        Instant t0 = q.start;
        int k3 = -1;
        for (int k = 0; k < q.gitter().size(); k++) if (q.gitter().get(k).name.equals("G3")) k3 = k;
        if (k3 < 0) throw new IllegalStateException("Archiv ohne G3");
        Gitter g3 = q.gitter().get(k3);
        Subgrid.Packung p3 = packung(ge, g3);
        // Pegel Hooge über alle Stände: die tiefste Zelle im Umkreis
        int n3 = g3.zellen(), cp = tiefste(g3, p3, P_OST, P_NORD, UMKREIS_PEGEL);
        float[][] st = new float[3][n3];
        double[] pegel = new double[z.length];
        boolean[] pNass = new boolean[z.length];
        for (int i = 0; i < z.length; i++) {
            q.stand(z[i], k3, st[0], st[1], st[2]);
            pegel[i] = st[0][cp];
            pNass[i] = st[2][cp] > 0 || st[2][cp] == 0 && st[1][cp] > 0;
        }
        double nachAnlauf = q.kopf.antrieb.containsKey("tide") ? anlaufAus(q.kopf.antrieb.get("tide")) : anlauf * 3600;
        // Niedrig- und Hochwasser nach dem Anlauf: Extreme, die mindestens 0,1 m [G] unter bzw. über dem Pegel davor und
        // danach liegen (nicht ein Rand des Laufs, nicht eine Stufe von float16); Hochwasser bei Tag, wenn es eines gibt
        int iNW = -1, iHW = -1;
        boolean hwTag = false, nwTag = false;
        for (int i = 1; i + 1 < z.length; i++) {
            if (z[i] < nachAnlauf || !pNass[i]) continue;
            double vorMax = -1e9, nachMax = -1e9, vorMin = 1e9, nachMin = 1e9;
            for (int j = 0; j < i; j++) if (z[j] >= nachAnlauf) { vorMax = Math.max(vorMax, pegel[j]); vorMin = Math.min(vorMin, pegel[j]); }
            for (int j = i + 1; j < z.length; j++) { nachMax = Math.max(nachMax, pegel[j]); nachMin = Math.min(nachMin, pegel[j]); }
            boolean tief = true, hoch = true;
            for (int j = i - 1; j >= 0 && z[j] >= nachAnlauf && pegel[j] < pegel[i] + 0.1; j--) if (pegel[j] < pegel[i]) tief = false;
            for (int j = i + 1; j < z.length && pegel[j] < pegel[i] + 0.1; j++) if (pegel[j] < pegel[i]) tief = false;
            for (int j = i - 1; j >= 0 && z[j] >= nachAnlauf && pegel[j] > pegel[i] - 0.1; j--) if (pegel[j] > pegel[i]) hoch = false;
            for (int j = i + 1; j < z.length && pegel[j] > pegel[i] - 0.1; j++) if (pegel[j] > pegel[i]) hoch = false;
            tief &= vorMax >= pegel[i] + 0.1 && nachMax >= pegel[i] + 0.1;
            hoch &= vorMin <= pegel[i] - 0.1 && nachMin <= pegel[i] - 0.1;
            boolean tag = Gestirne.bei(t0.plusMillis(Math.round(z[i] * 1000))).sonne(LON, LAT).hoeheGrad() > 5;
            if (tief && (iNW < 0 || tag && !nwTag || tag == nwTag && pegel[i] < pegel[iNW])) {
                iNW = i;
                nwTag = tag;
            }
            if (hoch) {
                if (iHW < 0 || tag && !hwTag || tag == hwTag && pegel[i] > pegel[iHW]) {
                    iHW = i;
                    hwTag = tag;
                }
            }
        }
        String ersatz = "";
        if (iNW < 0) {
            for (int i = 0; i < z.length && iNW < 0; i++) if (z[i] >= nachAnlauf) iNW = i;
            if (iNW < 0) iNW = 0;
            ersatz += "; kein Niedrigwasser nach dem Anlauf, statt dessen der erste Stand danach";
        }
        if (iHW < 0) {
            iHW = z.length - 1;
            for (int i = 0; i < z.length; i++) if (z[i] >= nachAnlauf && pegel[i] > pegel[iHW]) iHW = i;
            ersatz += "; kein Hochwasser mit Abstand, statt dessen der höchste Stand";
        }
        double tLU = t0.until(Instant.parse("2026-10-25T23:45:00Z"), java.time.temporal.ChronoUnit.MILLIS) / 1000.0;
        int iLU = Arrays.binarySearch(z, tLU);
        if (iLU < 0) iLU = iHW;
        else {
            // W10 (P8c, behoben mit P9b): der Scheitel aus dem Archiv – der höchste Stand im Umkreis von 3 h um diese Zeit
            int b = iLU;
            for (int i = 0; i < z.length; i++) if (Math.abs(z[i] - tLU) <= 3 * 3600 && pegel[i] > pegel[b]) b = i;
            iLU = b;
        }
        // Abendsonne: die Sonne am nächsten bei 3° über der Kimm, am Nachmittag, möglichst nach dem Anlauf [G]
        int iAb = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < z.length; i++) {
            Instant ti = t0.plusMillis(Math.round(z[i] * 1000));
            double hs = Gestirne.bei(ti).sonne(LON, LAT).hoeheGrad();
            if (hs < 0.5 || hs > 8 || ti.atZone(java.time.ZoneOffset.UTC).getHour() < 12) continue;
            double w = Math.abs(hs - 3) + (z[i] < nachAnlauf ? 100 : 0);
            if (w < best) {
                best = w;
                iAb = i;
            }
        }
        if (iAb < 0) iAb = iHW;
        return new Zeiten(z, t0, k3, g3, p3, pegel, cp, iNW, iHW, iLU, iAb, nachAnlauf, ersatz);
    }

    static void teil2(Geraet g, Konto konto, File gb, Gebiet ge, File ar, File arR, int[] f3) throws Exception {
        A.teil("2 Aus dem Archiv");
        pegelOrt(ge);
        boolean ruhig = arR != null && new File(arR, "G3" + File.separator + "lauf.txt").isFile();
        try (Archivquelle q = new Archivquelle(ar.toPath()); Szene sz = new Szene(g, konto, gb, ge, q, aBreite, aHoehe);
             Archivquelle qR = ruhig ? new Archivquelle(arR.toPath()) : null; Szene szR = ruhig ? new Szene(g, konto, gb, ge, qR, aBreite, aHoehe) : null) {
            Zeiten s = zeiten(q, ge);
            A.info("Archiv (Sturm)", f("%s: %d Stände ab %s, Gitter %s, %.1f MB je Stand", q.herkunft(), s.z().length, s.t0(), namen(q.gitter()), q.bytesJeStand() / 1e6));
            A.info("Zeiten, Sturm (" + s.ort() + ")", s.text());
            Zeiten r = null;
            if (ruhig) {
                r = zeiten(qR, ge);
                A.info("Archiv (ruhiger Tag, F48 A)", f("%s: %d Stände ab %s, %.1f MB je Stand", qR.herkunft(), r.z().length, r.t0(), qR.bytesJeStand() / 1e6));
                A.info("Zeiten, ruhiger Tag (" + r.ort() + ")", r.text());
            } else A.info("Archiv des ruhigen Tags", "fehlt (" + arR + "); Niedrigwasser und Abendsonne kommen aus dem Sturm. Teil 1 legt es an (F48 A).");
            prufstein5(sz, ge, gb, q, s.k3(), s.g3(), s.p3(), s.t0(), new int[] {s.iNW(), s.halb(), s.iHW(), s.iLU()},
                    new String[] {"Niedrigwasser", "halbe Tide", "Hochwasser", "Landunter"});
            if (ruhig) prufstein5(szR, ge, gb, qR, r.k3(), r.g3(), r.p3(), r.t0(), new int[] {r.iNW(), r.halb()},
                    new String[] {"ruhiger Tag, Niedrigwasser", "ruhiger Tag, halbe Tide"});
            prufstein6(sz, q, s.k3(), s.g3(), s.t0(), s.z(), s.iHW());
            Zeiten nw = ruhig ? r : s;
            Szene szNw = ruhig ? szR : sz;
            ansichten(new Ansicht[] {
                    new Ansicht(Ansicht.WEST, "Niedrigwasser" + (ruhig ? ", ruhiger Tag" : ""), szNw, nw.t0(), nw.z()[nw.iNW()]),
                    new Ansicht(Ansicht.WEST, "Hochwasser, Sturm", sz, s.t0(), s.z()[s.iHW()]),
                    new Ansicht(Ansicht.VOGEL, "Landunter", sz, s.t0(), s.z()[s.iLU()]),
                    new Ansicht(Ansicht.ABEND, ruhig ? "ruhiger Tag" : "Sturm", szNw, nw.t0(), nw.z()[nw.iAb()])});
        }
    }

    static double anlaufAus(String tide) {
        int p = tide.indexOf("Anlauf ");
        if (p < 0) return anlauf * 3600;
        String[] t = tide.substring(p + 7).split(" ");
        return Double.parseDouble(t[0]) * 3600;
    }

    static String zeit(Instant t0, double t) { return t0.plusMillis(Math.round(t * 1000)).toString().replace("T", " ").replace(":00Z", " UTC"); }

    static int zelle(Gitter g, double ost, double nord) {
        int i = (int) Math.floor((ost - g.x0) / g.dx), j = (int) Math.floor((nord - g.y0) / g.dy);
        return Math.max(0, Math.min(g.nx - 1, i)) + g.nx * Math.max(0, Math.min(g.ny - 1, j));
    }

    /** Prüfstein 5 mit den Ständen von hydro. */
    /** Die Packung von G3 für das Gitter des Archivs (ein Fenster des ganzen G3 oder dieses selbst). */
    static Subgrid.Packung packung(Gebiet ge, Gitter g3) throws Exception {
        Gitter voll = ge.gitter("G3");
        Subgrid.Packung pv = ge.packung("G3");
        int vi0 = (int) Math.round((g3.x0 - voll.x0) / voll.dx), vj0 = (int) Math.round((g3.y0 - voll.y0) / voll.dy);
        return vi0 == 0 && vj0 == 0 && g3.nx == voll.nx && g3.ny == voll.ny ? pv : pv.fenster(voll, vi0, vj0, g3.nx, g3.ny);
    }

    /** Der Pegel Hooge aus der Vorgabe, wenn es sie gibt. */
    static void pegelOrt(Gebiet ge) throws Exception {
        if (!ge.hatVorgabe()) return;
        Gebiet.Vorgabe.Pegel p = ge.vorgabe().pegel("Hooge");
        if (p == null) return;
        P_OST = p.ost();
        P_NORD = p.nord();
    }

    /** Tiefste Pixelhöhe einer Zelle nach der Tabelle (m NHN). */
    static double grund(Subgrid.Packung p, int c) { return p.basis()[c] + Float.float16ToFloat(p.tabelle()[c * 3 * p.stufen()]); }

    /** Die Zelle mit dem tiefsten Grund im Umkreis. */
    static int tiefste(Gitter g, Subgrid.Packung p, double ost, double nord, double r) {
        int best = zelle(g, ost, nord);
        int n = (int) Math.ceil(r / g.dx), ci = best % g.nx, cj = best / g.nx;
        for (int j = Math.max(0, cj - n); j <= Math.min(g.ny - 1, cj + n); j++)
            for (int i = Math.max(0, ci - n); i <= Math.min(g.nx - 1, ci + n); i++) {
                double x = g.x0 + (i + 0.5) * g.dx - ost, y = g.y0 + (j + 0.5) * g.dy - nord;
                if (x * x + y * y > r * r) continue;
                int c = i + g.nx * j;
                if (grund(p, c) < grund(p, best)) best = c;
            }
        return best;
    }

    static void prufstein5(Szene sz, Gebiet ge, File gb, Archivquelle q, int k3, Gitter g3, Subgrid.Packung p3, Instant t0, int[] idx, String[] namen) throws Exception {
        double[] z = q.zeiten();
        Gitter voll = ge.gitter("G3");
        int vi0 = (int) Math.round((g3.x0 - voll.x0) / voll.dx), vj0 = (int) Math.round((g3.y0 - voll.y0) / voll.dy);
        Gelaende gel = sz.gelaende.gelaende;          // derselbe Stapel wie das Bild, mit der Mindesthöhe
        A.info("Gelände der Wasserlinie", gel.mindesthoehe() == null ? "Stapel ohne Mindesthöhe" : "Stapel mit der Mindesthöhe der Bauwerke " + gel.mindesthoehe());
        int fi0 = (int) Math.round((F_OST - g3.x0) / g3.dx), fj0 = (int) Math.round((F_NORD - g3.y0) / g3.dy);
        if (fi0 < 0 || fj0 < 0 || fi0 + F_ZELLEN > g3.nx || fj0 + F_ZELLEN > g3.ny) throw new IllegalStateException("Fenster der Wasserlinie liegt nicht in " + g3);
        // Kanten der Bauwerke im Gitter des Archivs: je Zelle West- und Südkante
        boolean[] west = new boolean[g3.zellen()], sued = new boolean[g3.zellen()];
        if (new File(ge.eintrag("G3").ordner(), "bauwerke.txt").isFile())
            for (Gebiet.Bauten.Kante k : ge.bauwerke("G3").kanten()) {
                int i = k.i() - vi0, j = k.j() - vj0;
                if (i < 0 || j < 0 || i >= g3.nx || j >= g3.ny) continue;
                if (k.querX()) west[i + g3.nx * j] = true;
                else sued[i + g3.nx * j] = true;
            }
        int W = F_ZELLEN * 20, np = W * W;
        float[] zf = new float[np], zeile = new float[W];
        for (int r = 0; r < W; r++) {
            gel.fuelle(F_OST + 0.5, F_NORD + r + 0.5, 1, W, 1, zeile);
            System.arraycopy(zeile, 0, zf, r * W, W);
        }
        double augeO = F_OST + W / 2.0, augeN = F_NORD + W / 2.0;
        Blick b = new Blick(augeO, augeN, 200, 0, -89, 60).groesse(aBreite, aHoehe);
        int n3 = g3.zellen();
        float[] eta = new float[n3], h = new float[n3], age = new float[n3];
        for (int s = 0; s < idx.length; s++) {
            double ts = z[idx[s]];
            q.stand(ts, k3, eta, h, age);
            boolean[] nass = new boolean[n3];
            for (int c = 0; c < n3; c++) nass[c] = age[c] > 0 || age[c] == 0 && h[c] > 0;
            sz.zeichne(b, t0, ts, null);
            // je Pixel des Fensters η_Bild nach der Regel des Shaders (Wasserbild.etaBild; auf der Karte bitgleich, WaterCheck)
            boolean[] px = new boolean[np];
            float ox = (float) 0, oz;
            for (int r = 0; r < W; r++)
                for (int c = 0; c < W; c++) {
                    ox = (float) (F_OST + c + 0.5 - augeO);
                    oz = (float) (augeN - (F_NORD + r + 0.5));
                    float e = sz.wasser.etaBild(ox, oz, null);
                    px[r * W + c] = !Float.isNaN(e) && e > zf[r * W + c];
                }
            List<Double> ab = new ArrayList<>(), tabAb = new ArrayList<>();
            double bildG = 0, tabG = 0;
            long trockenNass = 0, kanteZellen = 0, kanteNass = 0, saumNass = 0;
            for (int j = 0; j < F_ZELLEN; j++)
                for (int i = 0; i < F_ZELLEN; i++) {
                    int cw = fi0 + i + g3.nx * (fj0 + j);
                    int nassPx = 0;
                    for (int r = 0; r < 20; r++) for (int c = 0; c < 20; c++) if (px[(j * 20 + r) * W + i * 20 + c]) nassPx++;
                    if (!nass[cw]) {
                        int ci = fi0 + i, cj = fj0 + j;
                        // seit C1b (Werkbuch HalligHooge 137/138, A52): Saum – Wasser in einer trockenen Zelle nur, wo ein nasser
                        // Seitennachbar über eine Kante ohne Bauwerk angrenzt (ob die Schwelle unter seinem Spiegel liegt,
                        // prüft WaterCheck Teil 0); sonst zählt es wie bisher als Fehler
                        boolean saum = com.dan.core.szene.Spiegelbild.SAUM && (ci > 0 && !west[cw] && nass[cw - 1] || ci + 1 < g3.nx && !west[cw + 1] && nass[cw + 1]
                                || cj > 0 && !sued[cw] && nass[cw - g3.nx] || cj + 1 < g3.ny && !sued[cw + g3.nx] && nass[cw + g3.nx]);
                        if (saum) saumNass += nassPx;
                        else trockenNass += nassPx;
                        // trockene Zelle hinter einer Deichkante mit nassem Nachbarn (Kante eines Rings)
                        boolean kante = ci > 0 && west[cw] && nass[cw - 1] || ci + 1 < g3.nx && west[cw + 1] && nass[cw + 1]
                                || cj > 0 && sued[cw] && nass[cw - g3.nx] || cj + 1 < g3.ny && sued[cw + g3.nx] && nass[cw + g3.nx];
                        if (kante) {
                            kanteZellen++;
                            kanteNass += nassPx;
                        }
                        continue;
                    }
                    // F46 A: Maßstab sind dieselben Pixel des Stapels unter η der Zelle (so benetzt hydro bei ebenem
                    // Spiegel in der Zelle); die Tabelle von hydro gegen dieselben Pixel steht zur Auskunft daneben
                    int eben = 0;
                    for (int r = 0; r < 20; r++) for (int c = 0; c < 20; c++) if (zf[(j * 20 + r) * W + i * 20 + c] < eta[cw]) eben++;
                    double soll = eben / 400.0;
                    if (!(soll > 0 && soll < 1)) continue;
                    double ist = nassPx / 400.0, tab = anteil(p3, cw, eta[cw]);
                    ab.add(Math.abs(ist - soll));
                    tabAb.add(Math.abs(tab - soll));
                    bildG += ist;
                    tabG += soll;
                }
            String wann = namen[s] + " " + zeit(t0, ts);
            if (ab.isEmpty()) A.info("Prüfstein 5: " + wann, "keine teilweise nasse Zelle im Fenster");
            else {
                double[] a = ab.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                double mittel = Arrays.stream(a).average().orElse(0), p95 = a[(int) Math.min(a.length - 1, Math.ceil(0.95 * a.length) - 1)];
                A.pruefe(mittel <= 0.01 && p95 <= 0.05, "Prüfstein 5: " + wann + " – benetzter Anteil im Bild gegen die Pixel des Stapels unter η der Zelle, je teilweise nasser Zelle von G3 (F46 A)",
                        f("%d Zellen: im Mittel %.2f %%, 95 %% ≤ %.2f %%, größte %.1f %% (Anteil gesamt %.1f gegen %.1f %%)", a.length, 100 * mittel, 100 * p95, 100 * a[a.length - 1],
                                100 * bildG / a.length, 100 * tabG / a.length), "≤ 1 %; ≤ 5 % [G]");
                double[] t = tabAb.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                A.info("Prüfstein 5: " + wann + " – Tabelle von hydro (A(η) / Zellfläche, 12 Stufen) gegen dieselben Pixel",
                        f("im Mittel %.2f %%, 95 %% ≤ %.2f %%, größte %.1f %%", 100 * Arrays.stream(t).average().orElse(0), 100 * t[(int) Math.min(t.length - 1, Math.ceil(0.95 * t.length) - 1)],
                                100 * t[t.length - 1]));
            }
            A.info("Prüfstein 5: " + wann + " – Saum (A52): Wasser in trockenen Zellen am Rand zu einer nassen Seitenzelle ohne Bauwerk", f("%d Pixel", saumNass));
            A.pruefe(trockenNass == 0 && kanteNass == 0, "Prüfstein 5: " + wann + " – Wasser in trockenen Zellen ohne Saum; davon hinter einer Deichkante mit nassem Nachbarn",
                    f("%d; %d Pixel (%d solche Zellen im Fenster)", trockenNass, kanteNass, kanteZellen), "0; 0");
        }
    }

    /** Anteil der Zellfläche unter η nach der Tabelle der Packung (wie WaterCheck). */
    static double anteil(Subgrid.Packung p, int c, double eta) {
        int S = p.stufen(), o = c * 3 * S;
        double b = p.basis()[c];
        if (!(eta > b + Float.float16ToFloat(p.tabelle()[o]))) return 0;
        int l = S - 1;
        while (b + Float.float16ToFloat(p.tabelle()[o + l]) > eta) l--;
        if (l == S - 1) return 1;
        double z0 = b + Float.float16ToFloat(p.tabelle()[o + l]), z1 = b + Float.float16ToFloat(p.tabelle()[o + l + 1]);
        double a0 = Float.float16ToFloat(p.tabelle()[o + 2 * S + l]), a1 = Float.float16ToFloat(p.tabelle()[o + 2 * S + l + 1]);
        return a0 + (a1 - a0) * (eta - z0) / (z1 - z0);
    }

    /** Prüfstein 6: η_Bild zwischen zwei Ständen von hydro; Wechsel des Intervalls. */
    static void prufstein6(Szene sz, Archivquelle q, int k3, Gitter g3, Instant t0, double[] z, int iHW) {
        // das steigende Wasser vor dem Hochwasser: Stände a = HW − 2, b = HW − 1, c = HW
        int ib = Math.max(1, Math.min(z.length - 2, iHW - 1)), ia = ib - 1, ic = ib + 1;
        int n3 = g3.zellen();
        float[][] ea = new float[3][n3], eb = new float[3][n3], ec = new float[3][n3];
        q.stand(z[ia], k3, ea[0], ea[1], ea[2]);
        q.stand(z[ib], k3, eb[0], eb[1], eb[2]);
        q.stand(z[ic], k3, ec[0], ec[1], ec[2]);
        double augeO = g3.x0 + g3.nx * g3.dx / 2, augeN = g3.y0 + g3.ny * g3.dy / 2;
        Blick b = new Blick(augeO, augeN, 300, 0, -89, 60).groesse(aBreite, aHoehe);
        // Zellen, die in allen drei Ständen nass sind
        List<Integer> zellen = new ArrayList<>();
        for (int c = 0; c < n3; c++) if (nass(ea, c) && nass(eb, c) && nass(ec, c)) zellen.add(c);
        long orte = 0, schritte = 0;
        int maxS = 0;
        double[] fr = {0, 0.25, 0.5, 0.75, 1};
        Spiegel s3 = null;
        for (Spiegel s : sz.bild.spiegel()) if (s.gitter.name.equals("G3")) s3 = s;
        for (double f : fr) {
            double t = z[ia] + f * (z[ib] - z[ia]);
            if (f == 0) t = z[ia] + 1e-6;               // auf einem Stand gälte er allein: knapp danach, im Intervall a, b
            if (f == 1) t = z[ib] - 1e-6;
            sz.zeichne(b, t0, t, null);
            float w = s3.gewicht(t);
            for (int c : zellen) {
                int i = c % g3.nx, j = c / g3.nx;
                float ox = (float) (g3.x0 + (i + 0.5) * g3.dx - augeO), oz = (float) (augeN - (g3.y0 + (j + 0.5) * g3.dy));
                float ist = sz.wasser.etaBild(ox, oz, null);
                float soll = ea[0][c] + w * (eb[0][c] - ea[0][c]);
                int d = schritte(ist, soll);
                maxS = Math.max(maxS, d);
                orte++;
                if (d > 1) schritte++;
            }
        }
        A.pruefe(maxS <= 1, "Prüfstein 6: η_Bild an den Zellmitten von G3 zu fünf Zeiten zwischen zwei Ständen von hydro (" + zeit(t0, z[ia]) + " – " + zeit(t0, z[ib]) + ") gegen a + s · (b − a)",
                f("%d Orte (%d Zellen, in allen Ständen nass), größte Abweichung %d float-Schritte, über 1: %d", orte, zellen.size(), maxS, schritte), "±1");
        // Wechsel des Intervalls bei b: kurz davor (a, b) und kurz danach (b, c)
        double eps = 1e-3;
        float[] vor = new float[zellen.size()];
        sz.zeichne(b, t0, z[ib] - eps, null);
        double[] paarVor = sz.bild.paar();
        for (int k = 0; k < vor.length; k++) vor[k] = etaZelle(sz, g3, zellen.get(k), augeO, augeN);
        sz.zeichne(b, t0, z[ib] + eps, null);
        double[] paarNach = sz.bild.paar();
        double sprung = 0, rate = 0;
        for (int k = 0; k < vor.length; k++) {
            int c = zellen.get(k);
            float nach = etaZelle(sz, g3, c, augeO, augeN);
            // was die Tide in 2 ms selbst ändert, abziehen
            double soll = (ec[0][c] - ea[0][c]) / (z[ic] - z[ia]) * 2 * eps;
            sprung = Math.max(sprung, Math.abs(nach - vor[k] - soll));
            rate = Math.max(rate, Math.abs(soll));
        }
        A.pruefe(sprung <= 1e-6 && paarVor[1] == z[ib] && paarNach[0] == z[ib] && paarNach[1] == z[ic], "Prüfstein 6: Wechsel des Intervalls bei " + zeit(t0, z[ib]) + " (±1 ms, Paare vorher und nachher)",
                f("%.1e m (Änderung der Tide in 2 ms bis %.1e m); Paare %s → %s", sprung, rate, paar(t0, paarVor), paar(t0, paarNach)), "≤ 1 · 10⁻⁶ m");
        A.info("Bild am Archiv", f("%d Mal Stände gesetzt, %d Mal verdrängt", sz.bild.gesetzt, sz.bild.verpasst));
    }

    static String paar(Instant t0, double[] p) { return "(" + zeit(t0, p[0]).substring(11) + ", " + zeit(t0, p[1]).substring(11) + ")"; }

    static float etaZelle(Szene sz, Gitter g3, int c, double augeO, double augeN) {
        int i = c % g3.nx, j = c / g3.nx;
        return sz.wasser.etaBild((float) (g3.x0 + (i + 0.5) * g3.dx - augeO), (float) (augeN - (g3.y0 + (j + 0.5) * g3.dy)), null);
    }

    static boolean nass(float[][] st, int c) { return st[2][c] > 0 || st[2][c] == 0 && st[1][c] > 0; }

    static int schritte(float a, float b) {
        if (Float.isNaN(a) || Float.isNaN(b)) return Float.isNaN(a) && Float.isNaN(b) ? 0 : Integer.MAX_VALUE / 2;
        int ia = Float.floatToIntBits(a), ib = Float.floatToIntBits(b);
        if (ia < 0) ia = 0x80000000 - ia;
        if (ib < 0) ib = 0x80000000 - ib;
        return Math.abs(ia - ib);
    }

    /** Prüfstein 3 in den vier Ansichten und Prüfstein 10. */
    /** Eine Ansicht des Status-Bilds (Prüfstein 10): welches Auge, wann (für den Namen), aus welcher Szene zu welcher Zeit. */
    record Ansicht(int auge, String wann, Szene sz, Instant t0, double t) {
        static final int WEST = 0, VOGEL = 1, ABEND = 2;
    }

    static void ansichten(Ansicht[] av) throws Exception {
        Gelaende gel = av[0].sz().gelaende.gelaende;
        double westH = gel.hoehe(WEST_OST, WEST_NORD, 1) + 1.7, swH = gel.hoehe(SW_OST, SW_NORD, 1) + 1.7, azAb = Double.NaN;
        Object[][] v = new Object[av.length][];
        for (int n = 0; n < av.length; n++) {
            Ansicht an = av[n];
            Blick bl;
            String name;
            switch (an.auge()) {
                case Ansicht.WEST -> {
                    bl = new Blick(WEST_OST, WEST_NORD, westH, WEST_RICHTUNG, -2, 55);
                    name = "Deich an der Westspitze über den Japsand, " + an.wann();
                }
                case Ansicht.VOGEL -> {
                    bl = new Blick(470_400, 6_044_300, 300, 20, -30, 60);
                    name = "Vogelblick aus 300 m über Hooge, " + an.wann();
                }
                default -> {
                    azAb = Gestirne.bei(an.t0().plusMillis(Math.round(an.t() * 1000))).sonne(LON, LAT).azimutGrad();
                    bl = new Blick(SW_OST, SW_NORD, swH, azAb, -1, 55);
                    name = "Tiefe Abendsonne über dem Watt, Südwestdeich, " + an.wann();
                }
            }
            v[n] = new Object[] {name, bl, an};
        }
        A.info("Augen der Ansichten", f("Westspitze O %.0f, N %.0f, %.2f m NHN, Blick %.0f°; Südwestdeich O %.0f, N %.0f, %.2f m NHN, Blick zur Sonne %.1f°",
                WEST_OST, WEST_NORD, westH, WEST_RICHTUNG, SW_OST, SW_NORD, swH, azAb));
        BufferedImage alle = new BufferedImage(aBreite * 2, aHoehe * 2, BufferedImage.TYPE_INT_RGB);
        for (int n = 0; n < v.length; n++) {
            Blick b = ((Blick) v[n][1]).groesse(aBreite, aHoehe);
            Szene sz = ((Ansicht) v[n][2]).sz();
            Instant t0 = ((Ansicht) v[n][2]).t0();
            double t = ((Ansicht) v[n][2]).t();
            // Belichtung wie ein Standbild: jede Messung gilt sofort, bis sie steht (das Histogramm reicht 18 Blenden; von
            // der Belichtung der vorigen Ansicht – Tag nach Nacht – braucht es mehrere Messungen)
            for (int k = 0; k < 12; k++) {
                sz.rahmen.belichtung.sofort();
                sz.zeichne(b, t0, t, null);
            }
            long[] r = dicht(b, sz.rahmen.ziel().hdr, sz.rahmen.ziel().tiefe, sz.wasser.kruemmung);
            long[] w = wasserImBild(b, sz.wasser, sz.rahmen.ziel().tiefe);
            A.pruefe(r[0] == 0 && r[1] == 0, "Prüfstein 3: " + v[n][0] + " (" + zeit(t0, t) + ") – Himmel unter der Kimm; NaN",
                    f("%d; %d Bildpunkte von %d", r[0], r[1], (long) aBreite * aHoehe), "0; 0");
            A.info("Wasser im Bild: " + v[n][0], f("Strahlen mit Wasser in Java %d (jeder 16. Bildpunkt), auf der Karte Wasser %d, Gelände davor %d; Wind am Auge %.1f, %.1f m/s; %s",
                    w[0], w[1], w[2], sz.bild.windU, sz.bild.windV, sz.rahmen.belichtung));
            float[] p = sz.aus.lies();
            for (int y = 0; y < aHoehe; y++)
                for (int x = 0; x < aBreite; x++) {
                    int i = 4 * (y * aBreite + x);
                    int rr = Math.round(p[i] * 255), gg = Math.round(p[i + 1] * 255), bb = Math.round(p[i + 2] * 255);
                    alle.setRGB(x + (n % 2) * aBreite, y + (n / 2) * aHoehe, (rr << 16) | (gg << 8) | bb);
                }
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, aHoehe / 36)));
            String txt = v[n][0] + " · " + zeit(t0, t);
            int x0 = (n % 2) * aBreite + 8, y0 = (n / 2) * aHoehe + aHoehe - 10;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(txt) + 8, g2.getFontMetrics().getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.drawString(txt, x0, y0);
            g2.dispose();
        }
        File png = new File("docs" + File.separator + "status" + File.separator + "P6_Bild.png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        A.info("Prüfstein 10: Status-Bild (Sichtprüfung)", png.getPath() + f(", %d × %d", alle.getWidth(), alle.getHeight()));
    }

    /**
     * {Himmel unter der Kimm, NaN}: gezählt werden Bildpunkte mit der Tiefe der Hinterebene, die tiefer zeigen als die
     * Kimm über dem tiefsten möglichen Grund (−30 m) mit Erdkrümmung, zwei Bildpunkte Rand (wie TerrainCheck).
     */
    static long[] dicht(Blick b, Bild hdr, Bild tiefe, double R) {
        float[] dd = tiefe.lies(), h = hdr.lies();
        double px = Math.toRadians(b.bildwinkel) / b.hoehe_px, kimm = Math.sqrt(2 * (b.hoehe + 30) / R);
        long himmel = 0, nan = 0;
        for (int y = 0; y < b.hoehe_px; y++)
            for (int x = 0; x < b.breite; x++) {
                int i = y * b.breite + x;
                for (int c = 0; c < 4; c++) if (!Float.isFinite(h[4 * i + c])) { nan++; break; }
                double[] s = b.strahl(x + 0.5, y + 0.5);
                if (dd[i] <= 0 && Math.asin(-s[1]) > kimm + 2 * px) himmel++;
            }
        return new long[] {himmel, nan};
    }

    /** {Strahlen mit Wasser nach Java, davon auf der Karte Wasser, davon Gelände davor} an jedem 16. Bildpunkt (wie die Vorschau zu P6d). */
    static long[] wasserImBild(Blick b, Wasserbild w, Bild tiefe) {
        float[] td = tiefe.lies();
        long wtr = 0, gpuW = 0, vor = 0;
        double kk = 1 / (2 * w.kruemmung);
        double[] fv = b.basis()[2];
        for (int y = 0; y < b.hoehe_px; y += 4)
            for (int x = 0; x < b.breite; x += 4) {
                double[] s = b.strahl(x + 0.5, y + 0.5);
                float e = w.startwert();
                double t = -1;
                for (int it = 0; it < 8; it++) {
                    double cc = e - b.hoehe, aa = kk * (s[0] * s[0] + s[2] * s[2]), D = s[1] * s[1] + 4 * aa * cc;
                    if (cc >= 0 || D < 0) { t = -1; break; }
                    t = -2 * cc / (-s[1] + Math.sqrt(D));
                    float en = w.etaBild((float) (s[0] * t), (float) (s[2] * t), null);
                    if (Float.isNaN(en)) { t = -1; break; }
                    e = en;
                }
                if (t <= 0) continue;
                wtr++;
                double soll = b.nah / (t * (s[0] * fv[0] + s[1] * fv[1] + s[2] * fv[2]));
                float ist = td[y * b.breite + x];
                if (Math.abs(ist - soll) < 1e-3 * soll) gpuW++;
                else if (ist > soll) vor++;
            }
        return new long[] {wtr, gpuW, vor};
    }

    // ------------------------------------------------------------------ Teil 3

    static void teil3(Geraet g, Konto konto, File gb, Gebiet ge, int[] f3) throws Exception {
        A.teil("3 Live: Zeiten (Prüfstein 8)");
        Lauf.Einstellung e = einstellung(gb, f3);
        if (ab.isAfter(start)) e.start = ab;                   // für die Zeiten genügt der Lauf ab dem Archiv, mit Anlauf
        e.bild = true;
        try (Lauf l = Lauf.bauen(e); Szene sz = new Szene(g, konto, gb, ge, l.bild, breite, hoehe); Stoppuhr uhr = new Stoppuhr(g, g.grafik, 32)) {
            A.info("Lauf", l + "; Bild am Bus über " + namen(l.bild.gitter()));
            if (!l.hinweise.isEmpty()) A.info("Hinweise beim Verdrahten", String.join("; ", l.hinweise));
            Gelaende gel = Gelaende.ordner(new File(gb, "gelaende"));
            Blick b = new Blick(OST, NORD, gel.hoehe(OST, NORD, 1) + 1.7, 270, -2, 55).groesse(breite, hoehe);
            Uhr u = new Uhr(e.start);
            l.takt.ziel = ziel;
            // Gemessen wird das Bild mit Gelände: die Stufen für das (feste) Auge erst ganz laden, dann ANLAUF Bilder zum
            // Anlaufen (Pipelines, erstes Hochladen), dann die Bilder der Messung. Der Lader läuft daneben wie in der App.
            double[] xz = sz.gelaende.raum(b);
            sz.gelaende.ziel(xz[0], xz[1]);
            sz.gelaende.ladeJetzt();
            sz.gelaende.start();
            l.takt.laufen(u);
            long n0 = System.nanoTime(), vorher = n0;
            double[] ms = new double[bilder];
            int mitGelaende = 0;
            try {
                for (int i = -ANLAUF; i < bilder; i++) {
                    long n = System.nanoTime();
                    if (i == 0) n0 = n;
                    double t = l.takt.fuehre(u, (n - vorher) / 1e9);
                    vorher = n;
                    sz.zeichne(b, e.start, t, uhr);
                    uhr.sammle(false);
                    if (i >= 0) {
                        ms[i] = (System.nanoTime() - n) / 1e6;
                        if (sz.gelaende.gezeigt() != null) mitGelaende++;
                    }
                    if (!l.takt.laeuft()) break;
                }
            } finally {
                l.takt.anhalten();
                sz.gelaende.stop();
            }
            double s = (System.nanoTime() - n0) / 1e9;
            uhr.sammle(true);
            double[] m = ms.clone();
            Arrays.sort(m);
            double fps = bilder / s, med = m[m.length / 2];
            StringBuilder d = new StringBuilder();
            for (String n : uhr.abschnitte()) if (uhr.ms(n) >= 0.05) d.append(f("%s %.2f; ", n, uhr.ms(n)));
            String wert = f("%d × %d, %d Bilder (nach %d zum Anlaufen) in %.1f s: %.1f Bilder/s, Median %.1f ms, langsamstes %.1f ms; Gelände in %d von %d Bildern; Szenenzeit %.0f s, Zeitraffer gehalten %.1f (Ziel %.0f, die Physik schafft %.1f), %d Mal angehalten",
                    breite, hoehe, bilder, ANLAUF, s, fps, med, m[m.length - 1], mitGelaende, bilder, u.sekunden(), u.zeitraffer, ziel, l.takt.erreichbar(), l.takt.angehalten());
            if (g.name.toLowerCase().contains("llvmpipe")) A.info("Prüfstein 8 (llvmpipe: nur zur Auskunft, die Grenze gilt auf der 680M)", wert);
            else if (Boolean.getBoolean("core.gpu.validierung")) A.info("Prüfstein 8 (mit Validierung verlangt: nur zur Auskunft, gemessen wird ohne)", wert);
            else A.pruefe(fps >= 30 && mitGelaende == bilder, "Prüfstein 8: Zeiten bei laufender Physik " + e.stufenText(), wert, "≥ 30 Bilder/s [G], jedes mit Gelände");
            A.info("Zeit je Durchgang (Stoppuhr, ms)", d.length() == 0 ? "keine Stoppuhr auf diesem Gerät" : d.toString());
            A.info("Bild am Bus", f("%d Mal Stände gesetzt, %d Mal verdrängt; außerhalb gelesen %d", sz.bild.gesetzt, sz.bild.verpasst, l.bus.ausserhalb()));
        }
    }
}
