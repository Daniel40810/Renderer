package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.lauf.Archivquelle;
import com.dan.render.terrain.Gelaendebild;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Das Sandfegen auf der Karte (P10c Teil 2, Werkbuch HalligHooge Teil A, 12.11, Datei 41, Prüfstein 17): der Schleier aus
 * fliegenden Körnern im Gelände ({@code render.terrain}, {@code gelaende.frag}).
 * <ul>
 * <li><b>1 Deckung:</b> ein gleichförmiger Fluss über dem Gebiet, ohne Streifen; die Deckung je Bildpunkt aus drei
 * Bildern (ohne Schleier, mit, mit undurchsichtigem Schleier) gegen 1 − exp(−q · 3 / (2 ρs d V) / sin β), β der Winkel
 * des Blicks zum Boden (eben gerechnet). Dazu der Blick von oben und das Mittel mit Streifen.</li>
 * <li><b>2 Kosten:</b> das Gelände je Bild mit und ohne Flugsand (Stoppuhr).</li>
 * <li><b>3 Im Gebiet:</b> der Fluss aus dem Archiv zur Zeit {@code --zeit} in der Ansicht; Status-Bild
 * {@code docs/status/<name>.png} (ohne und mit Sandfegen).</li>
 * </ul>
 * {@code --gebiet}, {@code --archiv}, {@code --zeit}, {@code --ort Ost,Nord}, {@code --richtung}, {@code --breite},
 * {@code --hoehe}, {@code --name}. Attest {@code docs/status/<name>.md}.
 */
public final class SandfegenKarteCheck {
    private static final Attest A = new Attest("com.dan.render.terrain (Sandfegen auf der Karte)", Gelaendebild.VERSION);
    static int breite = 1920, hoehe = 1080;

    public static void main(String[] args) {
        List<String> a = List.of(args);
        File gb = new File(AtmosLaufCheck.arg(a, "--gebiet", "D:\\Daten\\HalligHooge\\gebiet"));
        File ar = new File(AtmosLaufCheck.arg(a, "--archiv", "D:\\Daten\\HalligHooge\\_p9c\\archiv_m1b"));
        Instant zeit = Instant.parse(AtmosLaufCheck.arg(a, "--zeit", "2026-10-17T16:00:00Z"));
        String[] ot = AtmosLaufCheck.arg(a, "--ort", "465850,6042890").split(",");
        double ost = Double.parseDouble(ot[0].strip()), nord = Double.parseDouble(ot[1].strip()), richtung = Double.parseDouble(AtmosLaufCheck.arg(a, "--richtung", "20"));
        breite = Integer.parseInt(AtmosLaufCheck.arg(a, "--breite", "1920"));
        hoehe = Integer.parseInt(AtmosLaufCheck.arg(a, "--hoehe", "1080"));
        String name = AtmosLaufCheck.arg(a, "--name", "P10c_Sandfegen");
        try (Geraet g = new Geraet()) {
            A.info("Gerät", g.name + " (" + g.treiber + ")");
            Konto konto = g.konto("SandfegenKarteCheck", 3L << 30);
            pruefe(g, konto, gb, ar, zeit, ost, nord, richtung, name);
            A.teil("Aufräumen");
            A.pruefe(konto.belegt() == 0, "Speicherkonto", konto + ", Spitze " + Geraet.mb(konto.spitze()), "0 belegt");
            if (g.validierung) {
                String erste = g.validierungsFehler > 0 && !g.meldungen.isEmpty() ? ": " + g.meldungen.get(0) : "";
                A.pruefe(g.validierungsFehler == 0, "Validierungsschicht " + g.schicht + " über das Gelände mit Flugsand", g.validierungsFehler + " Fehler" + erste, "0 Fehler");
            } else A.info("Validierung", "nicht verlangt (-Dcore.gpu.validierung=true)");
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", name + ".md"), "P10c – SandfegenKarteCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static double hell(float[] b, int k) { return 0.2126 * b[4 * k] + 0.7152 * b[4 * k + 1] + 0.0722 * b[4 * k + 2]; }

    /** Gleichförmiger Fluss q (kg/(m s)) nach {@code grad} (wohin, von Nord über Ost) auf dem Gitter. */
    static float[] gleich(Gitter g, double q, double grad) {
        float[] f = new float[2 * g.zellen()];
        float qx = (float) (q * Math.sin(Math.toRadians(grad))), qy = (float) (q * Math.cos(Math.toRadians(grad)));
        for (int c = 0; c < g.zellen(); c++) {
            f[2 * c] = qx;
            f[2 * c + 1] = qy;
        }
        return f;
    }

    /** Sinus des Winkels zwischen dem Blick durch die Mitte des Bildpunkts und einer waagerechten Ebene. */
    static double sinus(Blick b, int px, int py) {
        double[][] bas = b.basis();
        double tt = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2), ax = (double) breite / hoehe / tt, ay = 1 / tt;
        double nx = (px + 0.5) / breite * 2 - 1, ny = (py + 0.5) / hoehe * 2 - 1;
        double x = bas[2][0] + bas[0][0] * nx * ax - bas[1][0] * ny * ay, y = bas[2][1] + bas[0][1] * nx * ax - bas[1][1] * ny * ay, z = bas[2][2] + bas[0][2] * nx * ax - bas[1][2] * ny * ay;
        return Math.abs(y) / Math.sqrt(x * x + y * y + z * z);
    }

    /** Deckung je Bildpunkt aus drei Bildern; NaN, wo der undurchsichtige Schleier das Bild nicht um wenigstens 5 % ändert (Wasser, Himmel, Sand in der Farbe des Schleiers). */
    static double[] deckung(float[] ohne, float[] mit, float[] dicht) {
        double[] d = new double[ohne.length / 4];
        for (int k = 0; k < d.length; k++) {
            double o = hell(ohne, k), m = hell(mit, k), v = hell(dicht, k);
            d[k] = Math.abs(v - o) > 0.05 * Math.max(Math.abs(v), Math.abs(o)) && Math.max(v, o) > 0 ? (m - o) / (v - o) : Double.NaN;
        }
        return d;
    }

    static void pruefe(Geraet g, Konto konto, File gb, File ar, Instant zeit, double ost, double nord, double richtung, String name) throws Exception {
        Gebiet ge = Gebiet.lies(gb);
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, breite, hoehe)) {
            Instant t0 = q.start;
            double t = Duration.between(t0, zeit).toMillis() / 1000.0;
            if (q.flugGitter() == null) throw new IllegalStateException(ar + ": kein Flugsand im Archiv (aeolian_<Gitter>)");
            Gelaendebild gel = sz.gelaende;
            double boden = gel.gelaende.hoehe(ost, nord, 1);
            Blick schraeg = new Blick(ost, nord, boden + 20, richtung, -30, 40).groesse(breite, hoehe);
            Blick oben = new Blick(ost, nord, boden + 300, richtung, -89.5, 40).groesse(breite, hoehe);
            Blick ansicht = new Blick(ost, nord, boden + 1.7, richtung, -3, 55).groesse(breite, hoehe);
            gel.flugZeit = 0;
            // ein Bild mit dem Stand des Archivs: danach liegt der Fluss am Gelände und lässt sich hier ersetzen
            KimmungKarteCheck.bild(sz, schraeg, t0, t);
            Gitter fg = sz.bild.flugGitter();
            float[] archiv = sz.bild.flugsand().clone();
            double k = gel.flugDicke();

            A.teil("1 Deckung des Schleiers (Prüfstein 17)");
            A.info("Schleier", f("Korn %.2f mm, Dichte %.0f kg/m³, Korngeschwindigkeit %.1f m/s [G]: senkrechte optische Dicke %.3f je kg/(m s) Fluss; Boden am Ort %.2f m NHN", gel.flugKorn * 1e3, gel.flugDichte,
                    gel.flugTempo, k, boden));
            gel.flugStreifen = 0;
            double q0 = 0.1, fluss = 198;
            gel.flugsand(null, null);
            float[] ohne = KimmungKarteCheck.bild(sz, schraeg, t0, t);
            gel.flugsand(fg, gleich(fg, q0, fluss));
            float[] mit = KimmungKarteCheck.bild(sz, schraeg, t0, t);
            gel.flugsand(fg, gleich(fg, 1000, fluss));
            float[] dicht = KimmungKarteCheck.bild(sz, schraeg, t0, t);
            double[] d = deckung(ohne, mit, dicht);
            double[] fehler = new double[d.length];
            int n = 0;
            double sumIst = 0, sumSoll = 0;
            for (int py = 0; py < hoehe; py++)
                for (int px = 0; px < breite; px++) {
                    int i = px + breite * py;
                    if (Double.isNaN(d[i])) continue;
                    double soll = 1 - Math.exp(-q0 * k / sinus(schraeg, px, py));
                    if (soll < 0.05 || soll > 0.95) continue;
                    fehler[n++] = Math.abs(d[i] - soll) / soll;
                    sumIst += d[i];
                    sumSoll += soll;
                }
            if (n < 1000) A.pruefe(false, "Prüfstein 17: Deckung gegen die Handrechnung", n + " Bildpunkte mit Gelände, das der Schleier ändert", "wenigstens 1000 Bildpunkte");
            else {
                Arrays.sort(fehler, 0, n);
                A.hoechstens(f("Prüfstein 17: Deckung im schrägen Blick (20 m über dem Boden, −30°, q = %.0f g/(m s) ohne Streifen) gegen 1 − exp(−τ / sin β), Median der Abweichung über %d Bildpunkte", q0 * 1e3, n),
                        100 * fehler[n / 2], 10.0, "%");
                A.info("Deckung", f("im Mittel %.3f, Soll %.3f; 90 %% der Bildpunkte auf %.1f %% (der Boden ist nicht eben: β gilt zur Fläche am Ort)", sumIst / n, sumSoll / n, 100 * fehler[(int) (0.9 * n)]));
            }
            // mit Streifen: das Mittel soll bleiben
            gel.flugStreifen = 1;
            gel.flugsand(fg, gleich(fg, q0, fluss));
            float[] streifen = KimmungKarteCheck.bild(sz, schraeg, t0, t);
            double[] ds = deckung(ohne, streifen, dicht);
            double a = 0, b = 0;
            int m = 0;
            for (int i = 0; i < d.length; i++)
                if (!Double.isNaN(d[i]) && !Double.isNaN(ds[i])) {
                    a += ds[i];
                    b += d[i];
                    m++;
                }
            A.info("Mit Streifen", m > 0 ? f("Deckung im Mittel %.3f gegen %.3f ohne Streifen (Verhältnis %.2f)", a / m, b / m, a / b) : "kein Bildpunkt");
            // von oben
            gel.flugStreifen = 0;
            gel.flugsand(null, null);
            float[] oOhne = KimmungKarteCheck.bild(sz, oben, t0, t);
            gel.flugsand(fg, gleich(fg, 0.010, fluss));
            float[] oMit = KimmungKarteCheck.bild(sz, oben, t0, t);
            gel.flugsand(fg, gleich(fg, 1000, fluss));
            float[] oDicht = KimmungKarteCheck.bild(sz, oben, t0, t);
            double[] od = deckung(oOhne, oMit, oDicht);
            double so = 0;
            int no = 0;
            for (double v : od)
                if (!Double.isNaN(v)) {
                    so += v;
                    no++;
                }
            if (no < 1000) A.pruefe(false, "Prüfstein 17: Deckung von oben", no + " Bildpunkte", "wenigstens 1000 Bildpunkte");
            else A.hoechstens(f("Prüfstein 17: Deckung von oben (300 m über dem Boden, q = 10 g/(m s)), Mittel über %d Bildpunkte (Soll %.2f %%)", no, 100 * (1 - Math.exp(-0.010 * k))), 100 * so / no, 2.0, "%");

            A.teil("2 Kosten (Prüfstein 17)");
            // Die Stoppuhr schwankt auf diesem Gerät von Messung zu Messung um mehrere Zehntel Millisekunden (Lauf B: +0,40 ms,
            // mit Validierung −0,18 ms, Lauf B2: +0,82 ms bei je einer Messung). Darum: einmal warm, dann sieben Runden im Wechsel
            // ohne/mit, und der Median der Unterschiede zählt; die Spanne steht dabei.
            final int runden = 7;
            double[][] ms = new double[2][runden];
            double[] mehr = new double[runden];
            boolean uhrDa = true;
            gel.flugStreifen = 1;
            for (int runde = -1; runde < runden; runde++) {
                for (int fall = 0; fall < 2; fall++) {
                    gel.flugsand(fall == 0 ? null : fg, fall == 0 ? null : gleich(fg, 0.010, fluss));
                    try (Stoppuhr uhr = new Stoppuhr(g, g.grafik, 32)) {
                        for (int i = 0; i < 40; i++) {
                            sz.zeichne(ansicht, t0, t, uhr);
                            uhr.sammle(false);
                        }
                        uhr.sammle(true);
                        uhrDa &= uhr.verfuegbar;
                        if (runde >= 0) ms[fall][runde] = uhr.ms("Gelände");
                    }
                }
                if (runde >= 0) mehr[runde] = ms[1][runde] - ms[0][runde];
            }
            double[] msOhne = ms[0].clone(), msMit = ms[1].clone(), sortiert = mehr.clone();
            Arrays.sort(msOhne);
            Arrays.sort(msMit);
            Arrays.sort(sortiert);
            if (!uhrDa) A.info("Prüfstein 17: Kosten", "keine Stoppuhr auf diesem Gerät");
            else {
                A.hoechstens(f("Prüfstein 17: Gelände je Bild mit Sandfegen überall gegen ohne (%d × %d, Ansicht in Augenhöhe; Median aus %d Runden im Wechsel: %.2f gegen %.2f ms)", breite, hoehe, runden,
                        msMit[runden / 2], msOhne[runden / 2]), sortiert[runden / 2], 0.5, "ms");
                StringBuilder sb = new StringBuilder();
                for (double je : mehr) sb.append(sb.length() == 0 ? "" : "; ").append(f("%+.2f", je));
                A.info("Kosten je Runde", f("%s ms; Spanne %+.2f bis %+.2f ms; ohne Sandfegen %.2f bis %.2f ms", sb, sortiert[0], sortiert[runden - 1], msOhne[0], msOhne[runden - 1]));
            }

            A.teil("3 Im Gebiet (" + ar.getName() + ", " + BildCheck.zeit(t0, t) + ", " + ansicht + ")");
            int zellen = 0;
            double groesst = 0, amOrt = 0;
            int co = fg.enthaelt(ost, nord) ? fg.index(fg.i(ost), fg.j(nord)) : -1;
            for (int c = 0; c < fg.zellen(); c++) {
                double v = Math.hypot(archiv[2 * c], archiv[2 * c + 1]);
                if (v > 0) zellen++;
                groesst = Math.max(groesst, v);
                if (c == co) amOrt = v;
            }
            A.info("Flugsand im Archiv zu dieser Zeit", f("Fluss in %d Zellen, größter %.1f g/(m s), am Ort der Ansicht %.1f g/(m s)", zellen, groesst * 1e3, amOrt * 1e3));
            gel.flugStreifen = 1;
            gel.flugsand(null, null);
            float[] gOhne = KimmungKarteCheck.bild(sz, ansicht, t0, t);
            float[] ausOhne = sz.aus.lies();
            gel.flugsand(fg, archiv);
            float[] gMit = KimmungKarteCheck.bild(sz, ansicht, t0, t);
            float[] ausMit = sz.aus.lies();
            double[] v = KimmungKarteCheck.vergleich(gMit, gOhne, 0.02), v10 = KimmungKarteCheck.vergleich(gMit, gOhne, 0.10);
            A.pruefe(zellen == 0 || v[0] > 0, "Das Sandfegen ändert das Bild der Ansicht", f("%.1f %% der Bildpunkte um mehr als 2 %%, %.1f %% um mehr als 10 %%; größte Änderung %.0f %%", 100 * v[0], 100 * v10[0], 100 * v[1]),
                    "mehr als 0 Bildpunkte");
            BufferedImage io = KimmungKarteCheck.ausgabe(ausOhne, breite, hoehe), im = KimmungKarteCheck.ausgabe(ausMit, breite, hoehe);
            int W = breite / 2, H = hoehe / 2;
            BufferedImage alle = new BufferedImage(2 * W, 2 * H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(io, 0, 0, W, H, null);
            g2.drawImage(im, W, 0, W, H, null);
            // Ausschnitt unter der Bildmitte in voller Auflösung
            int ax = breite / 2 - W / 2, ay = Math.min(hoehe - H, hoehe / 2 + hoehe / 16);
            g2.drawImage(io.getSubimage(ax, ay, W, H), 0, H, null);
            g2.drawImage(im.getSubimage(ax, ay, W, H), W, H, null);
            String[] txt = {"ohne Sandfegen", "mit Sandfegen", "Ausschnitt unter der Bildmitte: ohne", "Ausschnitt: mit"};
            for (int i = 0; i < 4; i++) {
                g2.setColor(new Color(0, 0, 0, 160));
                g2.fillRect((i % 2) * W + 4, (i / 2) * H + 3, 250, 18);
                g2.setColor(Color.WHITE);
                g2.drawString(txt[i], (i % 2) * W + 8, (i / 2) * H + 16);
            }
            g2.dispose();
            File png = new File("docs" + File.separator + "status" + File.separator + name + ".png");
            png.getParentFile().mkdirs();
            ImageIO.write(alle, "png", png);
            A.info("Status-Bild (Sichtprüfung)", png.getPath() + f(", %d × %d", alle.getWidth(), alle.getHeight()));
        }
    }
}
