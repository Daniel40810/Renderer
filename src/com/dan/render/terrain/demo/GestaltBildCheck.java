package com.dan.render.terrain.demo;

import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Gestalt;
import com.dan.render.terrain.Gelaendebild;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Gestalt im Bild des Geländes (P12b, Werkbuch HalligHooge Teil A, 12.13, Datei 69; Prüfsteine 12, 13 und 9) mit
 * dem Ersatzhimmel (gleichmäßig hell, L = 4000 cd/m²) und den echten Daten des Gebiets.
 * <ul>
 * <li><b>Prüfstein 12:</b> Blick knapp über einen großen Fething, Sonne unter dem Horizont, Kräuselung aus. Jeder
 * Bildpunkt, dessen Strahl (aus dem Tiefenpuffer) ganz im Fething auf das Gelände trifft, muss F · L_Himmel + (1 − F) · ρ/π · E_Himmel sein
 * (Fresnel nach Schlick mit n = 1,33, ρ der Körper des Wassers, E_Himmel aus {@code licht.comp}), in Java nachgerechnet:
 * 1 ± 0,03.</li>
 * <li><b>Prüfstein 13:</b> senkrecht von oben auf Flächen jeder Art (Asphalt, Klinker, Schotter, Beton, Grasweg, Holz,
 * Deckwerk, Fenne), je ein Bild mit Muster und eines ohne: Das Muster ändert die Albedo im Mittel um höchstens 2 %.</li>
 * <li><b>Prüfstein 9 (Teil):</b> Gestalt an gegen aus: Wo der Strahl im Umkreis von 1 m keine Gestalt trifft (Watt,
 * Himmel), sind beide Bilder bitgleich.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.render.terrain.demo.GestaltBildCheck [--gebiet D:\Daten\HalligHooge\gebiet]
 * </pre>
 */
public final class GestaltBildCheck {
    private static final Attest A = new Attest("com.dan.render.terrain (Gestalt)", Gelaendebild.VERSION);
    static final int B = 480, H = 270;
    static final double L_HIMMEL = TerrainCheck.L_HIMMEL;
    /** Körper des Wassers (gelaende.frag) */
    static final double[] KOERPER = {0.03, 0.035, 0.025};

    public static void main(String[] args) throws Exception {
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet";
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("GestaltBildCheck – com.dan.render.terrain " + Gelaendebild.VERSION + " (Werkbuch HalligHooge Teil A, P12b)");
        File gb = new File(gebiet);
        A.info("Gebiet", gb.getPath());
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("GestaltBildCheck", 2L << 30);
            try (Gelaendebild t = Gelaendebild.aus(g, konto, gb); TerrainCheck.Ersatz ers = new TerrainCheck.Ersatz(g, konto);
                 Bild hdr = new Bild(g, konto, "HDR", B, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe", B, H, Bild.D32F)) {
                t.flugZeit = 0;                // feste Zeit für die Kräuselung: jedes Bild gleich
                Gestalt ge = t.gestalt();
                A.pruefe(ge != null, "Gestaltkarte im Gebiet geladen", ge == null ? "fehlt" : f("%d Kacheln", ge.kacheln.size()), "vorhanden");
                if (ge != null) {
                    Bildziel z = new Bildziel(hdr, tiefe);
                    ers.ablegen(z);
                    z.vorbelichtung = 1;
                    spiegelung(t, ge, ers, z, g);
                    muster(t, ge, ers, z, g);
                    ohne(t, ge, ers, z, g);
                }
            }
            A.teil("4 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht, g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12b_GestaltBild.md"), "P12b – GestaltBildCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Prüfstein 12

    static void spiegelung(Gelaendebild t, Gestalt ge, TerrainCheck.Ersatz ers, Bildziel z, Geraet g) throws Exception {
        A.teil("1 Spiegelung auf einem Fething (Prüfstein 12)");
        double[] m = mitte(ge, Gestalt.FETHING, 40);
        A.info("Fething", f("dichteste Zelle zu 40 m bei O %.0f N %.0f (%.0f m² Wasser)", m[0], m[1], m[2]));
        Blick b = auge(t, m[0], m[1] - 55, 2.5, 0, -4, 50);
        ers.sonne(-10, 180, 0, 1, b.hoehe);
        // Kräuselung aus; das Ausblenden mit dem Fuß des Bildpunkts (ab 4 m) auch: Es hängt an der Ebene des Dreiecks,
        // die Java nicht genau nachbildet. Geprüft wird die Spiegelung selbst.
        t.gestaltKraeusel = 0;
        double wf = t.gestaltWasserFuss;
        t.gestaltWasserFuss = 1e5;
        try {
            float[] bild = zeichne(g, t, z, b);
            ByteBuffer lb = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
            t.licht().lies(lb, 0);
            double[] eh = {lb.getFloat(0), lb.getFloat(4), lb.getFloat(8)};
            A.info("Himmelslicht (licht.comp)", f("E = %.0f / %.0f / %.0f lx (π · L = %.0f)", eh[0], eh[1], eh[2], Math.PI * L_HIMMEL));
            int zahl = 0;
            double min = 9, max = 0, summe = 0;
            for (int py = 0; py < H; py++)
                for (int px = 0; px < B; px++) {
                    double[] d = b.strahl(px + 0.5, py + 0.5);
                    double[] hit = treffer(b, px, py);
                    if (hit == null) continue;
                    if (!ganz(ge, hit[0], hit[1], 0.6, Gestalt.FETHING)) continue;
                    double cv = -d[1], F = 0.02 + 0.98 * Math.pow(1 - cv, 5);
                    for (int c = 0; c < 3; c++) {
                        double soll = F * L_HIMMEL + (1 - F) * KOERPER[c] / Math.PI * eh[c], ist = bild[4 * (py * B + px) + c];
                        double q = ist / soll;
                        min = Math.min(min, q);
                        max = Math.max(max, q);
                        summe += q;
                    }
                    zahl++;
                }
            A.pruefe(zahl >= 500 && min >= 0.97 && max <= 1.03, "Prüfstein 12: Leuchtdichte auf dem Fething gegen F · L_Himmel + (1 − F) · ρ/π · E_Himmel (Java), je Kanal",
                    f("%d Bildpunkte; Verhältnis im Mittel %.4f, %.4f … %.4f", zahl, zahl > 0 ? summe / (3 * zahl) : Double.NaN, min, max), "≥ 500 Bildpunkte, 1 ± 0,03 [G]");
            png(bild, "P12b_Fething.png", 1.0 / L_HIMMEL);
            // Grund für das Wasserbild (F134 A): Alpha mal Leuchtdichte mit Gestalt = Leuchtdichte ohne Gestalt (der Fething
            // trägt nur Wasser, darunter das Gelände wie ohne Gestalt); außerhalb des Wassers Alpha 1
            t.gestaltAn = false;
            float[] ohne = zeichne(g, t, z, b);
            t.gestaltAn = true;
            double gmin = 9, gmax = 0;
            long eins = 0, keinEins = 0;
            for (int py = 0; py < H; py++)
                for (int px = 0; px < B; px++) {
                    int k = 4 * (py * B + px);
                    double[] hit = treffer(b, px, py);
                    if (hit == null) continue;
                    if (ganz(ge, hit[0], hit[1], 0.6, Gestalt.FETHING)) {
                        double y1 = 0.2126 * bild[k] + 0.7152 * bild[k + 1] + 0.0722 * bild[k + 2];
                        double y0 = 0.2126 * ohne[k] + 0.7152 * ohne[k + 1] + 0.0722 * ohne[k + 2];
                        double q = bild[k + 3] * y1 / y0;
                        gmin = Math.min(gmin, q);
                        gmax = Math.max(gmax, q);
                    } else if (ganz(ge, hit[0], hit[1], 1.0, Gestalt.KEINE) || ganz(ge, hit[0], hit[1], 1.0, Gestalt.FENNE)) {
                        if (bild[k + 3] == 1f) eins++; else keinEins++;
                    }
                }
            A.pruefe(gmin >= 0.99 && gmax <= 1.01 && keinEins == 0, "Grund für das Wasserbild (F134 A): Alpha · L mit Gestalt gegen L ohne Gestalt auf dem Fething; ohne Wasser Alpha 1",
                    f("%.4f … %.4f; Alpha 1 an %d Bildpunkten ohne Wasser, anders an %d", gmin, gmax, eins, keinEins), "1 ± 0,01 (Halbfloat); 0 anders");
        } finally {
            t.gestaltKraeusel = 1;
            t.gestaltWasserFuss = wf;
        }
    }

    // ------------------------------------------------------------------ Prüfstein 13

    static void muster(Gelaendebild t, Gestalt ge, TerrainCheck.Ersatz ers, Bildziel z, Geraet g) throws Exception {
        A.teil("2 Muster im Mittel (Prüfstein 13)");
        int[] arten = {Gestalt.ASPHALT, Gestalt.KLINKER, Gestalt.SCHOTTER, Gestalt.BETON, Gestalt.GRASWEG, Gestalt.HOLZ, Gestalt.DECKWERK, Gestalt.FENNE, Gestalt.SALZWIESE};
        for (int art : arten) {
            double[] m = mitte(ge, art, Gestalt.weg(art) ? 8 : 30);
            if (m[2] < 10) {
                A.info(Gestalt.name(art), f("keine Fläche ≥ 10 m² in einer Zelle (größte %.1f m²)", m[2]));
                continue;
            }
            boolean fein = Gestalt.weg(art);
            Blick b = auge(t, m[0], m[1], fein ? 12 : 40, 0, -89, 40);
            ers.sonne(45, 200, 100_000, 1, b.hoehe);
            t.gestaltMuster = 1;
            float[] mit = zeichne(g, t, z, b);
            t.gestaltMuster = 0;
            float[] ohne = zeichne(g, t, z, b);
            t.gestaltMuster = 1;
            double sm = 0, so = 0;
            int zahl = 0;
            for (int py = 0; py < H; py++)
                for (int px = 0; px < B; px++) {
                    double[] hit = treffer(b, px, py);
                    if (hit == null || !ganz(ge, hit[0], hit[1], 0.6, art)) continue;
                    int k = 4 * (py * B + px);
                    for (int c = 0; c < 3; c++) {
                        sm += mit[k + c];
                        so += ohne[k + c];
                    }
                    zahl++;
                }
            double q = so > 0 ? sm / so : Double.NaN;
            if (zahl < 300) {
                A.info(Gestalt.name(art), f("nur %d Bildpunkte ganz in der Fläche (O %.0f N %.0f), Verhältnis %.4f", zahl, m[0], m[1], q));
                continue;
            }
            A.pruefe(Math.abs(q - 1) <= 0.02, "Prüfstein 13: " + Gestalt.name(art) + " – Albedo mit Muster gegen ohne, im Mittel",
                    f("%.4f über %d Bildpunkte (O %.0f N %.0f, %.0f m von oben)", q, zahl, m[0], m[1], fein ? 12.0 : 40.0), "1 ± 0,02");
            if (art == Gestalt.KLINKER || art == Gestalt.DECKWERK) png(mit, "P12b_Muster_" + Gestalt.name(art) + ".png", 1.0 / 30000);
        }
    }

    // ------------------------------------------------------------------ Prüfstein 9

    static void ohne(Gelaendebild t, Gestalt ge, TerrainCheck.Ersatz ers, Bildziel z, Geraet g) throws Exception {
        A.teil("3 Außerhalb der Gestalt unverändert (Prüfstein 9)");
        double[] hl = ge.huelle();
        // vom Watt im Süden der Hallig nach Norden auf den Rand der Gestalt
        double[] m = mitte(ge, Gestalt.DECKWERK, 200);
        Blick b = auge(t, m[0], m[1] - 150, 15, 10, -5, 55);
        ers.sonne(30, 200, 100_000, 1, b.hoehe);
        t.gestaltAn = true;
        float[] mit = zeichne(g, t, z, b);
        t.gestaltAn = false;
        float[] aus = zeichne(g, t, z, b);
        t.gestaltAn = true;
        long anders = 0, draussen = 0, frei = 0;
        for (int py = 0; py < H; py++)
            for (int px = 0; px < B; px++) {
                int k = 4 * (py * B + px);
                boolean gleich = true;
                for (int c = 0; c < 4; c++) if (Float.floatToRawIntBits(mit[k + c]) != Float.floatToRawIntBits(aus[k + c])) gleich = false;
                double[] hit = treffer(b, px, py);
                boolean ohneGestalt = hit == null || ganz(ge, hit[0], hit[1], 1.0, Gestalt.KEINE);
                if (ohneGestalt) frei++;
                if (!gleich) {
                    anders++;
                    if (ohneGestalt) draussen++;
                }
            }
        A.pruefe(draussen == 0 && anders > 0, "Prüfstein 9: Gestalt an gegen aus – wo keine Gestalt getroffen wird, bitgleich",
                f("%d Bildpunkte anders, davon %d ohne Gestalt (ohne Gestalt %.0f %% des Bilds)", anders, draussen, 100.0 * frei / (B * H)), "0, und die Gestalt ändert etwas");
        A.info("Hülle der Gestalt", f("O %.0f … %.0f, N %.0f … %.0f", hl[0], hl[2], hl[1], hl[3]));
        png(mit, "P12b_Rand.png", 1.0 / 30000);
    }

    // ------------------------------------------------------------------ Hilfen

    static Blick auge(Gelaendebild t, double ost, double nord, double ueber, double richtung, double neigung, double winkel) {
        Blick b = new Blick(ost, nord, 0, richtung, neigung, winkel).groesse(B, H);
        float boden = t.gelaende.hoehe(ost, nord, 1);
        b.hoehe = (Float.isNaN(boden) ? 0 : boden) + ueber;
        return b;
    }

    /** Tiefe des letzten Bilds (umgekehrtes Z: nah / Abstand längs der Blickachse; 0: nichts getroffen). */
    static float[] tiefe;

    static float[] zeichne(Geraet g, Gelaendebild t, Bildziel z, Blick b) {
        z.blick = b;
        double[] xz = t.raum(b);
        t.ziel(xz[0], xz[1]);
        t.ladeJetzt();
        TerrainCheck.bild(g, t, z);
        tiefe = z.tiefe.lies();
        return z.hdr.lies();
    }

    /** Wo der Bildpunkt das Gelände trifft, aus dem Tiefenpuffer der Karte: {Ost, Nord, Weg}; null ohne Treffer. */
    static double[] treffer(Blick b, int px, int py) {
        float t = tiefe[py * B + px];
        if (!(t > 0)) return null;
        double[] d = b.strahl(px + 0.5, py + 0.5), v = b.strahl(B / 2.0, H / 2.0);
        double s = b.nah / t / (d[0] * v[0] + d[1] * v[1] + d[2] * v[2]);
        return new double[] {b.ost + d[0] * s, b.nord - d[2] * s, s};
    }

    /** Ist die Karte im Umkreis r überall von dieser Art (für KEINE: auch außerhalb der Karte)? */
    static boolean ganz(Gestalt ge, double ost, double nord, double r, int art) {
        for (double oy = -r; oy <= r + 1e-9; oy += r / 2)
            for (double ox = -r; ox <= r + 1e-9; ox += r / 2) {
                int[] w = ge.bei(ost + ox, nord + oy);
                int a = w == null ? 0 : w[0];
                if (a != art) return false;
            }
        return true;
    }

    /** Die Zelle (Kante s m) mit der größten Fläche dieser Art: {Ost, Nord der Mitte, Fläche m²}. */
    static double[] mitte(Gestalt ge, int art, double s) {
        java.util.HashMap<Long, Integer> z = new java.util.HashMap<>();
        double best = 0, bo = 0, bn = 0;
        for (Gestalt.Kachel k : ge.kacheln) {
            byte[] d = k.daten();
            for (int j = 0; j < ge.n; j++)
                for (int i = 0; i < ge.n; i++)
                    if ((d[2 * (j * ge.n + i)] & 0xFF) == art) {
                        double x = k.ost() + (i + 0.5) * ge.zelle, y = k.nord() + ge.kantenlaenge() - (j + 0.5) * ge.zelle;
                        long key = ((long) Math.floor(x / s) << 32) ^ (Math.floor(y / s) < 0 ? 0 : (long) Math.floor(y / s));
                        int v = z.merge(key, 1, Integer::sum);
                        if (v > best) {
                            best = v;
                            bo = (Math.floor(x / s) + 0.5) * s;
                            bn = (Math.floor(y / s) + 0.5) * s;
                        }
                    }
        }
        return new double[] {bo, bn, best * ge.zelle * ge.zelle};
    }

    static void png(float[] f, String name, double belichtung) throws Exception {
        BufferedImage im = new BufferedImage(B, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < B; x++) {
                int k = 4 * (y * B + x), rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = f[k + c] * belichtung;
                    v = v / (1 + v);
                    v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
                    rgb = (rgb << 8) | (int) Math.round(255 * Math.max(0, Math.min(1, v)));
                }
                im.setRGB(x, y, rgb);
            }
        File d = new File("docs/status");
        Files.createDirectories(d.toPath());
        File o = new File(d, name);
        ImageIO.write(im, "png", o);
        A.info("Bild", o.getPath());
    }
}
