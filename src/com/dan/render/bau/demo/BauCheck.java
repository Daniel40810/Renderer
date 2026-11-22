package com.dan.render.bau.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bauten;
import com.dan.render.bau.Hausbild;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.bau ohne Fenster (P12a, Werkbuch HalligHooge Teil A, 12.13, Prüfsteine 4 und 5) an einem erzeugten
 * Haus: Friesenhaus 16 × 9 m, Traufe 2,6 m, Reetdach 50°, auf einer Platte 240 × 240 m (flaches „Dach“ als Boden).
 * Der Himmel ist ein Ersatz mit bekannten Werten (render.bau kennt render.air nicht): Luft ohne Dämpfung, gleichmäßig
 * heller Himmel. Teile: 1 Ablage (Bauten schreiben und lesen), 2 Himmelslicht (Glieder gegen Java), 3 Licht auf der Wand
 * (Prüfstein 4), 4 Schatten (Prüfstein 5), 5 Fensterlicht, 6 Bilder, 7 Aufräumen. Endet mit „Alles in Ordnung.“ und
 * schreibt das Attest nach {@code docs/status/P12a_Bau.md}, die Bilder nach {@code docs/status/P12a_Haus*.png}.
 */
public final class BauCheck {
    private static final Attest A = new Attest("com.dan.render.bau", Hausbild.VERSION);
    static final double L_HIMMEL = 4000, E_SONNE = 80_000;
    static final int B = 640, H = 400;
    /** Ursprung des Raums (wie eine Stufe 1 m) und Lage des Hauses. */
    static final double E0 = 470_000, N0 = 6_048_000;

    public static void main(String[] args) throws Exception {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("BauCheck – com.dan.render.bau " + Hausbild.VERSION + " (Werkbuch HalligHooge Teil A, P12a)");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("BauCheck", 512L << 20);
            pruefen(g, konto);
            A.teil("7 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht, g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P12a_Bau.md"), "P12a – BauCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ das Haus

    /** Haus 16 (Ost–West) × 9 m, Traufe 2,6 m, Dach 50°, First längs; Mitte (x, z) im Raum, Boden y0; dazu die Platte. */
    static Bauten haus(double x, double z, double y0, double traufe, double neigung) {
        Bauten.Bauer b = new Bauten.Bauer();
        // Platte als Boden (flach, Albedo wie Fenne)
        Bauten.Gebaeude p = b.neu();
        p.boden = (float) y0 - 1;
        p.traufe = p.first = (float) y0;
        p.dach = Bauten.FLACH;
        p.dachAlbedo = new float[] {0.08f, 0.09f, 0.06f};
        p.wandAlbedo = new float[] {0.08f, 0.09f, 0.06f};
        p.kennung = "Platte";
        double s = 120;
        b.flaeche(Bauten.DACH, new double[] {x - s, y0, z + s, x + s, y0, z + s, x + s, y0, z - s, x - s, y0, z - s}, x, z);
        b.fertig();
        Bauten.Gebaeude h = b.neu();
        double lx = 8, lz = 4.5, tr = y0 + traufe, fi = tr + lz * Math.tan(Math.toRadians(neigung));
        h.boden = (float) y0;
        h.traufe = (float) tr;
        h.first = (float) fi;
        h.dach = Bauten.REET;
        h.dachAlbedo = new float[] {0.115f, 0.100f, 0.080f};
        h.wandAlbedo = new float[] {0.22f, 0.065f, 0.045f};
        h.kennung = "Friesenhaus";
        double x0 = x - lx, x1 = x + lx, z0 = z - lz, z1 = z + lz;
        // Wände (Süden z1, Norden z0; Giebel im Westen x0 und Osten x1)
        b.flaeche(Bauten.WAND, new double[] {x0, y0, z1, x1, y0, z1, x1, tr, z1, x0, tr, z1}, x, z);
        b.flaeche(Bauten.WAND, new double[] {x1, y0, z0, x0, y0, z0, x0, tr, z0, x1, tr, z0}, x, z);
        b.flaeche(Bauten.WAND, new double[] {x0, y0, z0, x0, y0, z1, x0, tr, z1, x0, fi, z, x0, tr, z0}, x, z);
        b.flaeche(Bauten.WAND, new double[] {x1, y0, z1, x1, y0, z0, x1, tr, z0, x1, fi, z, x1, tr, z1}, x, z);
        // Dach
        b.flaeche(Bauten.DACH, new double[] {x0, tr, z1, x1, tr, z1, x1, fi, z, x0, fi, z}, x, z);
        b.flaeche(Bauten.DACH, new double[] {x1, tr, z0, x0, tr, z0, x0, fi, z, x1, fi, z}, x, z);
        // Zwerchgiebel über der Tür in der Südwand (F129 A)
        b.zwerchgiebel(x, z1, 1, 0, 0, 1, tr, 3.2, 1.4, 50, neigung, x, z);
        b.fertig();
        return b.bauten(E0, N0);
    }

    /** Ersatz für den Himmel: Bilder und Werte unter den Namen von render.air (wie TerrainCheck). */
    static final class Ersatz implements AutoCloseable {
        final Bild durch, ansicht;
        final Puffer werte;
        final float[] w = new float[60];

        Ersatz(Geraet g, Konto k) {
            durch = new Bild(g, k, "Ersatz Durchlässigkeit", 256, 64, Bild.RGBA32F);
            durch.schreibe(new float[256 * 64 * 4]);                    // optische Dicke 0: Durchlässigkeit 1
            ansicht = new Bild(g, k, "Ersatz Ansicht", 256, 128, Bild.RGBA32F);
            werte = Puffer.speicher(g, k, "Ersatz Werte", 60 * 4);
            himmel(L_HIMMEL);
        }

        void himmel(double l) {
            float[] a = new float[256 * 128 * 4];
            for (int i = 0; i < 256 * 128; i++) {
                a[4 * i] = a[4 * i + 1] = a[4 * i + 2] = (float) l;
                a[4 * i + 3] = 1;
            }
            ansicht.schreibe(a);
        }

        /** Sonne aus Höhe und Azimut (Grad), E (lx), Vorbelichtung, Augenhöhe; legt sie auch als Zahlen ab (wie render.air). */
        double[] sonne(double hGrad, double azGrad, double e, double vb, double auge) {
            double h = Math.toRadians(hGrad), az = Math.toRadians(azGrad);
            Arrays.fill(w, 0);
            w[0] = 1e-5f; w[1] = 1e-5f; w[2] = 0.08f; w[3] = 0.85f;
            double[] l = {Math.cos(h) * Math.sin(az), Math.sin(h), -Math.cos(h) * Math.cos(az)};
            w[4] = (float) l[0]; w[5] = (float) l[1]; w[6] = (float) l[2]; w[7] = (float) Math.max(1, auge);
            w[8] = w[9] = w[10] = (float) e; w[11] = (float) vb;
            w[12] = 0; w[13] = -1; w[14] = 0;
            w[52] = w[53] = w[54] = w[56] = w[57] = w[58] = 1;
            werte.schreibe(w);
            return l;
        }

        void ablegen(Bildziel z, double[] l, double hGrad) {
            z.geteilt.put("Himmel/Durchlaessigkeit", durch);
            z.geteilt.put("Himmel/Ansicht", ansicht);
            z.puffer.put("Himmel/Werte", werte);
            z.werte.put("Himmel/Sonne", new double[] {l[0], l[1], l[2], E_SONNE, E_SONNE, E_SONNE, hGrad, 0, hGrad});
            z.werte.put("Himmel/Mond", new double[] {0, -1, 0, 0, 0, 0, -90, 0, 0, -90});
        }

        @Override public void close() {
            durch.close();
            ansicht.close();
            werte.close();
        }
    }

    static void bild(Geraet g, Hausbild t, Bildziel z) {
        Ablauf a = new Ablauf(g, g.grafik);
        a.zeichne("löschen", new Bild[] {z.hdr}, new float[][] {{0, 0, 0, 0}}, z.tiefe, 0f, null, c -> { });
        t.vorbereiten(a, z);
        t.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
    }

    static void pruefen(Geraet g, Konto konto) throws Exception {
        // ---------------------------------------------------------------- 1 Ablage
        A.teil("1 Ablage");
        double hx = 500, hz = 500, y0 = 4.0;                                   // Haus 500 m östlich und südlich des Ursprungs, Warft 4 m NHN
        Bauten bt = haus(hx, hz, y0, 2.6, 50);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        bt.schreibe(bo);
        Bauten zu = Bauten.lies(new java.io.ByteArrayInputStream(bo.toByteArray()));
        A.pruefe(Arrays.equals(zu.scheitel, bt.scheitel) && zu.gebaeude.size() == bt.gebaeude.size() && zu.gebaeude.get(1).kennung.equals("Friesenhaus")
                        && zu.e0 == E0 && zu.n0 == N0, "Bauten schreiben und lesen", f("%d Häuser, %d Dreiecke, %d Byte", zu.gebaeude.size(), zu.dreiecke(), bo.size()),
                "bitgleich");
        A.info("Haus", f("16 × 9 m, Traufe %.1f m, First %.2f m über dem Boden, Reet", 2.6, bt.gebaeude.get(1).first - y0));

        try (Hausbild t = new Hausbild(g, konto, bt, 2048); Ersatz ers = new Ersatz(g, konto);
             Bild hdr = new Bild(g, konto, "HDR", B, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe", B, H, Bild.D32F)) {
            Bildziel z = new Bildziel(hdr, tiefe);
            z.zeit = Instant.parse("2026-08-15T10:00:00Z");
            double augeH = y0 + 1.7;
            // ---------------------------------------------------------------- 2 Himmelslicht
            A.teil("2 Himmelslicht");
            double[] l = ers.sonne(30, 180, E_SONNE, 1, 1.7);
            ers.ablegen(z, l, 30);
            z.blick = new Blick(E0 + hx, N0 - hz - 25, augeH, 0, 5, 50).groesse(B, H);    // 25 m südlich, Blick nach Norden auf die Südwand
            bild(g, t, z);
            float[] sh = t.licht().floats(36);
            double soll0 = L_HIMMEL * 0.282095 * 4 * Math.PI, rest = 0;
            for (int k = 1; k < 9; k++) rest = Math.max(rest, Math.abs(sh[4 * k + 1]) / soll0);
            A.pruefe(Math.abs(sh[1] / soll0 - 1) <= 1e-3 && rest <= 1e-3, "Glieder bei gleichmäßigem Himmel: L₀₀ = L · Y₀₀ · 4π, alle anderen 0",
                    f("L₀₀ %.1f gegen %.1f, größtes anderes %.1e relativ", sh[1], soll0, rest), "relativ ≤ 1e-3 [G]");

            // ---------------------------------------------------------------- 3 Licht auf der Wand (Prüfstein 4)
            A.teil("3 Licht auf der Wand (Prüfstein 4)");
            // Mitte der Südwand zwischen den Fenstern ist Backstein; das Mittel über einen Streifen unter den Fenstern
            float[] px = hdr.lies();
            double[] wand = mittelWand(px, z.blick, hx, hz + 4.5, y0 + 0.30, y0 + 0.75, 6.0);
            double a = bt.gebaeude.get(1).wandAlbedo[1], cosI = Math.cos(Math.toRadians(30));   // Sonne aus Süden, 30° hoch: auf die Südwand cos 30°
            double erw = a / Math.PI * (E_SONNE * cosI + Math.PI * L_HIMMEL);
            A.pruefe(Math.abs(wand[0] / erw - 1) <= 0.03, "Südwand, Kanal G: Mittel gegen a/π · (E · cos i + π · L_Himmel) in Java",
                    f("%.1f gegen %.1f cd/m² (%.3f; %d Bildpunkte)", wand[0], erw, wand[0] / erw, (int) wand[1]), "1 ± 0,03 [G]");
            // Nordwand im Eigenschatten: nur Himmel
            z.blick = new Blick(E0 + hx, N0 - hz + 25, augeH, 180, 5, 50).groesse(B, H);
            bild(g, t, z);
            px = hdr.lies();
            wand = mittelWand(px, z.blick, hx, hz - 4.5, y0 + 0.30, y0 + 0.75, 6.0);
            erw = a / Math.PI * (Math.PI * L_HIMMEL);
            A.pruefe(Math.abs(wand[0] / erw - 1) <= 0.03, "Nordwand im Eigenschatten, Kanal G: nur Himmel und Boden",
                    f("%.1f gegen %.1f cd/m² (%.3f; %d Bildpunkte)", wand[0], erw, wand[0] / erw, (int) wand[1]), "1 ± 0,03 [G]");

            // ---------------------------------------------------------------- 4 Schatten (Prüfstein 5)
            A.teil("4 Schatten (Prüfstein 5)");
            // Senkrecht von oben über der Nordseite: Die Kante des Schattens der Traufe liegt h / tan 30° nördlich der Nordwand;
            // der First wirft ihn weiter: (First − Boden) / tan 30° nördlich der Firstlinie.
            z.blick = new Blick(E0 + hx, N0 - hz + 12, y0 + 70, 0, -90, 30).groesse(B, H);
            bild(g, t, z);
            px = hdr.lies();
            double fi = bt.gebaeude.get(1).first - y0;
            double kanteSoll = hz - fi / Math.tan(Math.toRadians(30));                // z der Kante (Süd positiv; Nord ist kleiner)
            double kante = kante(px, z.blick, hx, hz - 4.6, hz - 30);
            double texel = 2 * t.kaskaden[0] / t.n;
            A.pruefe(!Double.isNaN(kante) && Math.abs(kante - kanteSoll) <= Math.max(texel, 0.05) + 0.06, "Kante des Schattens des Firsts, Sonne 30° aus Süden",
                    f("%.2f m nördlich der Firstlinie, Geometrie %.2f m (Abweichung %.2f m; Texel %.2f m, Bildpunkt %.2f m)", hz - kante, hz - kanteSoll, kante - kanteSoll, texel,
                            2 * 70 * Math.tan(Math.toRadians(15)) / H), "± 1 Texel + 1 Bildpunkt");
            // Ohne Schatten: dieselbe Stelle hell
            double[] sonnig = mittelBoden(px, z.blick, hx + 20, hz - 15, 2), schattig = mittelBoden(px, z.blick, hx, hz - 8, 1);
            double erwS = 0.09 / Math.PI * (E_SONNE * 0.5 + Math.PI * L_HIMMEL), erwD = 0.09 / Math.PI * (Math.PI * L_HIMMEL);
            A.pruefe(Math.abs(sonnig[0] / erwS - 1) <= 0.02 && Math.abs(schattig[0] / erwD - 1) <= 0.05, "Boden in der Sonne und im Schatten, Kanal G",
                    f("Sonne %.1f gegen %.1f, Schatten %.1f gegen %.1f cd/m²", sonnig[0], erwS, schattig[0], erwD), "± 2 % / ± 5 % [G]");
            bildSpeichern(px, "P12a_Haus_oben.png", 1.0 / 900);

            // ---------------------------------------------------------------- 5 Fensterlicht
            A.teil("5 Fensterlicht");
            z.blick = new Blick(E0 + hx - 6, N0 - hz - 22, augeH, 15, 6, 50).groesse(B, H);
            z.zeit = Instant.parse("2026-11-19T17:30:00Z");                        // 18:30 Ortszeit
            ers.himmel(0.02);
            double[] lm = ers.sonne(-12, 240, E_SONNE, 1, 1.7);
            ers.ablegen(z, lm, -12);
            bild(g, t, z);
            float[] nacht = hdr.lies();
            int hell = 0;
            for (int i = 0; i < B * H; i++) if (nacht[4 * i] > 5) hell++;
            A.pruefe(hell > 50 && t.fensterAnteil > 0.5, "Abends (18:30, Sonne −12°): Fenster mit Licht",
                    f("Anteil %.2f, %d Bildpunkte über 5 cd/m²", t.fensterAnteil, hell), "Anteil 0,65 [G], Bildpunkte > 50");
            bildSpeichern(nacht, "P12a_Haus_abend.png", 1.0 / 30);
            z.zeit = Instant.parse("2026-08-15T10:00:00Z");
            ers.himmel(L_HIMMEL);
            l = ers.sonne(30, 180, E_SONNE, 1, 1.7);
            ers.ablegen(z, l, 30);
            bild(g, t, z);
            float[] tag = hdr.lies();
            bildSpeichern(tag, "P12a_Haus_tag.png", 1.0 / 900);
            A.pruefe(t.fensterAnteil == 0, "Am Tag kein Fensterlicht", f("Anteil %.2f", t.fensterAnteil), "0");
            // Abendsonne von Westen: lange Schatten, Giebel im Licht
            l = ers.sonne(8, 255, E_SONNE * 0.6, 1, 1.7);
            ers.ablegen(z, l, 8);
            z.blick = new Blick(E0 + hx - 30, N0 - hz - 30, y0 + 8, 45, -8, 50).groesse(B, H);
            bild(g, t, z);
            bildSpeichern(hdr.lies(), "P12a_Haus_abendsonne.png", 1.0 / 700);
            // Aus
            t.an = false;
            bild(g, t, z);
            float[] aus = hdr.lies();
            boolean leer = true;
            for (float v : aus) if (v != 0) { leer = false; break; }
            A.pruefe(leer && !z.geteilt.containsKey(Hausbild.SCHATTEN), "Schalter aus: nichts im Bild, kein Schatten abgelegt", leer ? "leer" : "nicht leer", "leer");
        }
    }

    // ------------------------------------------------------------------ Messen

    /** Mittel des Kanals G über die Bildpunkte, deren Lage (aus dem Strahl, auf der Ebene z = zw) in der Wand liegt. {Mittel, Zahl}. */
    static double[] mittelWand(float[] px, Blick b, double xm, double zw, double ya, double yb, double halb) {
        double sum = 0;
        int n = 0;
        double[] auge = {b.ost - E0, b.hoehe, N0 - b.nord};
        for (int y = 0; y < H; y++)
            for (int x = 0; x < B; x++) {
                double[] d = b.strahl(x + 0.5, y + 0.5);
                if (Math.abs(d[2]) < 1e-6) continue;
                double t = (zw - auge[2]) / d[2];
                if (t <= 0) continue;
                double wx = auge[0] + t * d[0], wy = auge[1] + t * d[1];
                if (Math.abs(wx - xm) > halb || Math.abs(wx - xm) < 0.7 || wy < ya || wy > yb) continue;   // ohne die Tür in der Mitte
                // zwischen zwei Fenstern? Die Fenster beginnen erst bei 0,85 m – der Streifen liegt darunter
                sum += px[4 * (y * B + x) + 1];
                n++;
            }
        return new double[] {n > 0 ? sum / n : Double.NaN, n};
    }

    /** Mittel G des Bodens um (x, z) im Umkreis r. */
    static double[] mittelBoden(float[] px, Blick b, double xm, double zm, double r) {
        double sum = 0;
        int n = 0;
        double[] auge = {b.ost - E0, b.hoehe, N0 - b.nord};
        double yb = 4.0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < B; x++) {
                double[] d = b.strahl(x + 0.5, y + 0.5);
                double t = (yb - auge[1]) / d[1];
                double wx = auge[0] + t * d[0], wz = auge[2] + t * d[2];
                if (Math.hypot(wx - xm, wz - zm) > r) continue;
                sum += px[4 * (y * B + x) + 1];
                n++;
            }
        return new double[] {n > 0 ? sum / n : Double.NaN, n};
    }

    /** Kante des Schattens längs der Linie x = xm von z = za (am Haus) nach z = zb (Norden): z, wo G die Mitte zwischen Schatten und Sonne kreuzt. */
    static double kante(float[] px, Blick b, double xm, double za, double zb) {
        double[] auge = {b.ost - E0, b.hoehe, N0 - b.nord};
        int schritte = 2000;
        double[] g = new double[schritte + 1], zz = new double[schritte + 1];
        for (int i = 0; i <= schritte; i++) {
            double z = za + (zb - za) * i / schritte;
            double[] rel = {xm - auge[0], 4.0 - auge[1], z - auge[2]};
            double[] p = b.bildpunkt(rel);
            zz[i] = z;
            if (p == null || p[0] < 0 || p[1] < 0 || p[0] >= B || p[1] >= H) { g[i] = Double.NaN; continue; }
            g[i] = px[4 * ((int) p[1] * B + (int) p[0]) + 1];
        }
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : g) if (!Double.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        double mitte = 0.5 * (lo + hi);
        for (int i = 1; i <= schritte; i++)
            if (!Double.isNaN(g[i - 1]) && !Double.isNaN(g[i]) && g[i - 1] < mitte && g[i] >= mitte)
                return zz[i - 1] + (zz[i] - zz[i - 1]) * (mitte - g[i - 1]) / (g[i] - g[i - 1]);
        return Double.NaN;
    }

    /** HDR → PNG mit einfacher Kurve (Reinhard, sRGB) für die Sichtprüfung. */
    static void bildSpeichern(float[] px, String name, double k) throws Exception {
        BufferedImage im = new BufferedImage(B, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < B; x++) {
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    double v = px[4 * (y * B + x) + c] * k;
                    v = v / (1 + v);
                    v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
                    rgb = (rgb << 8) | (int) Math.round(255 * Math.max(0, Math.min(1, v)));
                }
                im.setRGB(x, y, rgb);
            }
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        ImageIO.write(im, "png", d.resolve(name).toFile());
        A.info("Bild", d.resolve(name).toString());
    }
}
