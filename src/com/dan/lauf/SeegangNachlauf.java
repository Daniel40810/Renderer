package com.dan.lauf;

import com.dan.core.archiv.Archiv;
import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.takt.Takt;
import com.dan.physics.antrieb.Antrieb;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.WavesModul;
import com.dan.physics.waves.gpu.WavesRechner;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Der Seegang zu einem abgelegten Lauf (Seegang Werkbuch, S1; SF6 A): {@code physics.waves} rechnet die Gitter des
 * Seegangs (G2w, G3w) noch einmal, mit Wasser und Strömung aus dem Archiv, und legt ab, was das Archiv nicht hält:
 * Windsee und Dünung getrennt ({@code waves.sea}, {@code waves.swell}), die Dissipation nach Quellen
 * ({@code waves.dwc} Schaumkronen und {@code .dbf} Reibung als Raten aus dem Spektrum, {@code .dbr} Brechen und Kappung
 * als {@code diss} − {@code dwc}: Der Schritt der Quellen ist halb implizit, in stark brechenden Zellen verliert ein
 * Schritt von 60 s weniger als die Rate mal 60 s; was die Brandung bekommt, soll das sein, was das Modell verliert) und die Orbitalgeschwindigkeit am Boden ({@code waves.ubot}), dazu
 * die Kennwerte des Archivs neu ({@code waves.hs}, {@code .tp}, {@code .dir}, {@code .mss}, {@code .diss}), je Gitter
 * im Ordner {@code seegang_<Gitter>} neben dem Archiv, zu den Zeiten der Stände von {@code waves_<Gitter>}.
 * <ul>
 * <li><b>Wasser:</b> {@code water.h} aus G2 und G3 des Archivs (zwischen zwei Ständen linear), die Strömung auf G3 aus
 * dem Nachlauf der Strömung ({@code hydro_G3}, {@link Stroemung}), auf G2 keine (das Archiv hält sie nicht; im Lauf
 * rechnete G2w mit Strömung). Ein Modul {@code hydro} speist den Bus damit, wie {@link Archivspeiser}.</li>
 * <li><b>Wind:</b> ERA5 wie im Lauf (Schub nach dem Kopf des Archivs), ohne Anlauf; der Rand des gröbsten Gitters aus
 * dem Seegang von ERA5 ({@code antrieb/seegang}), wie im Lauf.</li>
 * <li><b>Anfang und Vorlauf [G]:</b> {@link Einstellung#vorlauf} vor dem ersten Stand beginnt der Seegang mit einem
 * JONSWAP je Zelle aus Hs, Tp und Richtung des ersten Stands im Archiv ({@link Waves#jonswap}, cos^(2s) mit
 * s = {@link Einstellung#breite}); im Vorlauf gilt das Wasser des ersten Stands (stetig am ersten Stand, passend zum
 * Anfang), der Wind der wahren Zeit. Abgelegt wird ab dem ersten Stand.</li>
 * <li><b>Prüfung</b> nebenher: Hs des Nachlaufs gegen Hs des Archivs je Gitter (Zellen mit Hs im Archiv über
 * {@link #HS_MIN}); Windsee und Dünung zusammen gegen Hs; die Summe von Schaumkronen und Brechen gegen {@code diss};
 * Werte des Spektrums, die nicht endlich oder negativ sind.</li>
 * </ul>
 * Was gerechnet wird, ist Werkbuch HalligHooge 7.3 und 12.9 (P8b, P8c) unverändert; die Diagnose ist neu (Waves 0.3).
 */
public final class SeegangNachlauf implements AutoCloseable {
    public static final String VERSION = "0.1";
    /** Ordner der Ablage je Gitter: {@code seegang_<Gitter>}. */
    public static final String ORDNER = "seegang_";
    /** Die Spuren der Ablage je Gitter, in dieser Folge. */
    public static final String[] SPUREN = {"waves.hs", "waves.tp", "waves.dir", "waves.ubot", "waves.mss", "waves.diss", "waves.sea", "waves.swell",
            "waves.dwc", "waves.dbr", "waves.dbf"};
    /** Nur Zellen mit Hs im Archiv darüber zählen in der Prüfung (m) [G]. */
    public static final double HS_MIN = 0.1;
    /** Ab dieser Zeit nach dem ersten Stand zählt die Prüfung „nach dem Anlauf“ (s) [G]. */
    public static final double ANLAUF = 3 * 3600;

    public static final class Einstellung {
        /** Ordner des Gebiets ({@code antrieb/era5}, {@code antrieb/seegang}). */
        public File gebiet;
        /** Ordner des abgelegten Laufs mit {@code G2/}, {@code G3/} und {@code waves_<Gitter>/}, dazu {@code hydro_G3/} aus dem Nachlauf der Strömung. */
        public Path archiv;
        /** Wohin {@code seegang_<Gitter>} kommt; null: in {@link #archiv}. Alte Ordner dort werden ersetzt. */
        public Path ziel;
        public boolean ablegen = true;
        /** Auf der Karte (Betrieb) oder dem Prozessor (Referenz, double). */
        public boolean karte = true;
        /** Vorlauf vor dem ersten Stand (s) [G]; ein Vielfaches der Kopplung des Seegangs. */
        public double vorlauf = 7200;
        /** s des Anfangsspektrums, cos^(2s)((θ − θ_m)/2) [G]. */
        public double breite = 10;
        /** Zum Prüfen: höchstens bis zu dieser Zeit nach dem ersten Stand (s); NaN: bis zum letzten Stand. */
        public double hoechstens = Double.NaN;
        /** Stützstellen {Frequenzen, Richtungen}; null: wie im Archiv. Kleiner nur zum Prüfen der Verdrahtung. */
        public int[] stuetzen;
        /** Die Strömung aus {@code hydro_G3} nehmen, wenn das Archiv sie hält. */
        public boolean strom = true;
        /**
         * Seit S3c (Seegang Werkbuch, SF14): erst ab dem Stand, der dieser Zeit am nächsten liegt (Anfang und Vorlauf
         * dort); null: ab dem ersten Stand. Zur Messung eines Ausschnitts.
         */
        public java.time.Instant ab;
    }

    /** Index des Stands, mit dem der Nachlauf anfängt (0 ohne {@link Einstellung#ab}). */
    private int anfangStand;

    public final Einstellung e;
    public final Bus bus;
    public final Takt takt;
    public final WavesModul waves;
    public final Antrieb antrieb;
    final Wasser wasser;
    /** Die Archive des Seegangs aus dem Lauf, je Gitter (gröbstes zuerst). */
    public final List<Archiv> original;
    public final List<String> hinweise;
    /** Erster und letzter abgelegter Stand, Beginn mit Vorlauf (s seit dem Start des Archivs), Kopplung des Seegangs (s). */
    public final double erste, letzte, beginn, kopplung;
    private final List<Archiv> ablage = new ArrayList<>();
    private final Katalog katalog = Katalog.grundstufe();

    // ---- Prüfung je Gitter
    /** Summe (Hs_N − Hs_A)², Summe Hs_A², Summe (Hs_N − Hs_A), Zahl; dasselbe nach {@link #ANLAUF}; größte Abweichung (m). */
    public final double[] dq, aq, dsum, dqSpaet, aqSpaet, groesste;
    public final long[] n, nSpaet;
    /** Größte relative Abweichung von Hs_Windsee² + Hs_Dünung² gegen Hs² (–), je Gitter. */
    public final double[] partFehler;
    /**
     * Summe über Zellen und Stände (W/m²): Schaumkronen + Brechen als Raten der Diagnose, {@code diss} des Schritts,
     * Reibung, Schaumkronen, Brechen als Rate, Brechen wie abgelegt ({@code diss} − Schaumkronen, mindestens 0).
     */
    public final double[] summeWcBr, summeDiss, summeBf, summeWc, summeBr, summeBrSchritt;
    /** Anteil der Windsee an m₀ über alle Zellen und Stände (zur Auskunft). */
    public final double[] m0Windsee, m0Alle;
    /** Werte des Spektrums, die nicht endlich oder negativ sind, über alle Stände. */
    public long unheil;
    public long staende;
    public double sekundenRechnen, sekundenAblage;

    private SeegangNachlauf(Einstellung e, Bus bus, WavesModul wm, Antrieb an, Wasser wa, List<Archiv> original, List<String> hinweise, double erste, double letzte,
                            double beginn, double kopplung) {
        this.e = e;
        this.bus = bus;
        waves = wm;
        antrieb = an;
        wasser = wa;
        this.original = original;
        this.hinweise = hinweise;
        this.erste = erste;
        this.letzte = letzte;
        this.beginn = beginn;
        this.kopplung = kopplung;
        int g = wm.gitter.size();
        dq = new double[g]; aq = new double[g]; dsum = new double[g]; dqSpaet = new double[g]; aqSpaet = new double[g]; groesste = new double[g];
        n = new long[g]; nSpaet = new long[g];
        partFehler = new double[g];
        summeWcBr = new double[g]; summeDiss = new double[g]; summeBf = new double[g]; summeWc = new double[g]; summeBr = new double[g]; summeBrSchritt = new double[g];
        m0Windsee = new double[g]; m0Alle = new double[g];
        takt = new Takt(bus);
    }

    /** Die Gitter des Seegangs eines Archivs: Ordner {@code waves_<Gitter>}, gröbstes zuerst (nach dem Namen). */
    public static List<Path> seegangOrdner(Path archiv) throws IOException {
        List<Path> l = new ArrayList<>();
        if (!Files.isDirectory(archiv)) return l;
        try (var s = Files.list(archiv)) {
            for (Path p : s.sorted().toList()) if (p.getFileName().toString().startsWith("waves_") && Files.isRegularFile(p.resolve("lauf.txt"))) l.add(p);
        }
        return l;
    }

    /** Baut Speiser, Antrieb und Seegang, verdrahtet den Bus, setzt den Anfang und beginnt beim Vorlauf. */
    public static SeegangNachlauf bauen(Einstellung e) throws IOException {
        List<Archiv> orig = new ArrayList<>();
        Wasser wa = null;
        WavesModul wm = null;
        try {
            for (Path p : seegangOrdner(e.archiv)) orig.add(Archiv.oeffnen(p));
            if (orig.isEmpty()) throw new IOException("SeegangNachlauf: " + e.archiv + " hält keinen Seegang (waves_<Gitter>)");
            Archiv o0 = orig.get(0);
            if (o0.staende() < 2) throw new IOException("SeegangNachlauf: weniger als zwei Stände des Seegangs");
            List<Gitter> gitter = new ArrayList<>();
            for (Archiv o : orig) {
                if (o.lauf.gitter.size() != 1) throw new IOException("SeegangNachlauf: " + o.ordner + " hält " + o.lauf.gitter.size() + " Gitter");
                gitter.add(o.lauf.gitter.values().iterator().next());
                if (o.staende() != o0.staende() || Math.abs(o.zeit(0) - o0.zeit(0)) > 1e-6) throw new IOException("SeegangNachlauf: die Gitter des Seegangs haben verschiedene Zeiten");
            }
            double kopplung = o0.zeit(1) - o0.zeit(0);
            int i0 = 0;
            if (e.ab != null) {
                double ta = (e.ab.toEpochMilli() - o0.lauf.start.toEpochMilli()) / 1000.0;
                i0 = (int) Math.max(0, Math.min(o0.staende() - 2, Math.round((ta - o0.zeit(0)) / kopplung)));
            }
            double erste = o0.zeit(i0);
            double letzte = o0.zeit(o0.staende() - 1);
            if (!Double.isNaN(e.hoechstens)) letzte = Math.min(letzte, erste + e.hoechstens);
            double vorlauf = Math.max(0, Math.round(e.vorlauf / kopplung)) * kopplung;
            double beginn = erste - vorlauf;
            List<String> hin = new ArrayList<>();
            wa = new Wasser(e.archiv, e.strom, hin);
            if (wa.erste > erste + 1e-6 || wa.letzte < letzte - 1e-6) throw new IOException("SeegangNachlauf: das Wasser im Archiv deckt den Seegang nicht");
            int[] st = e.stuetzen != null ? e.stuetzen : stuetzen(o0.lauf.antrieb.getOrDefault("seegang", ""));
            Archiv kopf = wa.a3;
            Antrieb an = new Antrieb(Wetterfeld.lies(e.gebiet, "era5", kopf.lauf.start), kopf.lauf.start);
            an.kopplung = wa.kopplung;
            an.anlauf = 0;
            String w = kopf.lauf.antrieb.getOrDefault("wetter", "");
            an.schub = w.contains("Charnock") ? Antrieb.Schub.CHARNOCK : w.contains("Wu") ? Antrieb.Schub.WU : Antrieb.Schub.ERA5;
            WavesModul.Rechner wr = e.karte ? new WavesRechner() : new WavesModul.Prozessor();
            try {
                wm = new WavesModul(gitter, st[0], st[1], Wetterfeld.lies(e.gebiet, Wetterfeld.SEEGANG, kopf.lauf.start), kopf.lauf.start, wr, kopplung,
                        an.schub != Antrieb.Schub.WU);
            } catch (RuntimeException | IOException x) {
                wr.close();
                throw x;
            }
            wm.ab = beginn;
            Bus bus = new Bus(Katalog.grundstufe());
            bus.anmelden(an).anmelden(wa).anmelden(wm);
            hin.addAll(bus.verdrahten());
            SeegangNachlauf s = new SeegangNachlauf(e, bus, wm, an, wa, orig, hin, erste, letzte, beginn, kopplung);
            s.anfangStand = i0;
            if (!an.deckt(beginn) || !an.deckt(letzte)) throw new IOException("SeegangNachlauf: das Wetter reicht nicht von " + an.zeit(beginn) + " bis " + an.zeit(letzte));
            wa.halten = erste;                                                     // im Vorlauf das Wasser des ersten Stands
            s.anfang();
            s.takt.beginn(beginn);
            if (e.ablegen) s.ablageAnlegen(st);
            return s;
        } catch (IOException | RuntimeException x) {
            if (wm != null) wm.rechner.close();
            if (wa != null) wa.close();
            for (Archiv o : orig) o.close();
            throw x;
        }
    }

    /** Stützstellen aus dem Kopf des Archivs („28 × 36 Stützstellen, …“); sonst die Vorgabe aus P8a. */
    static int[] stuetzen(String text) {
        Matcher m = Pattern.compile("(\\d+)\\s*[×x]\\s*(\\d+)").matcher(text);
        return m.find() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : new int[] {28, 36};
    }

    /** Der Anfang: je Gitter und Zelle ein JONSWAP aus dem ersten Stand des Archivs; auf die Karte. */
    private void anfang() throws IOException {
        for (int k = 0; k < waves.gitter.size(); k++) {
            Archiv o = original.get(k);
            Feld hs = o.feld("waves.hs", katalog), tp = o.feld("waves.tp", katalog), dir = o.feld("waves.dir", katalog);
            o.lies("waves.hs", anfangStand, hs);
            o.lies("waves.tp", anfangStand, tp);
            o.lies("waves.dir", anfangStand, dir);
            Waves w = waves.waves.get(k);
            IntStream.range(0, w.nc).parallel().forEach(c -> w.jonswap(c, hs.hol(c), tp.hol(c), Waves.wohin(dir.hol(c)), e.breite));
            waves.rechner.hoch(w);
        }
    }

    private void ablageAnlegen(int[] st) throws IOException {
        Path ziel = e.ziel != null ? e.ziel : e.archiv;
        for (int k = 0; k < waves.gitter.size(); k++) {
            Gitter g = waves.gitter.get(k);
            Path p = ziel.resolve(ORDNER + g.name);
            if (Files.exists(p)) try (var w = Files.walk(p)) {
                for (Path q : w.sorted(Comparator.reverseOrder()).toList()) Files.delete(q);
            }
            Archiv o = original.get(k);
            Archiv.Lauf kopf = new Archiv.Lauf();
            kopf.name = "Nachlauf Seegang zu " + o.lauf.name;
            kopf.start = o.lauf.start;
            kopf.seed = o.lauf.seed;
            for (Map.Entry<String, String> x : o.lauf.antrieb.entrySet()) kopf.antrieb.put(x.getKey(), x.getValue());
            kopf.antrieb.put("seegang.nachlauf", "SeegangNachlauf " + VERSION + ": " + st[0] + " × " + st[1] + " Stützstellen, Wasser aus dem Archiv, Strömung "
                    + (wasser.strom != null ? "auf G3 aus hydro_G3, auf G2 keine" : "keine") + ", Wind " + antrieb.schub + " aus ERA5 ohne Anlauf, Rand aus ERA5, Vorlauf "
                    + (erste - beginn) / 3600 + " h ab einem JONSWAP aus dem ersten Stand (s " + e.breite + ", Wasser des ersten Stands)");
            kopf.antrieb.put("seegang.trennung", "Windsee, wo " + Waves.BETA_WINDSEE + " · 28 u*/c · cos(θ − θ_w) > 1 (WAM); waves.sea und waves.swell je {Hs m, Tp s, woher ° von Gitternord, Richtungsbreite °}");
            kopf.antrieb.put("seegang.dissipation", "waves.dwc Schaumkronen und waves.dbf Bodenreibung als Raten aus dem Spektrum des Stands (W/m²); waves.dbr Brechen an der Tiefe und Kappung, wie der letzte Schritt der Quellen sie verlor (waves.diss − waves.dwc, mindestens 0); waves.diss wie im Lauf (Schritt der Quellen, mit Kappung)");
            for (var m : bus.module()) kopf.module.put(m.name(), m.version());
            kopf.module.put("seegang.nachlauf", VERSION);
            Archiv a = Archiv.anlegen(p, kopf);
            for (String sp : SPUREN) a.spur(new Feld(katalog.verlange(sp), g, Feld.Zahl.FLOAT), Archiv.Ablage.HALB);
            ablage.add(a);
        }
    }

    /** Meldet je Stand eine Zeile (null: still). */
    public java.util.function.Consumer<String> fortschritt;

    /** Rechnet bis zum letzten Stand, legt jeden Stand ab und prüft nebenher. */
    public void rechne() throws IOException {
        Archiv o0 = original.get(0);
        for (int k = 0; k < o0.staende(); k++) {
            double t = o0.zeit(k);
            if (t < erste - 1e-6) continue;                                        // vor dem Anfang (Einstellung.ab)
            if (t > letzte + 1e-6) break;
            long n0 = System.nanoTime();
            takt.bis(t);
            wasser.halten = Double.NaN;                                             // nach dem Vorlauf: das Wasser der wahren Zeit
            sekundenRechnen += (System.nanoTime() - n0) / 1e9;
            stand(k, t);
            staende++;
            if (fortschritt != null && (k % 6 == 0 || t >= letzte - 1e-6))
                fortschritt.accept(String.format(java.util.Locale.ROOT, "  %5.1f h von %.1f h, Rechnen %.0f s, Hs größte %s m", (t - erste) / 3600, (letzte - erste) / 3600,
                        sekundenRechnen, java.util.Arrays.toString(waves.hsMax())));
        }
    }

    /** Diagnose, Prüfung und Ablage zum Stand k (Zeit t). */
    private void stand(int k, double t) throws IOException {
        long n0 = System.nanoTime();
        unheil += waves.unheil();
        for (int g = 0; g < waves.gitter.size(); g++) {
            Waves w = waves.waves.get(g);
            Gitter gi = waves.gitter.get(g);
            Feld[] f = new Feld[SPUREN.length];
            for (int q = 0; q < f.length; q++) f[q] = new Feld(katalog.verlange(SPUREN[q]), gi, Feld.Zahl.FLOAT);
            double[][] teil = new double[w.nc][];
            double[][] dis = new double[w.nc][];
            IntStream.range(0, w.nc).parallel().forEach(c -> {
                boolean da = w.nass(c) && w.m0(c) > 0;
                if (!da) return;
                double tp = w.tp(c), r = w.richtung(c);
                f[0].f[c] = (float) w.hs(c);
                f[1].f[c] = Double.isFinite(tp) ? (float) tp : 0f;
                f[2].f[c] = Double.isFinite(r) ? (float) Waves.woher(r) : 0f;
                f[3].f[c] = (float) w.ubot(c);
                f[4].f[c] = (float) w.mss(c);
                f[5].f[c] = (float) w.diss[c];
                double[] p = w.partitionen(c);
                teil[c] = p;
                for (int s = 0; s < 2; s++) {
                    f[6 + s].f[4 * c] = (float) p[4 * s];
                    f[6 + s].f[4 * c + 1] = Double.isFinite(p[4 * s + 1]) ? (float) p[4 * s + 1] : 0f;
                    f[6 + s].f[4 * c + 2] = Double.isFinite(p[4 * s + 2]) ? (float) Waves.woher(p[4 * s + 2]) : 0f;
                    f[6 + s].f[4 * c + 3] = Double.isFinite(p[4 * s + 3]) ? (float) p[4 * s + 3] : 0f;
                }
                double[] d = w.dissipation(c);
                dis[c] = d;
                f[8].f[c] = (float) d[0];
                f[9].f[c] = (float) Math.max(0, w.diss[c] - d[0]);          // Brechen und Kappung, wie der Schritt sie verlor
                f[10].f[c] = (float) d[2];
            });
            // Prüfung: Hs gegen das Archiv, Windsee und Dünung gegen Hs, Senken gegen diss
            Archiv o = original.get(g);
            Feld hsA = o.feld("waves.hs", katalog);
            o.lies("waves.hs", k, hsA);
            boolean spaet = t - erste >= ANLAUF - 1e-6;
            for (int c = 0; c < w.nc; c++) {
                double a = hsA.hol(c);
                if (teil[c] != null) {
                    double hs = f[0].f[c], m0 = hs * hs, ps = teil[c][0] * teil[c][0] + teil[c][4] * teil[c][4];
                    double hsD = 4 * Math.sqrt(w.m0(c));
                    if (hsD > 1e-3) partFehler[g] = Math.max(partFehler[g], Math.abs(ps - hsD * hsD) / (hsD * hsD));
                    m0Windsee[g] += teil[c][0] * teil[c][0] / 16;
                    m0Alle[g] += m0 / 16;
                    summeWc[g] += dis[c][0];
                    summeWcBr[g] += dis[c][0] + dis[c][1];
                    summeBr[g] += dis[c][1];
                    summeBrSchritt[g] += Math.max(0, w.diss[c] - dis[c][0]);
                    summeBf[g] += dis[c][2];
                    summeDiss[g] += w.diss[c];
                }
                if (!(a > HS_MIN) || !w.nass(c)) continue;
                double d = f[0].f[c] - a;
                dq[g] += d * d;
                aq[g] += a * a;
                dsum[g] += d;
                n[g]++;
                groesste[g] = Math.max(groesste[g], Math.abs(d));
                if (spaet) {
                    dqSpaet[g] += d * d;
                    aqSpaet[g] += a * a;
                    nSpaet[g]++;
                }
            }
            if (!ablage.isEmpty()) ablage.get(g).stand(t, f);
        }
        sekundenAblage += (System.nanoTime() - n0) / 1e9;
    }

    /** Relative RMS-Abweichung von Hs gegen das Archiv je Gitter: √(Σ(Hs_N − Hs_A)² / Σ Hs_A²); {@code spaet}: nach {@link #ANLAUF}. */
    public double relativ(int g, boolean spaet) {
        return spaet ? (aqSpaet[g] > 0 ? Math.sqrt(dqSpaet[g] / aqSpaet[g]) : Double.NaN) : (aq[g] > 0 ? Math.sqrt(dq[g] / aq[g]) : Double.NaN);
    }

    @Override public void close() throws IOException {
        IOException fehler = null;
        for (Archiv a : ablage) try { a.close(); } catch (IOException x) { fehler = x; }
        ablage.clear();
        waves.rechner.close();
        wasser.close();
        for (Archiv o : original) o.close();
        if (fehler != null) throw fehler;
    }

    /** Die Archive mit dem Nachlauf des Seegangs neben einem Lauf: Unterordner {@code seegang_<Gitter>}. */
    public static List<String> vorhanden(Path archiv) throws IOException {
        List<String> l = new ArrayList<>();
        if (!Files.isDirectory(archiv)) return l;
        try (var s = Files.list(archiv)) {
            for (Path p : s.sorted().toList()) if (p.getFileName().toString().startsWith(ORDNER) && Files.isRegularFile(p.resolve("lauf.txt"))) l.add(p.getFileName().toString());
        }
        return l;
    }

    // ------------------------------------------------------------------ Wasser aus dem Archiv

    /**
     * Speist den Bus mit dem Wasser eines Archivs (steht für {@code hydro}, wie {@link Archivspeiser}): {@code water.h}
     * auf G2 und G3, {@code water.u} und {@code water.v} auf G3 aus {@code hydro_G3} (sonst 0), auf G2 0; zwischen
     * zwei Ständen linear. Solange {@link #halten} gesetzt ist, gilt der Stand zu dieser Zeit (Vorlauf).
     */
    static final class Wasser implements Modul {
        final Archiv a2, a3, strom;
        final Gitter g2, g3;
        final double erste, letzte, kopplung;
        private final Feld h2, h3, u3, v3;
        /** Zeit im Archiv, deren Stand fest gilt; NaN: die Zeit des Takts. */
        volatile double halten = Double.NaN;

        Wasser(Path archiv, boolean mitStrom, List<String> hinweise) throws IOException {
            Katalog k = Katalog.grundstufe();
            Archiv x2 = null, x3 = null, xs = null;
            try {
                x2 = Archiv.oeffnen(archiv.resolve("G2"));
                x3 = Archiv.oeffnen(archiv.resolve("G3"));
                Path ps = archiv.resolve(Stroemung.ORDNER + "G3");
                if (mitStrom && Files.isRegularFile(ps.resolve("lauf.txt"))) xs = Archiv.oeffnen(ps);
            } catch (IOException x) {
                if (x2 != null) x2.close();
                if (x3 != null) x3.close();
                throw x;
            }
            a2 = x2;
            a3 = x3;
            h2 = a2.feld("water.h", k);
            h3 = a3.feld("water.h", k);
            g2 = h2.gitter;
            g3 = h3.gitter;
            if (xs != null) {
                Feld u = xs.feld("water.u", k);
                if (u.gitter == null || !u.gitter.gleich(g3) || xs.staende() != a3.staende()) {
                    hinweise.add("hydro_G3 passt nicht zu G3 des Archivs (" + u.gitter + " gegen " + g3 + ", " + xs.staende() + " gegen " + a3.staende() + " Stände): ohne Strömung");
                    xs.close();
                    xs = null;
                }
            }
            strom = xs;
            if (strom == null) hinweise.add("ohne Strömung (kein Nachlauf der Strömung hydro_G3 im Archiv)");
            u3 = strom != null ? strom.feld("water.u", k) : null;
            v3 = strom != null ? strom.feld("water.v", k) : null;
            erste = a3.zeit(0);
            letzte = a3.zeit(a3.staende() - 1);
            kopplung = a3.zeit(1) - erste;
        }

        @Override public String name() { return "hydro"; }

        @Override public String version() { return "aus dem Archiv " + a3.ordner.getParent().getFileName() + (strom != null ? " mit Strömung aus hydro_G3" : " ohne Strömung"); }

        @Override public void anmelden(Anmeldung a) {
            a.kopplung(kopplung);
            for (Gitter g : new Gitter[] {g2, g3}) {
                a.schreibt("water.h", "m", Ort.ZELLE, g, Feld.Zahl.FLOAT);
                a.schreibt("water.u", "m/s", Ort.ZELLE, g, Feld.Zahl.FLOAT);
                a.schreibt("water.v", "m/s", Ort.ZELLE, g, Feld.Zahl.FLOAT);
            }
        }

        @Override public void beginn(double t0, Bus bus) { lege(bus, t0); }

        @Override public void schritt(double t0, double t1, Bus bus) { lege(bus, t1); }

        private void lege(Bus bus, double t) {
            double ta = Double.isNaN(halten) ? Math.max(erste, Math.min(letzte, t)) : halten;
            try {
                a2.lies("water.h", ta, h2);
                a3.lies("water.h", ta, h3);
                if (strom != null) {
                    strom.lies("water.u", ta, u3);
                    strom.lies("water.v", ta, v3);
                }
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
            kopiere(bus, "water.h", g2, h2);
            kopiere(bus, "water.u", g2, null);
            kopiere(bus, "water.v", g2, null);
            kopiere(bus, "water.h", g3, h3);
            kopiere(bus, "water.u", g3, u3);
            kopiere(bus, "water.v", g3, v3);
            for (Gitter g : new Gitter[] {g2, g3}) for (String n : new String[] {"water.h", "water.u", "water.v"}) bus.lege(this, n, g, t);
        }

        private void kopiere(Bus bus, String name, Gitter g, Feld von) {
            Feld z = bus.schreibfeld(this, name, g);
            if (von == null) java.util.Arrays.fill(z.f, 0f);
            else for (int i = 0; i < z.laenge; i++) {
                double v = von.hol(i);
                z.f[i] = Double.isFinite(v) ? (float) v : 0f;
            }
        }

        void close() {
            try {
                a2.close();
                a3.close();
                if (strom != null) strom.close();
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        }
    }
}
