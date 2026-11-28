package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Seekrank;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Bordkamera;
import com.dan.lauf.Buehne;
import com.dan.lauf.Flaechenwart;
import com.dan.lauf.Flotte;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.auftrieb.Bugschlag;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Vorgaben;
import com.dan.physics.auftrieb.Welle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung S7c (Seegang Werkbuch 89; seit S7d2, Datei 90: Auge frei, an Bord der Fähre, Kentern): die Kamera an Bord.
 * <ol>
 * <li><b>Bugschlag und W_f</b> (Prüfstein 22, ohne Grafikkarte): der Krabbenkutter gegen die See – in ruhigem Wasser und
 * in kleinen Wellen kein Schlag, in steilen Wellen (λ rund 1,2 L) Schläge, jeder über der Schwelle nach Ochi; dazu W_f
 * digital gegen analog.</li>
 * <li><b>An Bord</b> (Prüfsteine 20, 21, 23, 24): die Bühne ohne Fenster, Ansicht {@code bord_kutter} in drei Szenarien,
 * je 60 s Bild für Bild: das Auge sitzt in jedem Bild genau am gezeichneten Rumpf (bitgleich); der Rest des Rollens im
 * Bild gegen das Modell des ruhigen Blicks; MSDV_z und der Anteil, der sich erbricht (Auskunft, auf 2 h); im Seegang
 * 30 s als Video ({@code docs/status/S7c_bord_seegang.mp4} mit ffmpeg, sonst Bilder in {@code docs/status/S7c_video}).</li>
 * </ol>
 * <pre>  BordkameraCheck --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--teil n]</pre>
 */
public final class BordkameraCheck {
    static final Attest A = new Attest("Kamera an Bord (S7c)", Bordkamera.VERSION);

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
        System.out.println("BordkameraCheck – S7c, S7d2 (Seegang Werkbuch 89, 90)");
        long t0 = System.nanoTime();
        try {
            if (nur.isEmpty() || nur.contains("1")) SchwimmerCheck.zeit("1", BordkameraCheck::bugschlag);
            if (nur.isEmpty() || nur.contains("2")) {
                if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
                File sz = szenen;
                SchwimmerCheck.zeit("2", () -> anBord(sz, breite, hoehe));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer der Prüfung", f("%.1f s", (System.nanoTime() - t0) * 1e-9));
        Path pa = A.schreibe(Path.of("docs", "status", "S7c_Bordkamera.md"), "S7c – Kamera an Bord (Bordkamera " + Bordkamera.VERSION + ", Seekrank " + Seekrank.VERSION + ")");
        if (pa != null) System.out.println("\nAttest: " + pa);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1

    /** Der Kutter 60 s gegen eine Welle (a = 0: ruhig); Zahl der Schläge, größte Geschwindigkeit beim Eintauchen, ob er austauchte. */
    static double[] gegenSee(double a, double periode) {
        Welle w = a > 0 ? Welle.regelmaessig(a, periode, 180, 20) : Welle.ruhig(20);
        w.anlauf = 10;
        Koerper k = Vorgaben.krabbenkutter();
        k.einschwimmen(w, 0, 0, 0, 0, 0, 0);
        Bugschlag b = new Bugschlag(k);
        double t = 0, dt = 1.0 / 30, vMin = Double.POSITIVE_INFINITY;
        int aus = 0;
        for (int i = 0; i < 1800; i++) {
            k.schritt(w, dt);
            t += dt;
            if (b.pruefe(k, w, t)) vMin = Math.min(vMin, b.letzteV);
            if (b.aus) aus++;
        }
        return new double[] {b.zahl, b.groessteV, aus, vMin, b.schwelle};
    }

    static void bugschlag() {
        A.teil("1 Bugschlag nach Ochi (Prüfstein 22) und W_f");
        double[] r0 = gegenSee(0, 3.5), r1 = gegenSee(0.05, 3.5), r2 = gegenSee(0.8, 3.5);
        A.info("Krabbenkutter, Schwelle 0,093 √(g L)", f("%.2f m/s", r2[4]));
        A.info("ruhiges Wasser", f("%.0f Schläge, %.0f Bilder mit dem Bug über Wasser", r0[0], r0[2]));
        A.info("a 0,05 m, T 3,5 s gegenan", f("%.0f Schläge, %.0f Bilder mit dem Bug über Wasser, größte Geschwindigkeit beim Eintauchen %.2f m/s", r1[0], r1[2], r1[1]));
        A.info("a 0,8 m, T 3,5 s gegenan (λ 19 m)", f("%.0f Schläge, %.0f Bilder mit dem Bug über Wasser, größte Geschwindigkeit beim Eintauchen %.2f m/s, kleinste bei einem Schlag %.2f m/s",
                r2[0], r2[2], r2[1], r2[3]));
        A.pruefe(r0[0] == 0 && r1[0] == 0 && r2[0] >= 1 && r2[3] >= r2[4], "Bugschlag nur nach dem Austauchen und über der Schwelle",
                f("ruhig %.0f, klein %.0f, steil %.0f (jeder ≥ %.2f m/s)", r0[0], r1[0], r2[0], r2[4]), "0; 0; ≥ 1, jeder über der Schwelle");
        // W_f digital (30 Bilder je s) gegen analog
        double d = 0;
        for (double fr : new double[] {0.08, 0.1, 0.16, 0.25, 0.4, 0.63}) {
            Seekrank s = new Seekrank(1.0 / 30);
            double mx = 0;
            int n = (int) (400 * 30);
            for (int i = 0; i < n; i++) { double y = s.schritt(Math.sin(2 * Math.PI * fr * i / 30.0)); if (i > n / 2) mx = Math.max(mx, Math.abs(y)); }
            d = Math.max(d, Math.abs(mx / Seekrank.betrag(fr) - 1));
        }
        A.pruefe(d <= 0.01 && Math.abs(Seekrank.betrag(0.1) - 0.695) <= 0.005, "W_f (ISO 2631-1) digital gegen analog; bei 0,1 Hz",
                f("größte Abweichung %.2f %% (0,08–0,63 Hz); |W_f(0,1 Hz)| = %.3f (Tafel 0,695)", 100 * d, Seekrank.betrag(0.1)), "± 1 % [G]; ± 0,005");
        augeFrei();
    }

    /**
     * Seit S7d2 (Befund L5d 5): das Auge an Bord liegt frei, nicht in einem Bauteil des Netzes und mindestens 0,3 m davon
     * entfernt [G]. Innen oder außen: Zahl der Schnitte dreier Strahlen mit den Dreiecken (ungerade: innen; zwei von drei).
     */
    static void augeFrei() {
        for (String art : new String[] {"krabbenkutter", "faehre"}) {
            Koerper k = Flotte.vorgabe(art);
            com.dan.physics.auftrieb.Schiffbau.Netz n = com.dan.physics.auftrieb.Schiffbau.fuer(k, null);
            double[] e = Bordkamera.vorgabe(art, k);
            float[] v = n.scheitel;
            int F = com.dan.physics.auftrieb.Schiffbau.FLOATS;
            double[][] strahlen = {{0, 0, 1}, {0.6, 0.3, 0.74}, {-0.5, 0.7, 0.5}};
            int innen = 0;
            for (double[] r : strahlen) {
                int schnitte = 0;
                for (int i = 0; i + 3 * F <= v.length; i += 3 * F)
                    if (schnitt(e, r, v, i, i + F, i + 2 * F)) schnitte++;
                if (schnitte % 2 == 1) innen++;
            }
            double dMin = Double.POSITIVE_INFINITY;
            for (int i = 0; i + 3 * F <= v.length; i += 3 * F) dMin = Math.min(dMin, abstand(e, v, i, i + F, i + 2 * F));
            A.pruefe(innen < 2 && dMin >= 0.3, "Auge an Bord frei (seit S7d2): " + art,
                    f("Auge %.1f / %.1f / %.2f m im Körper; %s, nächstes Bauteil %.2f m", e[0], e[1], e[2], innen >= 2 ? "IN einem Bauteil" : "außen", dMin), "außen, ≥ 0,3 m [G]");
        }
    }

    /** Schneidet der Strahl von e in Richtung r das Dreieck (Möller–Trumbore)? */
    static boolean schnitt(double[] e, double[] r, float[] v, int a, int b, int c) {
        double e1x = v[b] - v[a], e1y = v[b + 1] - v[a + 1], e1z = v[b + 2] - v[a + 2];
        double e2x = v[c] - v[a], e2y = v[c + 1] - v[a + 1], e2z = v[c + 2] - v[a + 2];
        double px = r[1] * e2z - r[2] * e2y, py = r[2] * e2x - r[0] * e2z, pz = r[0] * e2y - r[1] * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return false;
        double tx = e[0] - v[a], ty = e[1] - v[a + 1], tz = e[2] - v[a + 2];
        double u = (tx * px + ty * py + tz * pz) / det;
        if (u < 0 || u > 1) return false;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double w = (r[0] * qx + r[1] * qy + r[2] * qz) / det;
        if (w < 0 || u + w > 1) return false;
        return (e2x * qx + e2y * qy + e2z * qz) / det > 1e-9;
    }

    /** Abstand des Punkts e vom Dreieck (nächster Punkt nach Ericson, Real-Time Collision Detection, 5.1.5). */
    static double abstand(double[] e, float[] v, int ia, int ib, int ic) {
        double[] a = {v[ia], v[ia + 1], v[ia + 2]}, b = {v[ib], v[ib + 1], v[ib + 2]}, c = {v[ic], v[ic + 1], v[ic + 2]};
        double[] ab = sub(b, a), ac = sub(c, a), ap = sub(e, a);
        double d1 = dot(ab, ap), d2 = dot(ac, ap);
        if (d1 <= 0 && d2 <= 0) return len(ap);
        double[] bp = sub(e, b);
        double d3 = dot(ab, bp), d4 = dot(ac, bp);
        if (d3 >= 0 && d4 <= d3) return len(bp);
        double vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) { double t = d1 / (d1 - d3); return len(sub(e, add(a, mul(ab, t)))); }
        double[] cp = sub(e, c);
        double d5 = dot(ab, cp), d6 = dot(ac, cp);
        if (d6 >= 0 && d5 <= d6) return len(cp);
        double vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) { double t = d2 / (d2 - d6); return len(sub(e, add(a, mul(ac, t)))); }
        double va = d3 * d6 - d5 * d4;
        if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) { double t = (d4 - d3) / ((d4 - d3) + (d5 - d6)); return len(sub(e, add(b, mul(sub(c, b), t)))); }
        double den = 1 / (va + vb + vc), vv = vb * den, ww = vc * den;
        return len(sub(e, add(a, add(mul(ab, vv), mul(ac, ww)))));
    }

    static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] mul(double[] a, double s) { return new double[] {a[0] * s, a[1] * s, a[2] * s}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }

    // ------------------------------------------------------------------ 2

    static void anBord(File szenen, int breite, int hoehe) {
        A.teil("2 An Bord des Kutters (Prüfsteine 20, 21, 23, 24)");
        try {
            Szenen sz = Szenen.lies(szenen);
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            try {
                Flaechenwart w = b.wart();
                String[][] faelle = {
                        {"seegang", "2026-10-25T13:30:00Z"},
                        {"ruhigsee", "2026-10-30T11:30:00Z"},
                        {"xaver", "2013-12-05T12:30:00Z"}};
                boolean bitgleich = true, video = false;
                int bilderVideo = 0;
                for (String[] fa : faelle) {
                    Szenario s = sz.szenario(fa[0]);
                    SchwimmerCheck.pegel(b, sz);                                   // seit S7d2
                    b.oeffne(s);
                    b.wasserbild().seegang = true;
                    Flotte fl = b.flotte();
                    Ansicht an = sz.ansicht("bord_kutter");
                    Bordkamera bk = b.bord(an);
                    if (bk == null) throw new IllegalStateException("keine Bordkamera auf „" + an.bord + "“ in " + fa[0]);
                    double t = (Instant.parse(fa[1]).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                    Blick bl0 = new Blick();
                    an.setze(bl0, b.gelaende, b.utm(), b.uhr().bei(t));
                    bl0.groesse(breite, hoehe);
                    b.standbild(bl0, t);
                    fl.neuSetzen();
                    double dt = 1.0 / 30;
                    long uhrNs = 0;
                    int bereit = -1, gemessen = 0;
                    List<Double> roll = new ArrayList<>(), bildRoll = new ArrayList<>(), hub = new ArrayList<>(), az = new ArrayList<>();
                    Path ordner = Path.of("docs", "status", "S7c_video");
                    boolean mitVideo = fa[0].equals("seegang");
                    if (mitVideo) Files.createDirectories(ordner);
                    Seekrank sk = null;
                    int schlag0 = 0;
                    long w0 = System.nanoTime();
                    for (int i = 0; i < 6000; i++) {
                        w.zeitraffer(1);
                        b.flotteBild(t, 1, dt);
                        Blick bl = bk.weiter(dt, t, bl0);
                        if (bl == null) bl = bl0;
                        bl.groesse(breite, hoehe);
                        w.vorBild(bl, t, uhrNs);
                        b.bildzeiten(bl, t, 1, false);
                        w.nachBild();
                        if (bereit < 0 && w.bereit() && !w.wechselt()) bereit = i;
                        if (bereit >= 0 && i > bereit + 90) {
                            if (sk == null) { sk = new Seekrank(dt); schlag0 = fl.schwimmer.get(bk.nr).bug.zahl; }
                            // Prüfstein 20: das Auge gegen den gezeichneten Rumpf
                            double[] l = b.schwimmerbild().lagen();
                            double[] p = Bordkamera.augeAus(l, bk.nr, bk.auge);
                            for (int c = 0; c < 3; c++) if (Double.doubleToRawLongBits(p[c]) != Double.doubleToRawLongBits(bk.augeWelt[c])) bitgleich = false;
                            roll.add(bk.kraengung);
                            bildRoll.add(bk.rollenBild);
                            if (!Double.isNaN(bk.az)) { sk.schritt(bk.az); az.add(bk.az); }
                            hub.add(bk.augeWelt[2]);
                            if (mitVideo && gemessen < 900) {
                                javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "jpg", ordner.resolve(String.format("bild_%04d.jpg", gemessen)).toFile());
                                bilderVideo = gemessen + 1;
                            }
                            gemessen++;
                            if (gemessen >= 1800) break;
                        }
                        t += dt;
                        uhrNs += Math.round(dt * 1e9);
                        if ((System.nanoTime() - w0) / 1e9 > 300) break;
                    }
                    fl.warte();
                    // Prüfstein 21: Rest des Rollens gegen das Modell bei der Frequenz des Rollens
                    double[] rr = mittelfrei(roll), rb = mittelfrei(bildRoll);
                    double om = 2 * Math.PI / periode(rr, dt);
                    double rest = rms(rb) / rms(rr), soll = Bordkamera.restRollen(om);
                    A.pruefe(gemessen > 300 && Math.abs(rest - soll) <= 0.05, fa[0] + ": Rollen der Kimm gegen das Rollen des Rumpfs",
                            f("%.1f %% (Modell %.1f %% bei T = %.1f s; Rumpf RMS %.2f°, %d Bilder)", 100 * rest, 100 * soll, 2 * Math.PI / om, rms(rr), gemessen), "Modell ± 5 Punkte [G]");
                    if (sk != null) {
                        double m2 = sk.msdv(7200);
                        A.info(fa[0] + ": seekrank an Bord (Prüfstein 23)", f("MSDV_z %.2f m/s^1,5 in %.0f s, auf 2 h %.1f m/s^1,5: rund %.0f %% erbrechen sich (K_m 1/3); Bugschläge %d",
                                sk.msdv(), sk.zeit(), m2, Seekrank.erbrechen(m2), fl.schwimmer.get(bk.nr).bug.zahl - schlag0));
                        // seit S7d2 (Befund L5d: seegang und xaver fast gleich): Hub und Beschleunigung am Auge, ungewichtet und gewichtet
                        A.info(fa[0] + ": am Auge", f("Hub RMS %.3f m, a_z RMS %.3f m/s², a_w RMS %.3f m/s² (W_f)", rms(mittelfrei(hub)), rms(mittelfrei(az)), sk.msdv() / Math.sqrt(Math.max(1e-9, sk.zeit()))));
                    }
                    Flotte.Schwimmer kut = fl.schwimmer.get(bk.nr);
                    A.pruefe(kut.kentern == null, fa[0] + ": Kutter nicht gekentert (seit S7d2)", f("größte Krängung %.1f°%s", kut.kraengungMax, kut.kentern != null ? "; " + kut.kentern : ""), "≤ " + (int) Flotte.KENTERN + "° [G]");
                    if (mitVideo && bilderVideo > 0) {
                        Path mp4 = Path.of("docs", "status", "S7c_bord_seegang.mp4");
                        video = ffmpeg(ordner, mp4);
                        A.info(fa[0] + ": Video (Prüfstein 24, Sichtprüfung)", video ? f("docs/status/S7c_bord_seegang.mp4 (%d Bilder, 30 je s)", bilderVideo)
                                : f("ffmpeg nicht gefunden: %d Bilder in docs/status/S7c_video", bilderVideo));
                    }
                    javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", "S7c_bord_" + fa[0] + ".png").toFile());
                }
                // seit S7d2: an Bord der Hilligenlei, wenn sie nach der Tide kommt (ruhigsee, Ankunft rund 12:45 UTC): ein Bild
                bitgleich &= faehreAnBord(b, w, sz, breite, hoehe);
                A.pruefe(bitgleich, "Auge fest am gezeichneten Rumpf, in jedem Bild (Prüfstein 20)", bitgleich ? "bitgleich" : "ANDERS", "bitgleich");
            } finally {
                b.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** An Bord der Fähre in ruhigsee ab 12:42 UTC (sie kommt), 600 Bilder; Bild S7c_bord_faehre.png. Gibt zurück, ob das Auge bitgleich am Rumpf saß. */
    static boolean faehreAnBord(Buehne b, Flaechenwart w, Szenen sz, int breite, int hoehe) throws Exception {
        Szenario s = sz.szenario("ruhigsee");
        SchwimmerCheck.pegel(b, sz);
        b.oeffne(s);
        b.wasserbild().seegang = true;
        Flotte fl = b.flotte();
        Ansicht an = sz.ansicht("bord_faehre");
        Bordkamera bk = b.bord(an);
        if (bk == null) throw new IllegalStateException("keine Bordkamera auf „" + an.bord + "“");
        double t = (Instant.parse("2026-10-30T12:42:00Z").toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
        Blick bl0 = new Blick();
        an.setze(bl0, b.gelaende, b.utm(), b.uhr().bei(t));
        bl0.groesse(breite, hoehe);
        b.standbild(bl0, t);
        fl.neuSetzen();
        double dt = 1.0 / 30;
        long uhrNs = 0, w0 = System.nanoTime();
        int bereit = -1, gemessen = 0;
        boolean gleich = true;
        for (int i = 0; i < 4000; i++) {
            w.zeitraffer(1);
            b.flotteBild(t, 1, dt);
            Blick bl = bk.weiter(dt, t, bl0);
            if (bl == null) bl = bl0;
            bl.groesse(breite, hoehe);
            w.vorBild(bl, t, uhrNs);
            b.bildzeiten(bl, t, 1, false);
            w.nachBild();
            if (bereit < 0 && w.bereit() && !w.wechselt()) bereit = i;
            if (bereit >= 0 && i > bereit + 90) {
                double[] p = Bordkamera.augeAus(b.schwimmerbild().lagen(), bk.nr, bk.auge);
                for (int c = 0; c < 3; c++) if (Double.doubleToRawLongBits(p[c]) != Double.doubleToRawLongBits(bk.augeWelt[c])) gleich = false;
                if (++gemessen >= 600) break;
            }
            t += dt;
            uhrNs += Math.round(dt * 1e9);
            if ((System.nanoTime() - w0) / 1e9 > 180) break;
        }
        fl.warte();
        javax.imageio.ImageIO.write(SeegangBilder.bild(b.letztesBild(), breite, hoehe), "png", Path.of("docs", "status", "S7c_bord_faehre.png").toFile());
        Flotte.Schwimmer fae = fl.schwimmer.get(bk.nr);
        double[] v = fae.koerper.geschwindigkeit();
        A.info("An Bord der Hilligenlei (ruhigsee, ab 12:42 UTC)", f("Bild docs/status/S7c_bord_faehre.png nach %d Bildern; Phase „%s“, Fahrt %.1f m/s, Kurs %.0f°; Auge %.1f / %.1f / %.2f m im Körper",
                gemessen, fae.phase(), Math.hypot(v[0], v[1]), fae.koerper.kurs(), bk.auge[0], bk.auge[1], bk.auge[2]));
        A.pruefe(fae.kentern == null, "ruhigsee: Fähre nicht gekentert (seit S7d2)", f("größte Krängung %.1f°", fae.kraengungMax), "≤ " + (int) Flotte.KENTERN + "° [G]");
        return gleich && gemessen > 0;
    }

    static double[] mittelfrei(List<Double> l) {
        double m = 0;
        for (double x : l) m += x / l.size();
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i) - m;
        return a;
    }

    static double rms(double[] a) {
        double s = 0;
        for (double x : a) s += x * x;
        return Math.sqrt(s / Math.max(1, a.length));
    }

    /** Mittlere Periode aus den steigenden Nulldurchgängen (s). */
    static double periode(double[] a, double dt) {
        int n = 0, erst = -1, letzt = -1;
        for (int i = 1; i < a.length; i++) if (a[i - 1] < 0 && a[i] >= 0) { if (erst < 0) erst = i; letzt = i; n++; }
        return n > 1 ? (letzt - erst) * dt / (n - 1) : 5.3;
    }

    /** Die Bilder eines Ordners mit ffmpeg zu MP4 (H.264, veryfast); false ohne ffmpeg. */
    static boolean ffmpeg(Path ordner, Path ziel) {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-framerate", "30", "-i", ordner.resolve("bild_%04d.jpg").toString(),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", ziel.toString()).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0 && Files.size(ziel) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
