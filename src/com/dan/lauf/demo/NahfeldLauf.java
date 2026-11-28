package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Nahfeldquelle;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.waves.gpu.NahfeldKarte;
import com.dan.physics.waves.nah.Nahfeld;

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
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Das Nahfeld mit echten Daten (Seegang Werkbuch, S2b Teil 3): je Fall (Szenario, Ansicht, Zeit) das grobe Nahfeld
 * (50 m, 5 km) und darin das feine (10 m, 1 km) aus {@link Nahfeldquelle}, eingeschwungen mit dem Anlauf auf der Karte.
 * Prüfstein S2.1: Hs im Mittel über die Zellen von G3w (100 m) gegen G3w, wo die Bindung gilt (im feinen Gitter ohne
 * die Blöcke am Rand und erst ab 20 Blöcken, SF24 A), also im tiefen,
 * gleichmäßigen Wasser (SF17 A; relativ, RMS ≤ 10 % [G]); für Watt, Rinnen, Kanten und Lee eine Auskunft.
 * Dazu einmal die Karte gegen die Referenz mit echten Daten (S2.2), die Zeit je Schritt auf der Karte und je Fall ein
 * Bild von Hs ({@code docs/status/seegang/<Kennung>_<nn>.png}).
 * <pre>  NahfeldLauf --szenen ..\HalligHooge\src\szenen\Hooge.szenen [--faelle sz/ansicht/zeit,…] [--kennung S2] [--ohne-referenz] [--klein]
 *   oder        --gebiet …\gebiet --archiv …\archiv --ort ost,nord,richtung --zeit 2026-10-25T12:30:00Z</pre>
 */
public final class NahfeldLauf {
    static final Attest A = new Attest("com.dan.lauf.Nahfeldquelle (Nahfeld mit Daten)", Nahfeldquelle.VERSION);
    static final String FAELLE = "seegang/brandung/2026-10-25T12:30:00Z,seegang/hafen/2026-10-25T12:30:00Z,xaver/brandung/2013-12-05T13:30:00Z,"
            + "xaver/hafen/2013-12-05T13:30:00Z,ruhigsee/prielmuendung/2026-10-30T08:00:00Z";
    /** Abstand der Mitte des groben und des feinen Nahfelds vor der Kamera (m) [G]. */
    static final double VOR_GROB = 500, VOR_FEIN = 300;
    static double TAU = Double.NaN;
    /** Kleinste Zahl gebundener Blöcke im feinen Gitter (ohne Rand) für den Prüfstein S2.1 fein [G] (SF24 A). */
    static final int MIN_BLOECKE = 20;
    static boolean SPEKTRAL = true, PROZESSOR = false;

    record Fall(String name, File gebiet, Path archiv, double ost, double nord, double richtung, Instant zeit) { }

    public static void main(String[] args) {
        String szenen = null, faelle = FAELLE, kennung = "S2", gebiet = null, archiv = null, ort = null, zeit = null;
        boolean referenz = true, klein = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--szenen" -> szenen = args[++i];
                case "--faelle" -> faelle = args[++i];
                case "--kennung" -> kennung = args[++i];
                case "--gebiet" -> gebiet = args[++i];
                case "--archiv" -> archiv = args[++i];
                case "--ort" -> ort = args[++i];
                case "--zeit" -> zeit = args[++i];
                case "--ohne-referenz" -> referenz = false;
                case "--klein" -> klein = true;
                case "--tau" -> TAU = Double.parseDouble(args[++i]);
                case "--nur-m0" -> SPEKTRAL = false;
                case "--prozessor" -> PROZESSOR = true;
                default -> throw new IllegalArgumentException("unbekannt: " + args[i]);
            }
        }
        long t0 = System.nanoTime();
        try {
            List<Fall> l = new ArrayList<>();
            if (gebiet != null) {
                String[] o = ort.split(",");
                l.add(new Fall("Probe", new File(gebiet), Path.of(archiv), Double.parseDouble(o[0]), Double.parseDouble(o[1]), Double.parseDouble(o[2]), Instant.parse(zeit)));
            } else {
                Szenen sz = Szenen.lies(new File(szenen));
                for (String e : faelle.split(",")) {
                    String[] q = e.strip().split("/");
                    Szenario s = sz.szenario(q[0]);
                    Ansicht an = sz.ansicht(q[1]);
                    if (s == null || an == null || s.archiv == null) { A.info("Fall " + e, "Szenario oder Ansicht fehlt"); continue; }
                    l.add(new Fall(q[0] + " – " + an.name, sz.gebiet(), s.archiv.toPath(), an.ost, an.nord, an.richtung, Instant.parse(q[2])));
                }
            }
            Path ordner = Path.of("docs", "status", "seegang");
            Files.createDirectories(ordner);
            int nr = 0;
            for (Fall fl : l) {
                nr++;
                fall(nr, fl, kennung, ordner, referenz && nr == 1, klein);
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Dauer", f("%.0f s", (System.nanoTime() - t0) * 1e-9));
        Path p = A.schreibe(Path.of("docs", "status", kennung + "_Nahfeld_Daten.md"), kennung + " – Nahfeld mit Daten (Nahfeldquelle " + Nahfeldquelle.VERSION + ", Nahfeld " + Nahfeld.VERSION + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void fall(int nr, Fall fl, String kennung, Path ordner, boolean referenz, boolean klein) throws Exception {
        A.teil(f("%d %s, %s", nr, fl.name, fl.zeit));
        try (Nahfeldquelle q = new Nahfeldquelle(fl.gebiet, fl.archiv)) {
            if (!Double.isNaN(TAU)) q.bindungZeit = TAU;
            q.spektralBinden = SPEKTRAL;
            double t = (fl.zeit.toEpochMilli() - q.start.toEpochMilli()) / 1000.0;
            double r = Math.toRadians(fl.richtung);
            Nahfeldquelle.Stufe sg = klein ? new Nahfeldquelle.Stufe("grob", 50, 40) : Nahfeldquelle.GROB, sf = klein ? new Nahfeldquelle.Stufe("fein", 10, 40) : Nahfeldquelle.FEIN;
            long b0 = System.nanoTime();
            Nahfeld grob = q.baue(sg, fl.ost + VOR_GROB * Math.sin(r), fl.nord + VOR_GROB * Math.cos(r), t);
            Nahfeld.Ziel zg = q.ziel(grob, t);
            double sBau = (System.nanoTime() - b0) * 1e-9;
            Nahfeld grobRef = referenz && !PROZESSOR ? kopie(grob) : null;
            int[] runden = new int[2];
            double[] sek = new double[2];
            long a0 = System.nanoTime();
            if (PROZESSOR) {
                grob.rand = grob.randAus(zg);
                runden[0] = grob.anlauf(3000, 1e-5, 600);
            } else try (NahfeldKarte k = NahfeldKarte.mitGeraet(grob)) {
                k.rand(grob.randVarianz(zg));
                runden[0] = k.anlauf(3000, 1e-5, 600);
                k.runter();
            }
            sek[0] = (System.nanoTime() - a0) * 1e-9;
            pruefeMittel("grob", grob, q, t);
            // fein im groben
            Nahfeld fein = q.baue(sf, fl.ost + VOR_FEIN * Math.sin(r), fl.nord + VOR_FEIN * Math.cos(r), t);
            fein.rand = Nahfeld.schachtel(grob, fein);
            double[] ms = new double[1];
            long a1 = System.nanoTime();
            if (PROZESSOR) {
                runden[1] = fein.anlauf(3000, 1e-5, 600);
                sek[1] = (System.nanoTime() - a1) * 1e-9;
            } else try (NahfeldKarte k = NahfeldKarte.mitGeraet(fein)) {
                k.rand(fein.randVarianz());
                runden[1] = k.anlauf(3000, 1e-5, 600);
                k.runter();
                sek[1] = (System.nanoTime() - a1) * 1e-9;
                k.schritt(0.5);
                long s0 = System.nanoTime();
                for (int i = 0; i < 10; i++) k.schritt(0.5);
                ms[0] = (System.nanoTime() - s0) * 1e-6 / 10;
            }
            pruefeMittel("fein", fein, q, t);
            A.info("Aufwand", f("Bau des groben %.1f s; Anlauf grob %d Runden in %.1f s, fein %d Runden in %.1f s; fein mit der Zeit %.1f ms je Schritt von 0,5 s",
                    sBau, runden[0], sek[0], runden[1], sek[1], ms[0]));
            A.info("Nahfeld", f("grob %d × %d zu %.0f m ab (%.0f, %.0f); fein %d × %d zu %.0f m ab (%.0f, %.0f); %d × %d Stützstellen; Wind (%.1f, %.1f) m/s; Pflanzen %s",
                    grob.nx, grob.ny, grob.dx, grob.x0, grob.y0, fein.nx, fein.ny, fein.dx, fein.x0, fein.y0, grob.nb, grob.nd, grob.windU[0], grob.windV[0],
                    fein.pflanzen != null ? "ja" : "keine"));
            String datei = f("%s_%02d.png", kennung, nr);
            bild(grob, fein, fl.name + ", " + fl.zeit, ordner.resolve(datei).toFile());
            A.info("Bild", "docs\\status\\seegang\\" + datei);
            if (grobRef != null) {
                long c0 = System.nanoTime();
                grobRef.rand = grobRef.randAus(zg);
                int rr = grobRef.anlauf(3000, 1e-5, 600);
                double gr = 0, hmax = 0;
                for (int c = 0; c < grob.nc; c++) { gr = Math.max(gr, Math.abs(grob.hs(c) - grobRef.hs(c))); hmax = Math.max(hmax, grobRef.hs(c)); }
                A.pruefe(gr <= 2e-3 * hmax, "S2.2 Karte gegen Referenz mit echten Daten (grobes Nahfeld, Anlauf)",
                        f("größte Abweichung von Hs %.2e des größten (%.3f m); Runden Referenz %d in %.0f s", gr / hmax, hmax, rr, (System.nanoTime() - c0) * 1e-9), "≤ 2e-3");
            }
        }
    }

    /** Eine Kopie mit denselben Eingaben und leerer Wirkung (für die Referenz). */
    static Nahfeld kopie(Nahfeld a) {
        Nahfeld b = new Nahfeld(a.nx, a.ny, a.dx, a.nb, a.f[0] / Math.sqrt(a.stufe), a.f[a.nb - 1] * Math.sqrt(a.stufe), a.nd);
        b.x0 = a.x0; b.y0 = a.y0;
        System.arraycopy(a.grund, 0, b.grund, 0, a.nc);
        System.arraycopy(a.spiegel, 0, b.spiegel, 0, a.nc);
        System.arraycopy(a.u, 0, b.u, 0, a.nc);
        System.arraycopy(a.v, 0, b.v, 0, a.nc);
        System.arraycopy(a.windU, 0, b.windU, 0, a.nc);
        System.arraycopy(a.windV, 0, b.windV, 0, a.nc);
        b.pflanzen = a.pflanzen == null ? null : a.pflanzen.clone();
        b.halm = a.halm == null ? null : a.halm.clone();
        b.bindungM0 = a.bindungM0 == null ? null : a.bindungM0.clone();
        b.bindungSpektrum = a.bindungSpektrum == null ? null : a.bindungSpektrum.clone();
        b.bindungBlock = a.bindungBlock;
        b.bindungZeit = a.bindungZeit;
        b.vorbereiten();
        return b;
    }

    /**
     * S2.1: Hs im Mittel über die Zellen von G3w gegen G3w, wo die Bindung gilt (SF17 A: Block ganz nass, im Mittel über
     * 2 m tief, Streuung der Tiefe unter 25 %, kein trockener Block und keine Wand in ihm oder um ihn; Hs von G3w über
     * 0,1 m). Für die freien Blöcke eine Auskunft, getrennt nach Watt (bis 2 m), Rinne oder Kante (Streuung über 25 %)
     * und Lee (trockene Zelle oder Wand in der Nähe).
     * <p>
     * Im feinen Gitter (SF24 A): Blöcke am Rand zählen nicht, denn dort gilt der Rand aus dem groben Gitter, und das prüft
     * S2.1 grob; sie kommen als Auskunft. Prüfstein erst ab {@link #MIN_BLOECKE} Blöcken, darunter Auskunft.
     */
    static void pruefeMittel(String stufe, Nahfeld n, Nahfeldquelle q, double t) throws Exception {
        boolean[] frei = n.freiVonBindung();
        boolean ohneRand = stufe.equals("fein");
        int bs = n.bindungBlock, bx = n.nx / bs, by = n.ny / bs, z = 0, zt = 0, zr = 0;
        double dq = 0, aq = 0, sn = 0, sg2 = 0, tn = 0, tg = 0, rn = 0, rg = 0, rdq = 0, raq = 0;
        double[][] fr = new double[3][4];                                          // Watt, Rinne, Lee: Zahl, Σ Nahfeld, Σ G3w, Σ d²
        List<double[]> schlimm = new ArrayList<>();
        for (int jb = 0; jb < by; jb++)
            for (int ib = 0; ib < bx; ib++) {
                double m = 0, hsum = 0, hq = 0;
                boolean nass = true, gebunden = true;
                for (int j = jb * bs; j < jb * bs + bs; j++)
                    for (int i = ib * bs; i < ib * bs + bs; i++) {
                        int c = j * n.nx + i;
                        if (!n.nass(c)) nass = false;
                        if (frei[c]) gebunden = false;
                        m += n.m0(c);
                        hsum += n.tiefe(c);
                        hq += n.tiefe(c) * n.tiefe(c);
                    }
                double hm = hsum / (bs * bs), hsd = Math.sqrt(Math.max(0, hq / (bs * bs) - hm * hm));
                if (!nass) continue;
                m /= bs * bs;
                double xm = n.x0 + (ib + 0.5) * bs * n.dx, ym = n.y0 + (jb + 0.5) * bs * n.dx;
                double mg = q.m0Grob(n, xm, ym, t);
                if (!(mg > 0.1 * 0.1 / 16)) continue;                                // Hs über 0,1 m
                double hn = 4 * Math.sqrt(m), hg = 4 * Math.sqrt(mg), d = hn - hg;
                if (gebunden && ohneRand && (ib == 0 || jb == 0 || ib == bx - 1 || jb == by - 1)) {
                    zr++; rn += hn; rg += hg; rdq += d * d; raq += hg * hg;
                    continue;
                }
                if (gebunden) {
                    dq += d * d; aq += hg * hg; z++; sn += hn; sg2 += hg; schlimm.add(new double[] {Math.abs(d), xm, ym, hn, hg});
                    double tgr = q.tiefeGrob(xm, ym, t);
                    if (Double.isFinite(tgr)) { tn += hm; tg += tgr; zt++; }
                    continue;
                }
                int art = hm <= n.bindungTiefe ? 0 : hsd > n.bindungStreuung * hm ? 1 : 2;
                fr[art][0]++; fr[art][1] += hn; fr[art][2] += hg; fr[art][3] += d * d;
            }
        String[] namen = {"Watt (bis 2 m)", "Rinne oder Kante (Streuung über 25 %)", "Lee (trocken oder Wand nah)"};
        StringBuilder sf = new StringBuilder();
        for (int k = 0; k < 3; k++)
            if (fr[k][0] > 0) sf.append(f("%s%s: %.0f Zellen, Nahfeld %.3f m gegen G3w %.3f m (%+.0f %%)", sf.length() > 0 ? "; " : "", namen[k],
                    fr[k][0], fr[k][1] / fr[k][0], fr[k][2] / fr[k][0], 100 * (fr[k][1] / fr[k][2] - 1)));
        A.info("S2.1 " + stufe + " frei", sf.length() > 0 ? sf.toString() : "keine freie Zelle von G3w mit Hs über 0,1 m");
        if (zr > 0) A.info("S2.1 " + stufe + " am Rand (Rand aus dem groben Gitter, SF24 A: Auskunft)",
                f("%d Zellen, Nahfeld %.3f m gegen G3w %.3f m, relativ %.1f %%", zr, rn / zr, rg / zr, 100 * Math.sqrt(rdq / raq)));
        if (z == 0) { A.info("S2.1 " + stufe, "keine gebundene Zelle von G3w mit Hs über 0,1 m"); return; }
        schlimm.sort((a, b) -> Double.compare(b[0], a[0]));
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < Math.min(4, schlimm.size()); k++)
            sb.append(f("%s(%.0f, %.0f): %.2f gegen %.2f m", k > 0 ? "; " : "", schlimm.get(k)[1], schlimm.get(k)[2], schlimm.get(k)[3], schlimm.get(k)[4]));
        A.info("S2.1 " + stufe + " im Einzelnen", f("Mittel Nahfeld %.3f m, G3w %.3f m; größte Abweichungen %s; Tiefe dort im Mittel Nahfeld %.2f m, G3 %.2f m (%d Zellen)",
                sn / z, sg2 / z, sb, tn / Math.max(1, zt), tg / Math.max(1, zt), zt));
        String wert = f("relativ %.1f %% über %d Zellen von G3w", 100 * Math.sqrt(dq / aq), z);
        String was = f("S2.1 %s: Hs im Mittel über 100 m gegen G3w, wo die Bindung gilt (tief und gleichmäßig%s)", stufe, ohneRand ? ", ohne Rand" : "");
        if (ohneRand && z < MIN_BLOECKE) A.info(was + f(" – unter %d Zellen nur Auskunft (SF24 A)", MIN_BLOECKE), wert);
        else A.pruefe(Math.sqrt(dq / aq) <= 0.10, was, wert, "≤ 10 % [G]");
    }

    // ------------------------------------------------------------------ Bild

    static void bild(Nahfeld grob, Nahfeld fein, String titel, File datei) throws Exception {
        int p = Math.max(2, 400 / grob.nx), w = grob.nx * p, h = grob.ny * p, pf = Math.max(2, 400 / fein.nx), wf = fein.nx * pf;
        BufferedImage bi = new BufferedImage(w + wf + 30, Math.max(h, fein.ny * pf) + 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(24, 24, 28));
        g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
        double hmax = 0;
        for (int c = 0; c < grob.nc; c++) hmax = Math.max(hmax, grob.hs(c));
        for (int c = 0; c < fein.nc; c++) hmax = Math.max(hmax, fein.hs(c));
        if (hmax <= 0) hmax = 1;
        male(g, grob, 0, 40, p, hmax);
        male(g, fein, w + 30, 40, pf, hmax);
        g.setColor(Color.WHITE);
        double fx = (fein.x0 - grob.x0) / grob.dx * p, fy = (fein.y0 - grob.y0) / grob.dx * p, fw = fein.nx * fein.dx / grob.dx * p;
        g.drawRect((int) fx, (int) (40 + h - fy - fw), (int) fw, (int) fw);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(titel, 6, 18);
        g.drawString(f("Hs 0 bis %.2f m; grob %.0f m, fein %.0f m; trocken grau", hmax, grob.dx, fein.dx), 6, 34);
        g.dispose();
        ImageIO.write(bi, "png", datei);
    }

    static void male(Graphics2D g, Nahfeld n, int x0, int y0, int p, double hmax) {
        for (int j = 0; j < n.ny; j++)
            for (int i = 0; i < n.nx; i++) {
                int c = j * n.nx + i;
                Color col;
                if (!n.nass(c)) {
                    int v = (int) Math.max(60, Math.min(200, 120 + 20 * n.grund[c]));
                    col = new Color(v, v, v - 10);
                } else col = farbe(n.hs(c) / hmax);
                g.setColor(col);
                g.fillRect(x0 + i * p, y0 + (n.ny - 1 - j) * p, p, p);
            }
    }

    /** Von Dunkelblau über Türkis und Gelb nach Weiß. */
    static Color farbe(double s) {
        s = Math.max(0, Math.min(1, s));
        double[][] st = {{10, 20, 80}, {20, 110, 170}, {60, 190, 170}, {240, 220, 80}, {255, 255, 255}};
        double x = s * (st.length - 1);
        int k = Math.min(st.length - 2, (int) x);
        double a = x - k;
        return new Color((int) (st[k][0] + a * (st[k + 1][0] - st[k][0])), (int) (st[k][1] + a * (st[k + 1][1] - st[k][1])), (int) (st[k][2] + a * (st[k + 1][2] - st[k][2])));
    }
}
