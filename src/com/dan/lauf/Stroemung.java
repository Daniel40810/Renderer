package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Bus;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.takt.Takt;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.hydro.Einseitig;
import com.dan.physics.hydro.Hydro;
import com.dan.physics.hydro.HydroModul;
import com.dan.physics.hydro.Rechnung;
import com.dan.physics.hydro.gpu.KarteRechnung;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Strömung zu einem abgelegten Lauf (P11c1, Werkbuch HalligHooge Teil A, 12.12; F110 A, F116 A): hydro rechnet G3
 * noch einmal, allein, mit dem Rand einseitig aus G2 im Archiv ({@link HydroModul#vorgabe0}), und legt ab, was das
 * Archiv nicht hält: {@code water.u}, {@code water.v} (in der Zelle, Achsen des Gitters; float16), {@code water.q} (Fluss
 * je Kante als Mittel über das Intervall, m²/s; float32) und den eigenen Wasserstand {@code water.eta} (float32), im
 * Unterordner {@code hydro_G3} des Archivs, zu denselben Zeiten wie das Archiv.
 * <ul>
 * <li><b>Anfang:</b> der erste Stand von G3 aus dem Archiv (Wasserstand je Zelle), in Ruhe (u = 0). Prüfstein 15 misst
 * darum erst nach {@link Einstellung#anlauf}.</li>
 * <li><b>Antrieb:</b> Wind und Luftdruck aus ERA5 wie im Lauf (Windschub nach der Angabe im Kopf des Archivs), ohne
 * Anlauf; keine Gleichgewichtstide (über 14 km ohne Gewicht), <b>keine Kraft des Seegangs</b> (F116 A: der Rand aus G2
 * trägt den Stau schon).</li>
 * <li><b>Boden:</b> Hält das Archiv {@code boden_G3}, verschiebt hydro die Tabellen je Intervall um {@code bed.dz} wie
 * im Lauf ({@link HydroModul#bodenquelle}). Die Rauheit aus den Rippeln ({@code surf.z0}) ist nicht abgelegt und fehlt.</li>
 * <li><b>Prüfung</b> nebenher: Wasserstand gegen das Archiv in Zellen, die in beiden nass sind (Prüfstein 15), und die
 * Massenbilanz eines Teilbeckens aus den Flüssen über seinen Rand gegen die Änderung seines Volumens (Prüfstein 16).</li>
 * </ul>
 */
public final class Stroemung implements AutoCloseable {
    public static final String VERSION = "0.1";
    public static final String ORDNER = "hydro_";
    static final String[] SPUREN = {"water.u", "water.v", "water.q", "water.eta"};

    public static final class Einstellung {
        /** Ordner des Gebiets (Packungen, Bauwerke, Wetter). */
        public File gebiet;
        /** Ordner des abgelegten Laufs mit {@code G2/} und {@code G3/}. */
        public Path archiv;
        /** Wohin {@code hydro_G3} kommt; null: in {@link #archiv}. Ein alter Ordner dort wird ersetzt. */
        public Path ziel;
        public boolean ablegen = true;
        /** Auf der Karte (Betrieb) oder dem Prozessor (Referenz, double). */
        public boolean karte = true;
        /** Den Boden aus {@code boden_G3} anwenden, wenn das Archiv ihn hält. */
        public boolean boden = true;
        /** Zeit nach dem Anfang, ab der Prüfstein 15 zählt (s) [G]. */
        public double anlauf = 7200;
        /** Das Teilbecken für Prüfstein 16 in Zellen von G3 (i0, j0, i1, j1; ausschließlich); null: das mittlere Drittel des Fensters. */
        public int[] becken;
        /** Zum Prüfen: höchstens bis zu dieser Zeit nach dem Anfang rechnen (s); NaN: bis zum letzten Stand. */
        public double hoechstens = Double.NaN;
        /**
         * Seit P13b (F153 A, F154 A): die Salzwiese aus {@code vegetation/zonen} des Gebiets rechnen lassen
         * ({@code physics.vegetation}); hydro liest dann {@code surf.z0} auf G3 (nur der Beitrag der Pflanzen).
         */
        public boolean vegetation;
    }

    public final Einstellung e;
    public final Bus bus;
    public final Takt takt;
    public final HydroModul hydro;
    public final Hydro h2, h3;
    public final Archiv a2, a3;
    /** Das Archiv des Bodens (null ohne). */
    public final Archiv aBoden;
    public final List<String> hinweise;
    /** Erster und letzter Stand (s seit dem Start des Archivs), ihr Abstand. */
    public final double erste, letzte, kopplung;
    private Archiv ablage;
    private final Feld f2eta, f2h, f3eta, f3h, fdz, fu, fv, fq, feta;

    // Prüfstein 15
    /** Summe der Quadrate, Zahl der Vergleiche, größte Abweichung (m), Histogramm |Δη| in mm (0–1000). */
    public double summeQ, groesste;
    public long vergleiche;
    public final long[] histogramm = new long[1001];
    /** Prüfstein 15 aufgeteilt (zur Auskunft): 0 Randstreifen ({@link #RAND} Zellen), 1 innen flach (Tiefe im Archiv unter 0,5 m), 2 innen tief. */
    public final double[] klasseQ = new double[3];
    public final long[] klasseN = new long[3];
    /** Breite des Randstreifens (Zellen). */
    public static final int RAND = 10;
    /** Ort der größten Abweichung: Zelle i, j, Zeit (s seit dem ersten Stand), Tiefe im Archiv (m). */
    public int groessteI, groessteJ;
    public double groessteT, groessteH;
    /** Mittel der Abweichung (Archiv − Nachlauf, m), zur Auskunft über einen Versatz. */
    public double summe;
    // Prüfstein 16
    public final int[] becken;
    /** Volumen des Beckens am Anfang und zuletzt (m³), Summe der Zuflüsse über seinen Rand (m³), größte Spanne des Volumens (m³). */
    public double volumenAnfang, volumen, zufluss, volumenMin = Double.POSITIVE_INFINITY, volumenMax = Double.NEGATIVE_INFINITY;
    /** Abgelegte Stände, Sekunden je Teil. */
    public long staende;
    public double sekundenRechnen, sekundenAblage;

    private Stroemung(Einstellung e, Bus bus, HydroModul hy, Hydro h2, Hydro h3, Archiv a2, Archiv a3, Archiv ab, List<String> hinweise) {
        this.e = e;
        this.bus = bus;
        hydro = hy;
        this.h2 = h2;
        this.h3 = h3;
        this.a2 = a2;
        this.a3 = a3;
        aBoden = ab;
        this.hinweise = hinweise;
        erste = a3.zeit(0);
        letzte = Double.isNaN(e.hoechstens) ? a3.zeit(a3.staende() - 1) : Math.min(a3.zeit(a3.staende() - 1), erste + e.hoechstens);
        kopplung = a3.zeit(1) - erste;
        Katalog k = Katalog.grundstufe();
        f2eta = a2.feld("water.eta", k);
        f2h = a2.feld("water.h", k);
        f3eta = a3.feld("water.eta", k);
        f3h = a3.feld("water.h", k);
        fdz = ab == null ? null : ab.feld("bed.dz", k);
        Gitter g = h3.gitter;
        fu = new Feld(k.verlange("water.u"), g, Feld.Zahl.FLOAT);
        fv = new Feld(k.verlange("water.v"), g, Feld.Zahl.FLOAT);
        fq = new Feld(k.verlange("water.q"), g, Feld.Zahl.FLOAT);
        feta = new Feld(k.verlange("water.eta"), g, Feld.Zahl.FLOAT);
        becken = e.becken != null ? e.becken.clone() : new int[] {g.nx / 3, g.ny / 3, 2 * g.nx / 3, 2 * g.ny / 3};
        takt = new Takt(bus);
    }

    /** Baut hydro auf G2 (nur als Rand) und G3, den Antrieb und den Bus; setzt alles auf den ersten Stand des Archivs. */
    public static Stroemung bauen(Einstellung e) throws IOException {
        Gebiet ge = Gebiet.lies(e.gebiet);
        Archiv a2 = Archiv.oeffnen(e.archiv.resolve("G2")), a3 = Archiv.oeffnen(e.archiv.resolve("G3"));
        Archiv ab = null;
        Rechnung r = null;
        try {
            if (a3.staende() < 2) throw new IOException("Strömung: " + e.archiv + " hält weniger als zwei Stände von G3");
            Gitter g2a = a2.lauf.gitter.get("G2"), g3a = a3.lauf.gitter.get("G3"), g2 = ge.gitter("G2"), voll = ge.gitter("G3");
            if (g2a == null || g3a == null) throw new IOException("Strömung: " + e.archiv + " hält G2 oder G3 nicht");
            if (!g2.gleich(g2a)) throw new IOException("Strömung: G2 im Archiv (" + g2a + ") ist nicht G2 des Gebiets (" + g2 + ")");
            Hydro h2 = new Hydro(g2, ge.packung("G2"));
            if (new File(ge.eintrag("G2").ordner(), "bauwerke.txt").isFile()) h2.bauwerke(ge.bauwerke("G2"));
            h2.fuellen(0);
            int i0 = (int) Math.round((g3a.x0 - voll.x0) / voll.dx), j0 = (int) Math.round((g3a.y0 - voll.y0) / voll.dy);
            if (i0 < 0 || j0 < 0 || i0 + g3a.nx > voll.nx || j0 + g3a.ny > voll.ny || Math.abs(voll.dx - g3a.dx) > 1e-9)
                throw new IOException("Strömung: G3 im Archiv (" + g3a + ") ist kein Fenster von G3 des Gebiets (" + voll + ")");
            Gitter gw = Gitter.utm("G3", voll.zone, voll.x0 + i0 * voll.dx, voll.y0 + j0 * voll.dy, voll.dx, g3a.nx, g3a.ny);
            Hydro h3 = new Hydro(gw, ge.packung("G3").fenster(voll, i0, j0, g3a.nx, g3a.ny));
            if (new File(ge.eintrag("G3").ordner(), "bauwerke.txt").isFile()) h3.bauwerke(ge.bauwerke("G3"), i0, j0);
            Katalog k = Katalog.grundstufe();
            double erste = a3.zeit(0);
            // Boden: erst die Tabellen verschieben, dann den Wasserstand setzen (das Volumen gilt auf dem verschobenen Boden)
            Path pb = e.archiv.resolve("boden_G3");
            if (e.boden && Files.isRegularFile(pb.resolve("lauf.txt"))) {
                ab = Archiv.oeffnen(pb);
                Feld dz = ab.feld("bed.dz", k);
                ab.lies("bed.dz", erste, dz);
                double[] d = new double[h3.zellen];
                for (int c = 0; c < d.length; c++) d[c] = dz.hol(c);
                h3.boden(d);
            }
            Feld eta = a3.feld("water.eta", k);
            a3.lies("water.eta", 0, eta);
            for (int c = 0; c < h3.zellen; c++) h3.setze(c, eta.hol(c));
            java.util.Arrays.fill(h3.u, 0);
            h2.zeit = erste;                                               // die Uhr der Gitter auf den ersten Stand (das Archiv beginnt nicht bei 0)
            h3.zeit = erste;
            r = e.karte ? new KarteRechnung() : new Rechnung.Prozessor();
            HydroModul hy = new HydroModul(List.of(new Hydro[] {h2}, new Hydro[] {h3}), r, a3.zeit(1) - erste, true, true, Set.of(), Set.of());
            final Feld v2eta = a2.feld("water.eta", k), v2h = a2.feld("water.h", k);
            hy.vorgabe0 = t -> {
                try {
                    a2.lies("water.eta", t, v2eta);
                    a2.lies("water.h", t, v2h);
                } catch (IOException x) {
                    throw new java.io.UncheckedIOException(x);
                }
                return Einseitig.Stand.aus(v2eta.f.clone(), v2h.f.clone());
            };
            if (ab != null) {
                final Archiv abb = ab;
                final Feld dz = ab.feld("bed.dz", k);
                hy.boden = Set.of("G3");
                hy.bodenquelle = (t, ziel) -> {
                    try {
                        abb.lies("bed.dz", t, dz);
                    } catch (IOException x) {
                        throw new java.io.UncheckedIOException(x);
                    }
                    for (int c = 0; c < ziel.length; c++) ziel[c] = dz.hol(c);
                    return true;
                };
            }
            Antrieb an = new Antrieb(Wetterfeld.lies(e.gebiet, "era5", a3.lauf.start), a3.lauf.start);
            an.kopplung = hy.kopplung;
            an.anlauf = 0;
            String w = a3.lauf.antrieb.getOrDefault("wetter", "");
            an.schub = w.contains("Charnock") ? Antrieb.Schub.CHARNOCK : w.contains("Wu") ? Antrieb.Schub.WU : Antrieb.Schub.ERA5;
            Bus bus = new Bus(Katalog.grundstufe());
            if (e.vegetation) hy.rauheitGitter = Set.of(gw.name);   // vor dem Anmelden: hydro meldet surf.z0 in anmelden an
            bus.anmelden(an);
            // Salzwiese vor hydro: hydro liest surf.z0 schon in beginn (Boden aus dem Archiv), Takt.beginn folgt der Anmeldung
            if (e.vegetation) {
                com.dan.core.szene.Zonen zo = com.dan.core.szene.Zonen.lies(e.gebiet);
                if (zo == null) throw new IOException("Strömung: Salzwiese verlangt, aber " + new File(e.gebiet, com.dan.core.szene.Zonen.ORDNER) + " fehlt (Aufbereitung, Schritt zonen)");
                com.dan.core.szene.Gestalt gs = com.dan.core.szene.Gestalt.lies(new File(e.gebiet, "gestalt"));
                com.dan.physics.vegetation.VegetationModul vm = new com.dan.physics.vegetation.VegetationModul(List.of(gw), zo, gs,
                        new com.dan.physics.vegetation.Salzwiese(), hy.kopplung, a3.lauf.start);
                bus.anmelden(vm).anmelden(new com.dan.core.bus.Verbund(bus.katalog, "surf.z0", gw, Feld.Zahl.FLOAT, hy.kopplung, "vegetation"));
            }
            bus.anmelden(hy);
            List<String> hin = bus.verdrahten();
            Stroemung s = new Stroemung(e, bus, hy, h2, h3, a2, a3, ab, hin);
            if (!an.deckt(s.erste) || !an.deckt(s.letzte)) throw new IOException("Strömung: das Wetter reicht nicht von " + an.zeit(s.erste) + " bis " + an.zeit(s.letzte));
            s.takt.beginn(s.erste);
            s.volumenAnfang = s.beckenVolumen();
            if (e.ablegen) s.ablageAnlegen(an);
            s.lege(s.erste, 0);
            return s;
        } catch (IOException | RuntimeException x) {
            if (r != null) r.close();
            a2.close();
            a3.close();
            if (ab != null) ab.close();
            throw x;
        }
    }

    private void ablageAnlegen(Antrieb an) throws IOException {
        Path ziel = (e.ziel != null ? e.ziel : e.archiv).resolve(ORDNER + h3.gitter.name);
        if (Files.exists(ziel)) try (var w = Files.walk(ziel)) {
            for (Path q : w.sorted(Comparator.reverseOrder()).toList()) Files.delete(q);
        }
        Archiv.Lauf kopf = new Archiv.Lauf();
        kopf.name = "Nachlauf hydro zu " + a3.lauf.name;
        kopf.start = a3.lauf.start;
        kopf.seed = a3.lauf.seed;
        for (Map.Entry<String, String> x : a3.lauf.antrieb.entrySet()) kopf.antrieb.put(x.getKey(), x.getValue());
        kopf.antrieb.put("stroemung", "Strömung " + VERSION + ": hydro auf G3 allein, Rand einseitig aus G2 im Archiv, Anfang aus dem ersten Stand in Ruhe, Wind "
                + an.schub + " aus ERA5 ohne Anlauf, ohne Gleichgewichtstide, ohne Kraft des Seegangs (F116 A)" + (aBoden != null ? ", Boden je Intervall aus boden_G3, ohne Rauheit aus Rippeln" : "") + (e.vegetation ? ", mit Salzwiese (Rauheit aus physics.vegetation)" : ""));
        for (var m : bus.module()) kopf.module.put(m.name(), m.version());
        kopf.module.put("stroemung", VERSION);
        ablage = Archiv.anlegen(ziel, kopf);
        ablage.spur(fu, Archiv.Ablage.HALB).spur(fv, Archiv.Ablage.HALB).spur(fq, Archiv.Ablage.FLOAT).spur(feta, Archiv.Ablage.FLOAT);
    }

    /** Meldet jede Stunde Szenenzeit eine Zeile (null: still). */
    public java.util.function.Consumer<String> fortschritt;

    /** Rechnet bis zum letzten Stand, legt jeden Stand ab und prüft nebenher. */
    public void rechne() throws IOException {
        for (int k = 1; k < a3.staende(); k++) {
            double t = a3.zeit(k);
            if (t > letzte + 1e-6) break;
            long n0 = System.nanoTime();
            takt.bis(t);
            sekundenRechnen += (System.nanoTime() - n0) / 1e9;
            pruefe(k, t);
            lege(t, kopplung);
            if (fortschritt != null && (k % 12 == 0 || t >= letzte - 1e-6))
                fortschritt.accept(String.format(java.util.Locale.ROOT, "  %5.1f h von %.1f h, Rechnen %.0f s, RMS bisher %.1f cm", (t - erste) / 3600, (letzte - erste) / 3600,
                        sekundenRechnen, 100 * rms()));
        }
    }

    /** Prüfstein 15 und 16 zum Stand k (Zeit t). */
    private void pruefe(int k, double t) throws IOException {
        a3.lies("water.eta", k, f3eta);
        a3.lies("water.h", k, f3h);
        if (t - erste >= e.anlauf) {
            for (int c = 0; c < h3.zellen; c++) {
                if (!(f3h.hol(c) > 0) || !(h3.vol[c] > 0)) continue;
                double d = f3eta.hol(c) - h3.eta[c];
                summeQ += d * d;
                summe += d;
                vergleiche++;
                int i = c % h3.nx, j = c / h3.nx;
                double ha = f3h.hol(c);
                int kl = i < RAND || j < RAND || i >= h3.nx - RAND || j >= h3.ny - RAND ? 0 : ha < 0.5 ? 1 : 2;
                klasseQ[kl] += d * d;
                klasseN[kl]++;
                if (Math.abs(d) > groesste) {
                    groessteI = i;
                    groessteJ = j;
                    groessteT = t - erste;
                    groessteH = ha;
                }
                groesste = Math.max(groesste, Math.abs(d));
                histogramm[(int) Math.min(1000, Math.round(Math.abs(d) * 1000))]++;
            }
        }
        // Becken: Zufluss über den Rand aus den Summen der Flüsse des Intervalls (m³), Volumen jetzt
        int[] b = becken;
        int nx = h3.nx;
        double z = 0;
        for (int j = b[1]; j < b[3]; j++) {
            z += h3.qSumme[b[0] + (nx + 1) * j];                              // West: positiv nach Osten, hinein
            z -= h3.qSumme[b[2] + (nx + 1) * j];                              // Ost: hinaus
        }
        for (int i = b[0]; i < b[2]; i++) {
            z += h3.qSumme[h3.kanteY(i, b[1])];                               // Süd: positiv nach Norden, hinein
            z -= h3.qSumme[h3.kanteY(i, b[3])];                               // Nord
        }
        zufluss += z;
        volumen = beckenVolumen();
        volumenMin = Math.min(volumenMin, volumen);
        volumenMax = Math.max(volumenMax, volumen);
    }

    double beckenVolumen() {
        int[] b = becken;
        double v = 0;
        for (int j = b[1]; j < b[3]; j++) for (int i = b[0]; i < b[2]; i++) v += h3.vol[i + h3.nx * j];
        return v;
    }

    /** Legt den Stand zur Zeit t ab; {@code tau}: Länge des Intervalls (0 am Anfang: Flüsse 0). */
    private void lege(double t, double tau) throws IOException {
        if (ablage == null) return;
        long n0 = System.nanoTime();
        for (int c = 0; c < h3.zellen; c++) {
            double[] uv = h3.geschwindigkeit(c);
            fu.f[c] = (float) uv[0];
            fv.f[c] = (float) uv[1];
            feta.f[c] = (float) h3.eta[c];
        }
        for (int q = 0; q < h3.kanten; q++) fq.f[q] = tau > 0 ? (float) (h3.qSumme[q] / (tau * h3.laenge(q))) : 0f;
        ablage.stand(t, fu, fv, fq, feta);
        staende++;
        sekundenAblage += (System.nanoTime() - n0) / 1e9;
    }

    /** RMS der Abweichung im Wasserstand (m) und der Anteil 95 % (m), nach dem Anlauf. */
    public double rms() { return vergleiche == 0 ? Double.NaN : Math.sqrt(summeQ / vergleiche); }

    public double quantil(double p) {
        long ziel = (long) Math.ceil(p * vergleiche), s = 0;
        for (int i = 0; i < histogramm.length; i++) {
            s += histogramm[i];
            if (s >= ziel) return i / 1000.0;
        }
        return 1;
    }

    /** Fehler der Bilanz des Beckens: |ΔV − Zufluss| durch die Spanne seines Volumens (Tidevolumen). */
    public double bilanzFehler() {
        double sp = volumenMax - volumenMin;
        return sp > 0 ? Math.abs(volumen - volumenAnfang - zufluss) / sp : Double.NaN;
    }

    /** Das Gitter des Nachlaufs (das Fenster von G3 im Archiv). */
    public Gitter gitter() { return h3.gitter; }

    @Override public void close() throws IOException {
        if (ablage != null) ablage.close();
        ablage = null;
        hydro.rechnung.close();
        a2.close();
        a3.close();
        if (aBoden != null) aBoden.close();
    }

    /** Die Archive mit Strömung neben einem Lauf: Unterordner {@code hydro_<Gitter>}. */
    public static List<String> vorhanden(Path archiv) throws IOException {
        List<String> l = new ArrayList<>();
        if (!Files.isDirectory(archiv)) return l;
        try (var s = Files.list(archiv)) {
            for (Path p : s.toList()) if (p.getFileName().toString().startsWith(ORDNER) && Files.isRegularFile(p.resolve("lauf.txt"))) l.add(p.getFileName().toString());
        }
        return l;
    }
}
