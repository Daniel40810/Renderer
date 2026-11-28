package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Auflauf;
import com.dan.lauf.Buehne;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.swash.Swash;
import com.dan.render.seegang.SeegangKarte;

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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * S6d-2 Flutkante (Seegang Werkbuch 84, SF58 A): das Rechteck in der Form für das Watt ({@code Auflauf} 0.11) am Japsand
 * bei auflaufendem Wasser, im Szenario {@code ruhigsee}.
 * <ol>
 * <li>Suche: Stände bei Tag (8–13 UTC) mit steigendem Pegel (am tiefsten Ort im Umkreis von 3 km), je Stand Strahlen vom
 * Japsand aus bis 3 km; wo nasses Wasser in trockenes Watt übergeht (Grund über ± 40 m flacher als 1:250), das Auge 100 m
 * davor über dem Wasser. Das erste Rechteck in der Form für das Watt gilt.</li>
 * <li>Der Fall wie in {@link AuflaufCheck} ({@value #DAUER_FALL} s, Pegel fest): Bilanz, Bild bitgleich, Kosten,
 * seeseitiger Rand nass, landseitige Spalte trocken.</li>
 * <li>Die Front ({@value #DAUER_FRONT} s, der Spiegel der Hydro steigt mit der Zeit – das Bild der Bühne alle
 * {@value #PEGEL_JE} s): je {@value #MESS_JE} s die nasse Fläche des Lösers (Zellen über der Trockengrenze, mit der
 * seeseitigen Kante verbunden) und die der Hydro (Grund unter ihrem Spiegel, ebenso verbunden). Seit L4x die Fläche statt
 * des Medians der Zeilen (der sprang zwischen Zeilen und stand an der Landseite an): die mittlere Geschwindigkeit der
 * Front längs des Rechtecks ist dA/dt durch die Länge. Ausgleichsgerade ab {@value #FIT_AB} s, solange beide unter 90 %
 * des Rechtecks liegen, ± 20 %. Bilanz.</li>
 * <li>Neu setzen, wenn die Front das Rechteck verlässt: Auskunft eine und zwei Stunden später.</li>
 * <li>Bilder der Anwendung (Sichtprüfung): {@code docs/status/seegang/S6d2_*.png}, zusammen {@code docs/status/S6d2_Bild.png}.</li>
 * </ol>
 * Attest {@code docs/status/S6d2_Flutkante.md}.
 */
public final class FlutkanteCheck {
    private FlutkanteCheck() { }

    /** Seit L4w: ruhigsee (p8c) – das Nahfeld braucht den Nachlauf des Seegangs (seegang_G3w), den ruhigsed (p9b) nicht hat. */
    static final String SZENARIO = "ruhigsee";
    static final int BREITE = 1920, HOEHE = 1080, BILDER_JE_S = 60;
    static final double DAUER_FALL = 120, DAUER_FRONT = 600, FIT_AB = 120, MESS_JE = 10, PEGEL_JE = 5;
    /** Grenzen [G, Datei 83/84]. */
    static final double FRONT_TOL = 0.20, BILANZ = 1e-4;
    /** Suche: Strahlen, Weite (m), Schritt (m), Auge vor der Front (m), flacher als, Höhe des Auges (m) [G]. */
    static final int STRAHLEN = 36, VERSUCHE = 30;
    static final double WEITE = 3000, SCHRITT = 5, VOR = 100, FLACH = 1.0 / 250, AUGE = 2;

    static final Attest A = new Attest("Seegang, Flutkante (S6d-2)", "Seegang Werkbuch");

    public static void main(String[] args) {
        File szenen = null;
        for (int i = 0; i + 1 < args.length; i += 2) if (args[i].equals("--szenen")) szenen = new File(args[i + 1]);
        System.out.println("FlutkanteCheck – S6d-2 (Seegang Werkbuch 84)");
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Ansicht an = sz.ansicht("japsand");
            Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE);
            SchaumCheck.b = b;
            try {
                Auflauf au = b.auflauf();
                A.pruefe(au != null, "Auflauf an der Bühne", au == null ? "keiner" : "ja", "vorhanden");
                if (au == null) return;
                A.info("Auflauf", f("Version %s; Form für das Watt: %d × %d Zellen zu %.0f m, seeseitiger Rand mindestens %.1f m tief, seewärts ab %.0f m, landwärts mindestens %.0f m, neu ab %.0f m Front vor der Landseite",
                        Auflauf.VERSION, Auflauf.NX, Auflauf.NY, Auflauf.DX_WATT, Auflauf.TIEFE_MIN_WATT, Auflauf.SEE_MIN_WATT, Auflauf.LAND_MIN_WATT, Auflauf.FRONT_RAND));
                Szenario sc = SchaumCheck.szenario(sz, SZENARIO);
                b.oeffne(sc);
                b.wasserbild().seegang = true;
                b.brandung(true);
                A.teil("1 Suche");
                double[] wahl = suche(b, au, sc, an);
                A.pruefe(wahl != null, "Rechteck in der Form für das Watt gesetzt (Japsand, auflaufendes Wasser, bei Tag)", wahl == null ? "keins" : au.bericht, "ja");
                if (wahl == null) return;
                double t0 = wahl[0];
                Blick bl = new Blick(wahl[1], wahl[2], wahl[3], wahl[4], -8, 60).groesse(BREITE, HOEHE);
                A.info("Blick", f("%s: Auge (%.0f, %.0f) %.1f m NHN, Richtung %.0f°; Pegel steigt am Bezugsort mit %.2f m/h", zeit(b, t0), bl.ost, bl.nord, bl.hoehe, bl.richtung, wahl[5]));
                A.teil("2 Fall (Pegel fest)");
                AuflaufCheck.fall(A, b, au, sz, sc, bl, t0, DAUER_FALL, true, "Watt");
                A.pruefe(au.watt(), "nach dem Fall noch die Form für das Watt", au.watt() ? "ja" : au.bericht, "ja");
                A.teil("3 Front (Pegel steigt)");
                double tEnde = front(b, au, sz, sc, bl, t0);
                A.teil("4 Neu setzen");
                neu(b, au, bl, t0);
                A.teil("5 Bilder");
                bilder(b, au, bl, t0, an);
            } finally {
                b.close();
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit der Prüfung", f("%.0f s", (System.nanoTime() - start) / 1e9));
        Path p = A.schreibe(Path.of("docs", "status", "S6d2_Flutkante.md"), "S6d-2 – Flutkante am Watt");
        System.out.println();
        System.out.println("Attest: " + p.toAbsolutePath());
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static String zeit(Buehne b, double t) { return b.uhr().start.plusMillis(Math.round(t * 1000)).toString().replace("T", " ").replace(":00Z", " UTC"); }

    /** {Zeit der Bühne (s), Auge Ost, Nord, Höhe, Richtung, Steigen (m/h)} oder null. */
    static double[] suche(Buehne b, Auflauf au, Szenario sc, Ansicht an) throws Exception {
        List<double[]> kand = new ArrayList<>();
        try (Archivquelle q = new Archivquelle(sc.archiv.toPath())) {
            int k3 = SeegangCheck.index(q.gitter(), "G3");
            Gitter g = q.gitter().get(k3);
            double[] zs = q.zeiten();
            float[] eta = new float[g.zellen()], h = new float[g.zellen()], age = new float[g.zellen()];
            q.stand(zs[0], k3, eta, h, age);
            int ref = -1;
            for (int c = 0; c < g.zellen(); c++) {
                double x = g.x(c % g.nx), y = g.y(c / g.nx);
                if (Math.hypot(x - an.ost, y - an.nord) <= 3000 && (ref < 0 || h[c] > h[ref])) ref = c;
            }
            double[] pegel = new double[zs.length];
            for (int i = 0; i < zs.length; i++) { q.stand(zs[i], k3, eta, h, age); pegel[i] = eta[ref]; }
            A.info("Bezugsort des Pegels", f("Zelle bei (%.0f, %.0f), %.1f m tief beim ersten Stand; Pegel %.2f bis %.2f m NHN über %d Stände", g.x(ref % g.nx), g.y(ref / g.nx), h[ref], min(pegel), max(pegel), zs.length));
            for (int i = 1; i + 1 < zs.length; i++) {
                Instant ti = q.start.plusMillis(Math.round(zs[i] * 1000));
                double std = ti.atZone(ZoneOffset.UTC).getHour() + ti.atZone(ZoneOffset.UTC).getMinute() / 60.0;
                double r = (pegel[i + 1] - pegel[i - 1]) / ((zs[i + 1] - zs[i - 1]) / 3600);
                if (std >= 8 && std <= 13.5 && r > 0.1) kand.add(new double[] {(ti.toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0, r});
            }
        }
        kand.sort((x, y) -> Double.compare(y[1], x[1]));
        // seit L4v: die Zeiten verteilt (mindestens 30 min auseinander), sonst liegen alle Kandidaten in derselben Viertelstunde
        List<double[]> verteilt = new ArrayList<>();
        for (double[] c : kand) {
            boolean nah = false;
            for (double[] v : verteilt) nah |= Math.abs(v[0] - c[0]) < 1800;
            if (!nah) verteilt.add(c);
        }
        int alle = kand.size();
        kand = verteilt;
        A.info("Kandidaten (bei Tag, Pegel steigt über 0,1 m/h)", f("%d, davon %d mindestens 30 min auseinander; schnellste %s", alle, kand.size(), kand.isEmpty() ? "–" : f("%.2f m/h", kand.get(0)[1])));
        StringBuilder log = new StringBuilder();
        int versuche = 0;
        for (int kk = 0; kk < Math.min(6, kand.size()); kk++) {
            double t = kand.get(kk)[0];
            Blick probe = new Blick(an.ost, an.nord, 20, 90, -10, 60).groesse(BREITE, HOEHE);
            b.standbild(probe, t);
            List<double[]> orte = new ArrayList<>();
            for (int s = 0; s < STRAHLEN; s++) {
                double a = Math.toRadians(360.0 * s / STRAHLEN), ex = Math.sin(a), ey = Math.cos(a);
                boolean vorNass = false;
                for (double r = 0; r <= WEITE; r += SCHRITT) {
                    double o = an.ost + r * ex, n = an.nord + r * ey;
                    boolean nass = !Double.isNaN(b.wasserUndSeegang(o, n)[0]);
                    if (vorNass && !nass && r >= VOR) {
                        double za = b.gelaende.hoehe(o - 40 * ex, n - 40 * ey, 1), zb = b.gelaende.hoehe(o + 40 * ex, n + 40 * ey, 1);
                        double neig = Math.abs(zb - za) / 80;
                        double eo = o - VOR * ex, en = n - VOR * ey;
                        double we = b.wasserUndSeegang(eo, en)[0];
                        if (neig < FLACH && !Double.isNaN(we)) orte.add(new double[] {eo, en, we + AUGE, Math.toDegrees(a), neig});
                        break;
                    }
                    vorNass = nass;
                }
            }
            orte.sort((x, y) -> Double.compare(x[4], y[4]));
            for (double[] o : orte) {
                if (versuche >= VERSUCHE) break;
                versuche++;
                Blick bl = new Blick(o[0], o[1], o[2], o[3], -8, 60).groesse(BREITE, HOEHE);
                au.vergiss();
                boolean ok = au.vorBild(bl, t, 1, b.seegangsnetze());
                if (log.length() < 3000) log.append(f("%s, (%.0f, %.0f) %.0f°, 1:%.0f: %s<br>", zeit(b, t), o[0], o[1], o[3], 1 / Math.max(1e-6, o[4]), ok ? (au.watt() ? "Watt" : "Strand") : au.bericht));
                if (ok && au.watt()) {
                    A.info("Versuche bis zum Rechteck am Watt", log.toString());
                    return new double[] {t, o[0], o[1], o[2], o[3], kand.get(kk)[1]};
                }
            }
        }
        A.info("Versuche (ohne Rechteck am Watt)", log.length() == 0 ? "keine Front gefunden" : log.toString());
        return null;
    }

    static double min(double[] a) { double m = Double.POSITIVE_INFINITY; for (double v : a) m = Math.min(m, v); return m; }

    static double max(double[] a) { double m = Double.NEGATIVE_INFINITY; for (double v : a) m = Math.max(m, v); return m; }

    /** Die Front des Lösers: je Zeile die letzte nasse Zelle, die über nasse Zellen der Zeile mit der seeseitigen Kante verbunden ist (m quer); Median. */
    static double frontLoeser(Swash s) {
        double[] x = new double[s.ny];
        for (int j = 0; j < s.ny; j++) {
            int i = 0;
            while (i < s.nx && s.tiefe(i, j) > s.hTrocken) i++;
            x[j] = i * s.dx;
        }
        Arrays.sort(x);
        return x[s.ny / 2];
    }

    /** Teil 3: Front des Lösers gegen die der Hydro bei steigendem Pegel; gibt die Zeit am Ende. */
    static double front(Buehne b, Auflauf au, Szenen sz, Szenario sc, Blick bl, double t0) throws Exception {
        Geraet g = b.geraet();
        b.standbild(bl, t0);
        Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bl, t0, null);
        double t = t0;
        try {
            SeegangKarte k = sf.karte;
            k.bezug(Math.floor(bl.ost / 64) * 64, Math.floor(bl.nord / 64) * 64);
            k.fernOst = bl.ost;
            k.fernNord = bl.nord;
            au.vergiss();
            boolean ok = au.vorBild(bl, t0, 1, b.seegangsnetze());
            A.pruefe(ok && au.watt(), "Rechteck in der Form für das Watt neu gesetzt", ok ? (au.watt() ? "ja" : "Strand") : au.bericht, "ja");
            if (!ok || !au.watt()) return t0;
            Swash s = au.swash();
            double v0 = s.volumen(), ws0 = au.wasserstand();
            long gesetzt0 = au.gesetzt, verpasst0 = au.verpasst;
            int n = (int) Math.round(DAUER_FRONT * BILDER_JE_S), je = (int) Math.round(MESS_JE * BILDER_JE_S), pj = (int) Math.round(PEGEL_JE * BILDER_JE_S);
            List<double[]> reihe = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (int q = 0; q <= n; q++) {
                t = t0 + (double) q / BILDER_JE_S;
                if (q > 0 && q % pj == 0) b.standbild(bl, t);                 // der Spiegel der Hydro zur Zeit t (Auflauf.wasserstand)
                Ablauf ab = new Ablauf(g);
                k.zeit(ab, t);
                k.bild(ab, bl.ost, bl.nord);
                au.vorbereiten(ab, null, k, t);
                g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                if (q % je == 0) {
                    au.karte().runter();
                    double fl = frontLoeser(s), fh = au.frontHydro(s.ny), ws = au.wasserstand();
                    double al = flaeche(s, (i, j) -> s.tiefe(i, j) > s.hTrocken), ah = flaeche(s, (i, j) -> s.b[s.id(i, j)] < ws);
                    reihe.add(new double[] {t - t0, al * s.dx / s.ny, ah * s.dx / s.ny, ws, fl, fh, al, ah});
                    if (q % (6 * je) == 0) text.append(f("%s%.0f s: Spiegel %+.3f m, nass Löser %.0f %%, Hydro %.0f %%; Median der Zeilen Löser %.0f m, Hydro %.0f m",
                            text.length() == 0 ? "" : "; ", t - t0, ws - ws0, 100 * al / (s.nx * s.ny), 100 * ah / (s.nx * s.ny), fl, fh));
                }
            }
            A.pruefe(au.an, "Auflauf nach den Bildern noch an (kein Fehler)", au.an ? "ja" : au.bericht, "ja");
            A.pruefe(au.gesetzt == gesetzt0, "kein neues Setzen während der Flut", f("%d", au.gesetzt - gesetzt0), "0");
            A.info("Verlauf (je 60 s)", text.toString());
            double voll = 0.9 * s.nx * s.ny;
            List<double[]> fenster = new ArrayList<>();
            for (double[] r : reihe) if (r[0] >= FIT_AB && r[6] < voll && r[7] < voll) fenster.add(r);
            A.pruefe(fenster.size() >= 5, "Messpunkte, solange die Flut das Rechteck nicht füllt (ab " + (int) FIT_AB + " s, beide unter 90 %)", f("%d", fenster.size()), "≥ 5");
            if (fenster.size() < 5) return t;
            double[] vl = gerade(fenster, 1), vh = gerade(fenster, 2);
            double dws = reihe.get(reihe.size() - 1)[3] - ws0;
            A.info("Spiegel der Hydro am seeseitigen Rand", f("steigt um %.3f m in %.0f s (%.2f m/h)", dws, DAUER_FRONT, dws / DAUER_FRONT * 3600));
            boolean flut = vh[0] > 0.005;
            A.pruefe(flut, "die Front der Hydro läuft landwärts (mindestens 5 mm/s)", f("%.4f m/s", vh[0]), "≥ 0,005 m/s");
            double rel = vl[0] / vh[0] - 1;
            A.pruefe(flut && Math.abs(rel) <= FRONT_TOL, "Prüfstein: Geschwindigkeit der Front des Lösers gegen die der Hydro (nasse Fläche durch die Länge, Ausgleichsgerade)",
                    f("%.4f gegen %.4f m/s (%+.0f %%) über %.0f s; Streuung um die Geraden %.2f / %.2f m", vl[0], vh[0], 100 * rel, fenster.get(fenster.size() - 1)[0] - fenster.get(0)[0], vl[1], vh[1]), "± 20 %");
            au.karte().runter();
            double v = s.volumen(), z = s.zufluss(), vs = s.versickert(), ag = s.abgeschnitten();
            double rest = (v - v0 - z + vs - ag) / Math.max(v0, 1);
            A.info("Wasser", f("V₀ %.1f m³, V %.1f m³, Zufluss %.2f m³, versickert %.2f m³, abgeschnitten %.3g m³", v0, v, z, vs, ag));
            A.hoechstens("Bilanz des Wassers bei steigendem Pegel, relativ", rest, BILANZ, "");
            A.pruefe(au.verpasst == verpasst0, "kein Bild mit zu wenig Schritten", f("%d", au.verpasst - verpasst0), "0");
            AuflaufCheck.bildPruefen(A, au, s, "Flut");
        } finally {
            g.ruhe();
            sf.close();
        }
        return t;
    }

    /** Ausgleichsgerade der Spalte c über der Zeit (Spalte 0): {Steigung, Streuung}. */
    static double[] gerade(List<double[]> r, int c) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (double[] p : r) { sx += p[0]; sy += p[c]; sxx += p[0] * p[0]; sxy += p[0] * p[c]; n++; }
        double m = (n * sxy - sx * sy) / (n * sxx - sx * sx), a = (sy - m * sx) / n, e = 0;
        for (double[] p : r) e += (p[c] - a - m * p[0]) * (p[c] - a - m * p[0]);
        return new double[] {m, Math.sqrt(e / Math.max(1, n))};
    }

    interface Zelle { boolean ja(int i, int j); }

    /** Zahl der Zellen, für die z gilt und die über solche Zellen (4er-Nachbarn) mit der seeseitigen Kante (i = 0) verbunden sind. */
    static int flaeche(Swash s, Zelle z) {
        boolean[] in = new boolean[s.nx * s.ny];
        int[] stapel = new int[s.nx * s.ny];
        int n = 0, zahl = 0;
        for (int j = 0; j < s.ny; j++) if (z.ja(0, j)) { in[j * s.nx] = true; stapel[n++] = j * s.nx; }
        while (n > 0) {
            int c = stapel[--n], i = c % s.nx, j = c / s.nx;
            zahl++;
            for (int d = 0; d < 4; d++) {
                int a = i + (d == 0 ? 1 : d == 1 ? -1 : 0), b = j + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (a < 0 || a >= s.nx || b < 0 || b >= s.ny || in[b * s.nx + a] || !z.ja(a, b)) continue;
                in[b * s.nx + a] = true;
                stapel[n++] = b * s.nx + a;
            }
        }
        return zahl;
    }

    /** Teil 4: eine und zwei Stunden später – Front der Hydro, verlässt sie das Rechteck, setzt vorBild neu? */
    static void neu(Buehne b, Auflauf au, Blick bl, double t0) {
        StringBuilder sb = new StringBuilder();
        for (double dt : new double[] {1800, 3600, 7200}) {
            b.standbild(bl, t0 + dt);
            double fh = au.frontHydro(Auflauf.NY);
            boolean raus = au.frontRaus(), vorher = au.aktiv();
            long g0 = au.gesetzt;
            boolean ok = au.vorBild(bl, t0 + dt, 1, b.seegangsnetze());
            String was = !vorher ? "ohne Rechteck: " + (ok ? "gesetzt (" + (au.watt() ? "Watt" : "Strand") + ")" : "nicht gesetzt (" + au.bericht + ")")
                    : au.gesetzt > g0 ? "neu gesetzt (" + (au.watt() ? "Watt" : "Strand") + ")" : ok ? "behält das Rechteck" : "neu versucht, keins: " + au.bericht;
            sb.append(f("%s+%.1f h: Front der Hydro im alten Rechteck %s (Landseite %.0f m), verlassen %s; %s",
                    sb.length() == 0 ? "" : "<br>", dt / 3600, vorher ? f("%.0f m", fh) : "–", Auflauf.NX * Auflauf.DX_WATT, raus ? "ja" : "nein", was));
        }
        A.info("Neu setzen, wenn die Front das Rechteck verlässt (Auskunft)", sb.toString());
    }

    /** Teil 5: Bilder der Anwendung nach dem Lauf der Front. */
    static void bilder(Buehne b, Auflauf au, Blick bl, double t0, Ansicht an) throws Exception {
        // wieder zur Zeit t0 setzen und die Anwendung laufen lassen (Flaechenwart, Bild des Auflaufs)
        b.standbild(bl, t0);
        au.vergiss();
        AuflaufBildCheck.b = b;
        AuflaufBildCheck.w = b.wart();
        AuflaufBildCheck.t = t0;
        AuflaufBildCheck.uhrNs = System.nanoTime();
        long n0 = System.nanoTime();
        int bilder = 0;
        while (!AuflaufBildCheck.w.bereit() && (System.nanoTime() - n0) / 1e9 < AuflaufBildCheck.BEREIT_S) { AuflaufBildCheck.bild(bl, false); bilder++; }
        A.pruefe(AuflaufBildCheck.w.bereit(), "Fläche mit Nahfeld bereit", f("nach %.0f s Uhr, %d Bildern", (System.nanoTime() - n0) / 1e9, bilder), f("≤ %.0f s", AuflaufBildCheck.BEREIT_S));
        for (int k = 0; k < 60 * BILDER_JE_S && !au.aktiv(); k++) AuflaufBildCheck.bild(bl, false);
        A.pruefe(au.watt(), "in der Anwendung: Rechteck am Watt", au.watt() ? "ja" : au.bericht, "ja");
        if (!au.aktiv()) return;
        for (int k = 0; k < 30 * BILDER_JE_S; k++) AuflaufBildCheck.bild(bl, false);
        Path ordner = Path.of("docs", "status", "seegang");
        Files.createDirectories(ordner);
        double[] lage = au.lage();
        // seit L4x: die Front des Lösers – die Zeile, deren Front dem Median der Zeilen mit Front im Rechteck am nächsten liegt
        b.geraet().ruhe();
        au.karte().runter();
        Swash s = au.swash();
        double[] xs = new double[s.ny];
        List<Double> innen = new ArrayList<>();
        for (int j = 0; j < s.ny; j++) {
            int i = 0;
            while (i < s.nx && s.tiefe(i, j) > s.hTrocken) i++;
            xs[j] = i * s.dx;
            if (i > 0 && i < s.nx) innen.add(xs[j]);
        }
        double med = SchaumCheck.median(innen);
        if (Double.isNaN(med)) med = au.frontHydro(s.ny);
        double fh = med;
        int jf = s.ny / 2;
        for (int j = 0; j < s.ny; j++) if (Math.abs(xs[j] - med) < Math.abs(xs[jf] - med)) jf = j;
        double fo = s.weltX(med, (jf + 0.5) * s.dx), fn = s.weltY(med, (jf + 0.5) * s.dx);
        double richtung = Math.toDegrees(Math.atan2(lage[2], lage[3]));
        double wz = lage[4];
        Blick nah = new Blick(fo - 12 * lage[2], fn - 12 * lage[3], wz + 1.6, richtung, -10, 60).groesse(BREITE, HOEHE);
        Blick oben = new Blick(fo - 30 * lage[2], fn - 30 * lage[3], wz + 35, richtung, -45, 60).groesse(BREITE, HOEHE);
        Blick laengs = new Blick(fo - 6 * lage[2] + 40 * lage[3], fn - 6 * lage[3] - 40 * lage[2], wz + 2.5, richtung - 70, -8, 60).groesse(BREITE, HOEHE);
        Path[] p = {ordner.resolve("S6d2_auge.png"), ordner.resolve("S6d2_nah.png"), ordner.resolve("S6d2_oben.png"), ordner.resolve("S6d2_laengs.png")};
        Blick[] bb = {bl, nah, oben, laengs};
        String[] namen = {"vom Auge des Setzens (100 m vor der Front)", "nah: 12 m vor der Front, 1,6 m über dem Wasser", "von oben: 35 m, 30 m vor der Front", "schräg längs der Front"};
        float[][] px = new float[4][];
        double tb = AuflaufBildCheck.t - 1.0 / BILDER_JE_S;
        for (int i = 0; i < 4; i++) {
            px[i] = b.standbild(bb[i], tb);
            ImageIO.write(SeegangBilder.bild(px[i], BREITE, HOEHE), "png", p[i].toFile());
            A.info("Standbild, " + namen[i], p[i].toString().replace('\\', '/'));
        }
        b.gelaendebild().swashAn = false;
        b.wasserbild().swashAn = false;
        float[] ohne = b.standbild(nah, tb);
        b.gelaendebild().swashAn = true;
        b.wasserbild().swashAn = true;
        ImageIO.write(SeegangBilder.bild(ohne, BREITE, HOEHE), "png", ordner.resolve("S6d2_nah_ohne.png").toFile());
        A.info("Front für die Bilder", f("%s; Front des Lösers in Zeile %d %.0f m von der seeseitigen Kante (Median von %d Zeilen mit Front im Rechteck) bei (%.0f, %.0f), Ruhewasser %.2f m NHN", zeit(b, tb), jf, fh, innen.size(), fo, fn, wz));
        statusbild(px, new String[] {namen[0] + " · " + zeit(b, tb), namen[1], namen[2], namen[3]});
        b.geraet().ruhe();
        au.karte().runter();
        AuflaufCheck.bildPruefen(A, au, au.swash(), "Anwendung");
    }

    static void statusbild(float[][] bilder, String[] namen) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2;
        BufferedImage alle = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        for (int n = 0; n < bilder.length; n++) {
            BufferedImage e = SeegangBilder.bild(bilder[n], BREITE, HOEHE);
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(e, (n % 2) * w, (n / 2) * h, w, h, null);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, h / 36)));
            int x0 = (n % 2) * w + 8, y0 = (n / 2) * h + h - 10;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(namen[n]) + 8, g2.getFontMetrics().getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.drawString(namen[n], x0, y0);
            g2.dispose();
        }
        File png = new File("docs" + File.separator + "status" + File.separator + "S6d2_Bild.png");
        ImageIO.write(alle, "png", png);
        A.info("Status-Bild (Sichtprüfung)", png.getPath());
    }
}
