package com.dan.render.air.demo;

import com.dan.core.astro.Gestirne;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.raum.Utm;
import com.dan.render.air.Himmel;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Scattering;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung von render.air ohne Fenster (P6c, Werkbuch HalligHooge Teil A, 12.7, Prüfstein 4). Teile: 1 Sonne und Mond
 * ({@code core.astro} gegen Werte aus {@code sky.astro}, Drehung in die Szene), 2 Tabellen auf der Karte gegen
 * {@link Scattering} (Kopie von {@code sky.Scattering}), 3 Himmel und Luftperspektive im Bild gegen dieselbe Rechnung
 * auf dem Prozessor, 4 Sonnenscheibe. Endet mit „Alles in Ordnung.“ und schreibt das Attest nach
 * {@code docs/status/P6_Himmel.md}. Die Grenzen stehen vor der Messung fest; [G] heißt gesetzt.
 */
public final class AirCheck {
    private static final Attest A = new Attest("com.dan.render.air", Himmel.VERSION);
    /** Auge auf der Hanswarft (UTM 32), wie FrameCheck. */
    private static final double OST = 470_900, NORD = 6_046_615;

    /**
     * Vergleichswerte aus {@code sky.astro} (Sky.Astro, wahre Höhe ohne Brechung, Azimut von Nord über Ost, Grad), am
     * 14.11.2026 einmal gerechnet und eingetragen (render darf {@code sky} nicht kennen): Zeit, Sonne h, A, Mond h, A,
     * Phasenwinkel, Helligkeit. Der Mond topozentrisch aus {@code FMoonPosition} nach Meeus 40.2/40.3 mit H′ = H − Δα:
     * {@code FRiseSet.topocentricAltitude} rechnet H + Δα und liegt damit bis 0,64° daneben (Befund P6c, 12.7).
     */
    private static final Object[][] SKY = {
            {"2026-10-25T06:30:00Z", 1.319163, 113.241372, -9.388775, 303.703163, 12.5754, -12.472},
            {"2026-10-25T12:00:00Z", 22.396026, 193.259615, -20.828812, 23.504968, 9.8466, -12.548},
            {"2026-10-25T15:45:00Z", 1.839826, 245.708736, 2.853228, 71.496442, 8.0857, -12.598},
            {"2026-06-21T03:10:00Z", 1.357050, 49.234625, -30.258004, 330.785033, 99.2186, -9.766},
            {"2026-06-21T11:20:00Z", 58.839186, 176.624589, 1.396813, 92.912835, 95.1117, -9.921},
            {"2026-12-21T08:40:00Z", 4.456306, 142.904717, -13.332506, 0.784902, 38.1637, -11.841},
            {"2026-11-14T21:00:00Z", -46.115236, 312.528591, -15.320423, 248.930248, 118.4385, -8.751},
            {"2027-03-20T17:55:00Z", -3.182002, 274.409912, 20.425026, 108.176887, 22.5177, -12.239}};

    public static void main(String[] args) {
        boolean attest = !List.of(args).contains("--ohne-attest");
        System.out.println("AirCheck – com.dan.render.air " + Himmel.VERSION + " (Werkbuch HalligHooge Teil A, P6c, seit P7e mit J)");
        gestirne();
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("AirCheck", 512L << 20);   // seit P10d: Teil 6 hält J für Wasser und Land (12 Bilder, 189 MB)
            karte(g, konto);
            tiefeSonne(g, konto);
            mischung(g, konto);
            A.teil("7 Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto nach allen Teilen", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.meldungen.isEmpty() ? "" : "; die erste Meldung: " + g.meldungen.get(0).replace('\n', ' ');
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht + (g.synchronPruefung ? " mit Prüfung der Synchronisation" : " ohne Prüfung der Synchronisation"),
                        g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierungsschicht", "nicht eingeschaltet (-Dcore.gpu.validierung=true, braucht das Vulkan SDK)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P6_Himmel.md"), "P6c – AirCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Sonne und Mond

    /** Winkel zwischen zwei Richtungen aus Höhe und Azimut (Grad). */
    private static double winkel(double h1, double a1, double h2, double a2) {
        double[] u = richtung(h1, a1), v = richtung(h2, a2);
        double kx = u[1] * v[2] - u[2] * v[1], ky = u[2] * v[0] - u[0] * v[2], kz = u[0] * v[1] - u[1] * v[0];
        return Math.toDegrees(Math.atan2(Math.sqrt(kx * kx + ky * ky + kz * kz), u[0] * v[0] + u[1] * v[1] + u[2] * v[2]));
    }

    private static double[] richtung(double h, double a) {
        double hr = Math.toRadians(h), ar = Math.toRadians(a);
        return new double[] {Math.cos(hr) * Math.sin(ar), Math.cos(hr) * Math.cos(ar), Math.sin(hr)};
    }

    private static void gestirne() {
        A.teil("1 Sonne und Mond");
        double[] ll = Utm.rueckwaerts(OST, NORD, 32);
        A.info("Ort", f("Hanswarft, UTM 32 O %.0f, N %.0f = %.5f° O, %.5f° N", OST, NORD, ll[0], ll[1]));
        double ws = 0, wm = 0, wp = 0, wmag = 0;
        for (Object[] r : SKY) {
            Gestirne.Stand st = Gestirne.bei(Instant.parse((String) r[0]));
            Gestirne.Richtung s = st.sonne(ll[0], ll[1]), m = st.mond(ll[0], ll[1]);
            ws = Math.max(ws, winkel(s.hoeheGrad(), s.azimutGrad(), (double) r[1], (double) r[2]));
            wm = Math.max(wm, winkel(m.hoeheGrad(), m.azimutGrad(), (double) r[3], (double) r[4]));
            double psi = Himmel.phasenwinkel(st);
            wp = Math.max(wp, Math.abs(psi - (double) r[5]));
            wmag = Math.max(wmag, Math.abs(Himmel.mondMag(psi, st.mondAbstand) - (double) r[6]));
        }
        A.pruefe(ws <= 0.01, "Sonne aus core.astro gegen sky.astro (" + SKY.length + " Zeitpunkte 2026/27, wahre Höhe und Azimut)", f("größter Winkel %.5f°", ws), "≤ 0,01° (Prüfstein 4)");
        A.pruefe(wm <= 0.01, "Mond aus core.astro gegen sky.astro (mit Parallaxe)", f("größter Winkel %.5f°", wm), "≤ 0,01° [G]");
        A.pruefe(wp <= 0.05 && wmag <= 0.005, "Phasenwinkel und Helligkeit des Monds gegen sky", f("Phasenwinkel ± %.4f°, Helligkeit ± %.4f mag", wp, wmag), "≤ 0,05° [G], ≤ 0,005 mag [G]");
        // Drehung in die Szene: ein Schritt nach wahrem Nord ist im Gitter um die Konvergenz gedreht
        double gam = Utm.faktoren(ll[0], ll[1], 32)[0];
        double[] p0 = Utm.vorwaerts(ll[0], ll[1], 32), p1 = Utm.vorwaerts(ll[0], ll[1] + 1e-4, 32), p2 = Utm.vorwaerts(ll[0] + 1e-4, ll[1], 32);
        double[] nordSzene = {p1[0] - p0[0], 0, -(p1[1] - p0[1])}, ostSzene = {p2[0] - p0[0], 0, -(p2[1] - p0[1])};
        double[] n = szene(0, 1, 0, ll), o = szene(1, 0, 0, ll);
        double dw = Math.max(Math.toDegrees(winkel3(n, nordSzene)), Math.toDegrees(winkel3(o, ostSzene)));
        A.pruefe(dw <= 1e-4, "Wahres Nord und Ost in der Szene (Gitter UTM 32) gegen einen Schritt in Länge und Breite", f("Konvergenz %.4f°, größter Winkel %.2e°", gam, dw), "≤ 1e-4° [G]");
        A.pruefe(Math.abs(Himmel.brechung(0) - 0.4841) < 0.002 && Math.abs(Himmel.brechung(90)) < 1e-4,
                "Strahlenbrechung (Sæmundsson) an der Kimm und im Zenit", f("%.4f° bei 0°, %.6f° bei 90°", Himmel.brechung(0), Himmel.brechung(90)), "0,48° ± 0,002°, 0");
    }

    private static double[] szene(double ost, double nord, double oben, double[] ll) {
        double gam = Math.toRadians(Utm.faktoren(ll[0], ll[1], 32)[0]), c = Math.cos(gam), s = Math.sin(gam);
        return new double[] {ost * c - nord * s, oben, -(nord * c + ost * s)};
    }

    private static double winkel3(double[] u, double[] v) {
        double kx = u[1] * v[2] - u[2] * v[1], ky = u[2] * v[0] - u[0] * v[2], kz = u[0] * v[1] - u[1] * v[0];
        return Math.atan2(Math.sqrt(kx * kx + ky * ky + kz * kz), u[0] * v[0] + u[1] * v[1] + u[2] * v[2]);
    }

    // ------------------------------------------------------------------ 2–4 Karte

    private static double lum(double r, double g, double b) { return 0.2126 * r + 0.7152 * g + 0.0722 * b; }

    private static double[] vec(float[] w, int i) { return new double[] {w[i], w[i + 1], w[i + 2]}; }

    private static double[] mal(double[] v, double s) { return new double[] {v[0] * s, v[1] * s, v[2] * s}; }

    /** Ein Bild durch den Himmel: Vorbereiten und Zeichnen in einem Ablauf. */
    private static void bild(Geraet g, Himmel h, Bildziel z) {
        Ablauf a = new Ablauf(g, g.grafik);
        h.vorbereiten(a, z);
        h.zeichnen(a, z);
        g.grafik.einmal(c -> a.aufnehmen(c, null));
    }

    private static void karte(Geraet g, Konto konto) {
        int W = 192, H = 108;
        try (Himmel h = new Himmel(g, konto);
             Bild hdr = new Bild(g, konto, "HDR", W, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe", W, H, Bild.D32F)) {
            Bildziel z = new Bildziel(hdr, tiefe);
            z.blick = new Blick(OST, NORD, 4, 120, 4, 70).groesse(W, H);
            z.zeit = Instant.parse("2026-10-25T07:20:00Z");                  // Sonne tief im Osten, Mond unter der Kimm
            z.vorbelichtung = 1e-3;
            // Tiefe: links Himmel (0), rechts Punkte in 50 m bis 40 km Abstand; HDR mit einer bekannten Farbe
            float[] t = new float[W * H], c = new float[W * H * 4];
            Random r = new Random(4);
            double[][] bas = z.blick.basis();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = y * W + x;
                    // Abstand längs des Strahls 50 m bis 40 km, über dem Boden (Strahlen nach unten enden vor der Kugel in 0 m)
                    double[] dir = z.blick.strahl(x + 0.5, y + 0.5);
                    double weg = 50 * Math.pow(800, r.nextDouble());
                    if (dir[1] < 0) weg = Math.min(weg, 0.95 * z.blick.hoehe / -dir[1]);
                    double d = x < W / 2 ? 0 : weg * (dir[0] * bas[2][0] + dir[1] * bas[2][1] + dir[2] * bas[2][2]);
                    t[i] = d == 0 ? 0 : (float) (z.blick.nah / d);
                    c[4 * i] = 0.3f + 0.4f * r.nextFloat();
                    c[4 * i + 1] = 0.3f + 0.4f * r.nextFloat();
                    c[4 * i + 2] = 0.3f + 0.4f * r.nextFloat();
                    c[4 * i + 3] = 1;
                    for (int k = 0; k < 3; k++) c[4 * i + k] = Float.float16ToFloat(Float.floatToFloat16(c[4 * i + k]));
                }
            tiefe.schreibe(t);
            hdr.schreibe(c);
            bild(g, h, z);

            // ---- 2 Tabellen (Prüfstein 4)
            A.teil("2 Tabellen auf der Karte");
            Atmosphere atm = h.luft();
            A.info("Luft", f("Sicht %.0f km [G], Bodenalbedo %.2f, Mie-Auslöschung %.3e /m", atm.visibilityKm, atm.groundAlbedo, atm.mieExtinction));
            Scattering sc = new Scattering(atm);
            float[] gt = h.durchlaessigkeit().lies(), gm = h.mehrfachstreuung().lies(), gv = h.ansicht().lies();
            float[] ct = sc.transmittanceTable(), cm = Scattering.jBild(sc.streuordnungen(), 0);
            double wt = 0, wm = 0;
            for (int i = 0; i < ct.length / 3; i++)
                for (int k = 0; k < 3; k++) wt = Math.max(wt, Math.abs(gt[i * 4 + k] - ct[i * 3 + k]) / Math.max(1e-3, ct[i * 3 + k]));
            double[] top = new double[3];
            for (int i = 0; i < cm.length / 4; i++) for (int k = 0; k < 3; k++) top[k] = Math.max(top[k], Math.abs(cm[i * 4 + k]));
            for (int i = 0; i < cm.length / 4; i++)
                for (int k = 0; k < 3; k++) wm = Math.max(wm, Math.abs(gm[i * 4 + k] - cm[i * 4 + k]) / top[k]);
            A.pruefe(wt <= 1e-3, "Durchlässigkeit als optische Tiefe (256 × 64) gegen Scattering, relativ (unter 10⁻³ absolut)", f("± %.2e", wt), "≤ 1e-3 [G] (Prüfstein 4)");
            A.pruefe(wm <= 1e-3, "J der drei Kanäle (" + Himmel.J_W + " × " + Himmel.J_H + ", float16) gegen Streuordnungen, bezogen auf den größten Wert je Kanal",
                    f("± %.2e", wm), "≤ 1e-3 [G] (Prüfstein 4)");
            float[] w = h.letzteWerte();
            double eye = w[7];
            double[] s = vec(w, 4), sE = vec(w, 8), m = vec(w, 12), mE = vec(w, 16);
            double ws = 0, lmax = 0;
            String wo = "";
            int nOben = 0;
            for (int k = 0; k < 256; k++) {
                int ix = (k * 37) % Himmel.ANSICHT_W, iy = 2 + (k * 13) % (Himmel.ANSICHT_H - 4);
                double[] d = ansichtRichtung((ix + 0.5) / Himmel.ANSICHT_W, (iy + 0.5) / Himmel.ANSICHT_H, eye);
                // seit P7e: Sonne spektral (15 Wellenlängen), Mond in drei Kanälen
                double[] sp = sc.radianceSpektral(eye, d, s, sE[0] / Himmel.SONNE_LX, Scattering.VIEW_STEPS), mo = sc.radiance(eye, d, s, new double[3], m, mE, Scattering.VIEW_STEPS);
                double[] cpu = {sp[0] + mo[0], sp[1] + mo[1], sp[2] + mo[2]};
                double[] nacht = nacht(sc, eye, d, w[15]);
                double lc = lum(cpu[0] + nacht[0], cpu[1] + nacht[1], cpu[2] + nacht[2]);
                int o = (iy * Himmel.ANSICHT_W + ix) * 4;
                double lg = lum(gv[o], gv[o + 1], gv[o + 2]);
                double wk = Math.abs(lg - lc) / Math.max(1e-30, lc);
                if (wk > ws) wo = f("Texel %d/%d (Blick %.3f hoch): Karte %.5g, Prozessor %.5g", ix, iy, d[1], lg, lc);
                ws = Math.max(ws, wk);
                lmax = Math.max(lmax, lc);
                if (d[1] > 0) nOben++;
            }
            A.pruefe(ws <= 1e-3, "Himmelsansicht (256 × 128) gegen Scattering.radianceSpektral (Sonne), radiance (Mond) und Nachthimmel, Leuchtdichte (256 Texel, " + nOben + " über der Kimm)",
                    f("± %.2e, hellster %.0f cd/m²", ws, lmax), "≤ 1e-3 [G] (Prüfstein 4)");
            A.info("Himmelsansicht, größte Abweichung", wo);

            // ---- 3 Himmel und Luftperspektive im Bild
            A.teil("3 Himmel und Luftperspektive im Bild");
            float[] o = hdr.lies();
            double vb = z.vorbelichtung, wh = 0, wl = 0, wd = 0;
            int nh = 0, nl = 0;
            double rr = Atmosphere.BOTTOM + eye;
            for (int y = 0; y < H; y += 3)
                for (int x = 0; x < W; x += 3) {
                    int i = y * W + x;
                    double[] d = z.blick.strahl(x + 0.5, y + 0.5);
                    if (t[i] == 0) {
                        double[] cpu = himmelCpu(sc, h, eye, d, w, gv);
                        for (int k = 0; k < 3; k++) wh = Math.max(wh, Math.abs(o[4 * i + k] - cpu[k] * vb) / Math.max(1e-6, cpu[k] * vb));
                        nh++;
                    } else {
                        double weg = z.blick.nah / t[i] / (d[0] * bas[2][0] + d[1] * bas[2][1] + d[2] * bas[2][2]);
                        double[][] st = sc.strecke(rr, d, s, sE, m, mE, weg, h.schritte);
                        for (int k = 0; k < 3; k++) {
                            double soll = c[4 * i + k] * st[1][k] + st[0][k] * vb;
                            wl = Math.max(wl, Math.abs(o[4 * i + k] - soll) / soll);
                        }
                        nl++;
                    }
                }
            A.pruefe(wh <= 2e-3, "Himmel im Bild (RGBA16F) gegen die Himmelsansicht auf dem Prozessor (" + nh + " Bildpunkte)", f("relativ ± %.2e", wh), "≤ 2e-3 [G] (float16: 4,9e-4)");
            A.pruefe(wl <= 2e-3, "Luftperspektive (" + h.schritte + " Schritte) gegen Scattering.strecke, bis 40 km über dem Boden (" + nl + " Bildpunkte)", f("relativ ± %.2e", wl), "≤ 2e-3 [G]");
            // gegen den genauen Weg: was 8 Schritte kosten
            for (double weg : new double[] {500, 5000, 30000}) {
                double[] d = z.blick.achse();
                double[][] a8 = sc.strecke(rr, d, s, sE, m, mE, weg, h.schritte), a64 = sc.strecke(rr, d, s, sE, m, mE, weg, 256);
                wd = Math.max(wd, Math.abs(lum(a8[0][0], a8[0][1], a8[0][2]) - lum(a64[0][0], a64[0][1], a64[0][2])) / lum(a64[0][0], a64[0][1], a64[0][2]));
                if (weg == 30000) A.info("Luftperspektive auf 30 km", f("Durchlässigkeit %.3f / %.3f / %.3f, Streulicht %.0f cd/m²", a64[1][0], a64[1][1], a64[1][2], lum(a64[0][0], a64[0][1], a64[0][2])));
            }
            A.pruefe(wd <= 0.01, "Luftperspektive mit " + h.schritte + " Schritten gegen 256 Schritte (Blickachse, 0,5 / 5 / 30 km)", f("Streulicht ± %.2e", wd), "≤ 1 % [G]");
            A.pruefe(h.neuGerechnet == 1, "Tabellen der Luft nur einmal gerechnet", h.neuGerechnet + "-mal", "1");
            bild(g, h, z);
            A.pruefe(h.neuGerechnet == 1 && h.ansichtGerechnet == 1, "Zweites Bild mit derselben Luft und Zeit: Tabellen und Ansicht bleiben (seit P7e)",
                    h.neuGerechnet + "-mal, Ansicht " + h.ansichtGerechnet + "-mal", "1, 1");
            Instant t0 = z.zeit;
            z.zeit = t0.plusSeconds(1);
            bild(g, h, z);
            int a1 = h.ansichtGerechnet;
            z.zeit = t0.plusSeconds(60);
            bild(g, h, z);
            int a2 = h.ansichtGerechnet;
            for (int i = 1; i < Himmel.GRUPPEN; i++) bild(g, h, z);
            A.pruefe(a1 == 1 && a2 == 1 && h.ansichtGerechnet == 2,
                    "Ansicht neu erst, wenn die Sonne um mehr als 0,01° wandert [G], dann über fünf Bilder verteilt (F62 A): 1 s später nicht, 60 s später nach einem Bild noch nicht, nach fünf ja",
                    a1 + " / " + a2 + " / " + h.ansichtGerechnet, "1 / 1 / 2");
            float[] verteilt = h.ansicht().lies();
            h.verteilt = false;
            h.verwirfAnsicht();
            bild(g, h, z);
            float[] zug = h.ansicht().lies();
            double gr = 0, wv = 0;
            for (int i = 0; i < zug.length; i++) if (i % 4 != 3) gr = Math.max(gr, Math.abs(zug[i]));
            for (int i = 0; i < zug.length; i++) if (i % 4 != 3) wv = Math.max(wv, Math.abs(verteilt[i] - zug[i]) / gr);
            A.pruefe(wv <= 1e-6, "Himmelsansicht über fünf Bilder verteilt gegen in einem Zug (F62 A), bezogen auf den hellsten Wert", f("± %.2e", wv), "≤ 1e-6 [G]");
            z.zeit = t0;
            bild(g, h, z);
            h.verteilt = true;
            A.pruefe(z.geteilt.keySet().containsAll(List.of(Himmel.DURCH, Himmel.MEHR, Himmel.ANSICHT)) && z.puffer.containsKey(Himmel.WERTE)
                    && z.werte.containsKey(Himmel.SONNE) && z.werte.containsKey(Himmel.MOND), "Abgelegt im Bildziel", z.geteilt.keySet() + ", " + z.puffer.keySet() + ", " + z.werte.keySet(), "alle sechs");
            // seit S9d-1 (Seegang Werkbuch 109, SF185 A): Höhe des Auges erst ab Himmel.HOEHE_MIN, Sprung erst ab HOEHE_SPRUNG
            double h0 = z.blick.hoehe;
            int b0 = h.ansichtGerechnet;
            z.blick.hoehe = h0 + 0.9 * Himmel.HOEHE_MIN;
            for (int i = 0; i < Himmel.GRUPPEN; i++) bild(g, h, z);
            int b1 = h.ansichtGerechnet;
            z.blick.hoehe = h0 + 1.1 * Himmel.HOEHE_MIN;
            bild(g, h, z);
            int b2 = h.ansichtGerechnet;
            for (int i = 1; i < Himmel.GRUPPEN; i++) bild(g, h, z);
            int b3 = h.ansichtGerechnet;
            z.blick.hoehe = h0;
            for (int i = 0; i < Himmel.GRUPPEN; i++) bild(g, h, z);
            A.pruefe(b1 == b0 && b2 == b0 && b3 == b0 + 1,
                    f("Höhe des Auges (seit S9d-1): %.0f m → +%.0f m keine neue Ansicht; +%.0f m neu, verteilt über fünf Bilder (kein Sprung unter %.0f m)",
                            h0, 0.9 * Himmel.HOEHE_MIN, 1.1 * Himmel.HOEHE_MIN, Himmel.HOEHE_SPRUNG),
                    (b1 - b0) + " / " + (b2 - b0) + " / " + (b3 - b0), "0 / 0 / 1");

            // ---- 4 Sonnenscheibe: Blick auf die Sonne, 2° Bildwinkel
            A.teil("4 Sonnenscheibe");
            double[] sonne = z.werte(Himmel.SONNE);
            double hs = sonne[8], azGitter = Math.toDegrees(Math.atan2(sonne[0], -sonne[2]));
            double hsS = Math.toDegrees(Math.asin(w[33]));
            z.blick = new Blick(OST, NORD, 4, azGitter, hsS, 2).groesse(W, W);
            try (Bild hq = new Bild(g, konto, "HDR Sonne", W, W, Bild.RGBA16F); Bild tq = new Bild(g, konto, "Tiefe Sonne", W, W, Bild.D32F)) {
                Bildziel zq = new Bildziel(hq, tq);
                zq.blick = z.blick;
                zq.zeit = z.zeit;
                zq.vorbelichtung = 1e-5;
                tq.schreibe(new float[W * W]);
                hq.schreibe(new float[W * W * 4]);
                bild(g, h, zq);
                float[] q = hq.lies();
                float[] wq = h.letzteWerte();
                double pix = 2 * Math.tan(Math.toRadians(1)) / W;          // Raumwinkel eines Bildpunkts in der Mitte ≈ pix²
                double summe = 0, rand = 0;
                double[] sS = vec(wq, 32);
                for (int y = 0; y < W; y++)
                    for (int x = 0; x < W; x++) {
                        double[] d = zq.blick.strahl(x + 0.5, y + 0.5);
                        double[] himmel = himmelOhneScheibe(sc, eye, d, wq, gv);
                        int i = y * W + x;
                        double l = lum(q[4 * i], q[4 * i + 1], q[4 * i + 2]) / zq.vorbelichtung - lum(himmel[0], himmel[1], himmel[2]);
                        double dz = d[0] * zq.blick.achse()[0] + d[1] * zq.blick.achse()[1] + d[2] * zq.blick.achse()[2];
                        summe += l * pix * pix * dz * dz * dz;                    // Raumwinkel des Bildpunkts
                        if (q[4 * i] >= 59999) rand++;
                    }
                double[] tSonne = sc.transmittance(rr, sS[1]);
                double soll = lum(wq[8] * tSonne[0], wq[9] * tSonne[1], wq[10] * tSonne[2]);
                A.info("Sonne", f("wahre Höhe %.3f°, scheinbar %.3f° (Brechung %.3f°), Azimut %.2f° (im Gitter %.2f°), Radius %.4f°", hs, hsS, hsS - hs, sonne[7],
                        azGitter, Math.toDegrees(Math.asin(wq[38]))));
                A.pruefe(rand == 0, "Scheibe im HDR-Bild unter der Grenze (Vorbelichtung 10⁻⁵)", f("%.0f Bildpunkte an der Grenze", rand), "0");
                A.pruefe(Math.abs(summe / soll - 1) <= 0.01, "Licht der Scheibe über ihre Bildpunkte = Beleuchtungsstärke der Sonne mal Durchlässigkeit",
                        f("%.0f lx gegen %.0f lx (%+.2f %%)", summe, soll, (summe / soll - 1) * 100), "± 1 % [G]");
                // Mitte der Scheibe in der Bildmitte (die Kamera zielt auf die scheinbare Sonne). Ungewichtet über die
                // Bildpunkte der Scheibe: Nach Licht gewichtet läge die Mitte höher, weil die Luft den unteren Rand stärker
                // dämpft (bei 7,8° Höhe rund 6 % über den Radius, 0,4 Bildpunkte).
                double gmax = 0;
                for (int i = 0; i < W * W; i++) gmax = Math.max(gmax, q[4 * i + 1]);
                double sx = 0, sy = 0, sw = 0;
                for (int y = 0; y < W; y++)
                    for (int x = 0; x < W; x++) {
                        int i = y * W + x;
                        if (q[4 * i + 1] > 0.2 * gmax) {
                            sx += x + 0.5;
                            sy += y + 0.5;
                            sw++;
                        }
                    }
                double ab = Math.hypot(sx / sw - W / 2.0, sy / sw - W / 2.0);
                A.pruefe(ab <= 0.25, "Scheibe in der Bildmitte, wenn der Blick auf die scheinbare Sonne zielt", f("%.3f Bildpunkte (%.5f°) daneben, %.0f Bildpunkte in der Scheibe", ab, ab * 2.0 / W, sw), "≤ 0,25 Bildpunkte [G]");
            }
        }
    }

    // ------------------------------------------------------------------ 5 Tiefe Sonne (seit P10d)

    /** Zeit am 25.10.2026 nachmittags, zu der die Sonne über der Hanswarft scheinbar hs Grad hoch steht (Halbierung). */
    static Instant zeitFuer(double hs) {
        long a = Instant.parse("2026-10-25T13:00:00Z").getEpochSecond(), b = Instant.parse("2026-10-25T16:30:00Z").getEpochSecond();
        for (int i = 0; i < 40; i++) {
            long m = (a + b) / 2;
            double[] sS = com.dan.core.szene.Lichtquellen.bei(Instant.ofEpochSecond(m), OST, NORD, 32).sonneSicht();
            if (Math.toDegrees(Math.asin(sS[1])) > hs) a = m;
            else b = m;
        }
        return Instant.ofEpochSecond((a + b) / 2);
    }

    /**
     * P10d, Prüfsteine 2 und 3 (Messung c): der Himmel im Bild der Karte bei tiefer Sonne (scheinbar 3,2° und 1°) gegen
     * dasselbe Modell in Java ({@link Scattering#radianceSpektral} für die Sonne, {@link Scattering#radiance} für den Mond,
     * dazu der Nachthimmel), beide mit der Richtung aus den Werten des Bilds (seit F100 A die gehobene). Blick zur Sonne,
     * ±40° in der Breite, −13° bis +37°; an jedem dritten Bildpunkt, ohne die Bildpunkte näher als 2° an der Sonne. Je
     * Band der Höhe: Karte durch Java, und die Ansicht in Java bilinear (Java an den vier Texelmitten) durch Java an der
     * Stelle – das trennt das Lesen der Ansicht vom Rechnen auf der Karte.
     */
    private static void tiefeSonne(Geraet g, Konto konto) {
        A.teil("5 Tiefe Sonne (P10d, Prüfsteine 2 und 3)");
        int W = 192, H = 108;
        String[] namen = {"unter der Kimm", "0° bis 2°", "2° bis 6°", "über 6°"};
        for (double hs : new double[] {3.2, 1.0}) {
            try (Himmel h = new Himmel(g, konto);
                 Bild hdr = new Bild(g, konto, "HDR tief", W, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe tief", W, H, Bild.D32F)) {
                Bildziel z = new Bildziel(hdr, tiefe);
                z.zeit = zeitFuer(hs);
                double[] sS0 = com.dan.core.szene.Lichtquellen.bei(z.zeit, OST, NORD, 32).sonneSicht();
                double azG = Math.toDegrees(Math.atan2(sS0[0], -sS0[2]));
                z.blick = new Blick(OST, NORD, 4, azG, 12, 50).groesse(W, H);
                z.vorbelichtung = 1e-3;
                tiefe.schreibe(new float[W * H]);
                hdr.schreibe(new float[W * H * 4]);
                h.verteilt = false;
                bild(g, h, z);
                float[] o = hdr.lies(), w = h.letzteWerte(), gv = h.ansicht().lies();
                Atmosphere atm = h.luft();
                Scattering sc = new Scattering(atm);
                double eye = w[7], vb = z.vorbelichtung;
                double[] s = vec(w, 4), sE = vec(w, 8), m = vec(w, 12), mE = vec(w, 16);
                double cs = Math.cos(Math.toRadians(2));
                int AW = Himmel.ANSICHT_W, AH = Himmel.ANSICHT_H;
                java.util.List<int[]> punkte = new java.util.ArrayList<>();
                for (int y = 1; y < H; y += 3) for (int x = 1; x < W; x += 3) punkte.add(new int[] {x, y});
                // je Punkt {Band, Karte rgb, Java rgb, Java bilinear rgb}
                double[][] r = punkte.parallelStream().map(pq -> {
                    double[] d = z.blick.strahl(pq[0] + 0.5, pq[1] + 0.5);
                    if (d[0] * w[32] + d[1] * w[33] + d[2] * w[34] > cs) return null;
                    double hw = Math.toDegrees(Math.asin(d[1]));
                    int band = d[1] < 0 ? 0 : hw < 2 ? 1 : hw < 6 ? 2 : 3;
                    double[] q = new double[10];
                    q[0] = band;
                    int i = pq[1] * W + pq[0];
                    for (int k = 0; k < 3; k++) q[1 + k] = o[4 * i + k] / vb;
                    double[] j = java(sc, eye, d, s, sE, m, mE, w[15]);
                    System.arraycopy(j, 0, q, 4, 3);
                    double[] uv = ansichtUv(d, eye);
                    double px = uv[0] * AW - 0.5, py = Math.max(0, Math.min(AH - 1, uv[1] * AH - 0.5));
                    int ix = (int) Math.floor(px), iy = Math.min((int) Math.floor(py), AH - 2);
                    double fx = px - ix, fy = py - iy;
                    int x0 = Math.floorMod(ix, AW), x1 = (x0 + 1) % AW;
                    double[][] e = new double[4][];
                    int[][] tx = {{x0, iy}, {x1, iy}, {x0, iy + 1}, {x1, iy + 1}};
                    for (int t = 0; t < 4; t++)
                        e[t] = java(sc, eye, ansichtRichtung((tx[t][0] + 0.5) / AW, (tx[t][1] + 0.5) / AH, eye), s, sE, m, mE, w[15]);
                    for (int k = 0; k < 3; k++)
                        q[7 + k] = (e[0][k] + (e[1][k] - e[0][k]) * fx) * (1 - fy) + (e[2][k] + (e[3][k] - e[2][k]) * fx) * fy;
                    return q;
                }).filter(java.util.Objects::nonNull).toArray(double[][]::new);
                double[][] sum = new double[4][10];
                for (double[] q : r) {
                    double[] t = sum[(int) q[0]];
                    t[0]++;
                    for (int k = 1; k < 10; k++) t[k] += q[k];
                }
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < 4; k++) {
                    double[] t = sum[k];
                    if (t[0] == 0) continue;
                    b.append(f("%s (%.0f Punkte): Karte / Java %.3f (R %.3f G %.3f B %.3f), Ansicht bilinear in Java / Java %.3f (R %.3f G %.3f B %.3f); ", namen[k], t[0],
                            lum(t[1], t[2], t[3]) / lum(t[4], t[5], t[6]), t[1] / t[4], t[2] / t[5], t[3] / t[6],
                            lum(t[7], t[8], t[9]) / lum(t[4], t[5], t[6]), t[7] / t[4], t[8] / t[5], t[9] / t[6]));
                }
                A.info(f("Sonne scheinbar %.2f° (wahr %.2f°, %s), Auge %.0f m; Licht aus der Richtung %.2f° (aus den Werten des Bilds)", Math.toDegrees(Math.asin(w[33])), wahr(z.zeit), z.zeit, eye,
                        Math.toDegrees(Math.asin(s[1]))), b.toString());
                if (hs > 2) {
                    double fm = 0;
                    for (int k = 2; k <= 3; k++) {
                        double[] t = sum[k];
                        if (t[0] == 0) continue;
                        for (int c = 0; c < 3; c++) fm = Math.max(fm, Math.abs(t[1 + c] / t[4 + c] - 1));
                    }
                    double[] t = new double[10];
                    for (int k = 2; k <= 3; k++) for (int q = 0; q < 10; q++) t[q] += sum[k][q];
                    A.pruefe(fm <= 0.02, "Prüfstein 2: Himmel im Bild der Karte gegen Java mit derselben Richtung, Sonne 3,2°, Höhe über 2°, je Band und Kanal",
                            f("größte Abweichung %.1f %% (über 2° im Ganzen R %.3f G %.3f B %.3f)", 100 * fm, t[1] / t[4], t[2] / t[5], t[3] / t[6]), "1 ± 0,02 [G]");
                }
            }
        }
    }

    /**
     * P10d (Messung d): J für die Albedo der Szene, auf der Karte zwischen Wasser (0,06) und Land (0,23) gemischt
     * ({@code jmisch.comp}) gegen dieselbe Mischung in Java (float16 wie die Karte); neu gemischt erst ab 0,003 Änderung
     * der Albedo, und dann folgt die Ansicht verteilt über fünf Bilder statt in einem Zug.
     */
    private static void mischung(Geraet g, Konto konto) {
        A.teil("6 Albedo aus dem Anteil des Wassers (P10d, Messung d; seit F105 A je Kanal)");
        int W = 64, H = 36;
        try (Himmel h = new Himmel(g, konto);
             Bild hdr = new Bild(g, konto, "HDR Mischung", W, H, Bild.RGBA16F); Bild tiefe = new Bild(g, konto, "Tiefe Mischung", W, H, Bild.D32F)) {
            Bildziel z = new Bildziel(hdr, tiefe);
            z.blick = new Blick(OST, NORD, 4, 200, 10, 60).groesse(W, H);
            z.zeit = Instant.parse("2026-10-25T12:00:00Z");
            z.vorbelichtung = 1e-3;
            tiefe.schreibe(new float[W * H]);
            h.wasserAnteil = 0.6;
            // seit F105 A: Albedo des Wassers je Kanal aus Wasseralbedo (Schwebstoff 30 g/m³, Wind 8 m/s)
            h.wasserOptik = new com.dan.core.szene.Wasseroptik();
            h.wind = 8;
            long t0 = System.nanoTime();
            bild(g, h, z);
            double sek = (System.nanoTime() - t0) / 1e9;
            double a = h.albedoWirksam();
            double[] ar = h.albedoWirksamRgb(), aw = h.albedoWasser(), fk = new double[3];
            for (int k = 0; k < 3; k++) fk[k] = (ar[k] - Himmel.ALB_WASSER) / (Himmel.ALB_LAND - Himmel.ALB_WASSER);
            float[] karte = h.mehrfachstreuung().lies();
            float[] jw = Scattering.jBild(com.dan.core.szene.Streuordnungen.kanaele(Atmosphere.forVisibility(h.sichtKm, Himmel.ALB_WASSER)), 0);
            float[] jl = Scattering.jBild(com.dan.core.szene.Streuordnungen.kanaele(Atmosphere.forVisibility(h.sichtKm, Himmel.ALB_LAND)), 0);
            double[] top = new double[3];
            for (int i = 0; i < jl.length / 4; i++) for (int k = 0; k < 3; k++) top[k] = Math.max(top[k], Math.abs(jl[i * 4 + k]));
            double wm = 0;
            for (int i = 0; i < jl.length / 4; i++)
                for (int k = 0; k < 3; k++) {
                    float hw = Float.float16ToFloat(Float.floatToFloat16(jw[i * 4 + k])), hl = Float.float16ToFloat(Float.floatToFloat16(jl[i * 4 + k]));
                    wm = Math.max(wm, Math.abs(karte[i * 4 + k] - (hw + (hl - hw) * fk[k])) / top[k]);
                }
            A.info("Mischung", f("Wasser (F105 A, Schwebstoff 30 g/m³, Wind 8 m/s, Sonne wie im Bild) R %.3f G %.3f B %.3f; Anteil Wasser 0,60 → Albedo R %.3f G %.3f B %.3f (nach Y %.3f), Anteil des Lands je Kanal %.2f / %.2f / %.2f; erstes Bild %.1f s (mit den Tabellen für Wasser und Land, beim ersten Mal gerechnet und abgelegt)",
                    aw[0], aw[1], aw[2], ar[0], ar[1], ar[2], a, fk[0], fk[1], fk[2], sek));
            A.pruefe(wm <= 2e-3 && Math.abs(h.luft().groundAlbedo - a) < 1e-12, "J der drei Kanäle auf der Karte gegen die Mischung in Java je Kanal (float16), bezogen auf den größten Wert je Kanal; die Luft der Werte mit der Albedo nach Y",
                    f("± %.2e; Luft %.3f", wm, h.luft().groundAlbedo), "≤ 2e-3 [G]");
            int m0 = h.mischungen, a0 = h.ansichtGerechnet;
            h.wasserAnteil = 0.61;                                         // je Kanal 0,01 (a_w − 0,23) anders: unter dem Schritt
            bild(g, h, z);
            int m1 = h.mischungen;
            h.wasserAnteil = 0.65;                                         // 0,05 (a_w − 0,23) anders: neu mischen, Ansicht verteilt
            bild(g, h, z);
            int m2 = h.mischungen, a2 = h.ansichtGerechnet;
            for (int i = 1; i < Himmel.GRUPPEN; i++) bild(g, h, z);
            int a3 = h.ansichtGerechnet;
            A.pruefe(m1 == m0 && m2 == m0 + 1 && a2 == a0 && a3 == a0 + 1,
                    "Neu gemischt erst ab 0,003 Änderung der Albedo [G]; danach die Ansicht über fünf Bilder verteilt, nicht in einem Zug",
                    f("Mischungen %d / %d / %d, Ansichten %d / %d / %d", m0, m1, m2, a0, a2, a3), "gleich / +1; gleich / gleich / +1");
        }
    }

    /** Wahre Höhe der Sonne (Grad) über der Hanswarft. */
    static double wahr(Instant t) { return com.dan.core.szene.Lichtquellen.bei(t, OST, NORD, 32).sonneHoehe(); }

    /** Java: Sonne spektral, Mond in drei Kanälen, Nachthimmel – wie die Himmelsansicht. */
    static double[] java(Scattering sc, double eye, double[] d, double[] s, double[] sE, double[] m, double[] mE, double zenit) {
        double[] sp = sc.radianceSpektral(eye, d, s, sE[0] / Himmel.SONNE_LX, Scattering.VIEW_STEPS), mo = sc.radiance(eye, d, s, new double[3], m, mE, Scattering.VIEW_STEPS);
        double[] n = nacht(sc, eye, d, zenit);
        return new double[] {sp[0] + mo[0] + n[0], sp[1] + mo[1] + n[1], sp[2] + mo[2] + n[2]};
    }

    /** Wie {@code ansichtRichtung} in luft.glsl. */
    static double[] ansichtRichtung(double u, double v, double h) {
        double r = Atmosphere.BOTTOM + h, vh = Math.sqrt(h * (2 * Atmosphere.BOTTOM + h));
        double beta = Math.acos(Math.max(-1, Math.min(1, vh / r))), zh = Math.PI - beta, zen;
        if (v < 0.5) {
            double c = 1 - 2 * v;
            zen = zh * (1 - c * c);
        } else {
            double c = v * 2 - 1;
            zen = zh + beta * c * c;
        }
        double az = u * 2 * Math.PI;
        return new double[] {Math.sin(zen) * Math.sin(az), Math.cos(zen), -Math.sin(zen) * Math.cos(az)};
    }

    static double[] ansichtUv(double[] d, double h) {
        double r = Atmosphere.BOTTOM + h, vh = Math.sqrt(h * (2 * Atmosphere.BOTTOM + h));
        double beta = Math.acos(Math.max(-1, Math.min(1, vh / r))), zh = Math.PI - beta;
        double zen = Math.acos(Math.max(-1, Math.min(1, d[1]))), v;
        if (zen < zh) v = (1 - Math.sqrt(Math.max(0, 1 - zen / zh))) * 0.5;
        else v = Math.sqrt(Math.max(0, (zen - zh) / beta)) * 0.5 + 0.5;
        double u = Math.atan2(d[0], -d[2]) / (2 * Math.PI);
        return new double[] {u - Math.floor(u), v};
    }

    /** Nachthimmel wie ansicht.comp. */
    static double[] nacht(Scattering sc, double eye, double[] d, double zenit) {
        double y = Math.max(0, d[1]), k = Math.pow(Atmosphere.BOTTOM / (Atmosphere.BOTTOM + 90000.0), 2), vr = 1 / Math.sqrt(1 - k * (1 - y * y));
        double[] t = sc.transmittance(Atmosphere.BOTTOM + eye, y);
        return new double[] {zenit * 0.86 * vr * t[0], zenit * 1.02 * vr * t[1], zenit * 1.04 * vr * t[2]};
    }

    /** Die Himmelsansicht der Karte bilinear gelesen, wie {@code ansicht()} in luft.glsl (die Tabelle selbst prüft Teil 2). */
    static double[] himmelOhneScheibe(Scattering sc, double eye, double[] d, float[] w, float[] gv) {
        int AW = Himmel.ANSICHT_W, AH = Himmel.ANSICHT_H;
        double[] uv = ansichtUv(d, eye);
        double px = uv[0] * AW - 0.5, py = Math.max(0, Math.min(AH - 1, uv[1] * AH - 0.5));
        int ix = (int) Math.floor(px), iy = Math.min((int) Math.floor(py), AH - 2);
        double fx = px - ix, fy = py - iy;
        int x0 = Math.floorMod(ix, AW), x1 = (x0 + 1) % AW;
        double[] o = new double[3];
        for (int k = 0; k < 3; k++) {
            double a = gv[(iy * AW + x0) * 4 + k], b = gv[(iy * AW + x1) * 4 + k], c = gv[((iy + 1) * AW + x0) * 4 + k], e = gv[((iy + 1) * AW + x1) * 4 + k];
            o[k] = (a + (b - a) * fx) * (1 - fy) + (c + (e - c) * fx) * fy;
        }
        return o;
    }

    /** Himmel je Bildpunkt wie himmel.comp, ohne Scheiben (die Prüfung zeigt den Blick nicht auf Sonne oder Mond). */
    static double[] himmelCpu(Scattering sc, Himmel h, double eye, double[] d, float[] w, float[] gv) {
        double[] cs = vec(w, 32), cmo = vec(w, 40);
        double ds = d[0] * cs[0] + d[1] * cs[1] + d[2] * cs[2], dm = d[0] * cmo[0] + d[1] * cmo[1] + d[2] * cmo[2];
        if (ds > w[35] - 1e-4 || dm > w[43] - 1e-4) throw new IllegalStateException("AirCheck: Sonne oder Mond im Bild von Teil 3");
        return himmelOhneScheibe(sc, eye, d, w, gv);
    }
}
