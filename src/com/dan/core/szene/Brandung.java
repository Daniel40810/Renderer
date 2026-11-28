package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Brandung an den Böschungen (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A, F162 A): je Bild aus den
 * {@link Kanten}, dem Wasserstand und dem Seegang die Brecher als Netz, der Auflauf als Wasserfilm auf dem Hang und die
 * Bündel der Gischt. Ohne Grafik und ohne Zustand: alles ist eine Funktion der Modellzeit (gleiche Zeit, gleiches Bild).
 * <p>
 * Je Abschnitt, der im Umkreis {@link #weite} um das Auge liegt, an dem Wasser steht (Fuß unter, Krone über dem Wasser)
 * und auf den die Wellen zulaufen:
 * <ul>
 * <li><b>Wellenfolge:</b> Periode Tp, Ankunft längs der Laufrichtung mit c = √(g h) am Fuß, so dass ein Kamm schräg an
 * der Kante entlangläuft. Höhe je Welle nach Rayleigh aus H_s am Ort (tiefenbegrenzt: H_s ≤ 0,5 h + 0,1 H_s [G]), längs
 * des Kamms in Blöcken von 25 m (wie {@code com.dan.surf.WaveTrain.crestLength}) linear überblendet.</li>
 * <li><b>Typ</b> nach Battjes aus ξ₀ = tan α / √(H/L₀) ({@link Brecherform#typ}); Form aus {@link Brecherform}: erst
 * ungebrochen, dann brechend, dann Walze (Anteile der Periode [G]).</li>
 * <li><b>Auflauf</b> nach EurOtop 2018 (Gl. 5.3/5.4): Ru2% = 1,65 γ_f ξ_m−1,0 H_s, höchstens γ_f (4 − 1,5/√ξ) H_s, mit
 * T_m−1,0 = Tp/1,1; je Welle Ru = Ru2% · H / H2% (H2% = 1,40 H_s nach Rayleigh). Über der Krone: Überlauf (nur gezeigt).</li>
 * <li><b>Gischt:</b> beim Aufschlag (Sturz) und am Hang (Reflexion, Brandung) ein Bündel Spritzwasser, ab Bft 8 dazu eine
 * Fahne vom Kamm; Tropfen wie {@code com.dan.spray.Parcel} (Schwerkraft, Reibung zum Wind), auf der Karte gerechnet.</li>
 * </ul>
 * Raum der Ausgabe: x = Ost − e0, y = Höhe (m NHN), z = n0 − Nord (wie {@code core.szene.Bauten}).
 * <p>
 * Seit S5b-4 (Seegang Werkbuch 46, SF51 A): Wo das Netz der Clipmap zeichnet, nimmt ein Abschnitt seine Wellen aus den
 * Kämmen der Fläche an seinem Fuß ({@link #fuesse}, {@link Fusswellen}): Aufschlag nach der {@link #laufzeit} vom Fuß zur
 * Wasserlinie, Höhe aus der Fläche. Damit hängen Gischt am Hang, Überlauf und Typ an den Kämmen, die man kommen sieht.
 * Diese Abschnitte haben einen Zustand (die Folge der Kämme); jenseits der Clipmap bleibt alles wie oben.
 * <p>
 * Seit S5b-5 (Seegang Werkbuch 50): Im Netz zeichnet ein gekoppelter Abschnitt die Lippe eines Sturz- oder
 * Reflexionsbrechers ({@link #lippen}), mit Zeit und Höhe aus der Folge.
 */
public final class Brandung {
    /** Wasserstand und Seegang am Ort (UTM); {@link #KEIN}: kein Wasser. Seegang {Hs, Tp, cos, sin (wohin), mss, diss} oder null. */
    public interface Wasser {
        float eta(double ost, double nord);
        double[] seegang(double ost, double nord);
    }

    public static final float KEIN = -1.0e30f;
    /** Floats je Scheitel: x, y, z, Schaum, nx, ny, nz, Art (0 Brecher, 1 Film, sonst dünn = 2 + Anteil). */
    public static final int SCHEITEL = 8;
    /** Floats je Bündel der Gischt (wie {@code gischt.vert}). */
    public static final int BUENDEL = 16;
    public static final int SPRITZ = 0, FAHNE = 2;

    /** Umkreis um das Auge (m), Punkte je Form, Abschnitte und Bündel höchstens [G]. */
    public double weite = 1200;
    /** Blickfeld: Richtung des Blicks (Einheitsvektor Ost, Nord) und cos des halben Winkels; Abschnitte näher als 40 m zählen immer [G]. */
    public double blickOst = 0, blickNord = 1, blickCos = -1;
    public int punkte = 40, abschnitteMax = 500, buendelMax = 600;
    /** Blocklänge längs des Kamms (m), Anteile der Periode: ungebrochen bis, brechend bis, Walze bis [G]. */
    public double block = 25, phAnlauf = 0.5, phBrechen = 0.75, phWalze = 0.85;
    /** Gischt: Lebenszeit (s), Tropfen je Bündel, Wind ab dem Fahnen wehen (m/s) [G]. */
    public double gischtLeben = 2.5, fahneAb = 17;
    public int tropfen = 48;
    public final double e0, n0;
    /**
     * Seit S3c-5 (SF28 A, Seegang Werkbuch 19/20): Bereich, in dem das Wasser das Netz aus der Clipmap zeichnet (UTM: Ost
     * von, Ost bis, Nord von, Nord bis), oder null. Abschnitte mit dem Fuß darin zeichnen weder Brecher noch Wasserfilm
     * (S5/S6 bringen sie neu aus der Fläche); Gischt, Typ und Auflauf werden weiter gerechnet.
     */
    public double[] netzBereich;
    /** Zuletzt: aktive Abschnitte ohne Brecher und Film, weil dort das Netz zeichnet. */
    public int imNetz;

    // Ergebnis des letzten Bilds
    public float[] netz = new float[0];
    public int scheitel;
    public float[] buendel = new float[0];
    public int buendelZahl;
    /** Zuletzt: aktive Abschnitte, je Typ (Index wie {@link Brecherform#typ}), Rechenzeit (ms). */
    public int aktiv;
    public final int[] jeTyp = new int[5];
    /** Seit S5b-3: Überläufe (Schwälle über die Krone) im letzten Bild. */
    public int ueberlaeufe;
    /**
     * Seit S5b-4 (Seegang Werkbuch 46, SF51 A): die Kämme der Fläche am Fuß ({@link Fusswellen}); null: überall die eigene
     * Wellenfolge. Wo das Netz zeichnet und eine frische Folge da ist, nimmt ein Abschnitt seine Wellen daraus: Zeit des
     * Aufschlags = Durchgang am Fuß + {@link #laufzeit}, Höhe = 2 |Z| · H_s / H_s,Fuß mit der linearen Höhe 2 |Z| des Kamms
     * (nach Rayleigh mit H_s,Fuß verteilt, seit S5b-4c die H_s der gezeichneten Wellen 4 √(Σ E g²): der Rang der Welle kommt
     * aus der Fläche, Verteilung und Maßstab für EurOtop wie bisher aus H_s am Fuß), Typ aus ξ wie bisher. Jenseits der
     * Clipmap und wo die Stufe am Fuß kaum Wellen zeichnet, bleibt die eigene Folge.
     */
    public volatile Fusswellen fuesse;
    /** Zuletzt (seit S5b-4): Abschnitte im Netz, deren Fuß die Karte suchen soll, und Abschnitte, die den Kämmen der Fläche folgten. */
    public int angefragt, gekoppelt;
    /** Seit S5b-4: Laufzeit eines Kamms vom Fuß bis zur Wasserlinie höchstens (s) [G]. */
    public double laufMax = 30;
    /**
     * Seit S5b-5 (Seegang Werkbuch 50): im Netz an Abschnitten, die den Kämmen der Fläche folgen, die Lippen der Sturz- und
     * Reflexionsbrecher (Typ aus ξ); Schwall zeichnet die Fläche selbst (Walze, S5a). Die Lippe steht vom Brechpunkt bis
     * zum Aufschlag an der Wasserlinie und danach kurz als Walze; der Kamm liegt nach der Zeit bis zum Aufschlag τ bei
     * u = g tan α τ² / 4 seewärts der Wasserlinie (lange Welle auf dem ebenen Hang, wie {@link #laufzeit}). Die Form
     * gilt an beiden Enden des Abschnitts mit dem Unterschied der Ankunft längs der Kante, damit Nachbarn aneinander
     * passen (ein starres Stück je Abschnitt gab Kacheln, B45).
     * <p>
     * Seit S5b-5c (Seegang Werkbuch 52, Befund L3e): <b>Vorgabe aus.</b> An den Kanten des Gebiets zerfällt die Lippe in
     * Splitter, weil Neigung, Fußhöhe und damit die Laufzeit vom Fuß zur Wasserlinie von Abschnitt zu Abschnitt springen
     * (zwischen Nachbarn an der Warft im Median 1,3 s, 95 % 7 s, bei 5,6 s Periode). Einschalten nur für Prüfungen, bis die
     * Lippe neu geplant ist.
     */
    public boolean lippen = false;
    /**
     * Seit S5b-5: die Enden mit den Nachbarn teilen (Ort, Richtung, Höhen und die Welle gemittelt, wenn der Nachbar den
     * Kamm auch kennt); aus nur für die Prüfung: jedes Ende für sich mit dem Unterschied der Ankunft längs der Kante.
     */
    public boolean lippenNachbarn = true;
    /** Seit S5b-5: Auftauchen der Lippe vor dem Brechen (Anteil von Tp) und Abtauchen nach der Walze (s) [G]. */
    public double lippeAn = 0.1, lippeAus = 0.5;
    /** Seit S5b-5: derselbe Kamm beim Nachbarn, wenn er höchstens so weit (Anteil von Tp) vom erwarteten Aufschlag liegt [G]. */
    public double lippeFang = 0.3;
    /** Seit S5b-5b: ein Kamm gilt an beiden Füßen als bekannt so lange nach dem späteren Durchgang (s; Meldung bis 4 Bilder spät), danach Auftauchen in (s) [G]. */
    public double lippeKennt = 0.15, lippeEin = 0.5;
    /** Zuletzt: Lippen im Netz (je Abschnitt und Welle eine); seit dem letzten Nullen: Summe über die Bilder. */
    public int lippenZahl;
    public long lippenSumme;
    /**
     * Seit S5b-5, mit {@link #protokollieren}: je Lippe {Kante, P, Aufschlag ta, H (gezeichnet), Gradient der Ankunft
     * längs t (s/m), Typ, Sichtbarkeit f an den Enden A (−t) und B (+t), höchster Punkt über Wasser und Grund an A und B
     * (m), Welle mit dem Nachbarn geteilt an A und B (1/0), Ort der Enden an der Wasserlinie A x, z, B x, z (Raum der
     * Ausgabe), ab {@link #LIPPE_KOPF} je Ende A und B die P Punkte x, y, z im Raum der Ausgabe}.
     */
    public static final int LIPPE_KOPF = 16;
    /** Seit S5b-5b, für Prüfungen: nach jedem {@link #rechne} aufgerufen (null: nichts). */
    public java.util.function.Consumer<Brandung> nachBild;
    public final List<double[]> lippenProtokoll = new ArrayList<>();
    public double ms;
    /** Zuletzt je aktivem Abschnitt {Kante, Typ, ξ₀, H, Ru2%, Ru dieser Welle, Hs am Ort, Tp} – für die Prüfung. */
    public final List<double[]> protokoll = new ArrayList<>();
    public boolean protokollieren;
    /** Seit S5b-4, mit {@link #protokollieren}: je aktivem Abschnitt und Welle {Kante, Nummer, Zeit des Aufschlags, Höhe, gekoppelt 0/1, Laufzeit vom Fuß}. */
    public final List<double[]> wellen = new ArrayList<>();

    /**
     * Seit S8c (Seegang Werkbuch 104, SF159 A): wohin die Brandung meldet, was klingt – je Welle und Abschnitt ein Brecher,
     * wenn ihr Aufschlag in die Zeit seit dem letzten {@link #rechne} fällt, und ein Überlauf, wenn sie über die Krone geht
     * (null: nichts). Springt die Zeit (rückwärts oder über 5 s), wird nichts gemeldet.
     */
    public volatile Tonereignisse ton;
    private double tTon = Double.NaN, tonVon = Double.POSITIVE_INFINITY;

    public Brandung(double e0, double n0) {
        this.e0 = e0;
        this.n0 = n0;
    }

    // ------------------------------------------------------------------ Formeln (öffentlich für die Prüfung)

    /** Zufall 0 … 1 aus zwei Zahlen (splitmix64). */
    public static double zufall(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 11) + 0.5) / (double) (1L << 53);
    }

    /** Höhe nach Rayleigh mit P(H &gt; x) = exp(−2 x² / H_s²). */
    public static double rayleigh(double hs, double u) { return hs * Math.sqrt(-Math.log(u) / 2); }

    /**
     * Seit S5b-3: mittlerer Überlauf q (m³/s je m) nach EurOtop 2018, Gl. 5.10 mit dem Höchstwert 5.11 (γ_b = γ_β = γ_v =
     * γ* = 1): H_s, Tp (T_m−1,0 = Tp / 1,1), tan α, γ_f, Freibord R_c (m).
     */
    public static double ueberlauf(double hs, double tp, double neigung, double rau, double rc) {
        if (!(hs > 0) || !(tp > 0) || !(neigung > 0)) return 0;
        double tm = tp / 1.1, l = 9.81 * tm * tm / (2 * Math.PI), xi = neigung / Math.sqrt(hs / l), r = Math.max(0, rc);
        double q1 = 0.023 / Math.sqrt(neigung) * xi * Math.exp(-Math.pow(2.7 * r / (xi * hs * rau), 1.3));
        double q2 = 0.09 * Math.exp(-Math.pow(1.5 * r / (hs * rau), 1.3));
        return Math.sqrt(9.81 * hs * hs * hs) * Math.min(q1, q2);
    }

    /** Seit S5b-3: Anteil der Wellen mit Überlauf nach EurOtop 2018, P_ov = exp(−(√(−ln 0,02) R_c / Ru2%)²). */
    public static double anteilUeberlauf(double rc, double ru2) {
        if (!(ru2 > 0)) return 0;
        double x = Math.sqrt(-Math.log(0.02)) * Math.max(0, rc) / ru2;
        return Math.exp(-x * x);
    }

    /**
     * Seit S5b-3: Volumen (m³ je m) der Welle mit dem Auflauf ru über dem Freibord rc, nach EurOtop 2018 (Weibull,
     * b = 0,73 + 55 (q / (g H_s T_m−1,0))^0,8, a = q T / (P_ov Γ(1 + 1/b))) mit T = Tp, der Folge der Wellen hier. Die
     * Welle bekommt das Volumen ihres Rangs: Die Wahrscheinlichkeit, dass ein Überlauf größer ist, ist die, dass ein
     * Auflauf größer ist als ihrer (Rayleigh wie Ru2%), geteilt durch P_ov. So stimmen die Verteilung der Volumina und ihr
     * Mittel q. 0 ohne Überlauf.
     */
    public static double volumen(double ru, double rc, double ru2, double hs, double tp, double neigung, double rau) {
        return volumen(ru, rc, ru2, hs, tp, neigung, rau, tp);
    }

    /**
     * Seit S5b-4: wie {@link #volumen(double, double, double, double, double, double, double)}, mit dem mittleren Abstand
     * tWelle (s) der Wellen statt Tp in a = q T / (P_ov Γ(1 + 1/b)): An der Fläche kommen die Kämme im Mittel alle T_m01,
     * nicht alle Tp; so stimmt das Mittel q über die Zeit.
     */
    public static double volumen(double ru, double rc, double ru2, double hs, double tp, double neigung, double rau, double tWelle) {
        if (!(ru > rc) || !(ru2 > 0)) return 0;
        double pov = anteilUeberlauf(rc, ru2), q = ueberlauf(hs, tp, neigung, rau, rc);
        if (!(pov > 0) || !(q > 0)) return 0;
        double b = 0.73 + 55 * Math.pow(q / (9.81 * hs * tp / 1.1), 0.8), a = q * tWelle / (pov * gammaFunktion(1 + 1 / b));
        double x = Math.sqrt(-Math.log(0.02)) * ru / ru2, p = Math.min(1, Math.exp(-x * x) / pov);
        return a * Math.pow(-Math.log(Math.max(p, 1e-12)), 1 / b);
    }

    /** Γ(x) für x &gt; 0 (Lanczos, g = 7). */
    public static double gammaFunktion(double x) {
        if (x < 0.5) return Math.PI / (Math.sin(Math.PI * x) * gammaFunktion(1 - x));
        double[] c = {0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
            12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        x -= 1;
        double a = c[0], t = x + 7.5;
        for (int i = 1; i < 9; i++) a += c[i] / (x + i);
        return Math.sqrt(2 * Math.PI) * Math.pow(t, x + 0.5) * Math.exp(-t) * a;
    }

    /**
     * Seit S5b-4: Laufzeit (s) eines Kamms vom Fuß (Tiefe hf) bis zur Wasserlinie auf dem ebenen Hang tan α, mit der
     * Geschwindigkeit der langen Welle √(g h): ∫ dx / √(g h) = 2 √(hf / g) / tan α [G: eine Walze läuft etwas schneller].
     */
    public static double laufzeit(double hf, double neigung) {
        if (!(hf > 0) || !(neigung > 0)) return 0;
        return 2 * Math.sqrt(hf / 9.81) / neigung;
    }

    /** H_s am Fuß, tiefenbegrenzt [G]. */
    public static double hsFuss(double hs, double hFuss) { return Math.min(hs, 0.5 * Math.max(hFuss, 0) + 0.1 * hs); }

    /** Ru2% nach EurOtop 2018, Gl. 5.3 mit der Grenze 5.4 (γ_b = γ_β = 1): H_s, Tp, tan α, γ_f. */
    public static double ru2(double hs, double tp, double neigung, double rau) {
        if (!(hs > 0) || !(tp > 0)) return 0;
        double tm = tp / 1.1, l = 9.81 * tm * tm / (2 * Math.PI), xi = neigung / Math.sqrt(hs / l);
        return hs * Math.min(1.65 * rau * xi, rau * (4 - 1.5 / Math.sqrt(xi)));
    }

    /** Zwei unabhängige Normalverteilte (Box-Muller) für Welle n und Block k. */
    static double[] normal2(long n, long k) {
        double u1 = zufall(n, 2 * k), u2 = zufall(n, 2 * k + 1), r = Math.sqrt(-2 * Math.log(u1));
        return new double[] {r * Math.cos(2 * Math.PI * u2), r * Math.sin(2 * Math.PI * u2)};
    }

    /**
     * Wellenhöhe der Welle n am Ort längs des Kamms ψ (m) mit H_s: H = H_s/2 · √(X² + Y²) mit X, Y normalverteilt ist
     * Rayleigh mit P(H &gt; x) = exp(−2x²/H_s²). Zwischen den Blöcken werden X und Y überblendet und auf die Streuung 1
     * zurückgeführt, so bleibt die Verteilung an jedem Ort Rayleigh (eine lineare Überblendung der Höhen machte die
     * hohen Wellen seltener: Ru2% 11 % zu klein, Lauf in der Arbeitsumgebung 20.11.).
     */
    public double hoehe(double hs, long n, double psi) {
        double b = psi / block, f = b - Math.floor(b);
        long k = (long) Math.floor(b);
        double[] a = normal2(n, k), c = normal2(n, k + 1);
        double w = Math.sqrt((1 - f) * (1 - f) + f * f);
        double x = ((1 - f) * a[0] + f * c[0]) / w, y = ((1 - f) * a[1] + f * c[1]) / w;
        return 0.5 * hs * Math.sqrt(x * x + y * y);
    }

    // ------------------------------------------------------------------ je Bild

    private float[] nz = new float[1 << 16];
    private float[] bz = new float[1 << 12];
    private int nn, bn;

    /**
     * Rechnet das Bild zur Modellzeit t für das Auge (UTM, Höhe) mit dem Wind (U₁₀ Ost, Nord in m/s). Ergebnis in
     * {@link #netz}, {@link #scheitel}, {@link #buendel}, {@link #buendelZahl}.
     */
    public void rechne(Kanten kanten, Wasser w, double t, double augeOst, double augeNord, double windU, double windV) {
        long t0 = System.nanoTime();
        double tv = tTon;                                                      // seit S8c: Aufschläge in (tonVon, t] melden
        tonVon = ton != null && !Double.isNaN(tv) && t > tv && t - tv < 5 ? tv : Double.POSITIVE_INFINITY;
        tTon = t;
        nn = 0;
        bn = 0;
        aktiv = 0;
        imNetz = 0;
        ueberlaeufe = 0;
        angefragt = 0;
        gekoppelt = 0;
        Fusswellen fw = fuesse;
        java.util.Arrays.fill(jeTyp, 0);
        protokoll.clear();
        wellen.clear();
        lippenZahl = 0;
        lippenProtokoll.clear();
        lzZahl = 0;
        geteilt.clear();
        formenGerechnet = 0;
        if (kanten != null) {
            // die nächsten Abschnitte zuerst, höchstens abschnitteMax
            List<Kanten.Kante> l = kanten.kanten;
            int n = l.size();
            double[] ab = new double[n];
            Integer[] ix = new Integer[n];
            int m = 0;
            for (int i = 0; i < n; i++) {
                Kanten.Kante k = l.get(i);
                double d = Math.hypot(k.ost() - augeOst, k.nord() - augeNord);
                if (d > weite) continue;
                if (d > 40 && ((k.ost() - augeOst) * blickOst + (k.nord() - augeNord) * blickNord) / d < blickCos) continue;
                ab[i] = d;
                ix[m++] = i;
            }
            java.util.Arrays.sort(ix, 0, m, (a, b) -> Double.compare(ab[a], ab[b]));
            for (int q = 0; q < m && aktiv < abschnitteMax; q++) abschnitt(l.get(ix[q]), ix[q], w, t, windU, windV, fw);
            if (lzZahl > 0) lippenZeichnen(kanten, t);                  // seit S5b-5: erst jetzt, mit den Nachbarn
        }
        if (fw != null) fw.anfragen(angefragt, afK, afO, afN, afW);   // seit S5b-4: die Füße für die nächste Suche
        scheitel = nn / SCHEITEL;
        buendelZahl = bn / BUENDEL;
        netz = nz;
        buendel = bz;
        ms = (System.nanoTime() - t0) / 1e6;
        if (nachBild != null) nachBild.accept(this);
    }

    // seit S5b-4: Anfrage der Füße und die Wellen eines Abschnitts (Nummer, Zeit des Aufschlags, Höhe)
    private int[] afK = new int[64];
    private double[] afO = new double[64], afN = new double[64], afW = new double[64];
    private final long[] wNr = new long[Fusswellen.KAEMME_MAX + 3];
    private final double[] wTa = new double[Fusswellen.KAEMME_MAX + 3], wH = new double[Fusswellen.KAEMME_MAX + 3], wHz = new double[Fusswellen.KAEMME_MAX + 3], wTz = new double[Fusswellen.KAEMME_MAX + 3];

    private void anfrage(int nr, double ost, double nord, double omega) {
        if (angefragt == afK.length) {
            int n = 2 * afK.length;
            afK = java.util.Arrays.copyOf(afK, n); afO = java.util.Arrays.copyOf(afO, n); afN = java.util.Arrays.copyOf(afN, n); afW = java.util.Arrays.copyOf(afW, n);
        }
        afK[angefragt] = nr; afO[angefragt] = ost; afN[angefragt] = nord; afW[angefragt] = omega;
        angefragt++;
    }

    private void abschnitt(Kanten.Kante k, int nr, Wasser w, double t, double wu, double wv, Fusswellen fw) {
        double nx = k.nx(), ny = k.ny();
        double[] s = w.seegang(k.ost() + 15 * nx, k.nord() + 15 * ny);
        if (s == null || !(s[0] > 0.05) || !(s[1] > 0.5)) return;
        double dl = Math.hypot(s[2], s[3]);
        if (dl <= 0) return;
        double dx = s[2] / dl, dy = s[3] / dl;
        double zu = -(dx * nx + dy * ny);                              // läuft die See auf den Hang zu?
        if (zu < 0.15) return;
        float eta = w.eta(k.ost() + 10 * nx, k.nord() + 10 * ny);
        if (eta == KEIN || eta < k.fuss() + 0.05 || eta > k.krone() - 0.05) return;
        double hf = eta - k.fuss(), tan = k.neigung(), tp = s[1];
        double hs = hsFuss(s[0], hf) * Math.sqrt(zu);                  // schräg anlaufend: weniger Energie auf den Hang [G]
        if (hs < 0.05) return;
        aktiv++;
        double[] nb = netzBereich;
        boolean form = nb == null || k.ost() < nb[0] || k.ost() > nb[1] || k.nord() < nb[2] || k.nord() > nb[3];
        if (!form) imNetz++;
        // seit S5b-4: im Netz die Kämme der Fläche am Fuß, wenn die Karte sie gefunden hat
        Fusswellen.Folge fo = null;
        if (!form && fw != null) {
            anfrage(nr, k.ost(), k.nord(), 2 * Math.PI / tp);
            fo = fw.folge(nr, t);
        }
        double c = Math.sqrt(9.81 * Math.max(0.5, Math.min(5, hf)));
        double hMax = 0.78 * hf + 0.05;
        // Wellen: die aktuelle (Nummer wn, Anteil ph der Periode, Höhe H) und für die Gischt die mit Aufschlag in der Nähe
        long wn;
        double ph, H, tWelle = tp;
        int nw = 0;
        if (fo != null) {
            // Aufschlag an der Wasserlinie (ph = phBrechen) nach der Laufzeit vom Fuß; die aktuelle Welle ist die, deren
            // Takt zuletzt begann (Takt von ta − phBrechen Tp bis ta + (1 − phBrechen) Tp)
            double lauf = Math.min(laufMax, laufzeit(hf, tan)), skal = fo.hsGezeichnet > 0.01 ? hs / fo.hsGezeichnet : 1;
            int akt = -1;
            double beginnAkt = Double.NEGATIVE_INFINITY;
            for (int j = fo.kaemme.size() - 1; j >= 0 && nw < wNr.length; j--) {   // die neuesten zuerst
                Fusswellen.Kamm km = fo.kaemme.get(j);
                double ta = km.zeit + lauf, beginn = ta - phBrechen * tp;
                wNr[nw] = km.nummer;
                wTa[nw] = ta;
                wH[nw] = Math.min(km.linear * skal, hMax);
                wHz[nw] = km.hoehe;                                     // seit S5b-5: gezeichnete Höhe 2 |Z'| für die Lippe
                wTz[nw] = km.zeit;                                      // seit S5b-5b: Durchgang am Fuß (dieselbe Welle beim Nachbarn)
                if (beginn <= t && beginn > beginnAkt) { akt = nw; beginnAkt = beginn; }
                nw++;
            }
            if (akt < 0) {                                              // noch kein Takt begonnen: die älteste Welle im Anlauf
                akt = nw - 1;
                ph = 0;
            } else ph = Math.min(0.999, phBrechen + (t - wTa[akt]) / tp);
            wn = wNr[akt];
            H = wH[akt];
            if (fo.tm01 > 0.5) tWelle = fo.tm01;                         // mittlerer Abstand der Kämme am Fuß
            gekoppelt++;
        } else {
            // Wellenfolge: Ankunft längs der Laufrichtung, Block längs des Kamms
            double chi = (k.ost() * dx + k.nord() * dy) / c, psi = -k.ost() * dy + k.nord() * dx;
            double tau = t - chi, phase = tau / tp;
            wn = (long) Math.floor(phase);
            ph = phase - wn;
            H = Math.min(hoehe(hs, wn, psi), hMax);
            for (long q = wn; q >= wn - 2; q--) {
                wNr[nw] = q;
                wTa[nw] = chi + (q + phBrechen) * tp;
                wH[nw] = q == wn ? H : Math.min(hoehe(hs, q, psi), hMax);
                nw++;
            }
        }
        double xi = Brecherform.iribarren(tan, Math.max(H, 0.02), tp);
        int typ = Brecherform.typ(xi);
        jeTyp[typ]++;
        double ru2 = ru2(hs, tp, tan, k.rau()), ru = ru2 * H / (1.398 * hs);
        if (protokollieren) {
            protokoll.add(new double[] {nr, typ, xi, H, ru2, ru, hs, tp, fo != null ? 1 : 0});
            double lauf = fo != null ? Math.min(laufMax, laufzeit(hf, tan)) : Double.NaN;
            for (int j = 0; j < nw; j++) wellen.add(new double[] {nr, wNr[j], wTa[j], wH[j], fo != null ? 1 : 0, lauf});
        }
        // Wasserlinie: vom Fuß landwärts bis zur Höhe η
        double uw = hf / tan;
        double wlo = k.ost() - nx * uw, wln = k.nord() - ny * uw;
        double tx = -ny, ty = nx;
        // ---- Brecher
        double back = Math.min(30, 0.4 * c * tp), front = 0.5 * back;
        double db = typ <= Brecherform.STURZ ? Math.min(H / 0.78, hf) / tan : 0.3 * H / tan;   // Brechpunkt seewärts der Wasserlinie (m)
        Brecherform.Curve cu = null;
        double uc = 0;
        if (ph < phAnlauf) {
            double near = ph / phAnlauf;
            uc = -db - (1 - near) * 0.5 * c * tp * phAnlauf;
            cu = Brecherform.unbroken(H, Math.max(0.05, -uc * tan + 0.0), back, front, near);
        } else if (ph < phBrechen) {
            double p = (ph - phAnlauf) / (phBrechen - phAnlauf);
            uc = -db * (1 - p);
            double hl = Math.max(0.05, -uc * tan);
            cu = switch (typ) {
                case Brecherform.SCHWALL -> Brecherform.spilling(H, hl, back, front, p);
                case Brecherform.STURZ -> Brecherform.plunging(H, hl, back, front, p);
                default -> Brecherform.collapsing(H, hl, back, front, p);
            };
        } else if (ph < phWalze) {
            double p = (ph - phBrechen) / (phWalze - phBrechen);
            cu = Brecherform.bore(Math.max(0.05, 0.6 * H * (1 - p)), 0.1, back * 0.5, front);
        }
        if (cu != null && form) strip(cu, wlo, wln, nx, ny, tx, ty, eta, uc, tan, uw);
        // seit S5b-5: im Netz die Lippe der gekoppelten Welle im Brechbereich
        if (fo != null && !form && lippen) merkeLippe(nr, nw, eta, hf, tan, tp, c, dx, dy, nx, ny, wlo, wln, hMax, k.ost(), k.nord());
        // ---- Auflauf (Wasserfilm): nach dem Brechen hinauf, dann zurück [G]
        if (form && ph >= phBrechen - 0.05) {
            double fs = (ph - (phBrechen - 0.05)) / (1 - (phBrechen - 0.05));
            double r = fs < 0.4 ? ru * Math.sin(0.5 * Math.PI * fs / 0.4) : ru * (1 - Math.pow((fs - 0.4) / 0.6, 2));
            double rmax = k.krone() - eta;                              // darüber: Überlauf, der Film endet an der Krone
            film(wlo, wln, nx, ny, tx, ty, eta, tan, Math.min(r, rmax) / tan, fs);
        }
        // ---- Gischt: Bündel der Wellen, deren Aufschlag (bei ph = phBrechen) in der Lebenszeit liegt
        for (int j = 0; j < nw; j++) {
            long q = wNr[j];
            double ta = wTa[j], alter = t - ta;
            if (alter < 0 || alter > gischtLeben) continue;
            double Hq = wH[j];
            int tq = Brecherform.typ(Brecherform.iribarren(tan, Math.max(Hq, 0.02), tp));
            double hj = tq == Brecherform.SCHWALL ? 0.25 * Hq : tq == Brecherform.STURZ ? 0.8 * Hq : 1.2 * Hq;   // Höhe des Schwalls [G]
            boolean jetzt = ta > tonVon && ta <= t;                         // seit S8c: der Aufschlag fiel in dieses Bild
            Tonereignisse te = ton;
            if (jetzt && te != null && Hq >= 0.1)
                te.melde(Tonereignisse.BRECHER, wlo, wln, eta, Math.min(1, Hq / 2) * (tq == Brecherform.STURZ ? 1 : tq == Brecherform.SCHWALL ? 0.5 : 0.8), ta);
            if (hj > 0.15) buendel(SPRITZ, wlo, wln, eta + 0.1, -nx, -ny, Math.sqrt(2 * 9.81 * hj), alter, tx, ty, wu, wv, nr * 131L + q);
            // seit S5b-3: Überlauf dieser Welle (EurOtop 2018, Weibull) – ein Schwall über die Krone
            double ruq = ru2 * Hq / (1.398 * hs), rc = k.krone() - eta;
            double vq = volumen(ruq, rc, ru2, hs, tp, tan, k.rau(), tWelle);
            if (vq > 0) {
                ueberlaeufe++;
                double lk = (k.krone() - k.fuss()) / Math.max(tan, 0.05), ko = k.ost() - nx * lk, kn = k.nord() - ny * lk;
                if (jetzt && te != null) te.melde(Tonereignisse.UEBERLAUF, ko, kn, k.krone(), Math.min(1, vq), ta);   // seit S8c
                buendel(SPRITZ, ko, kn, k.krone() + 0.05, -nx, -ny, Math.sqrt(2 * 9.81 * Math.min(3, 0.3 + 2 * vq)), alter, tx, ty, wu, wv, nr * 131L + q + 13);
            }
            double U = Math.hypot(wu, wv);
            if (U > fahneAb) buendel(FAHNE, wlo + nx * 0.5 * c * tp * 0.2, wln + ny * 0.5 * c * tp * 0.2, eta + 0.6 * Hq, 0, 0, 1.5, alter, tx, ty, wu, wv, nr * 131L + q + 7);
        }
    }

    /** Ein Querschnitt als Band längs der Kante (±2,6 m), Dreiecke direkt. */
    private void strip(Brecherform.Curve cu, double wlo, double wln, double nx, double ny, double tx, double ty, double eta, double uc, double tan, double uw) {
        int P = punkte;
        float[] px = new float[P], py = new float[P], pz = new float[P], fo = new float[P], th = new float[P], qn = new float[3 * P];
        double[] uu = new double[P], hh = new double[P];
        for (int a = 0; a < P; a++) {
            int kk = Math.min(Brecherform.K - 1, (int) Math.round(a * (Brecherform.K - 1) / (double) (P - 1)));
            double u = uc + cu.x[kk];                                    // landwärts von der Wasserlinie (m)
            double z = eta + cu.z[kk];
            double grund = u > -uw ? eta + u * tan : eta - uw * tan;     // Hang oder Fläche vor dem Fuß
            z = Math.max(z, grund + 0.01);
            uu[a] = u;
            hh[a] = z;
            fo[a] = (float) cu.foam[kk];
            th[a] = (float) cu.thin[kk];
        }
        for (int a = 0; a < P; a++) {
            int a0 = Math.max(0, a - 1), a1 = Math.min(P - 1, a + 1);
            double du = uu[a1] - uu[a0], dz = hh[a1] - hh[a0], l = Math.hypot(du, dz);
            double nu = l > 1e-9 ? -dz / l : 0, nzv = l > 1e-9 ? du / l : 1;  // Normale in (landwärts, oben)
            // landwärts ist −n: Normale in Ost/Nord = −n · nu
            qn[3 * a] = (float) (-nx * nu);
            qn[3 * a + 1] = (float) nzv;
            qn[3 * a + 2] = (float) (-ny * nu);
            px[a] = (float) (wlo - nx * uu[a] - e0);
            pz[a] = (float) (n0 - (wln - ny * uu[a]));
            py[a] = (float) hh[a];
        }
        double hl = Kanten.LAENGE / 2 + 0.1;
        for (int a = 0; a + 1 < P; a++) {
            int[] ia = {a, a + 1, a + 1, a, a + 1, a};
            double[] sa = {-hl, -hl, hl, -hl, hl, hl};
            for (int v = 0; v < 6; v++) {
                int b = ia[v];
                put((float) (px[b] + tx * sa[v]), py[b], (float) (pz[b] - ty * sa[v]), fo[b], qn[3 * b], qn[3 * b + 1], -qn[3 * b + 2], 2 + th[b]);
            }
        }
    }

    // ------------------------------------------------------------------ Lippen (seit S5b-5)

    /** Was ein Abschnitt für die Lippe braucht; erst nach allen Abschnitten gezeichnet, damit die Nachbarn bekannt sind. */
    private static final class Lz {
        int nr, nw, ix;
        double eta, hf, tan, tp, gs, nx, ny, wlo, wln, hMax, lauf, fo, fn, dx, dy, c;
        Lz na, nb;                                                      // Nachbarn an A (−t) und B (+t) in diesem Bild
        // seit S5b-5d: derselbe Kamm beim Nachbarn an A und B (Index, −1: keiner) und Zwischenwerte für das Glätten
        final int[] lA = new int[Fusswellen.KAEMME_MAX + 3], lB = new int[Fusswellen.KAEMME_MAX + 3];
        final double[] g1 = new double[3 * (Fusswellen.KAEMME_MAX + 3)], g2 = new double[3 * (Fusswellen.KAEMME_MAX + 3)];
        final double[] ta = new double[Fusswellen.KAEMME_MAX + 3], hz = new double[Fusswellen.KAEMME_MAX + 3], hr = new double[Fusswellen.KAEMME_MAX + 3], tz = new double[Fusswellen.KAEMME_MAX + 3];
    }

    /** Ein Ende der Lippe: Ort an der Wasserlinie, Richtung zur See, Höhen, Form, Sichtbarkeit f und Absenkung. */
    private static final class Ende {
        double px, py, nx, ny, eta, tan, uw, uc, f, sink, sicht, H, tb, tW, tau, back;
        int typ, lo, hi;
        boolean geteilt;
        double bekannt;
        Brecherform.Curve cu;
    }

    private final List<Lz> lzPool = new ArrayList<>();
    private int lzZahl;
    private final Ende endeA = new Ende(), endeB = new Ende();

    private void merkeLippe(int nr, int nw, double eta, double hf, double tan, double tp, double c, double dx, double dy,
                            double nx, double ny, double wlo, double wln, double hMax, double fo, double fn) {
        if (lzZahl == lzPool.size()) lzPool.add(new Lz());
        Lz z = lzPool.get(lzZahl);
        z.ix = lzZahl++;
        z.nr = nr; z.nw = nw; z.eta = eta; z.hf = hf; z.tan = tan; z.tp = tp; z.nx = nx; z.ny = ny; z.wlo = wlo; z.wln = wln; z.hMax = hMax;
        z.gs = (-ny * dx + nx * dy) / c;                                // Ankunft längs t (s/m), wie die Kämme am Fuß [G]
        for (int j = 0; j < nw; j++) { z.ta[j] = wTa[j]; z.hz[j] = wHz[j]; z.hr[j] = wH[j]; z.tz[j] = wTz[j]; }
        z.lauf = nw > 0 ? wTa[0] - wTz[0] : 0;                         // Laufzeit vom Fuß zur Wasserlinie
        z.fo = fo; z.fn = fn; z.dx = dx; z.dy = dy; z.c = c;
    }

    /** Seit S5b-5b: Nachbarn höchstens so weit auseinander (Wasserlinie zu Wasserlinie, m) [G]. */
    public double ketteMax = 12;
    private final java.util.Map<Long, List<Lz>> kettenGitter = new java.util.HashMap<>();
    private double[] kd = new double[64];
    private int[] kp = new int[64], kq = new int[64], ks = new int[64];
    private Integer[] kOrd = new Integer[0];

    /**
     * Seit S5b-5b (Befund am Bild 24.11., „wieder kachelig“): die Nachbarn je Bild aus den Wasserlinien der Abschnitte mit
     * Lippe, nicht aus einer festen Kette. Die Füße sind der steilste Punkt je Zelle von 5 m (Kantenkarte), oft im Zickzack
     * oben und unten am Hang, 3 bis 9 m auseinander; die Wasserlinien liegen dagegen auf einer Linie. Paare: Richtung zur
     * See ähnlich (n·n' ≥ 0,3), höchstens {@link #ketteMax} m, für beide nicht zu quer (|quer| ≤ 2 |längs| + 1 m,
     * |längs| ≥ 0,2 m: die Richtung zur See der Abschnitte ist oft um 20° bis 60° gegen die Linie verdreht); die
     * kürzesten zuerst, je Seite eines Abschnitts einer (gierig, damit gegenseitig) [G].
     */
    private void ketten() {
        int m = lzZahl;
        double z = ketteMax;
        for (List<Lz> li : kettenGitter.values()) li.clear();
        for (int q = 0; q < m; q++) {
            Lz a = lzPool.get(q);
            a.na = a.nb = null;
            kettenGitter.computeIfAbsent(schluessel(Math.floor(a.wlo / z), Math.floor(a.wln / z)), x -> new ArrayList<>()).add(a);
        }
        int c = 0;
        for (int q = 0; q < m; q++) {
            Lz a = lzPool.get(q);
            long gx = (long) Math.floor(a.wlo / z), gy = (long) Math.floor(a.wln / z);
            for (long x = gx - 1; x <= gx + 1; x++)
                for (long y = gy - 1; y <= gy + 1; y++) {
                    List<Lz> li = kettenGitter.get(schluessel(x, y));
                    if (li == null) continue;
                    for (Lz b : li) {
                        if (b.nr <= a.nr || a.nx * b.nx + a.ny * b.ny < 0.3) continue;
                        double dx = b.wlo - a.wlo, dy = b.wln - a.wln, d = Math.hypot(dx, dy);
                        if (d > z) continue;
                        double sa = dx * -a.ny + dy * a.nx, qa = dx * a.nx + dy * a.ny;
                        double sb = -(dx * -b.ny + dy * b.nx), qb = -(dx * b.nx + dy * b.ny);
                        if (Math.abs(sa) < 0.2 || Math.abs(qa) > 2 * Math.abs(sa) + 1 || Math.abs(sb) < 0.2 || Math.abs(qb) > 2 * Math.abs(sb) + 1) continue;
                        if (c == kd.length) {
                            kd = java.util.Arrays.copyOf(kd, 2 * c);
                            kp = java.util.Arrays.copyOf(kp, 2 * c);
                            kq = java.util.Arrays.copyOf(kq, 2 * c);
                            ks = java.util.Arrays.copyOf(ks, 2 * c);
                        }
                        kd[c] = d;
                        kp[c] = q;
                        kq[c] = b.ix;
                        ks[c] = (sa > 0 ? 1 : 0) | (sb > 0 ? 2 : 0);
                        c++;
                    }
                }
        }
        if (kOrd.length < c) kOrd = new Integer[Math.max(c, 2 * kOrd.length)];
        for (int i = 0; i < c; i++) kOrd[i] = i;
        final double[] dd = kd;
        java.util.Arrays.sort(kOrd, 0, c, (u, v) -> {
            int r = Double.compare(dd[u], dd[v]);
            return r != 0 ? r : Integer.compare(u, v);
        });
        for (int i = 0; i < c; i++) {
            int o = kOrd[i];
            Lz a = lzPool.get(kp[o]), b = lzPool.get(kq[o]);
            boolean aB = (ks[o] & 1) != 0, bB = (ks[o] & 2) != 0;
            if ((aB ? a.nb : a.na) != null || (bB ? b.nb : b.na) != null) continue;
            if (aB) a.nb = b; else a.na = b;
            if (bB) b.nb = a; else b.na = a;
        }
    }

    /** Seit S5b-5d: halbe Breite des Fensters längs der Kette (Abschnitte, je rund 5 m); 0: nicht glätten [G]. */
    public int glattBreite = 3;
    /**
     * Seit S5b-5d: die Spuren der Wellen glätten (an); die Lage (Neigung, Höhe am Fuß, Wasserlinie, Richtung) glätten nur
     * zum Vergleich (aus: an den echten Kanten mehr kurze Stücke, Warft 23 % → 41 % [Hilfe im Container]).
     */
    public boolean glattLage = false, glattSpur = true;
    private final List<Lz> kette = new ArrayList<>();
    private boolean[] besucht = new boolean[0];
    private double[] gTmp = new double[0];

    /**
     * Seit S5b-5d (Seegang Werkbuch 53): je Paar von Nachbarn derselbe Kamm – gegenseitig der nächste Durchgang am Fuß,
     * gemessen am erwarteten Unterschied zwischen den beiden Füßen (Lage der Füße längs der mittleren Laufrichtung durch
     * die mittlere Geschwindigkeit am Fuß), höchstens {@link #lippeFang} Tp daneben. Die Füße liegen bei flachem Hang bis
     * 40 m vor der Wasserlinie und quer verschoben; der Unterschied längs der Wasserlinie (S5b-5b) traf dort oft nicht.
     */
    private void spuren() {
        for (int q = 0; q < lzZahl; q++) {
            Lz a = lzPool.get(q);
            java.util.Arrays.fill(a.lA, 0, a.nw, -1);
            java.util.Arrays.fill(a.lB, 0, a.nw, -1);
        }
        for (int q = 0; q < lzZahl; q++) {
            Lz a = lzPool.get(q);
            for (int s = 0; s < 2; s++) {
                Lz b = s == 0 ? a.na : a.nb;
                if (b == null || b.ix < a.ix) continue;                     // jedes Paar einmal
                int[] la = s == 0 ? a.lA : a.lB, lb = b.na == a ? b.lA : b.lB;
                double ux = a.dx + b.dx, uy = a.dy + b.dy, lu = Math.hypot(ux, uy), cm = 0.5 * (a.c + b.c);
                double dt = lu > 0 && cm > 0 ? ((b.fo - a.fo) * ux + (b.fn - a.fn) * uy) / lu / cm : 0;
                double fang = lippeFang * 0.5 * (a.tp + b.tp);
                for (int j = 0; j < a.nw; j++) {
                    int m = naechster(b, a.tz[j] + dt, fang);
                    if (m >= 0 && naechster(a, b.tz[m] - dt, fang) == j) { la[j] = m; lb[m] = j; }
                }
            }
        }
    }

    /** Der Kamm von b, der zu Kamm j von a gehört (b Nachbar von a), oder −1. */
    private static int kamm(Lz a, Lz b, int j) {
        if (j < 0 || b == null) return -1;
        return a.na == b ? a.lA[j] : a.nb == b ? a.lB[j] : -1;
    }

    /**
     * Seit S5b-5d (Seegang Werkbuch 53, Befund L3e: Splitter): je Kette (aus {@link #ketten}) Neigung, Höhe am Fuß, η,
     * Tp, Gradient der Ankunft, Lage der Wasserlinie und Richtung zur See über ±{@link #glattBreite} Abschnitte glätten,
     * und je Welle längs ihrer Spur ({@link #spuren}) Aufschlag und Höhen – erst gleitender Median (Ausreißer), dann
     * gleitendes Dreiecksmittel. Vorher schaltete die Lippe alle paar Meter an und aus: Aufschlag und Höhe sprangen von
     * Abschnitt zu Abschnitt.
     */
    private void glaetten() {
        int m = lzZahl, w = glattBreite;
        if (w <= 0 || m == 0) return;
        if (besucht.length < m) besucht = new boolean[Math.max(m, 2 * besucht.length)];
        java.util.Arrays.fill(besucht, 0, m, false);
        for (int runde = 0; runde < 2; runde++)                         // erst offene Ketten (von einem Ende), dann Ringe
            for (int q = 0; q < m; q++) {
                Lz a = lzPool.get(q);
                if (besucht[a.ix] || runde == 0 && a.na != null && a.nb != null) continue;
                kette.clear();
                Lz vor = null, cur = a;
                while (cur != null && !besucht[cur.ix]) {
                    besucht[cur.ix] = true;
                    kette.add(cur);
                    Lz nx = cur.na != null && cur.na != vor ? cur.na : cur.nb != vor ? cur.nb : null;
                    vor = cur;
                    cur = nx;
                }
                if (glattLage) glaetteKette(kette, runde == 1, w);
                if (glattSpur) glaetteSpuren(kette, runde == 1, w);
            }
    }

    private static final int G_FELDER = 10;

    private void glaetteKette(List<Lz> k, boolean ring, int w) {
        int n = k.size();
        if (n < 2) return;
        if (gTmp.length < 2 * G_FELDER * n) gTmp = new double[2 * G_FELDER * n];
        double[] roh = gTmp;
        int o2 = G_FELDER * n;
        for (int i = 0; i < n; i++) {
            Lz a = k.get(i);
            double[] v = {a.tan, a.hf, a.eta, a.tp, a.gs, a.wlo, a.wln, a.nx, a.ny, a.hMax};
            System.arraycopy(v, 0, roh, G_FELDER * i, G_FELDER);
        }
        double[] fenster = new double[2 * w + 1];
        // gleitender Median für Neigung und Höhe am Fuß (Felder 0, 1), in die zweite Hälfte
        for (int i = 0; i < n; i++)
            for (int f = 0; f < G_FELDER; f++) {
                if (f > 1) { roh[o2 + G_FELDER * i + f] = roh[G_FELDER * i + f]; continue; }
                int c = 0;
                for (int d = -w; d <= w; d++) {
                    int j = i + d;
                    if (ring) j = Math.floorMod(j, n); else if (j < 0 || j >= n) continue;
                    fenster[c++] = roh[G_FELDER * j + f];
                }
                roh[o2 + G_FELDER * i + f] = median(fenster, c);
            }
        // gleitendes Dreiecksmittel über alle Felder, zurück in die Abschnitte
        double[] s = new double[G_FELDER];
        for (int i = 0; i < n; i++) {
            Lz a = k.get(i);
            java.util.Arrays.fill(s, 0);
            double gw = 0;
            for (int d = -w; d <= w; d++) {
                int j = i + d;
                if (ring) j = Math.floorMod(j, n); else if (j < 0 || j >= n) continue;
                double g = w + 1 - Math.abs(d);
                gw += g;
                for (int f = 0; f < G_FELDER; f++) s[f] += g * roh[o2 + G_FELDER * j + f];
            }
            for (int f = 0; f < G_FELDER; f++) s[f] /= gw;
            double l = Math.hypot(s[7], s[8]);
            a.tan = s[0]; a.hf = s[1]; a.eta = s[2]; a.tp = s[3]; a.gs = s[4]; a.wlo = s[5]; a.wln = s[6];
            if (l > 1e-6) { a.nx = s[7] / l; a.ny = s[8] / l; }
            a.hMax = s[9];
        }
    }

    /** Je Welle längs ihrer Spur in der Kette: Aufschlag, gezeichnete und Rang-Höhe (Median, dann Dreiecksmittel). */
    private void glaetteSpuren(List<Lz> k, boolean ring, int w) {
        int n = k.size();
        if (n < 2) return;
        double[] fenster = new double[2 * w + 1];
        for (int durchgang = 0; durchgang < 2; durchgang++) {
            for (int i = 0; i < n; i++) {
                Lz a = k.get(i);
                for (int j = 0; j < a.nw; j++)
                    for (int f = 0; f < 3; f++) {
                        double sg = 0, gw = 0;
                        int c = 0;
                        for (int richtung = -1; richtung <= 1; richtung += 2) {
                            int jj = j;
                            Lz cur = a;
                            for (int d = 0; d <= w; d++) {
                                if (d > 0) {
                                    int ii = i + richtung * d;
                                    if (ring) ii = Math.floorMod(ii, n); else if (ii < 0 || ii >= n) break;
                                    Lz nx = k.get(ii);
                                    jj = kamm(cur, nx, jj);
                                    if (jj < 0) break;
                                    cur = nx;
                                } else if (richtung > 0) continue;                // die Mitte nur einmal
                                double v = durchgang == 0 ? roh(cur, jj, f) : cur.g1[3 * jj + f];
                                if (durchgang == 0) fenster[c++] = v;
                                else { double g = w + 1 - d; sg += g * v; gw += g; }
                            }
                        }
                        if (durchgang == 0) a.g1[3 * j + f] = median(fenster, c);
                        else a.g2[3 * j + f] = sg / gw;
                    }
            }
        }
        for (Lz a : k)
            for (int j = 0; j < a.nw; j++) {
                a.ta[j] = a.g2[3 * j];
                a.hz[j] = a.g2[3 * j + 1];
                a.hr[j] = a.g2[3 * j + 2];
            }
    }

    private static double roh(Lz a, int j, int f) { return f == 0 ? a.ta[j] : f == 1 ? a.hz[j] : a.hr[j]; }

    private static double median(double[] v, int c) {
        java.util.Arrays.sort(v, 0, c);
        return c % 2 == 1 ? v[c / 2] : 0.5 * (v[c / 2 - 1] + v[c / 2]);
    }

    private static long schluessel(double x, double y) { return ((long) x << 32) ^ ((long) y & 0xffffffffL); }

    /**
     * Je gemerktem Abschnitt jede Welle der Folge, die an einem Ende sichtbar ist (f > 0): vom Auftauchen vor dem
     * Brechpunkt bis zum Abtauchen nach der Walze, wenn ξ dort Sturz oder Reflexion ergibt. Bei kurzem Abstand der Kämme
     * auch zwei (die Walze der einen, die Lippe der nächsten).
     */
    private void lippenZeichnen(Kanten kanten, double t) {
        if (lippenNachbarn) {
            ketten();
            spuren();
            glaetten();
        }
        for (int q = 0; q < lzZahl; q++) {
            Lz a = lzPool.get(q);
            Lz na = lippenNachbarn ? a.na : null, nb = lippenNachbarn ? a.nb : null;
            for (int j = 0; j < a.nw; j++) {
                if (!(a.hz[j] > 0.05)) continue;
                ende(a, j, na, -1, t, endeA);
                ende(a, j, nb, 1, t, endeB);
                if (endeA.f <= 0 && endeB.f <= 0) continue;
                form(endeA);
                form(endeB);
                stripZwei(a, j, endeA, endeB);
                lippenZahl++;
                lippenSumme++;
            }
        }
    }

    /**
     * Ein Ende (e = −1: A, +1: B) für die Welle j von a. Mit dem Nachbarn b gemittelt (Ort an der Wasserlinie, Richtung,
     * η, h, tan α, Tp; und die Welle, wenn b einen Kamm kennt, der mit j gegenseitig der nächste ist, gemessen am
     * erwarteten Unterschied der Ankunft längs der Kante, höchstens {@link #lippeFang} Tp daneben) – von beiden Seiten
     * gleich gerechnet; sonst a allein mit dem Unterschied der Ankunft längs t. Seit S5b-5b: ein Ende, das kein Nachbar
     * mit derselben Welle teilt (Ende der Kette, oder der Nachbar kennt den Kamm nicht), ist unsichtbar (f = 0): die
     * Lippe taucht dort ab, statt mit einer Kante zu enden.
     */
    private void ende(Lz a, int j, Lz b, int e, double t, Ende o) {
        double ta = a.ta[j], H = Math.min(a.hz[j], a.hMax), Hr = a.hr[j];
        double tx = -a.ny, ty = a.nx, eta, hf, tan, tp;
        if (b != null) {
            o.px = 0.5 * (a.wlo + b.wlo);
            o.py = 0.5 * (a.wln + b.wln);
            // seit S5b-5b: quer zur Verbindung der Wasserlinien (die Richtungen der Abschnitte sind oft verdreht); bei kurzer
            // Verbindung (unter 3 m) zunehmend das Mittel der Richtungen der beiden; von beiden Seiten gleich
            double sx = a.nx + b.nx, sy = a.ny + b.ny, ls = Math.hypot(sx, sy);
            double vx = b.wlo - a.wlo, vy = b.wln - a.wln, lv = Math.hypot(vx, vy);
            double qx = lv > 0 ? -vy / lv : 0, qy = lv > 0 ? vx / lv : 0;
            if (qx * sx + qy * sy < 0) { qx = -qx; qy = -qy; }
            double gw = Math.min(1, lv / 3);
            double nx = gw * qx + (1 - gw) * sx / ls, ny = gw * qy + (1 - gw) * sy / ls, l = Math.hypot(nx, ny);
            o.nx = nx / l;
            o.ny = ny / l;
            eta = 0.5 * (a.eta + b.eta);
            hf = 0.5 * (a.hf + b.hf);
            tan = 0.5 * (a.tan + b.tan);
            tp = 0.5 * (a.tp + b.tp);
            // derselbe Kamm beim Nachbarn: aus den Spuren (seit S5b-5d, nach dem Durchgang an den beiden Füßen)
            int m = kamm(a, b, j);
            o.geteilt = m >= 0;
            o.bekannt = m >= 0 ? Math.max(a.tz[j], b.tz[m]) : Double.NaN;
            o.lo = Math.min(a.nr, b.nr);
            o.hi = Math.max(a.nr, b.nr);
            if (m >= 0) {
                ta = 0.5 * (ta + b.ta[m]);
                H = 0.5 * (H + Math.min(b.hz[m], b.hMax));
                Hr = 0.5 * (Hr + b.hr[m]);
            } else ta += ((o.px - a.wlo) * tx + (o.py - a.wln) * ty) * a.gs;
        } else {
            double hl = Kanten.LAENGE / 2 + 0.1;
            o.px = a.wlo + e * hl * tx;
            o.py = a.wln + e * hl * ty;
            o.nx = a.nx;
            o.ny = a.ny;
            o.geteilt = false;
            eta = a.eta; hf = a.hf; tan = a.tan; tp = a.tp;
            ta += e * hl * a.gs;
        }
        int typ = Brecherform.typ(Brecherform.iribarren(tan, Math.max(Hr, 0.02), tp));
        boolean lippe = typ == Brecherform.STURZ || typ == Brecherform.REFLEXION;
        double db = typ <= Brecherform.STURZ ? Math.min(H / 0.78, hf) / tan : 0.3 * H / tan;   // Brechpunkt seewärts der Wasserlinie (m)
        double tb = laufzeit(db * tan, tan), tW = (phWalze - phBrechen) * tp, tAn = lippeAn * tp, tau = ta - t;
        o.f = !lippe || tau > tb + tAn ? 0 : tau > tb ? 1 - (tau - tb) / tAn : tau >= -tW ? 1 : Math.max(0, 1 - (-tW - tau) / lippeAus);
        double c = Math.sqrt(9.81 * Math.max(0.5, Math.min(5, hf)));
        o.back = Math.min(30, 0.4 * c * tp);
        o.typ = lippe ? typ : Brecherform.STURZ;
        o.H = H;
        o.tb = tb;
        o.tW = tW;
        o.tau = tau;
        o.eta = eta;
        o.tan = tan;
        o.uw = hf / tan;
        if (!o.geteilt && lippenNachbarn) o.f = 0;                       // seit S5b-5b: wo kein Nachbar diese Welle teilt, taucht die Lippe ab
        // seit S5b-5b: erst wenn beide Füße den Kamm sicher kennen (Durchgang + lippeKennt), in lippeEin auftauchen – der
        // Nachbar weiter längs der Kante sieht ihn später, sonst spränge das Ende von unsichtbar auf sichtbar
        if (o.geteilt) o.f *= Math.max(0, Math.min(1, (t - o.bekannt - lippeKennt) / lippeEin));
        o.sink = (1 - o.f) * (H + 0.5);                                   // unsichtbar: unter Wasser und Grund
    }

    /** Geteilte Enden dieses Bilds: dieselbe Form von beiden Seiten nur einmal rechnen. */
    private record Geteilt(int lo, int hi, double tau, Brecherform.Curve cu, double uc) {}
    private final java.util.Map<Long, Geteilt> geteilt = new java.util.HashMap<>();
    /** Zuletzt gerechnete Formen der Lippen (für die Rechenzeit). */
    public int formenGerechnet;

    /** Die Form eines Endes, erst wenn die Lippe gezeichnet wird; ein geteiltes Ende aus dem Speicher, wenn der Nachbar es schon hat. */
    private void form(Ende o) {
        long key = 0;
        if (o.geteilt) {
            key = o.lo * 0x9E3779B97F4A7C15L ^ o.hi * 0xC2B2AE3D27D4EB4FL ^ Double.doubleToLongBits(o.tau);
            Geteilt g = geteilt.get(key);
            if (g != null && g.lo == o.lo && g.hi == o.hi && g.tau == o.tau) { o.cu = g.cu; o.uc = g.uc; return; }
        }
        double[] uc = {0};
        o.cu = lippenForm(o.typ, o.H, o.tan, o.tb, o.tW, o.tau, o.back, 0.5 * o.back, uc);
        o.uc = uc[0];
        formenGerechnet++;
        if (o.geteilt) geteilt.put(key, new Geteilt(o.lo, o.hi, o.tau, o.cu, o.uc));
    }

    /** Der Kamm von z mit dem Durchgang am Fuß am nächsten an tz, höchstens d entfernt; −1 ohne. */
    private static int naechster(Lz z, double tz, double d) {
        int m = -1;
        for (int i = 0; i < z.nw; i++) {
            double x = Math.abs(z.tz[i] - tz);
            if (x < d) { d = x; m = i; }
        }
        return m;
    }

    /** Form der Lippe für die Zeit τ bis zum Aufschlag (geklemmt auf −T_Walze … t_b); uc: Lage des Kamms (m, landwärts der Wasserlinie). */
    private static Brecherform.Curve lippenForm(int typ, double H, double tan, double tb, double tWalze, double tr, double back, double front, double[] uc) {
        tr = Math.max(-tWalze, Math.min(tb, tr));
        if (tr >= 0) {
            double p = tb > 0 ? 1 - tr / tb : 1, u = 9.81 * tan * tr * tr / 4, hl = Math.max(0.05, u * tan);
            uc[0] = -u;
            return typ == Brecherform.STURZ ? Brecherform.plunging(H, hl, back, front, p) : Brecherform.collapsing(H, hl, back, front, p);
        }
        double p = tWalze > 0 ? -tr / tWalze : 1;
        uc[0] = 0;
        // Walze mit demselben Rücken wie die Lippe; gleich nach dem Aufschlag aus der Lippe übergeblendet (sonst springt die Form)
        Brecherform.Curve bo = Brecherform.bore(Math.max(0.05, 0.6 * H * (1 - p)), 0.1, back, front);
        double tBl = 0.3 * tWalze;
        if (-tr < tBl) {
            Brecherform.Curve la = typ == Brecherform.STURZ ? Brecherform.plunging(H, 0.05, back, front, 1) : Brecherform.collapsing(H, 0.05, back, front, 1);
            bo = Brecherform.blend(la, bo, -tr / tBl);
        }
        return bo;
    }

    /** Wie {@link #strip}, mit je einer Form an den Enden A und B; die Dreiecke verbinden sie. */
    private void stripZwei(Lz z, int j, Ende ea, Ende eb) {
        int P = punkte;
        double[][] xx = new double[2][P], yy = new double[2][P], hh = new double[2][P];
        float[][] fo = new float[2][P], th = new float[2][P], qn = new float[2][3 * P];
        Ende[] es = {ea, eb};
        for (int e = 0; e < 2; e++) {
            Ende en = es[e];
            Brecherform.Curve cu = en.cu;
            double[] uu = new double[P];
            en.sicht = Double.NEGATIVE_INFINITY;
            for (int a = 0; a < P; a++) {
                int kk = Math.min(Brecherform.K - 1, (int) Math.round(a * (Brecherform.K - 1) / (double) (P - 1)));
                double u = en.uc + cu.x[kk], zz = en.eta + cu.z[kk];
                double grund = u > -en.uw ? en.eta + u * en.tan : en.eta - en.uw * en.tan;
                double h = Math.max(zz, grund + 0.01) - en.sink;
                uu[a] = u;
                xx[e][a] = en.px - en.nx * u;
                yy[e][a] = en.py - en.ny * u;
                hh[e][a] = h;
                en.sicht = Math.max(en.sicht, h - Math.max(en.eta, grund));
                fo[e][a] = (float) cu.foam[kk];
                th[e][a] = (float) cu.thin[kk];
            }
            for (int a = 0; a < P; a++) {
                int a0 = Math.max(0, a - 1), a1 = Math.min(P - 1, a + 1);
                double du = uu[a1] - uu[a0], dz = hh[e][a1] - hh[e][a0], l = Math.hypot(du, dz);
                double nu = l > 1e-9 ? -dz / l : 0, nzv = l > 1e-9 ? du / l : 1;
                qn[e][3 * a] = (float) (-en.nx * nu);
                qn[e][3 * a + 1] = (float) nzv;
                qn[e][3 * a + 2] = (float) (-en.ny * nu);
            }
        }
        int[] en = {0, 0, 1, 0, 1, 1};
        for (int a = 0; a + 1 < P; a++) {
            int[] ia = {a, a + 1, a + 1, a, a + 1, a};
            for (int v = 0; v < 6; v++) {
                int b = ia[v], e = en[v];
                put((float) (xx[e][b] - e0), (float) hh[e][b], (float) (n0 - yy[e][b]), fo[e][b], qn[e][3 * b], qn[e][3 * b + 1], -qn[e][3 * b + 2], 2 + th[e][b]);
            }
        }
        if (protokollieren) {
            double[] q = new double[LIPPE_KOPF + 6 * P];
            q[0] = z.nr;
            q[1] = P;
            q[2] = z.ta[j];
            q[3] = z.hz[j];
            q[4] = z.gs;
            q[5] = Brecherform.typ(Brecherform.iribarren(z.tan, Math.max(z.hr[j], 0.02), z.tp));
            q[6] = ea.f;
            q[7] = eb.f;
            q[8] = ea.sicht;
            q[9] = eb.sicht;
            q[10] = ea.geteilt ? 1 : 0;
            q[11] = eb.geteilt ? 1 : 0;
            q[12] = ea.px - e0;
            q[13] = n0 - ea.py;
            q[14] = eb.px - e0;
            q[15] = n0 - eb.py;
            for (int e = 0; e < 2; e++)
                for (int a = 0; a < P; a++) {
                    int o = LIPPE_KOPF + 3 * (e * P + a);
                    q[o] = xx[e][a] - e0;
                    q[o + 1] = hh[e][a];
                    q[o + 2] = n0 - yy[e][a];
                }
            lippenProtokoll.add(q);
        }
    }

    /** Der Wasserfilm des Auflaufs von der Wasserlinie (−0,5 m) bis u_r (m landwärts), 6 Reihen. */
    private void film(double wlo, double wln, double nx, double ny, double tx, double ty, double eta, double tan, double ur, double fs) {
        if (ur <= 0.05) return;
        int R = 6;
        double hl = Kanten.LAENGE / 2 + 0.1, nl = Math.hypot(tan, 1);
        float nxo = (float) (nx * tan / nl), nyv = (float) (1 / nl), nzs = (float) (-ny * tan / nl);   // hangab und oben; z = −Nord
        for (int r = 0; r < R; r++) {
            double u0 = -0.5 + (ur + 0.5) * r / R, u1 = -0.5 + (ur + 0.5) * (r + 1) / R;
            double[] us = {u0, u1, u1, u0, u1, u0};
            double[] sa = {-hl, -hl, hl, -hl, hl, hl};
            for (int v = 0; v < 6; v++) {
                double u = us[v], rel = Math.max(0, Math.min(1, (u + 0.5) / (ur + 0.5)));
                double y = eta + u * tan + 0.02 + 0.04 * (1 - rel);
                float schaum = (float) Math.min(1, (fs < 0.4 ? 0.9 : 0.6) * (0.3 + 0.7 * rel));
                put((float) (wlo - nx * u + tx * sa[v] - e0), (float) y, (float) (n0 - (wln - ny * u + ty * sa[v])), schaum, nxo, nyv, nzs, 1);
            }
        }
    }

    /** Seit S5b-2: hängt fertige Bündel ({@link #BUENDEL} Werte je Bündel, z. B. {@link Kammgischt}) an die des letzten {@link #rechne} an. */
    public void anhaengen(float[] f) {
        if (f == null || f.length == 0) return;
        if (bn + f.length > bz.length) bz = java.util.Arrays.copyOf(bz, Math.max(2 * bz.length, bn + f.length));
        System.arraycopy(f, 0, bz, bn, f.length);
        bn += f.length;
        buendelZahl = bn / BUENDEL;
        buendel = bz;
    }

    private void put(float x, float y, float z, float f, float a, float b, float c, float d) {
        if (nn + SCHEITEL > nz.length) nz = java.util.Arrays.copyOf(nz, nz.length * 2);
        nz[nn++] = x; nz[nn++] = y; nz[nn++] = z; nz[nn++] = f;
        nz[nn++] = a; nz[nn++] = b; nz[nn++] = c; nz[nn++] = d;
    }

    /**
     * Ein Bündel (Aufbau wie {@code gischt.vert}): Ort, Alter, Startgeschwindigkeit (hangauf und nach oben), Wind,
     * Reibung, Streuung, Größe, Art, Zahl der Tropfen, Samen.
     */
    private void buendel(int art, double o, double n, double y, double rx, double ry, double v0, double alter, double tx, double ty, double wu, double wv, long samen) {
        if (bn / BUENDEL >= buendelMax) return;
        if (bn + BUENDEL > bz.length) bz = java.util.Arrays.copyOf(bz, bz.length * 2);
        double k = art == SPRITZ ? 0.3 : 1.2, streu = art == SPRITZ ? 2.0 : 3.0;
        double vh = art == SPRITZ ? 0.35 * v0 : 0;
        float[] b = {(float) (o - e0), (float) y, (float) (n0 - n), (float) alter,
                (float) (rx * vh), (float) (art == SPRITZ ? v0 : v0), (float) (-ry * vh), (float) k,
                (float) wu, (float) -wv, (float) streu, (float) (art == SPRITZ ? 0.5 * streu : 1.0),
                (float) (tx), (float) (-ty), (float) (art == SPRITZ ? 0.06 : 0.10), (float) (art * 65536 + (int) (zufall(samen, 3) * 65535))};
        System.arraycopy(b, 0, bz, bn, BUENDEL);
        bn += BUENDEL;
    }
}
