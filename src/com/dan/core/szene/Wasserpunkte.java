package com.dan.core.szene;

/**
 * Das Wasser unter den schwimmenden Körpern (Seegang Werkbuch, S7b-1, Datei 88): Austausch zwischen dem Lauf, der die
 * Körper rechnet, und der Karte, die die Fläche rechnet. Der Lauf legt je Bild eine {@link Anfrage} ab (Orte, an denen er
 * das Wasser braucht: je Körper eine Reihe von Säulen seiner Proben); die Karte nimmt die neueste, rechnet sie mit dem
 * Bild und gibt einige Bilder später einen {@link Stand} zurück. Beide Seiten tauschen nur unveränderliche Objekte aus,
 * darum darf jeder Faden lesen.
 * <p>
 * Je Punkt stehen im Stand {@link #KOPF} Werte und je Band {@link #JE_BAND}:
 * <ul>
 * <li>Kopf: η der Fläche über dem festen Ort (m, wie das Bild, mit dem Kielwasser; seit S7d2 ohne das eigene des Körpers), Re Z der linearen Wellen am Ort,
 * letzter Schritt der Suche (m), Schritte; Tiefe h (m), k_p (1/m), m0 (m²), 0; seit S7d Höhe des Kielwassers am Ort (m) und
 * seine mittlere Wellenzahl (1/m), seit S7b-4 die Verschiebung ξ (m, x und y) des Punkts der Fläche über dem Ort.</li>
 * <li>je Band b: Σ |Z|, Σ |Z| k, C0 = Σ Z (Re, Im), K1 = Σ k Z, G_x = Σ k_x Z, G_y = Σ k_y Z, U_x = Σ ω coth(k h) k_x/k Z,
 * U_y, W = Σ ω Z (je Re, Im). Z = a e^{iφ} je Welle, mit ∂Z/∂t = −iω Z und ∂Z/∂x = i k_x Z.</li>
 * </ul>
 * Die Karte ({@code render.seegang.Koerpersuche}) und die Referenz ({@link Oberflaeche#baender}) schreiben dasselbe.
 */
public final class Wasserpunkte {
    public static final String VERSION = "0.3";
    /** Werte je Punkt vor den Bändern (seit S7b-4/S7d 12: dazu Kielwasser und ξ) und je Band. */
    public static final int KOPF = 12, JE_BAND = 16;

    /** Orte, an denen der Lauf das Wasser braucht (UTM, m). Nach dem Ablegen nicht mehr ändern. */
    public static final class Anfrage {
        public final int zahl;
        public final double[] ost, nord;
        /** Je Körper (Nummer des Lauf) der erste Punkt und die Zahl; −1: keiner. */
        public final int[] anfang, anzahl;
        /** Laufende Nummer (vom Lauf vergeben). */
        public final long nr;
        /**
         * Seit S7d2 (Datei 90): je Körper seine Quelle im {@link Kielwasser} (null: keine). Die Karte lässt an seinen Punkten
         * sein eigenes Kielwasser weg [G]: Ein Schiff spürt sein eigenes Wellenbild nicht als Welle von außen.
         */
        public final Kielwasser.Quelle[] quelle;

        public Anfrage(long nr, double[] ost, double[] nord, int zahl, int[] anfang, int[] anzahl) {
            this(nr, ost, nord, zahl, anfang, anzahl, null);
        }

        public Anfrage(long nr, double[] ost, double[] nord, int zahl, int[] anfang, int[] anzahl, Kielwasser.Quelle[] quelle) {
            this.nr = nr;
            this.ost = ost;
            this.nord = nord;
            this.zahl = zahl;
            this.anfang = anfang;
            this.anzahl = anzahl;
            this.quelle = quelle;
        }

        /** Die Quelle des Körpers, zu dem Punkt i gehört (null: keine). */
        public Kielwasser.Quelle quelleVon(int i) {
            if (quelle == null) return null;
            for (int k = 0; k < anfang.length && k < quelle.length; k++)
                if (anfang[k] >= 0 && i >= anfang[k] && i < anfang[k] + anzahl[k]) return quelle[k];
            return null;
        }
    }

    /** Das Wasser an den Punkten einer Anfrage zur Zeit t der Karte. */
    public static final class Stand {
        public final Anfrage anfrage;
        /** Zeit der Karte (s, Modellzeit wie das Bild). */
        public final double t;
        /** Bänder und ω je Band (rad/s). */
        public final int nb;
        public final double[] omega;
        /** Je Punkt {@link #je} Werte. */
        public final float[] werte;
        public final int je;

        public Stand(Anfrage anfrage, double t, double[] omega, float[] werte) {
            this.anfrage = anfrage;
            this.t = t;
            this.omega = omega;
            this.nb = omega.length;
            this.werte = werte;
            this.je = KOPF + JE_BAND * nb;
        }
    }

    private volatile Anfrage anfrage;
    private volatile Stand stand;
    /** Zuletzt gelieferte Stände (Zähler, zur Auskunft). */
    public volatile long staende;

    /** Der Lauf legt eine neue Anfrage ab (null: keine Körper). */
    public void anfrage(Anfrage a) { anfrage = a; }

    /** Die neueste Anfrage (für die Karte); null: keine. */
    public Anfrage anfrage() { return anfrage; }

    /** Die Karte gibt einen Stand ab. */
    public void stand(Stand s) {
        stand = s;
        staende++;
    }

    /** Der neueste Stand (für den Lauf); null: noch keiner. */
    public Stand stand() { return stand; }

    /** Vergisst Anfrage und Stand (neues Szenario, Sprung der Zeit). */
    public void leeren() {
        anfrage = null;
        stand = null;
    }
}
