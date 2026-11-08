package com.dan.gertsch.demo;

import com.dan.gertsch.Quelle;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Eine Testszene, wie sie von außen käme (Phase 5): Huflattich (Tussilago farfara) im Gras, von schräg oben, nach dem
 * Vorbild der Vorlage „Blätter“. Sie hat ihr eigenes kleines Modell (Pflanzen mit Stielen und Blättern, Halme mit Fuß,
 * Höhe, Richtung und Biegung, Streu, Erdbeeren mit drei Blättchen) und kennt das Paket nur über {@link Quelle}:
 * {@link #alsQuelle()} ist die Brücke, wie eine App sie für Bäume aus {@code forest} und Gras aus {@code reed} schreibt.
 * <p>
 * Welt wie im Dickicht: Meter, y nach oben, Boden y = 0; die Kamera ({@link #kamera}) steht bei x = z = 0 in 1,5 m Höhe und
 * blickt 55° nach unten. Größen nach der Flora [G]: Blätter des Huflattichs 10–30 cm, Stiele bis 20 cm; Halme 25–60 cm.
 */
public final class Huflattich {
    public long seed = 1;
    /** Bereich (m): Breite in x (mittig) und Tiefe in z. */
    public double breite = 3.2, nah = 0.2, fern = 3.4;
    /** Pflanzen, Blätter je Pflanze (von bis), Länge eines Blatts (von bis, m), Breite durch Länge, Höhe der Stiele (m). */
    public int pflanzen = 75, blattMin = 3, blattMax = 6;
    public double laengeMin = 0.11, laengeMax = 0.24, rund = 0.95, stielMin = 0.06, stielMax = 0.2;
    /** Neigung der Blätter gegen die Waagerechte (Grad, bis). */
    public double neigung = 35;
    /** Halme, ihre Höhe (von bis, m), Breite am Fuß (m). */
    public int halme = 1500;
    public double halmMin = 0.22, halmMax = 0.6, halmBreite = 0.0045;
    /** Streu (liegende Blättchen und Nadeln), ihre Länge (m). */
    public int streu = 4500;
    public double streuLaenge = 0.022;
    /** Erdbeeren: Pflanzen mit Blättern aus drei Blättchen. */
    public int erdbeeren = 60;

    /** Albedo (linear) [G]: Huflattich oben, Stiel, Halm grün und dürr, Streu, Erdbeere, Boden. */
    public float[] blatt = {0.075f, 0.13f, 0.06f}, stiel = {0.22f, 0.26f, 0.05f}, halmGruen = {0.06f, 0.15f, 0.05f},
            halmDuerr = {0.24f, 0.2f, 0.08f}, streuFarbe = {0.32f, 0.12f, 0.035f}, erdbeere = {0.045f, 0.09f, 0.035f},
            boden = {0.022f, 0.018f, 0.013f};

    /** Ein Blatt des Modells: Ansatz (Spitze des Stiels), Richtung, in die es zeigt (waagerecht, rad), Neigung (rad), Länge, Breite, Albedo. */
    static final class Blatt {
        double x, y, z, richtung, neig, laenge, breite;
        float[] farbe;
        int pflanze;
    }

    /** Ein Halm des Modells: Fuß, Höhe, Richtung der Neigung (rad), Neigung (0 = senkrecht, 1 = 45°), Biegung, Breite, Albedo. */
    static final class Halm {
        double x, z, hoehe, richtung, neig, biegung, breite;
        float[] farbe;
    }

    final List<Blatt> blaetter = new ArrayList<>();
    final List<Halm> halmListe = new ArrayList<>();
    /** Stiele: Fuß und Spitze (x, y, z), Radius. */
    final List<double[]> stiele = new ArrayList<>();

    /** Kamera der Szene: 1,5 m hoch, 55° nach unten. */
    public com.dan.gertsch.Kamera kamera() {
        com.dan.gertsch.Kamera k = new com.dan.gertsch.Kamera();
        k.auge = new double[]{0, 1.5, 0};
        k.neigung = -55;
        k.nah = 0.1;
        return k;
    }

    public Huflattich bauen() {
        blaetter.clear();
        halmListe.clear();
        stiele.clear();
        SplittableRandom r = new SplittableRandom(seed);
        double hb = breite / 2;
        // Huflattich: Stiele aus einem Fuß, Blätter fast waagerecht, nach außen
        for (int p = 0; p < pflanzen; p++) {
            double x = r.nextDouble(-hb, hb), z = nah + r.nextDouble() * (fern - nah);
            int n = blattMin + r.nextInt(blattMax - blattMin + 1);
            double groesse = laengeMin + (laengeMax - laengeMin) * Math.pow(r.nextDouble(), 0.8), dreh = r.nextDouble(2 * Math.PI);
            for (int k = 0; k < n; k++) {
                double ri = dreh + 2 * Math.PI * k / n + r.nextGaussian() * 0.35, h = stielMin + (stielMax - stielMin) * r.nextDouble();
                double weg = 0.03 + 0.06 * r.nextDouble();
                double sx = x + Math.sin(ri) * weg, sz = z + Math.cos(ri) * weg;
                stiele.add(new double[]{x, 0, z, sx, h, sz, 0.0025 + 0.002 * r.nextDouble()});
                Blatt b = new Blatt();
                b.laenge = groesse * (0.75 + 0.35 * r.nextDouble());
                b.breite = b.laenge * rund * (0.9 + 0.2 * r.nextDouble());
                b.richtung = ri;
                b.neig = Math.toRadians(neigung * r.nextDouble());
                // die Mitte des Blatts liegt ein Stück vor dem Ansatz
                b.x = sx + Math.sin(ri) * 0.42 * b.laenge * Math.cos(b.neig);
                b.z = sz + Math.cos(ri) * 0.42 * b.laenge * Math.cos(b.neig);
                b.y = h + 0.42 * b.laenge * Math.sin(b.neig) * (r.nextBoolean() ? 1 : -0.4);
                double hell = 0.8 + 0.4 * r.nextDouble();
                b.farbe = new float[]{(float) (blatt[0] * hell), (float) (blatt[1] * hell), (float) (blatt[2] * hell)};
                b.pflanze = p;
                blaetter.add(b);
            }
        }
        // Erdbeeren: drei Blättchen auf einem Stiel
        for (int p = 0; p < erdbeeren; p++) {
            double x = r.nextDouble(-hb, hb), z = nah + r.nextDouble() * (fern - nah), h = 0.05 + 0.1 * r.nextDouble(), ri0 = r.nextDouble(2 * Math.PI);
            stiele.add(new double[]{x, 0, z, x, h, z, 0.0015});
            double l = 0.03 + 0.03 * r.nextDouble(), hell = 0.8 + 0.4 * r.nextDouble();
            for (int k = 0; k < 3; k++) {
                Blatt b = new Blatt();
                b.richtung = ri0 + 2 * Math.PI * k / 3;
                b.neig = Math.toRadians(15 + 25 * r.nextDouble());
                b.laenge = l;
                b.breite = l * 0.7;
                b.x = x + Math.sin(b.richtung) * 0.5 * l;
                b.z = z + Math.cos(b.richtung) * 0.5 * l;
                b.y = h + 0.3 * l * Math.sin(b.neig);
                b.farbe = new float[]{(float) (erdbeere[0] * hell), (float) (erdbeere[1] * hell), (float) (erdbeere[2] * hell)};
                b.pflanze = pflanzen + p;
                blaetter.add(b);
            }
        }
        // Halme in Büscheln
        for (int i = 0; i < halme; ) {
            double xb = r.nextDouble(-hb, hb), zb = nah + r.nextDouble() * (fern - nah);
            int bueschel = 4 + r.nextInt(10);
            for (int j = 0; j < bueschel && i < halme; j++, i++) {
                Halm hm = new Halm();
                hm.x = xb + r.nextGaussian() * 0.03;
                hm.z = zb + r.nextGaussian() * 0.03;
                hm.hoehe = halmMin + (halmMax - halmMin) * r.nextDouble();
                hm.richtung = r.nextDouble(2 * Math.PI);
                hm.neig = 0.45 + 0.9 * r.nextDouble();
                hm.biegung = 0.1 + 0.25 * r.nextDouble();
                hm.breite = halmBreite * (0.6 + 0.8 * r.nextDouble());
                boolean duerr = r.nextDouble() < 0.18;
                double hell = 0.75 + 0.5 * r.nextDouble();
                float[] f = duerr ? halmDuerr : halmGruen;
                hm.farbe = new float[]{(float) (f[0] * hell), (float) (f[1] * hell), (float) (f[2] * hell)};
                halmListe.add(hm);
            }
        }
        // Streu: liegende Blättchen und Nadeln
        for (int i = 0; i < streu; i++) {
            Blatt b = new Blatt();
            b.x = r.nextDouble(-hb, hb);
            b.z = nah + r.nextDouble() * (fern - nah);
            b.y = 0.003;
            b.richtung = r.nextDouble(2 * Math.PI);
            b.neig = Math.toRadians(8 * r.nextDouble());
            b.laenge = streuLaenge * (0.5 + r.nextDouble());
            b.breite = b.laenge * (0.15 + 0.4 * r.nextDouble());
            double hell = 0.6 + 0.8 * r.nextDouble();
            b.farbe = new float[]{(float) (streuFarbe[0] * hell), (float) (streuFarbe[1] * hell), (float) (streuFarbe[2] * hell)};
            b.pflanze = -1;
            blaetter.add(b);
        }
        return this;
    }

    /**
     * Die Brücke ins Paket: das eigene Modell als {@link Quelle}. Ein Blatt zeigt mit seiner Längsachse in seine Richtung,
     * um seine Neigung aus der Waagerechten gehoben; ein Halm reicht vom Fuß zur Spitze, die um die Neigung zur Seite
     * steht; Stiele werden Glieder.
     */
    public Quelle alsQuelle() {
        Quelle.Sammlung q = new Quelle.Sammlung();
        q.bodenAlbedo = boden.clone();
        for (Blatt b : blaetter) {
            Quelle.Blatt o = q.blatt();
            double sr = Math.sin(b.richtung), cr = Math.cos(b.richtung), sn = Math.sin(b.neig), cn = Math.cos(b.neig);
            // Längsachse in Richtung, um die Neigung gehoben; Normale senkrecht dazu, nach oben
            o.richtung(-sr * sn, cn, -cr * sn, sr * cn, sn, cr * cn);
            o.x = (float) b.x; o.y = (float) b.y; o.z = (float) b.z;
            o.laenge = (float) b.laenge; o.breite = (float) b.breite;
            o.r = b.farbe[0]; o.g = b.farbe[1]; o.b = b.farbe[2];
            o.gruppe = b.pflanze;
            o.liegt = b.pflanze < 0;
        }
        for (Halm h : halmListe) {
            Quelle.Halm o = q.halm();
            double seit = h.hoehe * Math.sin(h.neig * Math.PI / 4), hoch = h.hoehe * Math.cos(h.neig * Math.PI / 4);
            o.fx = (float) h.x; o.fy = 0; o.fz = (float) h.z;
            o.sx = (float) (h.x + Math.sin(h.richtung) * seit); o.sy = (float) hoch; o.sz = (float) (h.z + Math.cos(h.richtung) * seit);
            o.breite = (float) h.breite;
            o.biegung = (float) h.biegung;
            o.r = h.farbe[0]; o.g = h.farbe[1]; o.b = h.farbe[2];
        }
        for (double[] s : stiele) {
            Quelle.Glied g = q.glied();
            g.x0 = (float) s[0]; g.y0 = (float) s[1]; g.z0 = (float) s[2];
            g.x1 = (float) s[3]; g.y1 = (float) s[4]; g.z1 = (float) s[5];
            g.r0 = (float) s[6]; g.r1 = (float) (s[6] * 0.7);
            g.r = stiel[0]; g.g = stiel[1]; g.b = stiel[2];
            g.stamm = false;
        }
        return q;
    }
}
