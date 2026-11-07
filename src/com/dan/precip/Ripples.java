package com.dan.precip;

/**
 * Ringe auf dem Wasser als echte Kapillar- und Schwerewellen, spektral gelöst: Auf einem n × n-Raster (Zweierpotenz,
 * ringsum geschlossen) wird jede Welle e^(i k·x) für sich und genau fortgeschrieben – ein gedämpfter Schwinger mit
 * ω²(k) = g k + (σ/ρ) k³ (Lamb 1932). So stimmt die Ausbreitung für jede Wellenlänge, die das Raster trägt, ohne
 * Zeitschrittgrenze und ohne die Fehler eines Faltungskerns.
 * <p>
 * Ein Tropfen gibt der Fläche einen Druckstoß I(x) (Pa·s) mit dem Integral seines Impulses m v, als Gaußglocke von der
 * Breite seines Kraters. Nach Cauchy und Poisson setzt das jeder Welle die Geschwindigkeit ∂h/∂t um −|k| Î(k) / ρ;
 * daraus entsteht von allein, was man auf einem Teich im Regen sieht: die Mulde, die auseinanderlaufenden Ringe – außen
 * die langen, innen dicht die kurzen –, und eine ruhige Mitte, die mit der kleinsten Gruppengeschwindigkeit von
 * 17,7 cm/s wächst, weil keine Welle langsamer Energie trägt.
 * <p>
 * Gedämpft wird mit der Zähigkeit, 2 ν k² je Amplitude, verstärkt durch die natürlichen Häute auf See- und Brackwasser
 * ({@link #FILM}, [G]), dazu ein kleiner Grundverlust. Die Grafikkarte rechnet dasselbe in {@code gl/ripple_*.comp}
 * mit eigener FFT; {@link Field} ist die Rechnung auf dem Prozessor für die Prüfung.
 */
public final class Ripples {

    /** Verstärkung der Zähigkeitsdämpfung durch Oberflächenhäute [G] (Lamb: 1, verschmutzte Häfen bis 10). */
    public static final double FILM = 3;
    /** Grundverlust der Amplitude (1/s) [G]: Ringe sind nach gut zwei Sekunden vergangen. */
    public static final double BASE = 0.35;

    public final int n;
    public final double dx, size, kLow, kHigh;

    /** Ein Feld von n × n Zellen zu dx m, das alle Wellen bis zur Rastergrenze π/dx trägt. */
    public Ripples(int n, double dx) { this(n, dx, 0, Math.PI / dx); }

    /**
     * Ein Feld, das nur das Band kLow ≤ |k| &lt; kHigh trägt (1/m): für Kaskaden, in denen ein feines Feld die kurzen,
     * ein grobes die langen Wellen hält. Über π/dx (in den Ecken des Spektrums) bleibt alles still, damit das Feld in
     * jeder Richtung gleich ist.
     */
    public Ripples(int n, double dx, double kLow, double kHigh) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("n muss eine Zweierpotenz sein: " + n);
        this.n = n;
        this.dx = dx;
        this.size = n * dx;
        this.kLow = kLow;
        this.kHigh = Math.min(kHigh, Math.PI / dx);
    }

    /** Wellenzahl (1/m) der Mode mit dem Index i (0..n−1) in einer Richtung, Vorzeichen wie bei der FFT. */
    public double kAxis(int i) { return 2 * Math.PI / size * (i < n / 2 ? i : i - n); }

    /** Trägt das Feld die Wellenzahl |k|? */
    public boolean inBand(double k) { return k >= kLow && k < kHigh && k > 0; }

    /** Abklingrate γ (1/s) der Amplitude einer Welle mit der Wellenzahl k. */
    public static double decay(double k) { return Drops.viscousDecay(k) * FILM + BASE; }

    /** Impuls (N·s) eines Tropfens von D mm mit v m/s: m v. */
    public static double momentum(double d, double v) { return Drops.RHO_W * Math.PI / 6 * Math.pow(d * 1e-3, 3) * v; }

    /**
     * Verstärkung des Druckstoßes: 1, der reine Impuls m v. Geprüft an der Messung von Craeye, Sobieski, Bliven und
     * Guissard (1999, IEEE J. Oceanic Eng. 24, 323): 0,8 s nach dem Einschlag eines 2,8-mm-Tropfens (93 % der
     * Fallgeschwindigkeit) steht der höchste Ring bei 13 cm mit rund 0,1 mm, beim 2,2-mm-Tropfen mit 0,05 mm, Wellenlänge
     * knapp 3 cm; das Feld gibt 0,10 und 0,045 mm bei 15 cm und 2,6 cm ({@code PrecipCheck}). Bis W7b Runde 4 stand hier
     * 3, damit die Mulde den Krater nach Engel (1966) trifft – der Krater ist aber nichtlinear und kurz; die Ringe, die
     * man sieht, waren damit dreimal zu hoch.
     */
    public static final double GAIN = 1;

    /** Druckstoß (N·s), den das Feld nimmt: GAIN · m v. */
    public static double impulse(double d, double v) { return GAIN * momentum(d, v); }

    /** Breite σ (m) des Druckstoßes: ein halber Durchmesser [G], aber mindestens 0,7 Zellen (sonst sieht ihn das Raster nicht). */
    public static double spread(double d, double dx) { return Math.max(0.5e-3 * d, 0.7 * dx); }

    /**
     * Eine Welle um dt fortschreiben: Ḣ = V, V̇ = −ω² H − 2γ V, genau gelöst. s hält H und V als (re H, im H, re V, im V)
     * ab Stelle o.
     */
    public static void advance(double[] s, int o, double w2, double gamma, double dt) {
        double wd = Math.sqrt(Math.max(1e-12, w2 - gamma * gamma)), e = Math.exp(-gamma * dt);
        double c = Math.cos(wd * dt), sn = Math.sin(wd * dt);
        double hh = e * (c + gamma / wd * sn), hv = e * sn / wd, vv = e * (c - gamma / wd * sn), vh = -e * w2 / wd * sn;
        for (int q = 0; q < 2; q++) {
            double h = s[o + q], v = s[o + 2 + q];
            s[o + q] = hh * h + hv * v;
            s[o + 2 + q] = vh * h + vv * v;
        }
    }

    /**
     * Mittlere quadratische Steigung ⟨(∂h/∂x)² + (∂h/∂z)²⟩, die Regen auf ruhigem Wasser auf Dauer hält: Einschläge
     * aller Tropfen von d0 bis d1 mm (Poisson, je Größe N(D) v(D) dD je m² und s), jeder setzt jeder Welle die
     * Geschwindigkeit um −k Î(k)/ρ; eine Welle hält davon im Mittel (k Î/ρ)² / (4 γ ω²) über die Zeit. Also
     * ⟨|∇h|²⟩ = 1/(2π) ∫ dD N v (I/ρ)² ∫ k⁵ e^(−k²σ²) / (4 γ ω²) dk.
     * dx 0: das Wasser selbst (Kraterbreite σ = D/2, alle Wellen); sonst, was ein Raster mit dx m trägt (σ =
     * {@link #spread}, k &lt; π/dx). Die Differenz ist die Rauigkeit unter dem Raster ({@link #subgridVariance}).
     * Gegen das Feld geprüft ({@code PrecipCheck}).
     */
    public static double slopeVariance(double n0, double lambda, double d0, double d1, double dx) {
        if (n0 <= 0 || d1 <= d0) return 0;
        int nd = 96, nk = 480;
        double dd = (d1 - d0) / nd, sum = 0;
        for (int a = 0; a < nd; a++) {
            double d = d0 + (a + 0.5) * dd, v = Drops.terminal(d), flux = n0 * Math.exp(-lambda * d) * v * dd;
            double i = impulse(d, v) / Drops.RHO_W, sg = dx > 0 ? spread(d, dx) : 0.5e-3 * d;
            double k1 = dx > 0 ? Math.PI / dx : 6 / sg, l0 = 0, l1 = Math.log(k1), dl = (l1 - l0) / nk, inner = 0;
            for (int b = 0; b < nk; b++) {
                double k = Math.exp(l0 + (b + 0.5) * dl), w = Drops.omega(k);
                inner += Math.pow(k, 6) * Math.exp(-k * k * sg * sg) / (4 * decay(k) * w * w) * dl;   // k⁵ dk = k⁶ d(ln k)
            }
            sum += flux * i * i * inner;
        }
        return sum / (2 * Math.PI);
    }

    /**
     * Wie fleckig die Steigung des Regens ist: der Anteil p der Fläche, der sie trägt – als Mischung aus glatt (1 − p)
     * und Gauß (p, Varianz/p), angepasst an ⟨|s|⟩ und ⟨s²⟩: p = (2/π) ⟨|s|⟩² / ⟨s²⟩ (für Gauß 1). Gemessen am Feld mit
     * 1-mm-Raster (W7b Runde 5): unter 16 mm Wellenlänge p = 0,14 / 0,20 / 0,26 bei 1 / 5 / 20 mm/h – die kurzen Wellen
     * sitzen um die jungen Einschläge, dazwischen bleibt der Spiegel glatt –, darüber (die Ringe, die ein 8-mm-Raster
     * trägt) p = 0,34. Die Kurtosis ist noch viel größer (280 bei 5 mm/h: die Kerne der Krater); für das Bild zählt die
     * Masse. {unter, über 16 mm} [G: Potenz durch die drei Messungen].
     */
    public static double[] intermittency(double rate) {
        double r = Math.max(0.05, rate);
        return new double[]{Math.max(0.05, Math.min(0.6, 0.20 * Math.pow(r / 5, 0.21))), 0.34};
    }

    /** Was davon unter einem Raster mit dx m liegt (die Rauigkeit, die das Feld nicht trägt): {@link #slopeVariance} bei 0 minus bei dx. */
    public static double subgridVariance(double n0, double lambda, double d0, double d1, double dx) {
        return Math.max(0, slopeVariance(n0, lambda, d0, d1, 0) - slopeVariance(n0, lambda, d0, d1, dx));
    }

    /** Kleinste Gruppengeschwindigkeit (m/s) der Kapillar- und Schwerewellen und ihre Wellenzahl: {c, k}. */
    public static double[] groupMinimum() {
        double best = 1e9, bk = 0;
        for (double k = 20; k < 2000; k *= 1.0005) {
            double c = Drops.groupSpeed(k);
            if (c < best) { best = c; bk = k; }
        }
        return new double[]{best, bk};
    }

    // ------------------------------------------------------------------ Rechnung auf dem Prozessor

    /** Das Feld auf dem Prozessor: Spektrum von Höhe und Geschwindigkeit, Quelle im Ort. */
    public final class Field {
        /** (re H, im H, re V, im V) je Mode, Index (j · n + i) · 4. */
        public final double[] spec = new double[n * n * 4];
        private final double[] srcRe = new double[n * n], srcIm = new double[n * n];
        public double time;

        /** Ein Tropfen von D mm mit v m/s trifft bei x, z (m, im Feld ringsum geschlossen). */
        public void impact(double x, double z, double d, double v) {
            double p = impulse(d, v), sg = spread(d, dx);
            int r = (int) Math.ceil(3.5 * sg / dx);
            double cx = x / dx, cz = z / dx, sum = 0;
            int ci = (int) Math.floor(cx), cj = (int) Math.floor(cz);
            for (int pass = 0; pass < 2; pass++) {
                double norm = pass == 0 ? 0 : p / (sum * dx * dx);
                for (int j = cj - r; j <= cj + r + 1; j++)
                    for (int i = ci - r; i <= ci + r + 1; i++) {
                        double ex = (i + 0.5 - cx) * dx, ez = (j + 0.5 - cz) * dx, w = Math.exp(-(ex * ex + ez * ez) / (2 * sg * sg));
                        if (pass == 0) sum += w;
                        else srcRe[Math.floorMod(j, n) * n + Math.floorMod(i, n)] += norm * w;
                    }
            }
        }

        /** Um dt weiter: Quelle in die Wellen, dann jede Welle genau fortgeschrieben. */
        public void step(double dt) {
            Fft.transform2d(srcRe, srcIm, n, false);
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    int m = j * n + i, o = m * 4;
                    double k = Math.hypot(kAxis(i), kAxis(j));
                    if (!inBand(k)) {
                        java.util.Arrays.fill(spec, o, o + 4, 0);
                    } else {
                        spec[o + 2] -= k / Drops.RHO_W * srcRe[m];
                        spec[o + 3] -= k / Drops.RHO_W * srcIm[m];
                        double w = Drops.omega(k);
                        advance(spec, o, w * w, decay(k), dt);
                    }
                    srcRe[m] = srcIm[m] = 0;
                }
            time += dt;
        }

        /** Höhe (m) und Steigungen ∂h/∂x, ∂h/∂z je Zelle: {h, sx, sz}, Index j · n + i. */
        public double[][] surface() {
            double[] hr = new double[n * n], hi = new double[n * n], sr = new double[n * n], si = new double[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    int m = j * n + i, o = m * 4;
                    double kx = i == n / 2 ? 0 : kAxis(i), kz = j == n / 2 ? 0 : kAxis(j);
                    double re = spec[o], im = spec[o + 1];
                    // h + i ∂x h in einer Rückwandlung: Ĥ + i (i kx Ĥ) = Ĥ (1 − kx)
                    hr[m] = re * (1 - kx);
                    hi[m] = im * (1 - kx);
                    // ∂z h: i kz Ĥ
                    sr[m] = -kz * im;
                    si[m] = kz * re;
                }
            Fft.transform2d(hr, hi, n, true);
            Fft.transform2d(sr, si, n, true);
            double s = 1.0 / ((double) n * n);
            double[] h = new double[n * n], sx = new double[n * n], sz = new double[n * n];
            for (int m = 0; m < n * n; m++) { h[m] = hr[m] * s; sx[m] = hi[m] * s; sz[m] = sr[m] * s; }
            return new double[][]{h, sx, sz};
        }

        /** Höhe (m) am Ort aus einer gesetzten Fläche (zum Anlegen eines Anfangszustands): h(x) wird ins Spektrum gelegt. */
        public void setHeight(java.util.function.DoubleBinaryOperator h) {
            double[] re = new double[n * n], im = new double[n * n];
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) re[j * n + i] = h.applyAsDouble((i + 0.5) * dx, (j + 0.5) * dx);
            Fft.transform2d(re, im, n, false);
            for (int m = 0; m < n * n; m++) {
                spec[m * 4] = re[m];
                spec[m * 4 + 1] = im[m];
                spec[m * 4 + 2] = spec[m * 4 + 3] = 0;
            }
        }
    }

    /** Schnelle Fourier-Transformation (Radix 2) für die Prüfung; Vorzeichen: invers = +i, ohne Normierung. */
    static final class Fft {
        private Fft() {}

        static void transform(double[] re, double[] im, int off, int n, boolean inverse) {
            for (int i = 1, j = 0; i < n; i++) {
                int bit = n >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    int a = off + i, b = off + j;
                    double t = re[a]; re[a] = re[b]; re[b] = t;
                    t = im[a]; im[a] = im[b]; im[b] = t;
                }
            }
            double sign = inverse ? 1 : -1;
            for (int len = 2; len <= n; len <<= 1) {
                int half = len >> 1;
                for (int i = 0; i < n; i += len)
                    for (int k = 0; k < half; k++) {
                        double ang = 2 * Math.PI * k / len, cr = Math.cos(ang), ci = sign * Math.sin(ang);
                        int a = off + i + k, b = a + half;
                        double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                        re[b] = re[a] - xr; im[b] = im[a] - xi;
                        re[a] += xr; im[a] += xi;
                    }
            }
        }

        static void transform2d(double[] re, double[] im, int n, boolean inverse) {
            for (int z = 0; z < n; z++) transform(re, im, z * n, n, inverse);
            double[] cr = new double[n * n], ci = new double[n * n];
            for (int z = 0; z < n; z++)
                for (int x = 0; x < n; x++) { cr[x * n + z] = re[z * n + x]; ci[x * n + z] = im[z * n + x]; }
            for (int x = 0; x < n; x++) transform(cr, ci, x * n, n, inverse);
            for (int z = 0; z < n; z++)
                for (int x = 0; x < n; x++) { re[z * n + x] = cr[x * n + z]; im[z * n + x] = ci[x * n + z]; }
        }
    }
}
