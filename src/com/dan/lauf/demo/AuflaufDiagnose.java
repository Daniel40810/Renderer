package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Kanten;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Auflauf;
import com.dan.lauf.Buehne;
import com.dan.lauf.Seegangsflaeche;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;
import com.dan.physics.swash.Swash;
import com.dan.render.seegang.SeegangKarte;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Diagnose zu L4f (Seegang Werkbuch, Datei 73, S6b-3b): zeichnet die Fälle des {@link AuflaufMassstabCheck} am echten Ort
 * auf, damit die Ursachen der drei Verstöße in der Arbeitsumgebung ausgewertet werden können. Keine Prüfsteine.
 * <p>
 * Je Fall in {@code docs/status/diag_L4g/<Fall>/} (float32 little endian):
 * <ul>
 * <li>{@code meta.txt}: Gitter, Ruhewasser, Richtungen, Seegang, Kante, Bericht des Setzens, Sonden;</li>
 * <li>{@code b.f32}, {@code h0.f32}: Grund relativ zum Ruhewasser (NaN: Fels) und Tiefe am Anfang, je (j, i);</li>
 * <li>{@code saum.f32}: je Aufnahme der Saum aller Zeilen (m über dem Ruhewasser, NaN ohne);</li>
 * <li>{@code sonde.f32}: je Aufnahme für die Sondenzeilen (h, hu, hv) aller Zellen der Zeile;</li>
 * <li>{@code rand.f32}: je Aufnahme je Punkt der Randwelle (η, q_ost, q_nord, m0);</li>
 * <li>{@code h_<s>.f32}: Tiefe im ganzen Gitter alle 60 s; {@code bilanz.txt}: alle 10 s Volumen und Flüsse je Seite;
 * {@code ostzu.f32}: alle 10 s der Zufluss je Zeile über die Landseite (kumuliert).</li>
 * </ul>
 * Aufruf: {@code AuflaufDiagnose --szenen <Hooge.szenen> [--dauer 300] [--fall Deich,Deckwerk,Sand] [--hz 20]}.
 */
public final class AuflaufDiagnose {
    static final int[] SONDEN = {64, 192, 320, 448};

    private AuflaufDiagnose() { }

    public static void main(String[] args) {
        File szenen = null;
        double dauer = 300;
        int hz = 20;
        String faelleArg = "Deich,Deckwerk,Sand";
        for (int i = 0; i + 1 < args.length; i += 2)
            switch (args[i]) {
                case "--szenen" -> szenen = new File(args[i + 1]);
                case "--dauer" -> dauer = Double.parseDouble(args[i + 1]);
                case "--fall" -> faelleArg = args[i + 1];
                case "--hz" -> hz = Integer.parseInt(args[i + 1]);
                default -> { }
            }
        System.out.println("AuflaufDiagnose – S6b-3b (Seegang Werkbuch, Datei 73)");
        int rc = 0;
        long start = System.nanoTime();
        try {
            if (szenen == null) throw new IllegalArgumentException("--szenen fehlt");
            Szenen sz = Szenen.lies(szenen);
            Ansicht an = sz.ansicht("brandung");
            int breite = 1920, hoehe = 1080;
            Buehne b = Buehne.ohneFenster(sz.gebiet(), breite, hoehe);
            SchaumCheck.b = b;
            Attest A = new Attest("Diagnose S6b-3b", "Seegang Werkbuch");
            try {
                Auflauf au = b.auflauf();
                if (au == null) throw new IllegalStateException("kein Auflauf an der Bühne");
                String offen = null;
                if (faelleArg.equalsIgnoreCase("Kanten")) {
                    for (String[] f : new String[][] {{"seegang", "2026-10-25T12:30:00Z"}, {"ruhigsee", "2026-10-30T14:00:00Z"}}) {
                        b.oeffne(SchaumCheck.szenario(sz, f[0]));
                        Blick bl = SchaumCheck.blick(an, f[1], breite, hoehe);
                        b.wasserbild().seegang = true;
                        b.brandung(true);
                        b.standbild(bl, SchaumCheck.t);
                        kantenliste(b, SchaumCheck.t, f[0] + " " + f[1]);
                    }
                    faelleArg = "";
                }
                for (String name : faelleArg.split(",")) {
                    if (name.isBlank()) continue;
                    AuflaufMassstabCheck.Fall fa = null;
                    for (AuflaufMassstabCheck.Fall f : faelle()) if (f.name().equalsIgnoreCase(name.trim())) fa = f;
                    if (fa == null) { System.out.println("unbekannter Fall " + name); continue; }
                    System.out.println();
                    System.out.println("== " + fa.name());
                    Szenario sc = SchaumCheck.szenario(sz, fa.szenario());
                    if (!fa.szenario().equals(offen)) { b.oeffne(sc); offen = fa.szenario(); }
                    Blick bl = SchaumCheck.blick(an, fa.zeit(), breite, hoehe);
                    b.wasserbild().seegang = true;
                    b.brandung(true);
                    b.standbild(bl, SchaumCheck.t);
                    double t0 = SchaumCheck.t;
                    bl = AuflaufCheck.kante(A, b, t0, breite, hoehe, fa.name(), fa.arten());
                    if (bl == null) { System.out.println("keine Kante"); continue; }
                    Path dir = Path.of("docs", "status", "diag_L4g", fa.name());
                    Files.createDirectories(dir);
                    fall(b, au, sz, sc, bl, t0, dauer, hz, fa, AuflaufCheck.letzteKante, dir);
                }
            } finally {
                b.close();
            }
        } catch (Throwable e) {
            e.printStackTrace(System.out);
            rc = 1;
        }
        System.out.printf(Locale.GERMAN, "Rechenzeit %.0f s%n", (System.nanoTime() - start) / 1e9);
        System.exit(rc);
    }

    static AuflaufMassstabCheck.Fall[] faelle() {
        return new AuflaufMassstabCheck.Fall[] {
            new AuflaufMassstabCheck.Fall("Sand", "ruhigsee", "2026-10-30T14:00:00Z", true, art -> art != com.dan.core.szene.Gestalt.DEICH && art != com.dan.core.szene.Gestalt.WARFT && art != com.dan.core.szene.Gestalt.DECKWERK),
            new AuflaufMassstabCheck.Fall("Deckwerk", "seegang", "2026-10-25T12:30:00Z", false, art -> art == com.dan.core.szene.Gestalt.DECKWERK),
            new AuflaufMassstabCheck.Fall("Deich", "seegang", "2026-10-25T12:30:00Z", false, art -> art == com.dan.core.szene.Gestalt.DEICH)};
    }

    static void fall(Buehne b, Auflauf au, Szenen sz, Szenario sc, Blick bl, double t0, double dauer, int hz, AuflaufMassstabCheck.Fall fa, Kanten.Kante ka, Path dir) throws Exception {
        Geraet g = b.geraet();
        Seegangsflaeche sf = Seegangsflaeche.baue(g, b.konto(), sz.gebiet(), sc.archiv.toPath(), b.seegangsnetze(), bl, t0, null);
        try {
            SeegangKarte k = sf.karte;
            k.bezug(Math.floor(bl.ost / 64) * 64, Math.floor(bl.nord / 64) * 64);
            k.fernOst = bl.ost;
            k.fernNord = bl.nord;
            au.vergiss();
            boolean gesetzt = au.vorBild(bl, t0, 1, b.seegangsnetze());
            System.out.println("Setzen: " + au.bericht);
            if (!gesetzt) return;
            Swash s = au.swash();
            double ruhe = s.bezug, v0 = s.volumen();
            int nx = s.nx, ny = s.ny;
            float[] bb = new float[nx * ny], h0 = new float[nx * ny];
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int c = s.id(i, j);
                    bb[j * nx + i] = Double.isNaN(s.b[c]) ? Float.NaN : (float) (s.b[c] - ruhe);
                    h0[j * nx + i] = (float) s.h[c];
                }
            schreibe(dir.resolve("b.f32"), bb);
            schreibe(dir.resolve("h0.f32"), h0);
            double[] lage = au.lage();
            int np = (int) Math.ceil(Auflauf.NY * Auflauf.DX / Auflauf.PUNKT_ABSTAND) + 1;
            int jeAufnahme = Math.max(1, AuflaufMassstabCheck.BILDER_JE_S / hz);
            try (PrintWriter m = new PrintWriter(Files.newBufferedWriter(dir.resolve("meta.txt"), StandardCharsets.UTF_8))) {
                m.printf(Locale.ROOT, "fall %s%nnx %d%nny %d%ndx %.4f%nruhe %.4f%nost0 %.3f%nnord0 %.3f%nwinkel %.6f%n", fa.name(), nx, ny, s.dx, ruhe, s.x0, s.y0, s.winkel);
                m.printf(Locale.ROOT, "pOst %.3f%npNord %.3f%nxOst %.6f%nxNord %.6f%ntanb %.4f%nrSchaetzung %.4f%nland %.3f%n", lage[0], lage[1], lage[2], lage[3], lage[5], lage[6], lage[7]);
                m.printf(Locale.ROOT, "hs %.4f%ntp %.4f%nkroneX %.3f%nkroneZ %.4f%n", au.hs, au.tp, au.kroneX, au.kroneZ);
                m.printf(Locale.ROOT, "kante_art %d%nkante_fuss %.4f%nkante_krone %.4f%nkante_neigung %.5f%nkante_rau %.3f%nkante_ost %.3f%nkante_nord %.3f%n", ka.art(), ka.fuss(), ka.krone(), ka.neigung(), ka.rau(), ka.ost(), ka.nord());
                m.printf(Locale.ROOT, "dauer %.1f%nhz %d%njeAufnahme %d%nbilder_je_s %d%nnp %d%npunkt_abstand %.3f%n", dauer, hz, jeAufnahme, AuflaufMassstabCheck.BILDER_JE_S, np, Auflauf.PUNKT_ABSTAND);
                m.print("sonden");
                for (int j : SONDEN) m.print(" " + j);
                m.println();
                m.println("bericht " + au.bericht);
            }
            int n = (int) Math.round(dauer * AuflaufMassstabCheck.BILDER_JE_S);
            ByteBuffer zeile = ByteBuffer.allocate(4 * Math.max(ny, Math.max(3 * nx, 4 * np))).order(ByteOrder.LITTLE_ENDIAN);
            long t1 = System.nanoTime();
            try (OutputStream oSaum = strom(dir.resolve("saum.f32")); OutputStream oSonde = strom(dir.resolve("sonde.f32"));
                 OutputStream oRand = strom(dir.resolve("rand.f32")); OutputStream oOst = strom(dir.resolve("ostzu.f32"));
                 PrintWriter bil = new PrintWriter(Files.newBufferedWriter(dir.resolve("bilanz.txt"), StandardCharsets.UTF_8))) {
                bil.println("t V-V0 W E S N versickert abgeschnitten");
                for (int q = 0; q <= n; q++) {
                    double t = t0 + (double) q / AuflaufMassstabCheck.BILDER_JE_S;
                    Ablauf ab = new Ablauf(g);
                    k.zeit(ab, t);
                    k.bild(ab, bl.ost, bl.nord);
                    au.vorbereiten(ab, null, k, t);
                    au.karte().saum(ab);
                    g.rechnen.einmal(c -> ab.aufnehmen(c, null));
                    if (q % jeAufnahme == 0) {
                        double[] z = au.karte().saumLesen();
                        zeile.clear();
                        for (int j = 0; j < ny; j++) zeile.putFloat((float) z[j]);
                        oSaum.write(zeile.array(), 0, 4 * ny);
                        for (int j : SONDEN) {
                            float[] r = au.karte().zeileLesen(j);
                            zeile.clear();
                            for (float x : r) zeile.putFloat(x);
                            oSonde.write(zeile.array(), 0, 4 * r.length);
                        }
                        float[] rw = au.randwelle().lies();
                        zeile.clear();
                        for (int i = 0; i < np; i++) for (int a = 0; a < 4; a++) zeile.putFloat(rw[8 * i + a]);
                        oRand.write(zeile.array(), 0, 16 * np);
                    }
                    if (q % (10 * AuflaufMassstabCheck.BILDER_JE_S) == 0) {
                        au.karte().runter();
                        double[] w = au.karte().randZufluss(Swash.W), e = au.karte().randZufluss(Swash.E), so = au.karte().randZufluss(Swash.S), no = au.karte().randZufluss(Swash.N);
                        bil.printf(Locale.ROOT, "%.1f %.4f %.4f %.4f %.4f %.4f %.4f %.4f%n", q / (double) AuflaufMassstabCheck.BILDER_JE_S, s.volumen() - v0, sum(w), sum(e), sum(so), sum(no), s.versickert(), s.abgeschnitten());
                        bil.flush();
                        zeile.clear();
                        for (int j = 0; j < ny; j++) zeile.putFloat((float) e[j]);
                        oOst.write(zeile.array(), 0, 4 * ny);
                        if (q % (60 * AuflaufMassstabCheck.BILDER_JE_S) == 0) {
                            float[] hh = new float[nx * ny];
                            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) hh[j * nx + i] = (float) s.h[s.id(i, j)];
                            schreibe(dir.resolve(String.format(Locale.ROOT, "h_%04d.f32", q / AuflaufMassstabCheck.BILDER_JE_S)), hh);
                        }
                        System.out.printf(Locale.GERMAN, "  %5.0f s: V−V0 %.1f m³, W %+.1f, E %+.1f, S %+.1f, N %+.1f m³ (%.0f s Rechenzeit)%n", q / (double) AuflaufMassstabCheck.BILDER_JE_S,
                                s.volumen() - v0, sum(w), sum(e), sum(so), sum(no), (System.nanoTime() - t1) / 1e9);
                    }
                }
            }
            System.out.println("an: " + au.an + ", Schritte " + au.schritte + ", verpasst " + au.verpasst);
        } finally {
            b.geraet().ruhe();
            sf.close();
        }
    }

    /**
     * Alle aktiven Kanten der Brandung zur Zeit t0 (wie {@link AuflaufCheck#kante}), je Art gezählt; die 40 mit dem
     * höchsten Seegang am Fuß unter den Arten 0–2 mit Lage, Neigung, ξ₀ und Abstand zur nächsten Kante der Arten 3–5.
     */
    static void kantenliste(Buehne b, double t0, String titel) {
        com.dan.render.brandung.Brandungsbild bb = b.brandungsbild();
        if (bb == null || bb.quelle == null) { System.out.println("Kanten " + titel + ": keine Brandung"); return; }
        com.dan.core.szene.Brandung such = new com.dan.core.szene.Brandung(bb.brandung.e0, bb.brandung.n0);
        such.weite = 1e9;
        such.abschnitteMax = Integer.MAX_VALUE;
        such.protokollieren = true;
        double[] w = bb.quelle.wind();
        such.rechne(bb.kanten, bb.quelle.wasser(), t0, bb.brandung.e0, bb.brandung.n0, w[0], w[1]);
        java.util.List<Kanten.Kante> alle = bb.kanten.kanten;
        int[] jeArt = new int[8];
        java.util.List<double[]> kand = new java.util.ArrayList<>();
        for (double[] q : such.protokoll) {
            Kanten.Kante ka = alle.get((int) q[0]);
            jeArt[Math.min(7, ka.art())]++;
            if (ka.art() <= 2) kand.add(q);
        }
        kand.sort((x, y) -> Double.compare(y[6], x[6]));
        System.out.printf(Locale.GERMAN, "%nKanten %s: aktiv %d (Art 0: %d, Fenne %d, Salzwiese %d, Deich %d, Warft %d, Deckwerk %d)%n",
                titel, such.protokoll.size(), jeArt[0], jeArt[1], jeArt[2], jeArt[3], jeArt[4], jeArt[5]);
        System.out.println("  Nr Art Ost Nord Fuss Krone tanA Hs Tp xi0 Abstand_Bauwerk_m");
        for (int r = 0; r < Math.min(40, kand.size()); r++) {
            double[] q = kand.get(r);
            Kanten.Kante ka = alle.get((int) q[0]);
            double ab = Double.POSITIVE_INFINITY;
            for (Kanten.Kante o : alle) if (o.art() >= 3 && o.art() <= 5) ab = Math.min(ab, Math.hypot(o.ost() - ka.ost(), o.nord() - ka.nord()));
            double l0 = 9.81 * q[7] * q[7] / (2 * Math.PI), xi0 = ka.neigung() / Math.sqrt(Math.max(1e-3, q[6]) / l0);
            System.out.printf(Locale.ROOT, "  %d %d %.0f %.0f %.2f %.2f %.3f %.2f %.1f %.2f %.0f%n", (int) q[0], ka.art(), ka.ost(), ka.nord(), ka.fuss(), ka.krone(), ka.neigung(), q[6], q[7], xi0, ab);
        }
    }

    static double sum(double[] a) { double s = 0; for (double x : a) s += x; return s; }

    static OutputStream strom(Path p) throws IOException { return new BufferedOutputStream(new FileOutputStream(p.toFile()), 1 << 20); }

    static void schreibe(Path p, float[] f) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4 * f.length).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : f) bb.putFloat(x);
        Files.write(p, bb.array());
    }
}
