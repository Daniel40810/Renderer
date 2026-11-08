package com.dan.gertsch;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Die Kennwerte eines Holzschnitts, wie {@link #messen} sie findet: Papier und Farbe, Größe und Lage der gestochenen
 * Punkte, Verteilung der Tonwerte (Anteil Papier), Wolken im Farbauftrag und Korn. Gemessen an Gertschs Holzschnitt
 * liegen sie als {@link Vorlagen#druck()} vor; {@link Stich} druckt danach, und dieselbe Messung auf einem Druck sagt,
 * wie nah er der Vorlage kommt.
 * <p>
 * Ein Holzschnitt nach Gertsch hat eine Farbe auf hellem Papier. Wo er in den Stock sticht, bleibt ein Punkt Papier;
 * das Licht entsteht allein aus der Dichte dieser Punkte. Die Messung trennt deshalb je Bildpunkt Papier und Farbe:
 * <ol>
 * <li><b>Farbe</b> ohne Punkte: eine Öffnung (Minimum, dann Maximum über ein Quadrat) nimmt die hellen Punkte weg und
 * gibt eine Schwelle; das Feld der Farbe ist das Mittel der Bildpunkte darunter, nur aus Gegenden mit wenig Punkten
 * (normierte Faltung). Ein Mittel, kein Minimum: sonst zählte das Korn der Farbe als Papier.</li>
 * <li><b>Papier</b>: das Mittel der hellsten 0,1 % (im linearen Licht).</li>
 * <li><b>Anteil Papier</b> je Bildpunkt: a = (Y − Y_Farbe) / (Y_Papier − Y_Farbe), nach oben auf 1 begrenzt (nach unten
 * nicht, damit sich das Rauschen der Farbe heraus mittelt). Das Mittel von a über einige Punkte ist der Ton.</li>
 * <li><b>Punkte</b> in dünnen Gegenden (Ton unter 0,1): Spitzen von a; die Summe von a um die Spitze ist die Fläche
 * des Punkts, auch wenn die Aufnahme ihn verwischt. Daraus der wirksame Durchmesser, der Abstand zum nächsten Punkt und
 * die Ordnung nach Clark und Evans (1954): R = mittlerer Abstand zum Nächsten / (0,5 / √Dichte), 1 für Zufall,
 * größer für gleichmäßig verteilte Punkte (Blue Noise etwa 1,5 bis 1,8), kleiner für Haufen.</li>
 * <li><b>Wolken</b>: Streuung von L* im Feld der Farbe und seine Autokorrelation waagerecht und senkrecht bei den
 * Abständen {@link #LAG}.</li>
 * <li><b>Korn</b>: Streuung von L* je Bildpunkt um das geglättete Bild, wo nur Farbe ist.</li>
 * </ol>
 * Alle Abstände der Messung sind für {@link #BREITE} Bildpunkte Breite gewählt (die Vorlage hat 1438) und wachsen mit
 * der Breite; Größen stehen als Anteile der Bildbreite. Alle Felder sind offen.
 */
public final class Druck {
    /** Breite, für die die Abstände der Messung gewählt sind (Bildpunkte); so breit drucken {@code StichDemo} und die Prüfung. */
    public static final int BREITE = 1440;
    /** Abstände der Autokorrelation der Wolken (Anteile der Breite). */
    public static final double[] LAG = {0.02, 0.04, 0.08, 0.16, 0.31};
    /** Perzentile der Tonverteilung: 0, 5, … 100 %. */
    public static final int TON = 21;

    public String name = "", titel = "", quelle = "";
    public int breite, hoehe;
    /** Papier und Farbe in Lab. */
    public double[] papier = new double[3], farbe = new double[3];
    /** Wirksamer Durchmesser eines Punkts (gleiche Fläche wie eine Scheibe) und Median des Abstands zum nächsten, Anteile der Breite. */
    public double punkt, abstand;
    /** Ordnung der Punkte nach Clark und Evans (1 Zufall, darüber gleichmäßig), und wie viele Punkte gezählt wurden. */
    public double ordnung;
    public int punkte;
    /** Ton: Perzentile 0, 5, … 100 % des Anteils Papier (Mittel über einige Punkte), und sein Mittel. */
    public double[] ton = new double[TON];
    public double tonMittel;
    /** Wolken: Streuung von L* der Farbe; Autokorrelation waagerecht und senkrecht bei {@link #LAG}. */
    public double wolken;
    public double[] wolkenX = new double[LAG.length], wolkenY = new double[LAG.length];
    /** Korn: Streuung von L* je Bildpunkt in der Farbe. */
    public double korn;
    /** Nur nach einer Messung: wie lange sie dauerte. */
    public double sekunden;

    /** Misst einen Holzschnitt (oder einen Druck von {@link Stich}). */
    public static Druck messen(BufferedImage bild) {
        int w = bild.getWidth(), h = bild.getHeight();
        return messen(bild.getRGB(0, 0, w, h, null, 0, w), w, h);
    }

    /** Misst ARGB-Bildpunkte (zeilenweise, w × h). */
    public static Druck messen(int[] argb, int w, int h) {
        long t0 = System.nanoTime();
        int n = w * h;
        double k = w / (double) BREITE;
        int rand = Math.max(2, (int) Math.round(6 * k)), oeffnen = Math.max(2, (int) Math.round(3 * k));
        float[] R = new float[n], G = new float[n], B = new float[n], Y = new float[n];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, p = argb[i];
                R[i] = (float) Lab.linear(p >> 16);
                G[i] = (float) Lab.linear(p >> 8);
                B[i] = (float) Lab.linear(p);
                Y[i] = 0.2126f * R[i] + 0.7152f * G[i] + 0.0722f * B[i];
            }
        });
        Druck d = new Druck();
        d.breite = w;
        d.hoehe = h;

        // Papier: die hellsten 0,1 % innerhalb des Rands
        float[] innen = new float[(w - 2 * rand) * (h - 2 * rand)];
        int m = 0;
        for (int y = rand; y < h - rand; y++) for (int x = rand; x < w - rand; x++) innen[m++] = Y[y * w + x];
        Arrays.sort(innen);
        float schwelle = innen[(int) (0.999 * (innen.length - 1))];
        double[] pap = new double[3];
        int pn = 0;
        for (int y = rand; y < h - rand; y++)
            for (int x = rand; x < w - rand; x++) {
                int i = y * w + x;
                if (Y[i] >= schwelle) { pap[0] += R[i]; pap[1] += G[i]; pap[2] += B[i]; pn++; }
            }
        Lab.vonLinear(pap[0] / pn, pap[1] / pn, pap[2] / pn, d.papier);
        double yPapier = (0.2126 * pap[0] + 0.7152 * pap[1] + 0.0722 * pap[2]) / pn;

        // Farbe ohne Punkte: Öffnung, dann wo wenig Punkte sind
        float[] farbe = maxi(mini(Y, w, h, oeffnen), w, h, oeffnen);
        float[] hell = new float[n];
        for (int i = 0; i < n; i++) hell[i] = Y[i] > 1.3f * farbe[i] ? 1 : 0;
        float[] dichte = gauss(hell, w, h, 6 * k);
        double[] fa = new double[3];
        int fn = 0;
        for (int y = rand; y < h - rand; y++)
            for (int x = rand; x < w - rand; x++) {
                int i = y * w + x;
                if (dichte[i] < 0.05f && Y[i] <= 1.1f * farbe[i]) { fa[0] += R[i]; fa[1] += G[i]; fa[2] += B[i]; fn++; }
            }
        if (fn == 0) throw new IllegalArgumentException("gertsch: keine Fläche nur in Farbe gefunden – ist das ein Holzschnitt?");
        Lab.vonLinear(fa[0] / fn, fa[1] / fn, fa[2] / fn, d.farbe);
        // Feld der Farbe: Mittel der Bildpunkte, die nach Farbe aussehen (unter einem Viertel zum Papier über der Öffnung),
        // nicht die Öffnung selbst: das Minimum läge bei Korn unter der Farbe, und jeder Bildpunkt Farbe zählte als etwas Papier
        float[] maske = new float[n], wert = new float[n];
        for (int i = 0; i < n; i++)
            if (dichte[i] < 0.15f && Y[i] < farbe[i] + 0.25f * (yPapier - farbe[i])) { maske[i] = 1; wert[i] = Y[i]; }
        float[] gm = gauss(maske, w, h, 4 * k), gw = gauss(wert, w, h, 4 * k);
        float[] gm2 = gauss(maske, w, h, 25 * k), gw2 = gauss(wert, w, h, 25 * k);
        float mittelFarbe = (float) ((0.2126 * fa[0] + 0.7152 * fa[1] + 0.0722 * fa[2]) / fn);
        float[] feld = new float[n];
        for (int i = 0; i < n; i++)
            feld[i] = gm[i] > 0.05f ? gw[i] / gm[i] : gm2[i] > 1e-4f ? gw2[i] / gm2[i] : mittelFarbe;

        // Anteil Papier je Bildpunkt und Ton
        float[] a = new float[n];
        // nach unten nicht begrenzt: das Rauschen in der Farbe mittelt sich so heraus, statt als Papier zu zählen
        for (int i = 0; i < n; i++) a[i] = (float) Math.min(1, (Y[i] - feld[i]) / (yPapier - feld[i]));
        float[] ton = gauss(a, w, h, 4 * k);
        float[] tv = new float[(w - 2 * rand) * (h - 2 * rand)];
        m = 0;
        double summe = 0;
        for (int y = rand; y < h - rand; y++) for (int x = rand; x < w - rand; x++) { tv[m] = ton[y * w + x]; summe += tv[m++]; }
        Arrays.sort(tv);
        for (int q = 0; q < TON; q++) d.ton[q] = Math.max(0, tv[(int) Math.round(q / (TON - 1.0) * (tv.length - 1))]);
        d.tonMittel = summe / tv.length;

        // Punkte in dünnen Gegenden: Spitzen von a (5 × 5), Fläche = Summe von a um die Spitze
        int[] spitzen = new int[n / 4];
        float[] flaeche = new float[n / 4];
        int sp = 0;
        int r0 = rand + 3;
        for (int y = r0; y < h - r0; y++)
            for (int x = r0; x < w - r0; x++) {
                int i = y * w + x;
                float v = a[i];
                if (v < 0.2f || ton[i] > 0.10f) continue;
                boolean spitze = true;
                for (int dy = -2; dy <= 2 && spitze; dy++)
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        float q = a[i + dy * w + dx];
                        if (q > v || (q == v && (dy < 0 || (dy == 0 && dx < 0)))) { spitze = false; break; }
                    }
                if (!spitze) continue;
                float s = 0;
                for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) s += a[i + dy * w + dx];
                if (sp == spitzen.length) { spitzen = Arrays.copyOf(spitzen, 2 * sp); flaeche = Arrays.copyOf(flaeche, 2 * sp); }
                spitzen[sp] = i;
                flaeche[sp++] = s;
            }
        d.punkte = sp;
        if (sp > 0) {
            float[] fl = Arrays.copyOf(flaeche, sp);
            Arrays.sort(fl);
            d.punkt = 2 * Math.sqrt(fl[sp / 2] / Math.PI) / w;
            double[] nn = naechste(spitzen, sp, w, h, 10 * k);
            d.abstand = nn[0] / w;
            d.ordnung = nn[1];
        }

        // Wolken: Feld der Farbe in L*, wo es gemessen ist (nicht nur ergänzt)
        float[] feldL = new float[n];
        boolean[] gueltig = new boolean[n];
        double mu = 0;
        int gn = 0;
        for (int i = 0; i < n; i++) {
            feldL[i] = (float) lstern(feld[i]);
            int x = i % w, y = i / w;
            gueltig[i] = gm[i] > 0.3f && x >= rand && y >= rand && x < w - rand && y < h - rand;
            if (gueltig[i]) { mu += feldL[i]; gn++; }
        }
        mu /= Math.max(1, gn);
        double var = 0;
        for (int i = 0; i < n; i++) if (gueltig[i]) var += (feldL[i] - mu) * (feldL[i] - mu);
        var /= Math.max(1, gn);
        d.wolken = Math.sqrt(var);
        for (int l = 0; l < LAG.length; l++) {
            int lag = (int) Math.round(LAG[l] * w);
            double sx = 0, sy = 0;
            long nx = 0, ny = 0;
            for (int y = 0; y < h; y += 2)
                for (int x = 0; x < w; x += 2) {
                    int i = y * w + x;
                    if (!gueltig[i]) continue;
                    double dv = feldL[i] - mu;
                    if (x + lag < w && gueltig[i + lag]) { sx += dv * (feldL[i + lag] - mu); nx++; }
                    if (y + lag < h && gueltig[i + lag * w]) { sy += dv * (feldL[i + lag * w] - mu); ny++; }
                }
            d.wolkenX[l] = nx == 0 || var == 0 ? 0 : sx / nx / var;
            d.wolkenY[l] = ny == 0 || var == 0 ? 0 : sy / ny / var;
        }

        // Korn: L* je Bildpunkt um das Geglättete, wo nur Farbe ist
        float[] glatt = gauss(Y, w, h, 1.5 * k);
        double ks = 0;
        int kn = 0;
        for (int y = rand; y < h - rand; y++)
            for (int x = rand; x < w - rand; x++) {
                int i = y * w + x;
                if (dichte[i] < 0.03f && Y[i] <= 1.1f * farbe[i]) {
                    double dl = lstern(Y[i]) - lstern(glatt[i]);
                    ks += dl * dl;
                    kn++;
                }
            }
        d.korn = kn == 0 ? 0 : Math.sqrt(ks / kn);
        d.sekunden = (System.nanoTime() - t0) / 1e9;
        return d;
    }

    /**
     * Median des Abstands zum nächsten Punkt (Bildpunkte) und Ordnung nach Clark und Evans, die Dichte je Punkt aus den
     * Nachbarn im Umkreis {@code radius} (so stört es wenig, dass die Dichte im Bild wechselt).
     */
    static double[] naechste(int[] idx, int n, int w, int h, double radius) {
        int zelle = Math.max(4, (int) Math.ceil(radius / 2)), gw = w / zelle + 1, gh = h / zelle + 1;
        int[] kopf = new int[gw * gh], weiter = new int[n];
        Arrays.fill(kopf, -1);
        for (int p = 0; p < n; p++) {
            int c = (idx[p] / w / zelle) * gw + (idx[p] % w) / zelle;
            weiter[p] = kopf[c];
            kopf[c] = p;
        }
        int reich = (int) Math.ceil(radius / zelle);
        double summeR = 0;
        int zahlR = 0;
        double[] nn = new double[n];
        int nnZahl = 0;
        for (int p = 0; p < n; p++) {
            int px = idx[p] % w, py = idx[p] / w;
            if (px < radius || py < radius || px > w - radius || py > h - radius) continue;
            int gx = px / zelle, gy = py / zelle, zahl = 0;
            double best = Double.MAX_VALUE;
            for (int yy = Math.max(0, gy - reich); yy <= Math.min(gh - 1, gy + reich); yy++)
                for (int xx = Math.max(0, gx - reich); xx <= Math.min(gw - 1, gx + reich); xx++)
                    for (int q = kopf[yy * gw + xx]; q >= 0; q = weiter[q]) {
                        if (q == p) continue;
                        double dd = Math.hypot(idx[q] % w - px, idx[q] / w - py);
                        if (dd < best) best = dd;
                        if (dd <= radius) zahl++;
                    }
            if (zahl < 2 || best > radius) continue;
            double dichte = zahl / (Math.PI * radius * radius);
            summeR += best / (0.5 / Math.sqrt(dichte));
            zahlR++;
            nn[nnZahl++] = best;
        }
        if (nnZahl == 0) return new double[]{0, 0};
        Arrays.sort(nn, 0, nnZahl);
        return new double[]{nn[nnZahl / 2], summeR / zahlR};
    }

    static double lstern(double y) { return y > Lab.E ? 116 * Math.cbrt(y) - 16 : y * Lab.K; }

    /** Minimum über ein Quadrat (2r + 1), getrennt nach Zeilen und Spalten, am Rand begrenzt. */
    static float[] mini(float[] v, int w, int h, int r) { return extrem(v, w, h, r, false); }

    static float[] maxi(float[] v, int w, int h, int r) { return extrem(v, w, h, r, true); }

    private static float[] extrem(float[] v, int w, int h, int r, boolean max) {
        float[] t = new float[v.length], o = new float[v.length];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float e = v[y * w + x];
                for (int q = Math.max(0, x - r); q <= Math.min(w - 1, x + r); q++) e = max ? Math.max(e, v[y * w + q]) : Math.min(e, v[y * w + q]);
                t[y * w + x] = e;
            }
        });
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float e = t[y * w + x];
                for (int q = Math.max(0, y - r); q <= Math.min(h - 1, y + r); q++) e = max ? Math.max(e, t[q * w + x]) : Math.min(e, t[q * w + x]);
                o[y * w + x] = e;
            }
        });
        return o;
    }

    /** Gauß-Glättung, getrennt, am Rand fortgesetzt. */
    static float[] gauss(float[] v, int w, int h, double sigma) {
        int r = Math.max(1, (int) Math.ceil(3 * sigma));
        float[] k = new float[2 * r + 1];
        double s = 0;
        for (int i = -r; i <= r; i++) s += k[i + r] = (float) Math.exp(-i * i / (2 * sigma * sigma));
        for (int i = 0; i < k.length; i++) k[i] /= (float) s;
        float[] t = new float[v.length], o = new float[v.length];
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float e = 0;
                for (int i = -r; i <= r; i++) e += k[i + r] * v[y * w + Math.max(0, Math.min(w - 1, x + i))];
                t[y * w + x] = e;
            }
        });
        IntStream.range(0, h).parallel().forEach(y -> {
            for (int x = 0; x < w; x++) {
                float e = 0;
                for (int i = -r; i <= r; i++) e += k[i + r] * t[Math.max(0, Math.min(h - 1, y + i)) * w + x];
                o[y * w + x] = e;
            }
        });
        return o;
    }

    /** Ton zum Anteil p (0..1) der Fläche, aus den Perzentilen linear. */
    public double tonBei(double p) {
        double x = Math.max(0, Math.min(1, p)) * (TON - 1);
        int i = Math.min(TON - 2, (int) x);
        return ton[i] + (x - i) * (ton[i + 1] - ton[i]);
    }

    /** Kennwerte als Text. */
    public String bericht() {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.GERMAN, "%s%s: %d × %d, gemessen in %.1f s%n", titel.isEmpty() ? name : titel,
                quelle.isEmpty() ? "" : " (" + quelle + ")", breite, hoehe, sekunden));
        s.append(String.format(Locale.GERMAN, "  Papier      L%.1f/%+.1f/%+.1f   Farbe L%.1f/%+.1f/%+.1f%n", papier[0], papier[1], papier[2], farbe[0], farbe[1], farbe[2]));
        s.append(String.format(Locale.GERMAN, "  Punkte      %.3f %% der Breite (%.2f px bei %d), Abstand %.3f %% (%.1f px), Ordnung R %.2f, %d gezählt%n",
                100 * punkt, punkt * BREITE, BREITE, 100 * abstand, abstand * BREITE, ordnung, punkte));
        s.append(String.format(Locale.GERMAN, "  Ton         Papier im Mittel %.1f %%, Median %.1f %%, 75 %% %.1f %%, 95 %% %.1f %%, größter %.1f %%%n",
                100 * tonMittel, 100 * ton[10], 100 * ton[15], 100 * ton[19], 100 * ton[20]));
        s.append(String.format(Locale.GERMAN, "  Wolken      Streuung L* %.2f, Autokorrelation waagerecht/senkrecht", wolken));
        for (int l = 0; l < LAG.length; l++) s.append(String.format(Locale.GERMAN, "  %.0f %%: %.2f/%.2f", 100 * LAG[l], wolkenX[l], wolkenY[l]));
        s.append(String.format(Locale.GERMAN, "%n  Korn        Streuung L* %.2f je Bildpunkt%n", korn));
        return s.toString();
    }

    /** Java-Quelltext, der diese Kennwerte als Preset baut (für {@link Vorlagen}). */
    public String alsJava(String methode, String javadoc) {
        StringBuilder s = new StringBuilder();
        s.append("    /** ").append(javadoc).append(" */\n");
        s.append("    public static Druck ").append(methode).append("() {\n");
        s.append("        Druck d = new Druck();\n");
        s.append(String.format(Locale.ROOT, "        d.name = \"%s\"; d.titel = \"%s\"; d.quelle = \"%s\"; d.breite = %d; d.hoehe = %d;%n", name, titel, quelle, breite, hoehe));
        s.append("        d.papier = new double[]").append(Vorlage.feld(papier, 2)).append("; d.farbe = new double[]").append(Vorlage.feld(farbe, 2)).append(";\n");
        s.append(String.format(Locale.ROOT, "        d.punkt = %.6f; d.abstand = %.6f; d.ordnung = %.4f; d.punkte = %d;%n", punkt, abstand, ordnung, punkte));
        s.append("        d.ton = new double[]").append(Vorlage.feld(ton, 4)).append(";\n");
        s.append(String.format(Locale.ROOT, "        d.tonMittel = %.4f; d.wolken = %.3f; d.korn = %.3f;%n", tonMittel, wolken, korn));
        s.append("        d.wolkenX = new double[]").append(Vorlage.feld(wolkenX, 3)).append("; d.wolkenY = new double[]").append(Vorlage.feld(wolkenY, 3)).append(";\n");
        s.append("        return d;\n    }\n");
        return s.toString();
    }
}
