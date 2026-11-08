package com.dan.gertsch;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Szene von außen: Blätter (einzeln oder als Büschel), Halme sowie Stämme und Äste, so wie eine App sie aus ihren eigenen Paketen
 * hereinreicht, etwa Bäume aus {@code forest} und Gras aus {@code reed} oder {@code ground}. Das Paket kennt diese Pakete
 * nicht (Paketvertrag); die App schreibt die Brücke, die ihre Daten in diese drei kleinen Klassen füllt.
 * {@link Dickicht#aus(Quelle)} macht daraus die Szene, die {@link Malweise}, {@link Licht}, {@code gl/GertschGl} und der
 * Holzschnitt zeichnen.
 * <p>
 * <b>Welt</b> wie im {@link Dickicht}: Meter, y nach oben, der Boden ist die Ebene y = 0, die Kamera steht nahe dem
 * Ursprung und blickt etwa nach +z (Gier und Neigung frei). Eine App verschiebt und dreht ihre Szene dorthin. Albedo
 * linear (0..1).
 * <p>
 * Was das Paket selbst ableitet: Zufall und Phase des Flatterns je Blatt (aus der Reihenfolge, reproduzierbar), die
 * Zweige, zu denen die Blätter für Größenregel, Wind und Grafikkarte zusammengefasst werden (nach {@link Blatt#gruppe}),
 * und die Bögen der Halme.
 */
public interface Quelle {

    /** Ein Blatt (oder Nadelbüschel, Blättchen): ein flacher Fleck in seiner Ebene. */
    final class Blatt {
        /** Mitte (m). */
        public float x, y, z;
        /** Normale der Blattfläche (Einheitsvektor). */
        public float nx, ny, nz;
        /** Richtung der Längsachse, in der Blattfläche (Einheitsvektor, senkrecht zur Normale). */
        public float ax, ay, az;
        /** Länge und Breite (m). */
        public float laenge, breite;
        /** Albedo (linear). */
        public float r, g, b;
        /**
         * Gruppe, mit der das Blatt im Wind schwingt (etwa der Ast oder die Pflanze). Aufeinanderfolgende Blätter mit
         * gleicher Gruppe kommen in denselben Zweig.
         */
        public int gruppe;
        /** Liegt am Boden (Streu): bewegt sich im Wind nicht. */
        public boolean liegt;

        /** Richtungen aus Normale und einer ungefähren Längsachse (wird in die Blattfläche gelegt und normiert). */
        public Blatt richtung(double nx, double ny, double nz, double ax, double ay, double az) {
            double ln = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= ln; ny /= ln; nz /= ln;
            double d = ax * nx + ay * ny + az * nz;
            ax -= d * nx; ay -= d * ny; az -= d * nz;
            double la = Math.sqrt(ax * ax + ay * ay + az * az);
            if (la < 1e-6) { ax = ny; ay = -nx; az = 0; la = Math.sqrt(ax * ax + ay * ay); if (la < 1e-6) { ax = 1; ay = 0; az = 0; la = 1; } }
            this.nx = (float) nx; this.ny = (float) ny; this.nz = (float) nz;
            this.ax = (float) (ax / la); this.ay = (float) (ay / la); this.az = (float) (az / la);
            return this;
        }
    }

    /**
     * Ein Büschel Blätter, wie {@code forest} eine Blattstelle als Fächer zeichnet: Mitte, Radius, Zahl der Blätter,
     * ihre Länge, Breite durch Länge, Streuung ihrer Neigung um die Waagerechte (Grad), Albedo. Die Blätter darin legt
     * das Paket aus dem Zufall an (in einer flachen Kugel, wie die Zweige des {@link Dickicht}); so kosten auch Bäume mit
     * Hunderttausenden Blättern nur ein paar Werte je Büschel. Mehr als 255 Blätter werden auf mehrere Zweige verteilt.
     */
    final class Bueschel {
        public float x, y, z, radius;
        public int blaetter;
        public float laenge, schmal = 0.6f, neigung = 20, r, g, b;
        /** Liegt am Boden (Streu): bewegt sich im Wind nicht. */
        public boolean liegt;
    }

    /**
     * Ein Halm: vom Fuß zur Spitze, Breite am Fuß (zur Spitze schmaler), Biegung: wie weit die Mitte von der Geraden
     * zwischen Fuß und Spitze abweicht, als Anteil der Länge; positiv nach oben gewölbt (ein überhängender Halm), negativ
     * nach unten.
     */
    final class Halm {
        public float fx, fy, fz, sx, sy, sz, breite, biegung, r, g, b;
    }

    /** Ein Stück Stamm oder Ast: Zylinderstumpf von (x0, y0, z0) mit Radius r0 nach (x1, y1, z1) mit r1; Albedo. */
    final class Glied {
        public float x0, y0, z0, r0, x1, y1, z1, r1, r, g, b;
        /** Stamm (sonst Ast): Stämme bekommen in der Malweise Streifen mit Lichtflecken. */
        public boolean stamm;
    }

    int blaetter();

    /** Füllt Blatt i (0 ≤ i < {@link #blaetter()}) in {@code o}. */
    void blatt(int i, Blatt o);

    /** Zahl der Büschel (Büschel heißt auch in der Mehrzahl so). */
    default int bueschelZahl() { return 0; }

    default void bueschel(int i, Bueschel o) { }

    default int halme() { return 0; }

    default void halm(int i, Halm o) { }

    default int glieder() { return 0; }

    default void glied(int i, Glied o) { }

    /** Albedo des Bodens (linear); null: wie im Dickicht. */
    default float[] boden() { return null; }

    /**
     * Eine Quelle zum Füllen: Listen von Blättern, Halmen und Gliedern. So kann eine App ihre Szene einfach anhängen,
     * statt die Schnittstelle selbst zu schreiben.
     */
    final class Sammlung implements Quelle {
        public final List<Blatt> blattListe = new ArrayList<>();
        public final List<Bueschel> bueschelListe = new ArrayList<>();
        public final List<Halm> halmListe = new ArrayList<>();
        public final List<Glied> gliedListe = new ArrayList<>();
        public float[] bodenAlbedo;

        @Override public int blaetter() { return blattListe.size(); }

        @Override public void blatt(int i, Blatt o) {
            Blatt b = blattListe.get(i);
            o.x = b.x; o.y = b.y; o.z = b.z; o.nx = b.nx; o.ny = b.ny; o.nz = b.nz; o.ax = b.ax; o.ay = b.ay; o.az = b.az;
            o.laenge = b.laenge; o.breite = b.breite; o.r = b.r; o.g = b.g; o.b = b.b; o.gruppe = b.gruppe; o.liegt = b.liegt;
        }

        @Override public int bueschelZahl() { return bueschelListe.size(); }

        @Override public void bueschel(int i, Bueschel o) {
            Bueschel b = bueschelListe.get(i);
            o.x = b.x; o.y = b.y; o.z = b.z; o.radius = b.radius; o.blaetter = b.blaetter; o.laenge = b.laenge; o.schmal = b.schmal;
            o.neigung = b.neigung; o.r = b.r; o.g = b.g; o.b = b.b; o.liegt = b.liegt;
        }

        @Override public int halme() { return halmListe.size(); }

        @Override public void halm(int i, Halm o) {
            Halm h = halmListe.get(i);
            o.fx = h.fx; o.fy = h.fy; o.fz = h.fz; o.sx = h.sx; o.sy = h.sy; o.sz = h.sz; o.breite = h.breite; o.biegung = h.biegung;
            o.r = h.r; o.g = h.g; o.b = h.b;
        }

        @Override public int glieder() { return gliedListe.size(); }

        @Override public void glied(int i, Glied o) {
            Glied g = gliedListe.get(i);
            o.x0 = g.x0; o.y0 = g.y0; o.z0 = g.z0; o.r0 = g.r0; o.x1 = g.x1; o.y1 = g.y1; o.z1 = g.z1; o.r1 = g.r1;
            o.r = g.r; o.g = g.g; o.b = g.b; o.stamm = g.stamm;
        }

        @Override public float[] boden() { return bodenAlbedo; }

        /** Hängt ein Blatt an und gibt es zum Füllen zurück. */
        public Blatt blatt() { Blatt b = new Blatt(); blattListe.add(b); return b; }

        public Bueschel bueschel() { Bueschel b = new Bueschel(); bueschelListe.add(b); return b; }

        public Halm halm() { Halm h = new Halm(); halmListe.add(h); return h; }

        public Glied glied() { Glied g = new Glied(); gliedListe.add(g); return g; }
    }
}
