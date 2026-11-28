package com.dan.core.szene;

import com.dan.core.gitter.Gitter;

import java.util.List;

/**
 * Der Wasserspiegel im Bild, η_Bild, für ein Auge zu einer Zeit (P6d, F47 A; seit P7a, F52 A, in der gemeinsamen Szene,
 * vorher in {@code render.water.Wasserbild}). Das Echtzeitbild legt {@link #kn} und {@link #kf} in den Kopf des Shaders
 * ({@code wasser.glsl} rechnet dieselbe Regel in float), die Referenz fragt {@link #etaBild} auf dem Prozessor; beide
 * lesen also dieselbe Regel aus derselben Quelle.
 * <p>
 * <b>Regel.</b> Im feinsten Gitter, das den Ort hat: Ist die eigene Zelle zur Zeit t nicht nass, ist dort kein Wasser.
 * Sonst bilinear zwischen den Zellmitten, aber nur über nasse Nachbarn, die weder ein Bauwerk noch ein Überfall
 * (vollkommener Überfall nach der Wehrformel) abtrennt; über Eck genügt ein freier Weg über eine Seitenzelle. Ein
 * getrennter oder trockener Nachbar zählt mit dem Wert der eigenen Zelle. Nass zur Zeit t: (s &lt; 1 und nass in A)
 * oder (s &gt; 0 und nass in B); η der Zelle linear zwischen den Ständen A und B mit dem Gewicht s.
 * <p>
 * <b>Saum (seit C1b, Werkbuch HalligHooge 137, A52).</b> Eine trockene Zelle zeigt am Rand zu einer nassen Nachbarzelle
 * deren Spiegel, wenn die gemeinsame Kante weder ein Bauwerk trägt noch über dem Spiegel liegt (Schwelle aus der
 * Subgrid-Packung): je Ort der nächste Nachbar nach Ost oder West und nach Nord oder Süd (wie die bilinearen Nachbarn),
 * nur über eine Seite, von zweien der höhere Spiegel; ohne solchen Nachbarn kein Wasser. Das Gelände schneidet die
 * Wasserlinie dann auf 1 m wie in einer nassen Zelle; vorher brach sie an der Zellgrenze ab (Treppen und trockene
 * Flecken in Zellform, Lauf C1). {@link #SAUM} schaltet die Regel ({@code -Dwasser.saum=aus}: wie bis C1).
 * <p>
 * <b>Lage.</b> Der Ort (x Ost, z Süd) ist relativ zum Auge in float; je Gitter liegen die Zelle des Auges als ganze Zahl
 * und der Rest in float vor ({@link #kf}), so wie auf der Karte. Darum rechnen Karte, Echtzeitbild und Referenz bitgleich.
 */
public final class Spiegelbild {
    /** Größte Zahl der Gitter. */
    public static final int NETZE = 8;
    /** Seit C1b (A52): der Saum an (Vorgabe) oder aus ({@code -Dwasser.saum=aus}); gilt für jeden neuen Spiegel. */
    public static volatile boolean SAUM = !"aus".equalsIgnoreCase(System.getProperty("wasser.saum", "an"));
    /**
     * Je Gitter q acht ganze Zahlen ab 8q: Zelle des Auges i, j; nx, ny; Anfang im Puffer der Stände; Platz von Stand A;
     * gesetzt (0/1); Zahl der Bauwerkskanten.
     */
    public final int[] kn = new int[8 * NETZE];
    /** Je Gitter q acht float ab 8q: Rest des Auges in Zellen x, y; Zellgröße; Kehrwert; Gewicht s des Stands B; Saum (1/0), 0, 0. */
    public final float[] kf = new float[8 * NETZE];
    /** Die Gitter in der Reihenfolge des Kopfs (grob zuerst, wie sie das Bild bekommt). */
    public final Spiegel[] netze;
    /** Auge (UTM, m) und Zeit (s), für die dieser Spiegel gilt. */
    public final double ost, nord, zeit;

    /** Für das Auge (ost, nord) zur Zeit t über den Gittern {@code netze}; höchstens {@link #NETZE}. */
    public Spiegelbild(List<Spiegel> netze, double ost, double nord, double t) {
        if (netze.size() > NETZE) throw new IllegalArgumentException("Wasser: höchstens " + NETZE + " Gitter, nicht " + netze.size());
        this.netze = netze.toArray(new Spiegel[0]);
        this.ost = ost;
        this.nord = nord;
        this.zeit = t;
        long basis = 0;
        for (int q = 0; q < this.netze.length; q++) {
            Spiegel s = this.netze[q];
            Gitter gi = s.gitter;
            double ux = (ost - gi.x0) / gi.dx, uy = (nord - gi.y0) / gi.dy;
            long ia = (long) Math.floor(ux), ja = (long) Math.floor(uy);
            synchronized (s) {
                kn[8 * q] = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, ia));
                kn[8 * q + 1] = (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, ja));
                kn[8 * q + 2] = gi.nx;
                kn[8 * q + 3] = gi.ny;
                kn[8 * q + 4] = (int) basis;
                kn[8 * q + 5] = s.platzB >= 0 ? s.platzA() : 0;
                kn[8 * q + 6] = s.gesetzt() ? 1 : 0;
                kn[8 * q + 7] = s.bauwerke;
                kf[8 * q] = (float) (ux - ia);
                kf[8 * q + 1] = (float) (uy - ja);
                kf[8 * q + 2] = (float) gi.dx;
                kf[8 * q + 3] = (float) (1 / gi.dx);
                kf[8 * q + 4] = s.gewicht(t);
                kf[8 * q + 5] = SAUM ? 1f : 0f;
            }
            basis += s.zellen;
        }
    }

    /** Anfang der Suche im Shader: η der nächsten nassen Zelle um das Auge im ersten Gitter, das es hat; sonst 0. */
    public float startPegel() {
        for (int q = 0; q < netze.length; q++) {
            Spiegel s = netze[q];
            if (kn[8 * q + 6] == 0) continue;
            int ia = kn[8 * q], ja = kn[8 * q + 1], nx = kn[8 * q + 2], ny = kn[8 * q + 3];
            if (ia < 0 || ja < 0 || ia >= nx || ja >= ny) continue;
            for (int r = 0; r <= 64; r++)
                for (int j = ja - r; j <= ja + r; j++)
                    for (int i = ia - r; i <= ia + r; i++) {
                        if (Math.max(Math.abs(i - ia), Math.abs(j - ja)) != r || i < 0 || j < 0 || i >= nx || j >= ny) continue;
                        if (nassJetzt(q, s.art[i + nx * j])) return etaZelle(q, i, j);
                    }
        }
        return 0;
    }

    /**
     * η_Bild (m NHN) am Ort (x Ost, z Süd) relativ zum Auge, in float wie {@code wasser.glsl}; {@code Float.NaN}, wo kein
     * Wasser ist. {@code gitter[0]}: Nummer des genutzten Gitters (−1 keins), darf null sein.
     */
    public float etaBild(float px, float pz, int[] gitter) {
        if (gitter != null) gitter[0] = -1;
        for (int q = 0; q < netze.length; q++) {
            if (kn[8 * q + 6] == 0) continue;
            float inv = kf[8 * q + 3];
            float ux = px * inv + kf[8 * q], uy = -pz * inv + kf[8 * q + 1];
            float fx0 = (float) Math.floor(ux), fy0 = (float) Math.floor(uy);
            int ci = kn[8 * q] + (int) fx0, cj = kn[8 * q + 1] + (int) fy0;
            if (ci < 0 || cj < 0 || ci >= kn[8 * q + 2] || cj >= kn[8 * q + 3]) continue;
            if (gitter != null) gitter[0] = q;
            float bx = ux - 0.5f, by = uy - 0.5f;
            float gx = (float) Math.floor(bx), gy = (float) Math.floor(by);
            int i0 = kn[8 * q] + (int) gx, j0 = kn[8 * q + 1] + (int) gy;
            if (!nassJetzt(q, netze[q].art[ci + kn[8 * q + 2] * cj])) return kf[8 * q + 5] > 0.5f ? saum(q, ci, cj, i0 == ci ? ci + 1 : i0, j0 == cj ? cj + 1 : j0) : Float.NaN;
            float eo = etaZelle(q, ci, cj);
            float fx = bx - gx, fy = by - gy;
            float v00 = nachbar(q, ci, cj, i0, j0, eo), v10 = nachbar(q, ci, cj, i0 + 1, j0, eo);
            float v01 = nachbar(q, ci, cj, i0, j0 + 1, eo), v11 = nachbar(q, ci, cj, i0 + 1, j0 + 1, eo);
            float e0 = v00 + (v10 - v00) * fx, e1 = v01 + (v11 - v01) * fx;
            return e0 + (e1 - e0) * fy;
        }
        return Float.NaN;
    }

    /**
     * Seit S3c-5 (B47, Seegang Werkbuch 19/20): η eines Scheitels des Netzes am Ort (x Ost, z Süd) relativ zum Auge, wie
     * {@code netz.comp} ({@code etaOder}): η_Bild, wo Wasser ist; sonst der Spiegel des nächsten nassen Nachbarn im Abstand
     * s = max(Zelle, dL) – erst die vier Seitennachbarn, dann die vier über Eck, von mehreren der höchste; ohne nassen
     * Nachbarn {@code pegel} (der Pegel des Kopfs). In float wie auf der Karte.
     */
    public float etaScheitel(float px, float pz, float dL, float pegel) {
        int[] g = {-1};
        float e = etaBild(px, pz, g);
        if (!Float.isNaN(e)) return e;
        float s = g[0] >= 0 ? Math.max(dL, kf[8 * g[0] + 2]) : dL;
        float m = max4(etaBild(px + s, pz, null), etaBild(px - s, pz, null), etaBild(px, pz + s, null), etaBild(px, pz - s, null));
        if (Float.isNaN(m)) m = max4(etaBild(px + s, pz + s, null), etaBild(px + s, pz + -s, null), etaBild(px + -s, pz + s, null), etaBild(px - s, pz - s, null));
        return Float.isNaN(m) ? pegel : m;
    }

    /** Größter der vier Werte, NaN (kein Wasser) zählt nicht; NaN, wenn alle NaN sind. */
    private static float max4(float a, float b, float c, float d) {
        float m = Float.NaN;
        for (float v : new float[] {a, b, c, d}) if (!Float.isNaN(v) && (Float.isNaN(m) || v > m)) m = v;
        return m;
    }

    /** η_Bild am Ort (Ost, Nord) in UTM: der Ort wird relativ zum Auge in float genommen wie auf der Karte. */
    public float etaBildUtm(double ost, double nord, int[] gitter) {
        return etaBild((float) (ost - this.ost), (float) (this.nord - nord), gitter);
    }

    /**
     * Seit C1b (A52): η im Saum der trockenen Zelle (ci, cj) aus den Seitennachbarn (ni, cj) und (ci, nj); NaN ohne
     * Nachbarn, der nass ist und dessen Spiegel über einer Kante ohne Bauwerk über der Schwelle steht. In float wie
     * {@code wasser.glsl} (nur Vergleiche, kein Rechnen).
     */
    private float saum(int q, int ci, int cj, int ni, int nj) {
        float a = saumNachbar(q, ci, cj, ni, cj), b = saumNachbar(q, ci, cj, ci, nj);
        if (Float.isNaN(a)) return b;
        if (Float.isNaN(b)) return a;
        return Math.max(a, b);
    }

    private float saumNachbar(int q, int ci, int cj, int ni, int nj) {
        int nx = kn[8 * q + 2], ny = kn[8 * q + 3];
        if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) return Float.NaN;
        Spiegel sp = netze[q];
        int[] art = sp.art;
        if (!nassJetzt(q, art[ni + nx * nj])) return Float.NaN;
        int c;
        float s;
        if (ni == ci + 1) { c = ni + nx * cj; if ((art[c] & Spiegel.WEST) != 0) return Float.NaN; s = sp.schwelleW[c]; }
        else if (ni == ci - 1) { c = ci + nx * cj; if ((art[c] & Spiegel.WEST) != 0) return Float.NaN; s = sp.schwelleW[c]; }
        else if (nj == cj + 1) { c = ci + nx * nj; if ((art[c] & Spiegel.SUED) != 0) return Float.NaN; s = sp.schwelleS[c]; }
        else { c = ci + nx * cj; if ((art[c] & Spiegel.SUED) != 0) return Float.NaN; s = sp.schwelleS[c]; }
        float e = etaZelle(q, ni, nj);
        return e > s ? e : Float.NaN;
    }

    private boolean nassJetzt(int q, int a) {
        float s = kf[8 * q + 4];
        int pa = kn[8 * q + 5], pb = 1 - pa;
        return s < 1 && (a & (1 << pa)) != 0 || s > 0 && (a & (1 << pb)) != 0;
    }

    private float etaZelle(int q, int i, int j) {
        Spiegel s = netze[q];
        int c = i + kn[8 * q + 2] * j, pa = kn[8 * q + 5];
        float a = s.eta[pa][c], e = s.eta[1 - pa][c];
        return a + kf[8 * q + 4] * (e - a);
    }

    private boolean offen(int q, int i, int j, int i2, int j2) {
        Spiegel sp = netze[q];
        int[] art = sp.art;
        int nx = kn[8 * q + 2];
        int c;
        float s;
        if (i2 == i + 1) { c = i2 + nx * j; if ((art[c] & Spiegel.WEST) != 0) return false; s = sp.schwelleW[c]; }
        else if (i2 == i - 1) { c = i + nx * j; if ((art[c] & Spiegel.WEST) != 0) return false; s = sp.schwelleW[c]; }
        else if (j2 == j + 1) { c = i + nx * j2; if ((art[c] & Spiegel.SUED) != 0) return false; s = sp.schwelleS[c]; }
        else { c = i + nx * j; if ((art[c] & Spiegel.SUED) != 0) return false; s = sp.schwelleS[c]; }
        return !ueberfall(q, i, j, i2, j2, s);
    }

    /**
     * Fällt das Wasser zur Zeit t über die Kante mit der Schwelle s zwischen (i, j) und (i2, j2)? (F47 A, vollkommener
     * Überfall nach der Wehrformel: beide Zellen nass, und das Unterwasser steht weniger als 2/3 so hoch über der Schwelle
     * wie das Oberwasser; darunter liegendes Unterwasser eingeschlossen.) In float wie {@code wasser.glsl}.
     */
    private boolean ueberfall(int q, int i, int j, int i2, int j2, float s) {
        int[] art = netze[q].art;
        int nx = kn[8 * q + 2];
        if (!nassJetzt(q, art[i + nx * j]) || !nassJetzt(q, art[i2 + nx * j2])) return false;
        float a = etaZelle(q, i, j), b = etaZelle(q, i2, j2);
        float unten = Math.min(a, b) - s, oben = Math.max(a, b) - s;
        return 3.0f * unten < 2.0f * oben;
    }

    private float nachbar(int q, int ci, int cj, int ni, int nj, float eo) {
        int nx = kn[8 * q + 2], ny = kn[8 * q + 3];
        if (ni < 0 || nj < 0 || ni >= nx || nj >= ny || ni == ci && nj == cj) return eo;
        boolean frei = ni != ci && nj != cj
                ? offen(q, ci, cj, ni, cj) && offen(q, ni, cj, ni, nj) || offen(q, ci, cj, ci, nj) && offen(q, ci, nj, ni, nj)
                : offen(q, ci, cj, ni, nj);
        if (!frei || !nassJetzt(q, netze[q].art[ni + nx * nj])) return eo;
        return etaZelle(q, ni, nj);
    }
}
