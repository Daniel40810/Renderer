package com.dan.lauf.demo;

import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Tonereignisse;
import com.dan.core.szene.Wasseroptik;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Linsenglas;
import com.dan.lauf.Schwimmerblick;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Welle;
import com.dan.render.water.Unterwasser;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von S8c und S8d (Seegang Werkbuch, Datei 104): Ton und Schwimmerblick.
 * <ol>
 * <li><b>Ohne Karte:</b> der Kopf schwimmt mit der Mitte im Pegel und folgt einer langen Welle; der Schwimmzug hebt das Auge
 * von −5 bis +10 cm (SF160).</li>
 * <li><b>Karte in der Prüfszene</b> (Prüfstein 39): Unterwasser auf der Karte – halber Winkel des Snellschen Fensters,
 * Durchlässigkeit gegen exp(−c s), die Wasserlinie auf der Linse gegen die Ebene der Fläche am Auge in double (± 1 Bildpunkt).
 * Ohne Himmel (die Probe liest nur Weg und Fenster).</li>
 * <li><b>In der Anwendung:</b> Prüfstein 38 (Teil 1: je Brecher im Umkreis genau ein Ereignis, Brandung des Sturms an den
 * Kanten des Gebiets); der Schwimmerblick am ruhigen Tag mit Bildern und Kosten (Prüfstein 41: unter Wasser ≤ 1,5 ms).</li>
 * </ol>
 * Laufzeit und Pegel der Stimmen (Prüfstein 38, Teil 2) prüft {@code com.dan.steuerhaus.demo.TonCheck}.
 * <pre>  SeegangS8cdCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil n]</pre>
 */
public final class SeegangS8cdCheck {
    static final Attest A = new Attest("Seegang S8c/S8d: Ton und Schwimmerblick", Schwimmerblick.VERSION);

    public static void main(String[] args) {
        File szenen = null;
        int breite = 1280, hoehe = 720;
        java.util.Set<String> nur = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[++i]);
                case "--teil" -> nur.add(args[++i]);
                default -> { }
            }
        System.out.println("SeegangS8cdCheck – S8c, S8d (Seegang Werkbuch 104)");
        long t0 = System.nanoTime();
        try {
            if (nur.isEmpty() || nur.contains("1")) SchwimmerCheck.zeit("1", SeegangS8cdCheck::ohneKarte);
            if (nur.isEmpty() || nur.contains("2")) SchwimmerCheck.zeit("2", SeegangS8cdCheck::pruefszene);
            if (nur.isEmpty() || nur.contains("3")) {
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                SchwimmerCheck.zeit("3", () -> anwendung(sz, breite, hoehe));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        String datei = nur.isEmpty() ? "S8cd_Seegang.md" : "S8cd_Seegang_teil" + String.join("", new java.util.TreeSet<>(nur)) + ".md";
        Path pa = A.schreibe(Path.of("docs", "status", datei), "S8c/S8d – Ton und Schwimmerblick (Schwimmerblick " + Schwimmerblick.VERSION
                + ", Unterwasser " + Unterwasser.VERSION + ", Tonereignisse " + Tonereignisse.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1

    static void ohneKarte() {
        A.teil("1 Ohne Karte: der Kopf im Wasser, der Schwimmzug (SF160)");
        // in ruhigem Wasser: Mitte im Pegel
        Welle still = Welle.regelmaessig(0, 10, 0, 20);
        Koerper k = Vorgaben.kopf(0.05);
        k.einschwimmen(still, 0, 0, 0, 0, 0, 0);
        for (int i = 0; i < 600; i++) k.schritt(still, 1.0 / 60);
        double mitte = k.punktWelt(0, 0, 0.11)[2];
        A.pruefe(Math.abs(mitte) <= 0.01, "Kopf in ruhigem Wasser: Mitte im Pegel", f("%+.1f mm nach 10 s", 1000 * mitte), "± 10 mm [G]");
        // in einer Dünung (a 0,4 m, T 6 s): Hub der Mitte gegen η
        Welle w = Welle.regelmaessig(0.4, 6, 180, 20);
        w.anlauf = 6;
        k = Vorgaben.kopf(0.05);
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        double t = 0, dMax = 0, hMin = 1e9, hMax = -1e9;
        double[] out = new double[6];
        for (int i = 0; i < 60 * 40; i++) {
            k.schritt(w, 1.0 / 60);
            t += 1.0 / 60;
            if (t < 20) continue;
            double[] m = k.punktWelt(0, 0, 0.11);
            w.welle(m[0], m[1], 0, t, out);
            dMax = Math.max(dMax, Math.abs(m[2] - out[0]));
            hMin = Math.min(hMin, m[2]);
            hMax = Math.max(hMax, m[2]);
        }
        A.pruefe(dMax <= 0.05, "Kopf in der Dünung (a 0,4 m, T 6 s): Mitte gegen η am Ort", f("Hub %.2f m (Welle 0,80 m), größte Abweichung %.1f cm", hMax - hMin, 100 * dMax), "± 5 cm [G]");
        // Schwimmzug
        double vMin = 1e9, vMax = -1e9;
        Schwimmerblick sb = new Schwimmerblick(null, 0);
        for (int i = 0; i < 1000; i++) {
            double v = sb.versatz(i * Schwimmerblick.ZUG / 1000);
            vMin = Math.min(vMin, v);
            vMax = Math.max(vMax, v);
        }
        A.pruefe(Math.abs(vMin + 0.05) < 1e-3 && Math.abs(vMax - 0.10) < 1e-3, "Schwimmzug: Auge gegen die Mitte des Kopfs", f("%+.3f … %+.3f m in %.1f s", vMin, vMax, Schwimmerblick.ZUG), "−0,05 … +0,10 m (SF160)");
    }

    // ------------------------------------------------------------------ 2

    static void pruefszene() {
        A.teil("2 Karte in der Prüfszene: unter Wasser (Prüfstein 39)");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("Prüfung S8d", 64L << 20);
            Wasseroptik op = new Wasseroptik();
            try (Unterwasser u = new Unterwasser(g, konto, op, null);
                 Puffer hw = Puffer.speicher(g, konto, "Prüfung, Himmel/Werte", 16 * 16);
                 Bild durch = new Bild(g, konto, "Prüfung, Durchlässigkeit", 256, 64, Bild.RGBA16F);
                 Bild ansicht = new Bild(g, konto, "Prüfung, Ansicht", 256, 128, Bild.RGBA16F)) {
                hw.schreibe(new float[64]);
                durch.schreibe(new float[4 * 256 * 64]);
                ansicht.schreibe(new float[4 * 256 * 128]);
                u.kraeusel = 0;
                u.streifen = 0;
                snell(u, op, hw, durch, ansicht);
                truebung(u, op, hw, durch, ansicht);
                linie(u, hw, durch, ansicht);
            }
        }
    }

    /** Halber Winkel des Snellschen Fensters: Blick nach oben, glatte Fläche, Übergang frei → Totalreflexion längs der Mitte. */
    static void snell(Unterwasser u, Wasseroptik op, Puffer hw, Bild durch, Bild ansicht) {
        double[] winkel = new double[3];
        String[] was = new String[3];
        int k = 0;
        for (double richtung : new double[] {0, 120, 250}) {
            Blick b = new Blick(0, 0, 0, richtung, 45, 80);
            u.setze(1, 0, -1, new double[] {0, 1, 0}, 10, 0, Linsenglas.BREITE, Linsenglas.HOEHE);
            int m = 4001;
            float[] uv = new float[2 * m];
            for (int i = 0; i < m; i++) { uv[2 * i] = 0.5f; uv[2 * i + 1] = (float) i / (m - 1); }
            float[] r = u.probe(b, 1280, 720, uv, 2.0, hw, durch, ansicht);
            double grenze = Double.NaN;
            int wechsel = 0;
            for (int i = 1; i < m; i++) {
                float f0 = r[8 * (i - 1) + 1], f1 = r[8 * i + 1];
                if (f0 != f1) {
                    wechsel++;
                    double z0 = Math.toDegrees(Math.acos(r[8 * (i - 1) + 4])), z1 = Math.toDegrees(Math.acos(r[8 * i + 4]));
                    grenze = 0.5 * (z0 + z1);
                }
            }
            winkel[k] = grenze;
            was[k] = f("%.0f°: %.2f° (%d Wechsel)", richtung, grenze, wechsel);
            k++;
        }
        double soll = Math.toDegrees(Math.asin(1 / op.n));
        double dMax = 0;
        for (double w : winkel) dMax = Math.max(dMax, Double.isNaN(w) ? 99 : Math.abs(w - 48.6));
        A.pruefe(dMax <= 0.5, "Snellsches Fenster: halber Winkel im Bild (Prüfstein 39)", f("%s; asin(1/n) bei n = %.3f: %.2f°", String.join(", ", was), op.n, soll), "48,6° ± 0,5°");
    }

    /** Durchlässigkeit des Wegs gegen exp(−c s) je Kanal im Mittel, für Strahlen nach oben aus 0,3, 1 und 3 m Tiefe. */
    static void truebung(Unterwasser u, Wasseroptik op, Puffer hw, Bild durch, Bild ansicht) {
        double dMax = 0;
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (double ssc : new double[] {5, 30, 100}) {
            op.schwebstoff = ssc;
            double[][] o = u.optik();
            for (double tiefe : new double[] {0.3, 1, 3}) {
                Blick b = new Blick(0, 0, 0, 30, 60, 50);
                u.setze(1, 0, -1, new double[] {0, 1, 0}, 10, 0, Linsenglas.BREITE, Linsenglas.HOEHE);
                int m = 64;
                float[] uv = new float[2 * m];
                for (int i = 0; i < m; i++) { uv[2 * i] = (float) ((i % 8 + 0.5) / 8); uv[2 * i + 1] = (float) ((i / 8 + 0.5) / 8); }
                float[] r = u.probe(b, 1280, 720, uv, tiefe, hw, durch, ansicht);
                for (int i = 0; i < m; i++) {
                    double dy = r[8 * i + 4];
                    if (dy <= 1e-3) continue;
                    double s = tiefe / dy, soll = 0;
                    for (int c = 0; c < 3; c++) soll += Math.exp(-o[0][c] * s) / 3;
                    if (soll < 1e-4) continue;
                    dMax = Math.max(dMax, Math.abs(r[8 * i + 2] - soll) / soll);
                    n++;
                }
            }
            sb.append(f("%s%.0f g/m³: c %.2f/%.2f/%.2f, K_d %.2f/%.2f/%.2f 1/m", sb.length() > 0 ? "; " : "", ssc, o[0][0], o[0][1], o[0][2], o[1][0], o[1][1], o[1][2]));
        }
        op.schwebstoff = 30;
        A.info("Optik unter Wasser (R G B)", sb.toString());
        A.pruefe(n > 0 && dMax <= 0.05, "Trübung: Durchlässigkeit gegen exp(−c s) (Prüfstein 39)", f("%d Strahlen, drei Schwebstoffe, drei Tiefen: größte Abweichung %.2e relativ", n, dMax), "± 5 %");
    }

    /**
     * Die Wasserlinie auf der Linse: das Auge eines Schwimmerblicks (Höhe, η, Gefälle, Blick) wie in der Anwendung; die Linie
     * im Bild der Karte (Wechsel der Tiefe von ≤ 0 nach > 0 je Spalte) gegen den Schnitt der Ebene der Fläche am Auge mit der
     * Scheibe in double.
     */
    static void linie(Unterwasser u, Puffer hw, Bild durch, Bild ansicht) {
        int bw = 1280, bh = 720;
        double dMax = 0;
        int spalten = 0, bandFehlt = 0;
        java.util.Random rnd = new java.util.Random(39);
        for (int fall = 0; fall < 6; fall++) {
            double eta = 1.2, auge = eta + (rnd.nextDouble() - 0.5) * 0.03, gO = 0.15 * (rnd.nextDouble() - 0.5), gN = 0.15 * (rnd.nextDouble() - 0.5);
            Blick b = new Blick(0, 0, auge, 360 * rnd.nextDouble(), 10 * (rnd.nextDouble() - 0.5), 70);
            double[][] bas = b.basis();
            double[] r = {bas[0][0], -bas[0][2], bas[0][1]}, o = {bas[1][0], -bas[1][2], bas[1][1]};
            double tiefe = eta - auge, jr = gO * r[0] + gN * r[1] - r[2], jo = gO * o[0] + gN * o[1] - o[2];
            double nn = Math.sqrt(gO * gO + gN * gN + 1);
            u.setze(tiefe, jr, jo, new double[] {-gO / nn, 1 / nn, gN / nn}, 5, 0, Linsenglas.BREITE, Linsenglas.HOEHE);
            int spalteN = 16, m = spalteN * bh;
            float[] uv = new float[2 * m];
            for (int s = 0; s < spalteN; s++)
                for (int y = 0; y < bh; y++) { uv[2 * (s * bh + y)] = (float) ((s + 0.5) / spalteN); uv[2 * (s * bh + y) + 1] = (float) ((y + 0.5) / bh); }
            float[] p = u.probe(b, bw, bh, uv, 0, hw, durch, ansicht);
            int[] band = u.band(bw, bh);
            for (int s = 0; s < spalteN; s++) {
                // Karte: erste Zeile von oben, deren Tiefe > 0 ist (Mitte zwischen den Bildpunkten)
                double karte = Double.NaN;
                for (int y = 1; y < bh; y++)
                    if (p[8 * (s * bh + y - 1)] <= 0 && p[8 * (s * bh + y)] > 0) {
                        double a0 = p[8 * (s * bh + y - 1)], a1 = p[8 * (s * bh + y)];
                        karte = y - 0.5 + (0 - a0) / (a1 - a0);
                        break;
                    }
                // double: Punkt P = Auge + xl r + yl o auf der Scheibe; Tiefe η + g·(P − Auge)_waagerecht − P_z = 0
                double xl = ((s + 0.5) / spalteN - 0.5) * Linsenglas.BREITE;
                double cO = gO * o[0] + gN * o[1] - o[2];
                double yl = -(eta - auge + xl * (gO * r[0] + gN * r[1] - r[2])) / cO;
                double zeile = (0.5 - yl / Linsenglas.HOEHE) * bh;                // Bildzeile (Mitte des Bildpunkts bei y + 0,5)
                if (zeile < 1 || zeile > bh - 1) { if (!Double.isNaN(karte)) dMax = Math.max(dMax, 99); continue; }
                if (Double.isNaN(karte)) { dMax = Math.max(dMax, 99); continue; }
                dMax = Math.max(dMax, Math.abs(karte - zeile));
                spalten++;
                // seit L6b2: das kopierte Band enthält den Meniskus (|Tiefe| < MENISKUS) dieser Spalte
                for (int y = 0; y < bh; y++)
                    if (Math.abs(p[8 * (s * bh + y)]) < Unterwasser.MENISKUS && (band == null || y < band[0] || y >= band[1])) bandFehlt++;
            }
        }
        A.pruefe(spalten > 20 && dMax <= 1, "Meniskus: Wasserlinie auf der Linse gegen den Pegel am Auge (Prüfstein 39)", f("%d Spalten in 6 Lagen (Auge ±1,5 cm um η, Gefälle bis 7,5 %%, Blick beliebig): größte Abweichung %.2f Bildpunkte", spalten, dMax), "± 1 Bildpunkt");
        A.pruefe(bandFehlt == 0, "Meniskus im kopierten Band (seit L6b2)", f("%d Bildpunkte des Meniskus außerhalb", bandFehlt), "0");
    }

    // ------------------------------------------------------------------ 3

    static void anwendung(File szenen, int breite, int hoehe) {
        A.teil("3 In der Anwendung: Ereignisse der Brandung (38), Schwimmerblick (39, 41)");
        try {
            Szenen sz = Szenen.lies(szenen);
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                brecherZaehlen(b, sz, breite, hoehe);
                schwimmerImBild(b, sz, breite, hoehe);
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prüfstein 38, Teil 1: je Brecher im Umkreis genau ein Ereignis. Die Brandung des Sturms (seegang, 12:30 UTC) an den
     * Kanten um das Auge vor der Kante mit dem höchsten Seegang am Fuß ({@code AuflaufCheck.kante}; bis L6b die Ansicht
     * {@code brandung}, deren Kanten weiter als 300 m lagen), als eigene Rechnung mit den Kanten und dem Wasser der Bühne,
     * Umkreis 300 m wie {@code Ereignisklang}, 120 s in Bildern zu 1/30 s. Wahrheit: das Protokoll der Wellen je Bild (Kante,
     * Nummer, Zeit des Aufschlags, Höhe); eine Welle klingt, wenn ihr Aufschlag in das Bild fällt.
     */
    static void brecherZaehlen(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("seegang");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        b.brandung(true);
        Ansicht an = sz.ansicht("brandung");
        double t = (Instant.parse("2026-10-25T12:30:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl = new Blick();
        an.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
        bl.groesse(breite, hoehe);
        b.standbild(bl, t);
        com.dan.render.brandung.Brandungsbild bb = b.brandungsbild();
        if (bb == null || bb.quelle == null) { A.pruefe(false, "Prüfstein 38: Brandung im Gebiet", "keine", "vorhanden"); return; }
        Blick vk = AuflaufCheck.kante(A, b, t, breite, hoehe, "Prüfstein 38");
        if (vk == null) return;
        bl = vk;
        Brandung br = new Brandung(bb.brandung.e0, bb.brandung.n0);
        br.weite = 300;                                                     // wie Ereignisklang.UMKREIS (das Steuerhaus filtert genauso)
        br.abschnitteMax = Integer.MAX_VALUE;
        br.blickCos = -2;                                                   // rundum
        br.protokollieren = true;
        Tonereignisse te = new Tonereignisse();
        br.ton = te;
        double[] w = bb.quelle.wind();
        double dt = 1.0 / 30, tPrev = Double.NaN;
        long gesehen = 0;
        int bilder = 0, wellenNah = 0, doppelt = 0, fehlt = 0, zuviel = 0, falsch = 0;
        Map<Long, double[]> zuletzt = new HashMap<>();                       // (Kante, Nummer) → {ta zuletzt, gemeldet 0/1}
        for (double ts = t; ts < t + 120; ts += dt) {
            br.rechne(bb.kanten, bb.quelle.wasser(), ts, bl.ost, bl.nord, w[0], w[1]);
            List<Tonereignisse.Ereignis> neu = te.seit(gesehen);
            if (!neu.isEmpty()) gesehen = neu.get(neu.size() - 1).nr();
            int brecher = 0;
            for (Tonereignisse.Ereignis e : neu) if (e.art().equals(Tonereignisse.BRECHER)) brecher++;
            int soll = 0;
            if (!Double.isNaN(tPrev)) {
                for (double[] q : br.wellen) {
                    long key = (long) q[0] << 32 | ((long) q[1] & 0xffffffffL);
                    double ta = q[2], H = q[3];
                    boolean jetzt = ta > tPrev && ta <= ts && H >= 0.1;
                    double[] z = zuletzt.get(key);
                    if (jetzt) {
                        soll++;
                        if (z != null && z[1] > 0) doppelt++;
                        zuletzt.put(key, new double[] {ta, 1});
                    } else {
                        // übersprungen: im letzten Bild noch vor dem Aufschlag, jetzt schon vor dem letzten Bild
                        if (z != null && z[1] == 0 && z[0] > tPrev && ta <= tPrev && H >= 0.1) fehlt++;
                        zuletzt.put(key, new double[] {ta, z != null ? z[1] : 0});
                    }
                    wellenNah++;
                }
                if (brecher > soll) zuviel += brecher - soll;
                else if (brecher < soll) falsch += soll - brecher;
            }
            tPrev = ts;
            bilder++;
        }
        long alle = te.zahl(Tonereignisse.BRECHER), ueber = te.zahl(Tonereignisse.UEBERLAUF);
        A.info("Prüfstein 38: Brandung im Umkreis", f("Auge (%.0f, %.0f); %d Bilder (120 s), %d Wellen im Protokoll, %d Brecher und %d Überläufe gemeldet, Wellen mit Aufschlag: %d verschiedene",
                bl.ost, bl.nord, bilder, wellenNah, alle, ueber, zuletzt.values().stream().filter(z -> z[1] > 0).count()));
        A.pruefe(alle > 0 && zuviel == 0 && falsch == 0 && doppelt == 0 && fehlt == 0, "Ton: je Brecher im Umkreis genau ein Ereignis (Prüfstein 38)",
                f("%d Brecher; je Bild mehr gemeldet als Aufschläge %d, weniger %d; eine Welle zweimal %d; übersprungen %d", alle, zuviel, falsch, doppelt, fehlt), "gleich; 0; 0; 0");
    }

    /** Der Schwimmerblick am ruhigen Tag (ruhigsee, 14:30 UTC): Bilder über, an und unter der Linie, Kosten, Überspülung. */
    static void schwimmerImBild(Buehne b, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("ruhigsee");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        Flaechenwart w = b.wart();
        Flotte fl = b.flotte();
        Ansicht an = sz.ansicht("schwimmer");
        if (!(b.bordKamera(an) instanceof Schwimmerblick sb)) {
            A.pruefe(false, "Schwimmerblick im Szenario ruhigsee", "kein Kopf „" + an.bord + "“", "da");
            return;
        }
        double t = (Instant.parse("2026-10-30T14:30:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl0 = new Blick();
        an.setze(bl0, b.gelaende, b.utm(), b.uhr().bei(t));
        bl0.groesse(breite, hoehe);
        b.standbild(bl0, t);
        fl.neuSetzen();
        double dt = 1.0 / 30, uwMs = 0, uwSumme = 0, linseMs = 0, uwAnfang = 0;
        int mitLinse = 0, nUw = 0;
        List<Double> uwListe = new java.util.ArrayList<>(), linseListe = new java.util.ArrayList<>();
        long kopiertSumme = 0;
        long uhrNs = 0, w0 = System.nanoTime();
        int bereit = -1, gemessen = 0, teilweise = 0, ganz = 0, trocken = 0, maxTropfen = 0;
        boolean[] bild = new boolean[3];
        String[] namen = {"S8d_schwimmer_linie.png", "S8d_schwimmer_unter.png", "S8d_schwimmer_tropfen.png"};
        int ueber0 = 0;
        double kopfMin = 1e9, kopfMax = -1e9, augeMin = 1e9, augeMax = -1e9;
        for (int i = 0; i < 3000; i++) {
            w.zeitraffer(1);
            b.flotteBild(t, 1, dt);
            Blick bl = sb.weiter(dt, t, bl0);
            if (bl == null) bl = bl0;
            bl.groesse(breite, hoehe);
            w.vorBild(bl, t, uhrNs);
            Buehne.Bildzeiten z = b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt()) { bereit = i; ueber0 = sb.ueberspuelt; }
            if (bereit >= 0 && i > bereit + 30) {
                gemessen++;
                double us = 0, ls = 0;
                for (Map.Entry<String, Double> e : z.abschnitte().entrySet()) {
                    if (e.getKey().startsWith("Unterwasser")) us += e.getValue();
                    if (e.getKey().startsWith("Linse")) ls += e.getValue();
                }
                if (b.linsenbild() != null && b.linsenbild().aktiv) { linseMs = Math.max(linseMs, ls); mitLinse++; linseListe.add(ls); }
                Unterwasser u = b.unterwasser();
                boolean gez = u != null && u.gezeichnet;
                if (gez) kopiertSumme += u.kopiert;
                if (gez) {
                    nUw++;
                    if (nUw <= 3) uwAnfang = Math.max(uwAnfang, us);             // die ersten drei: Kopie und Leitung werden angelegt
                    else uwMs = Math.max(uwMs, us);
                    uwSumme += us;
                    uwListe.add(us);
                }
                if (!Double.isNaN(sb.eta)) {
                    augeMin = Math.min(augeMin, sb.auge[2] - sb.eta);
                    augeMax = Math.max(augeMax, sb.auge[2] - sb.eta);
                    double km = sb.auge[2] - sb.versatz(t) - sb.eta;
                    kopfMin = Math.min(kopfMin, km);
                    kopfMax = Math.max(kopfMax, km);
                }
                maxTropfen = Math.max(maxTropfen, sb.linse.glas.snapshot().count());
                if (sb.ganzUnter()) ganz++;
                else if (gez) teilweise++;
                else trocken++;
                // Bilder: Linie quer durchs Bild (Mitte der Scheibe nahe am Pegel), ganz unter Wasser, 1 s nach dem Auftauchen
                int k = -1;
                if (!bild[0] && gez && !sb.ganzUnter() && Math.abs(sb.tiefe) < 0.004) k = 0;
                else if (!bild[1] && sb.ganzUnter() && sb.tiefe > 0.04) k = 1;
                if (!bild[2] && !gez && sb.linse.glas.snapshot().count() >= 10) k = 2;
                if (k >= 0) {
                    javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", namen[k]).toFile());
                    bild[k] = true;
                }
                if (gemessen >= 1800) break;
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            if ((System.nanoTime() - w0) / 1e9 > 420) break;
        }
        // das Snellsche Fenster im Bild: Auge 6 cm unter der Mitte des Kopfs, Blick 45° nach oben, ohne Schwimmzug
        sb.zug = false;
        sb.festerVersatz = -0.06;
        sb.neigung = 45;
        for (int i = 0; i < 20; i++) {
            b.flotteBild(t, 1, dt);
            Blick bl = sb.weiter(dt, t, bl0);
            if (bl == null) bl = bl0;
            bl.groesse(breite, hoehe);
            w.vorBild(bl, t, uhrNs);
            b.bildzeiten(bl, t, 1, true);
            w.nachBild();
            t += dt;
            uhrNs += Math.round(dt * 1e9);
        }
        javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", "S8d_schwimmer_fenster.png").toFile());
        A.info("Snellsches Fenster im Bild", f("docs/status/S8d_schwimmer_fenster.png: Auge %.1f cm unter der Fläche, Blick 45° nach oben, Bildwinkel %.0f°", 100 * sb.tiefe, sb.bildwinkel));
        sb.zug = true;
        sb.neigung = an.neigung;
        fl.warte();
        int mitUw = teilweise + ganz;
        A.info("Schwimmerblick (ruhigsee, ab 14:30 UTC)", f("%d Bilder: Scheibe trocken %d, an der Linie %d, ganz unter Wasser %d; Mitte des Kopfs gegen η %+.1f … %+.1f cm, Auge %+.1f … %+.1f cm; aufgetaucht %d-mal, Tropfen jetzt %d; Bilder %s",
                gemessen, trocken, teilweise, ganz, 100 * kopfMin, 100 * kopfMax, 100 * augeMin, 100 * augeMax, sb.ueberspuelt - ueber0, sb.linse.glas.snapshot().count(),
                String.join(", ", java.util.stream.IntStream.range(0, 3).mapToObj(i -> "docs/status/" + namen[i] + (bild[i] ? "" : " (keins)")).toList())));
        A.pruefe(mitUw > 0 && trocken > 0, "Schwimmerblick: Auge an der Wasserlinie (SF160)", f("über Wasser %d, an der Linie %d, unter Wasser %d Bilder", trocken, teilweise, ganz), "beides kommt vor");
        java.util.Collections.sort(uwListe);
        double p99 = uwListe.isEmpty() ? 0 : uwListe.get(Math.min(uwListe.size() - 1, (int) (0.99 * uwListe.size())));
        A.pruefe(nUw > 3 && uwMs <= 1.5, "Kosten: unter Wasser (Prüfstein 41)", nUw <= 3 ? "kaum unter Wasser" : f("höchstens %.3f ms (ohne die ersten drei Bilder, in denen Kopie und Leitung angelegt werden: bis %.1f ms), 99 %% bis %.3f ms, im Mittel %.3f ms in %d Bildern",
                uwMs, uwAnfang, p99, uwSumme / nUw, nUw), "≤ 1,5 ms [G]");
        A.pruefe(sb.ueberspuelt - ueber0 > 0 && maxTropfen > 0, "Schwimmerblick: Tropfen nach dem Auftauchen", f("%d-mal aufgetaucht, bis %d Tropfen auf der Scheibe", sb.ueberspuelt - ueber0, maxTropfen), "mindestens einmal; Tropfen");
        java.util.Collections.sort(linseListe);
        double lp99 = linseListe.isEmpty() ? 0 : linseListe.get(Math.min(linseListe.size() - 1, (int) (0.99 * linseListe.size())));
        double lMittel = linseListe.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        A.pruefe(mitLinse > 0 && linseMs <= 0.3, "Kosten: Linse nach dem Auftauchen (Prüfstein 41)", mitLinse == 0 ? "keine Tropfen auf der Linse"
                : f("höchstens %.3f ms, 99 %% bis %.3f ms, im Mittel %.3f ms in %d Bildern mit Tropfen", linseMs, lp99, lMittel, mitLinse), "≤ 0,3 ms [G]");
        A.info("Unter Wasser: kopierte Zeilen (Band des Meniskus)", f("im Mittel %.0f von %d je Bild unter Wasser", nUw > 0 ? (double) kopiertSumme / nUw : 0.0, hoehe));
        b.bordKamera(null);
    }
}
