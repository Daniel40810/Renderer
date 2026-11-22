package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Gehoelz;
import com.dan.render.bau.Hausbild;
import com.dan.render.bewuchs.Bewuchsbild;
import com.dan.render.terrain.Gelaendebild;

import javax.imageio.ImageIO;
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
 * Der Bewuchs im Bild (P12c, Werkbuch HalligHooge Teil A, 12.13, Datei 75; Prüfsteine 17, 16 und 9) mit den echten
 * Daten des Gebiets (Gelände, Häuser, Gehölze) und einem Ersatzhimmel (gleichmäßig hell, Sonne nach Vorgabe).
 * <ul>
 * <li><b>Prüfstein 17 (Bild):</b> senkrecht von oben über die dichtesten Gehölze, im Sommer (Tag 200) ohne Wind: Anteil
 * der Kronen (Polygone aus {@code gehoelz.bin}), den Gehölze im Bild bedecken (Tiefe mit gegen ohne Bewuchs), ≥ 70 %
 * [G]; Höhe der höchsten Fläche je Gehölz gegen die gemessene (Median), ± 1 m.</li>
 * <li><b>Prüfstein 16:</b> am Auge über der Fenne, Sonne unter dem Horizont: mittlere Leuchtdichte mit Halmen gegen ohne
 * im Ring 12 … 20 m (vor dem Übergang) – Halme haben die Albedo des Bodens.</li>
 * <li><b>Prüfstein 9 (Teil):</b> Bewuchs aus ist bitgleich zum Bild ohne den Teil.</li>
 * <li>Netze: Dreiecke je Stufe; Laub im Sommer und im Winter.</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.BewuchsCheck [--gebiet D:\Daten\HalligHooge\gebiet]
 * </pre>
 */
public final class BewuchsCheck {
    private static final Attest A = new Attest("com.dan.render.bewuchs", Bewuchsbild.VERSION);
    static final int B = 640, H = 400;
    static final double L_HIMMEL = 4000, E_SONNE = 100_000;

    public static void main(String[] args) throws Exception {
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet";
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--gebiet")) gebiet = args[i + 1];
        System.out.println("BewuchsCheck – com.dan.render.bewuchs " + Bewuchsbild.VERSION + " (Werkbuch HalligHooge Teil A, P12c)");
        File gb = new File(gebiet);
        A.info("Gebiet", gb.getPath());
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("BewuchsCheck", 3L << 30);
            try (Gelaendebild t = Gelaendebild.aus(g, konto, gb); Hausbild hb = Hausbild.aus(g, konto, gb); Bewuchsbild bw = Bewuchsbild.aus(g, konto, gb);
                 Ersatz ers = new Ersatz(g, konto); Bild hdr = new Bild(g, konto, "HDR", B, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe", B, H, Bild.D32F)) {
                A.pruefe(bw != null, "Gehölze im Gebiet (bau/gehoelz.bin)", bw == null ? "fehlen" : bw.gehoelz.pflanzen.size() + " Gehölze", "vorhanden");
                if (bw != null) {
                    t.flugZeit = 0;
                    if (hb != null) hb.kruemmung = t.hoehe.curvatureRadius;
                    bw.kruemmung = t.hoehe.curvatureRadius;
                    bw.windZeit = 0;
                    bw.halme(t.gelaende, t.gestalt(), t.material.boden);
                    Bildziel z = new Bildziel(hdr, tiefe);
                    z.vorbelichtung = 1;
                    netze(bw);
                    kronen(g, t, hb, bw, ers, z);
                    halme(g, t, hb, bw, ers, z);
                    aus(g, t, hb, bw, ers, z);
                }
            }
            A.teil("5 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht, g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P12c_Bewuchs.md"), "P12c – BewuchsCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Netze

    static void netze(Bewuchsbild bw) {
        A.teil("1 Netze und Laub");
        bw.baue(200);
        int[] sommer = {bw.dreiecke(0), bw.dreiecke(1), bw.dreiecke(2)};
        bw.baue(355);
        int[] winter = {bw.dreiecke(0), bw.dreiecke(1), bw.dreiecke(2)};
        A.info("Dreiecke je Stufe (alle Arten, zwei Varianten)", f("Tag 200: nah %d, mittel %d, Silhouette %d; Tag 355: nah %d, mittel %d, Silhouette %d",
                sommer[0], sommer[1], sommer[2], winter[0], winter[1], winter[2]));
        A.pruefe(winter[0] < 0.6 * sommer[0] && winter[0] > 0, "Laub nach dem Tag: im Winter kahl (nur Rinde), im Sommer belaubt",
                f("nahe Stufe im Winter %.0f %% der Dreiecke des Sommers", 100.0 * winter[0] / Math.max(1, sommer[0])), "< 60 %, > 0");
    }

    // ------------------------------------------------------------------ 2 Kronen (Prüfstein 17)

    static void kronen(Geraet g, Gelaendebild t, Hausbild hb, Bewuchsbild bw, Ersatz ers, Bildziel z) throws Exception {
        A.teil("2 Kronen im Bild (Prüfstein 17)");
        Gehoelz ge = bw.gehoelz;
        // dichteste Stelle: Zelle zu 60 m mit der größten Kronenfläche
        java.util.Map<Long, Double> fl = new java.util.HashMap<>();
        double best = 0, bx = 0, bz = 0;
        for (Gehoelz.Pflanze p : ge.pflanzen) {
            long k = ((long) Math.floor(p.x / 60) << 32) ^ (Math.floor(p.z / 60) < 0 ? 0 : (long) Math.floor(p.z / 60));
            double v = fl.merge(k, (double) p.flaeche, Double::sum);
            if (v > best) { best = v; bx = (Math.floor(p.x / 60) + 0.5) * 60; bz = (Math.floor(p.z / 60) + 0.5) * 60; }
        }
        double ost = ge.e0 + bx, nord = ge.n0 - bz;
        float boden = t.gelaende.hoehe(ost, nord, 1);
        Blick b = new Blick(ost, nord, (Float.isNaN(boden) ? 2 : boden) + 150, 0, -89.9, 30).groesse(B, H);
        ers.sonne(60, 180, E_SONNE, 1, b.hoehe);
        bw.windU = bw.windV = 0;
        bw.halmeAn = false;
        bw.baue(200);
        z.zeit = Instant.parse("2026-07-19T11:00:00Z");
        bw.an = true;
        float[] mit = zeichne(g, t, hb, bw, ers, z, b);
        float[] tMit = z.tiefe.lies();
        bw.an = false;
        zeichne(g, t, hb, bw, ers, z, b);
        float[] tOhne = z.tiefe.lies();
        bw.an = true;
        bw.halmeAn = true;
        // je Bildpunkt: Gehölz, wo die Tiefe näher ist als ohne; Lage am Boden aus der Tiefe ohne, Höhe aus der Tiefe mit
        double[] v = b.strahl(B / 2.0, H / 2.0);
        long inKrone = 0, bedeckt = 0, baumAussen = 0, baumAlle = 0;
        List<Double> dh = new ArrayList<>();
        double[] top = new double[ge.pflanzen.size()];
        Arrays.fill(top, Double.NEGATIVE_INFINITY);
        int[] innen = new int[ge.pflanzen.size()];
        List<Integer> nah = new ArrayList<>();
        for (int i = 0; i < ge.pflanzen.size(); i++)
            if (Math.hypot(ge.pflanzen.get(i).x - bx, ge.pflanzen.get(i).z - bz) < 80) nah.add(i);
        for (int py = 0; py < H; py++)
            for (int px = 0; px < B; px++) {
                int k = py * B + px;
                if (!(tOhne[k] > 0)) continue;
                double[] d = b.strahl(px + 0.5, py + 0.5);
                double cs = d[0] * v[0] + d[1] * v[1] + d[2] * v[2];
                double sO = b.nah / tOhne[k] / cs;
                double x = b.ost - ge.e0 + d[0] * sO, zz = ge.n0 - b.nord + d[2] * sO;
                boolean baum = tMit[k] > tOhne[k] * 1.0005;
                double yMit = b.hoehe + d[1] * (b.nah / tMit[k] / cs);
                if (baum) baumAlle++;
                boolean drin = false;
                // in welcher Krone?
                for (int i : nah) {
                    Gehoelz.Pflanze p = ge.pflanzen.get(i);
                    double dx = x - p.x, dzN = -(zz - p.z);                          // nach Ost, nach Nord
                    double r = Math.hypot(dx, dzN);
                    if (r > p.kroneMax() || r > p.kroneIn(Math.atan2(dzN, dx))) continue;
                    inKrone++;
                    innen[i]++;
                    drin = true;
                    if (baum) {
                        bedeckt++;
                        top[i] = Math.max(top[i], yMit - p.boden);
                    }
                    break;
                }
                if (baum && !drin) baumAussen++;
            }
        int gezaehlt = 0;
        for (int i = 0; i < ge.pflanzen.size(); i++)
            if (innen[i] >= 20 && top[i] > Double.NEGATIVE_INFINITY) {
                dh.add(top[i] - ge.pflanzen.get(i).hoehe);
                gezaehlt++;
            }
        dh.sort(null);
        double med = dh.isEmpty() ? Double.NaN : dh.get(dh.size() / 2);
        A.info("Gehölz außerhalb der gemessenen Kronen", f("%.1f %% der Bildpunkte mit Gehölz (Lage am Boden unter der Fläche, also auch Schräge der Sicht)",
                100.0 * baumAussen / Math.max(1, baumAlle)));
        A.info("Blick", f("150 m über O %.0f N %.0f (dichteste Zelle zu 60 m, %.0f m² Krone), Sommer (Tag 200), ohne Wind", ost, nord, best));
        // Grenze: Die Kronen aus gehoelz.bin enthalten rund 17 % Fläche ohne Pflanze (GehoelzCheck), und Laub hat Lücken;
        // mehr als rund 83 % sind nicht zu erwarten. 70 % [G] (Datei 78)
        A.pruefe(inKrone > 1000 && bedeckt >= 0.70 * inKrone, "Prüfstein 17 (Bild): Fläche der gemessenen Kronen, die Gehölze im Bild bedecken",
                f("%.1f %% von %d Bildpunkten", 100.0 * bedeckt / Math.max(1, inKrone), inKrone), "≥ 70 % [G]");
        A.pruefe(gezaehlt >= 5 && Math.abs(med) <= 1.0, "Prüfstein 17 (Bild): Höhe der höchsten Fläche je Gehölz gegen die gemessene (Median)",
                f("%+.2f m über %d Gehölze (10 %%: %+.2f, 90 %%: %+.2f)", med, gezaehlt, dh.isEmpty() ? Double.NaN : dh.get(dh.size() / 10),
                        dh.isEmpty() ? Double.NaN : dh.get((9 * dh.size()) / 10)), "± 1 m");
        png(mit, "P12c_Kronen_oben.png", 1.0 / 30000);
    }

    // ------------------------------------------------------------------ 3 Halme (Prüfstein 16)

    static void halme(Geraet g, Gelaendebild t, Hausbild hb, Bewuchsbild bw, Ersatz ers, Bildziel z) throws Exception {
        A.teil("3 Halme am Auge (Prüfstein 16)");
        // am Auge über der Fenne nahe der Hanswarft (oder dort, wo die meisten Gehölze stehen)
        double ost = 470800, nord = 6046470;
        float boden = t.gelaende.hoehe(ost, nord, 1);
        Blick b = new Blick(ost, nord, (Float.isNaN(boden) ? 2 : boden) + 1.7, 45, -12, 60).groesse(B, H);
        ers.sonne(-10, 180, 0, 1, b.hoehe);
        z.zeit = Instant.parse("2026-10-30T11:00:00Z");
        bw.windU = bw.windV = 0;
        bw.halmeAn = true;
        float[] mit = zeichne(g, t, hb, bw, ers, z, b);
        float[] tMit = z.tiefe.lies();
        bw.halmeAn = false;
        float[] ohne = zeichne(g, t, hb, bw, ers, z, b);
        float[] tOhne = z.tiefe.lies();
        bw.halmeAn = true;
        double[] v = b.strahl(B / 2.0, H / 2.0);
        double sm = 0, so = 0;
        long zahl = 0, halmpunkte = 0;
        for (int py = 0; py < H; py++)
            for (int px = 0; px < B; px++) {
                int k = py * B + px;
                if (!(tOhne[k] > 0)) continue;
                double[] d = b.strahl(px + 0.5, py + 0.5);
                double s = b.nah / tOhne[k] / (d[0] * v[0] + d[1] * v[1] + d[2] * v[2]);
                double r = s * Math.hypot(d[0], d[2]);
                if (r < 12 || r > 20) continue;
                double ym = 0.2126 * mit[4 * k] + 0.7152 * mit[4 * k + 1] + 0.0722 * mit[4 * k + 2];
                double yo = 0.2126 * ohne[4 * k] + 0.7152 * ohne[4 * k + 1] + 0.0722 * ohne[4 * k + 2];
                sm += ym;
                so += yo;
                zahl++;
                if (tMit[k] != tOhne[k]) halmpunkte++;
            }
        double q = so > 0 ? sm / so : Double.NaN;
        A.info("Halme", f("%d Büschel um das Auge; im Ring 12 … 20 m sind %.0f %% der Bildpunkte Halme", bw.bueschel(), 100.0 * halmpunkte / Math.max(1, zahl)));
        A.pruefe(zahl > 1000 && halmpunkte > 0.1 * zahl && Math.abs(q - 1) <= 0.05, "Prüfstein 16: mittlere Leuchtdichte mit Halmen gegen ohne im Ring 12 … 20 m (nur Himmelslicht)",
                f("%.4f über %d Bildpunkte", q, zahl), "1 ± 0,05 [G]");
        ers.sonne(25, 190, E_SONNE, 1, b.hoehe);
        bw.windU = 7;
        bw.windV = 3;
        float[] tag = zeichne(g, t, hb, bw, ers, z, b);
        png(tag, "P12c_Halme.png", 1.0 / 30000);
    }

    // ------------------------------------------------------------------ 4 aus (Prüfstein 9)

    static void aus(Geraet g, Gelaendebild t, Hausbild hb, Bewuchsbild bw, Ersatz ers, Bildziel z) throws Exception {
        A.teil("4 Bewuchs aus (Prüfstein 9)");
        double ost = 471030, nord = 6046470;
        float boden = t.gelaende.hoehe(ost, nord, 1);
        Blick b = new Blick(ost, nord, (Float.isNaN(boden) ? 2 : boden) + 1.7, 320, 4, 50).groesse(B, H);
        ers.sonne(25, 190, E_SONNE, 1, b.hoehe);
        z.zeit = Instant.parse("2026-10-30T11:00:00Z");
        bw.an = false;
        float[] aus = zeichne(g, t, hb, bw, ers, z, b);
        float[] ohneTeil = zeichne(g, t, hb, null, ers, z, b);
        bw.an = true;
        float[] mit = zeichne(g, t, hb, bw, ers, z, b);
        long anders = 0, geaendert = 0;
        for (int k = 0; k < aus.length; k++) {
            if (Float.floatToRawIntBits(aus[k]) != Float.floatToRawIntBits(ohneTeil[k])) anders++;
            if (Float.floatToRawIntBits(mit[k]) != Float.floatToRawIntBits(ohneTeil[k])) geaendert++;
        }
        A.pruefe(anders == 0 && geaendert > 0, "Prüfstein 9 (Teil): Bewuchs aus gegen das Bild ohne den Teil",
                f("%d Werte anders (mit Bewuchs: %d)", anders, geaendert), "0, und der Bewuchs ändert etwas");
        png(mit, "P12c_Gehoelz.png", 1.0 / 30000);
    }

    // ------------------------------------------------------------------ Hilfen

    static float[] zeichne(Geraet g, Gelaendebild t, Hausbild hb, Bewuchsbild bw, Ersatz ers, Bildziel z, Blick b) {
        z.blick = b;
        ers.ablegen(z);
        double[] xz = t.raum(b);
        t.ziel(xz[0], xz[1]);
        t.ladeJetzt();
        List<Bildteil> teile = new ArrayList<>();
        if (hb != null) teile.add(hb);
        if (bw != null) teile.add(bw);
        teile.add(t);
        Ablauf a = new Ablauf(g, g.grafik);
        a.zeichne("löschen", new Bild[] {z.hdr}, new float[][] {{0, 0, 0, 0}}, z.tiefe, 0f, null, c -> { });
        for (Bildteil p : teile) p.vorbereiten(a, z);
        for (Bildteil p : teile) p.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
        return z.hdr.lies();
    }

    static void png(float[] px, String name, double k) throws Exception {
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

    /** Ersatz für den Himmel: Bilder und Werte unter den Namen von render.air (wie in den Prüfungen von render.bau). */
    static final class Ersatz implements AutoCloseable {
        final Bild durch, ansicht;
        final Puffer werte;
        final float[] w = new float[60];
        double[] l = {0, 1, 0};
        double hGrad = 45;

        Ersatz(Geraet g, Konto k) {
            durch = new Bild(g, k, "Ersatz Durchlässigkeit", 256, 64, Bild.RGBA32F);
            durch.schreibe(new float[256 * 64 * 4]);
            ansicht = new Bild(g, k, "Ersatz Ansicht", 256, 128, Bild.RGBA32F);
            float[] a = new float[256 * 128 * 4];
            for (int i = 0; i < 256 * 128; i++) {
                a[4 * i] = a[4 * i + 1] = a[4 * i + 2] = (float) L_HIMMEL;
                a[4 * i + 3] = 1;
            }
            ansicht.schreibe(a);
            werte = Puffer.speicher(g, k, "Ersatz Werte", 60 * 4);
        }

        void sonne(double hGrad, double azGrad, double e, double vb, double auge) {
            double h = Math.toRadians(hGrad), az = Math.toRadians(azGrad);
            Arrays.fill(w, 0);
            w[0] = 1e-5f; w[1] = 1e-5f; w[2] = 0.08f; w[3] = 0.85f;
            l = new double[] {Math.cos(h) * Math.sin(az), Math.sin(h), -Math.cos(h) * Math.cos(az)};
            w[4] = (float) l[0]; w[5] = (float) l[1]; w[6] = (float) l[2]; w[7] = (float) Math.max(1, auge);
            w[8] = w[9] = w[10] = (float) e; w[11] = (float) vb;
            w[12] = 0; w[13] = -1; w[14] = 0;
            w[52] = w[53] = w[54] = w[56] = w[57] = w[58] = 1;
            werte.schreibe(w);
            this.hGrad = hGrad;
        }

        void ablegen(Bildziel z) {
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
}
