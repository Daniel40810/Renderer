package com.dan.core.bus;

import com.dan.core.bus.Anmeldung.Anschluss;
import com.dan.core.feld.Eintrag;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Umrechnung;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Der Kopplungsbus (Werkbuch HalligHooge Teil A, 5.5): Module sprechen nur über Felder aus dem Katalog miteinander.
 * <ul>
 * <li><b>Anmelden und verdrahten.</b> Jedes Modul meldet an, was es liest und schreibt. {@link #verdrahten} prüft Name,
 * Einheit, Ort und Gitter gegen den Katalog; fehlt ein Schreiber oder gibt es zwei, startet nichts
 * ({@link Verdrahtungsfehler} mit allen Mängeln).</li>
 * <li><b>Schreiben.</b> Der Schreiber füllt sein Schreibfeld ({@link #schreibfeld}) und legt es mit Zeitstempel ab
 * ({@link #lege}). Der Bus hält die drei jüngsten Stände ({@link #STAENDE}).</li>
 * <li><b>Lesen zu einer Zeit t.</b> Zustände werden zwischen den beiden Ständen um t linear interpoliert ({@link #lies}).
 * Flüsse sammelt der Schreiber über seine Schritte ({@link #sammle}); gelesen wird das Zeitmittel über das letzte
 * Kopplungsintervall, damit die Masse erhalten bleibt.</li>
 * <li><b>Anderes Gitter.</b> Liest ein Modul ein Zellfeld auf einem anderen Gitter, rechnet der Bus bilinear um
 * ({@link Umrechnung#zustand}). Mengen überträgt ein Modul selbst mit {@link Umrechnung#menge}.</li>
 * <li><b>Mehrere Gitter (seit P5c).</b> Ein Schreiber darf ein Feld auf mehreren Gittern ablegen (hydro rechnet G0–G4,
 * F27 A): je Gitter eine Leitung mit eigenen Ständen; „ein Schreiber“ gilt weiter je Feld. Ein Leser bekommt das
 * Gitter des Schreibers, das seinem gleicht; sonst, wenn der Schreiber nur eines hat, dieses; sonst das feinste, das
 * seines ganz deckt (umgerechnet).</li>
 * <li><b>Antrieb im Intervall (seit P5c).</b> Wer ein Feld mit {@link Anmeldung#liestImIntervall} anmeldet, liest es zu
 * jeder Zeit seines Intervalls; der Takt lässt den Schreiber dafür bis zu dessen Ende vorausrechnen
 * ({@link #antriebe}), und die Leitung hält so viele Stände, dass das Intervall darin liegt.</li>
 * </ul>
 * <b>Fäden:</b> Die Physik rechnet in ihrem Faden und legt ab, das Bild liest in seinem. Ein Leser sieht immer zwei
 * vollständige Stände; das Ablegen wartet, bis laufende Leser fertig sind.
 */
public final class Bus {
    /** Eine Leitung: ein Feld auf einem Gitter mit seinem Schreiber und den jüngsten Ständen (ältester zuerst). */
    private static final class Leitung {
        final Eintrag eintrag;
        final Modul schreiber;
        final Gitter gitter;
        final ReentrantReadWriteLock schloss = new ReentrantReadWriteLock();
        final Feld[] stand;
        int anzahl;
        Feld frei;
        double[] summe;
        double dauer;
        final Map<String, Umrechnung> umrechnung = new LinkedHashMap<>();

        Leitung(Eintrag eintrag, Modul schreiber, Feld muster, int tiefe) {
            this.eintrag = eintrag;
            this.schreiber = schreiber;
            this.gitter = muster.gitter;
            stand = new Feld[tiefe];
            for (int i = 0; i < tiefe; i++) stand[i] = muster.gleiches();
            frei = muster;
        }

        Feld juengster() { return stand[anzahl - 1]; }
    }

    /**
     * Zahl der Stände, die der Bus je Feld hält [G]. Gelesen wird zwischen zwei benachbarten; der dritte lässt die
     * Physik ein Kopplungsintervall vorausrechnen, während das Bild noch im vorigen liest ({@code takt.Takt}). Liest ein
     * Modul ein Feld im Intervall, dessen Schreiber kürzer koppelt, kommen so viele dazu, wie das Intervall des Lesers
     * Intervalle des Schreibers überdeckt.
     */
    public static final int STAENDE = 3;

    public final Katalog katalog;
    private final List<Anmeldung> anmeldungen = new ArrayList<>();
    /** Je Feld die Leitungen, eine je Gitter des Schreibers. */
    private final Map<String, List<Leitung>> leitungen = new LinkedHashMap<>();
    /** Je Leseanschluss die Leitung, aus der er liest. */
    private final Map<Anschluss, Leitung> quelle = new IdentityHashMap<>();
    private final List<String> hinweise = new ArrayList<>();
    private final AtomicLong ausserhalb = new AtomicLong();
    private boolean verdrahtet;

    public Bus(Katalog katalog) { this.katalog = katalog; }

    /** Meldet ein Modul an; die Reihenfolge der Anmeldung ist die Reihenfolge im Takt. */
    public Bus anmelden(Modul m) {
        if (verdrahtet) throw new IllegalStateException("Der Bus ist schon verdrahtet");
        for (Anmeldung a : anmeldungen) if (a.modul.name().equals(m.name())) throw new IllegalArgumentException("Modul doppelt angemeldet: " + m.name());
        Anmeldung a = new Anmeldung(m);
        m.anmelden(a);
        anmeldungen.add(a);
        return this;
    }

    /**
     * Prüft alle Anschlüsse und legt die Leitungen an. Bei Mängeln startet nichts. Gibt Hinweise zurück (etwa ein
     * Leser, den das Werkbuch bei diesem Feld nicht nennt).
     */
    public List<String> verdrahten() {
        List<String> maengel = new ArrayList<>();
        hinweise.clear();
        leitungen.clear();
        quelle.clear();
        Map<String, List<Anmeldung>> schreiber = new LinkedHashMap<>();
        for (Anmeldung a : anmeldungen) {
            String m = a.modul.name();
            if (!(a.kopplung > 0)) maengel.add(m + ": Kopplungsintervall fehlt");
            if (Double.isInfinite(a.kopplung) && !a.schreibt.isEmpty()) maengel.add(m + ": schreibt Felder, braucht also ein Kopplungsintervall");
            for (int k = 0; k < a.schreibt.size(); k++) {
                Anschluss s = a.schreibt.get(k);
                Eintrag e = pruefe(m, "schreibt", s, maengel);
                if (e == null) continue;
                if (!e.darfSchreiben(m)) maengel.add(m + " schreibt " + s.name() + ": Schreiber laut Katalog ist " + String.join(" oder ", e.schreiber()));
                if (e.ort() == Ort.TABELLE) maengel.add(m + " schreibt " + s.name() + ": Tabellen (Subgrid) laufen nicht über den Bus");
                if (s.gitter() == null && e.ort() != Ort.GLOBAL) maengel.add(m + " schreibt " + s.name() + ": Gitter fehlt");
                if (e.schichten() == 0 && s.schichten() < 1) maengel.add(m + " schreibt " + s.name() + ": Zahl der Schichten fehlt");
                if (e.schichten() > 0 && s.schichten() != 0 && s.schichten() != e.schichten())
                    maengel.add(m + " schreibt " + s.name() + ": " + s.schichten() + " Schichten, der Katalog legt " + e.schichten() + " fest");
                for (int q = 0; q < k; q++) {
                    Anschluss v = a.schreibt.get(q);
                    if (v.name().equals(s.name()) && (v.gitter() == null || v.gitter().gleich(s.gitter())))
                        maengel.add(m + " schreibt " + s.name() + " zweimal auf " + (s.gitter() == null ? "kein Gitter" : s.gitter().name));
                }
                List<Anmeldung> w = schreiber.computeIfAbsent(s.name(), x -> new ArrayList<>());
                if (!w.contains(a)) w.add(a);
            }
        }
        for (Map.Entry<String, List<Anmeldung>> s : schreiber.entrySet())
            if (s.getValue().size() > 1) {
                List<String> n = new ArrayList<>();
                for (Anmeldung a : s.getValue()) n.add(a.modul.name());
                maengel.add(s.getKey() + ": zwei Schreiber (" + String.join(", ", n) + ")");
            }
        // Leser: je Anschluss das Gitter des Schreibers, aus dem er liest
        Map<Anschluss, Anschluss> von = new IdentityHashMap<>();
        Map<Anschluss, Anmeldung> vonModul = new IdentityHashMap<>();
        for (Anmeldung a : anmeldungen) {
            String m = a.modul.name();
            for (int k = 0; k < a.liest.size(); k++) {
                Anschluss l = a.liest.get(k);
                Eintrag e = pruefe(m, "liest", l, maengel);
                if (e == null) continue;
                for (int q = 0; q < k; q++) {
                    Anschluss v = a.liest.get(q);
                    if (v.name().equals(l.name()) && (v.gitter() == null ? l.gitter() == null : v.gitter().gleich(l.gitter())))
                        maengel.add(m + " liest " + l.name() + " zweimal auf " + (l.gitter() == null ? "kein Gitter" : l.gitter().name));
                }
                List<Anmeldung> w = schreiber.get(l.name());
                if (w == null) { maengel.add(m + " liest " + l.name() + ": kein Schreiber angemeldet"); continue; }
                if (!e.alsLeserGenannt(m)) hinweise.add(m + " liest " + l.name() + ": im Werkbuch nicht als Leser genannt (" + String.join(", ", e.leser()) + ")");
                if (l.intervall() && w.get(0) == a) maengel.add(m + " liest " + l.name() + " im Intervall und schreibt es selbst");
                List<Anschluss> qs = new ArrayList<>();
                for (Anschluss s : w.get(0).schreibt) if (s.name().equals(l.name())) qs.add(s);
                Anschluss q = waehle(l, qs);
                von.put(l, q);
                vonModul.put(l, w.get(0));
                if (e.ort() == Ort.GLOBAL || q == null || q.gitter() == null) continue;
                if (l.gitter() == null) { maengel.add(m + " liest " + l.name() + ": Gitter fehlt"); continue; }
                if (l.gitter().gleich(q.gitter())) continue;
                if (qs.size() > 1 && !deckt(q.gitter(), l.gitter())) {
                    List<String> n = new ArrayList<>();
                    for (Anschluss s : qs) n.add(s.gitter().name);
                    maengel.add(m + " liest " + l.name() + " auf " + l.gitter().name + ": keines der Gitter des Schreibers (" + String.join(", ", n) + ") deckt es");
                    continue;
                }
                if (e.art() == Eintrag.Art.FLUSS || (e.ort() != Ort.ZELLE && e.ort() != Ort.SAEULE))
                    maengel.add(m + " liest " + l.name() + " auf " + l.gitter().name + ", geschrieben wird auf " + q.gitter().name
                            + ": Der Bus rechnet nur Zustände in Zellen um; Flüsse, Kanten und Ränder rechnet das Modul selbst um");
            }
        }
        // Antrieb im Intervall: keine Kreise
        String kreis = kreis();
        if (kreis != null) maengel.add("Antrieb im Kreis: " + kreis + " (jeder liest den nächsten im Intervall)");
        if (!maengel.isEmpty()) throw new Verdrahtungsfehler(maengel);
        // Tiefe je Leitung: drei Stände, mehr, wo ein Leser im Intervall länger koppelt als der Schreiber
        Map<Anschluss, Integer> tiefe = new IdentityHashMap<>();
        for (Anmeldung a : anmeldungen)
            for (Anschluss l : a.liest) {
                Anschluss q = von.get(l);
                if (!l.intervall() || q == null) continue;
                double kw = vonModul.get(l).kopplung;
                int t = STAENDE + Math.max(0, (int) Math.ceil(a.kopplung / kw - 1e-9) - 1);
                tiefe.merge(q, t, Math::max);
            }
        Map<Anschluss, Leitung> leitung = new IdentityHashMap<>();
        for (Anmeldung a : anmeldungen)
            for (Anschluss s : a.schreibt) {
                Eintrag e = katalog.verlange(s.name());
                Feld muster = new Feld(e, s.gitter(), s.zahl(), e.schichten() > 0 ? e.schichten() : s.schichten());
                Leitung l = new Leitung(e, a.modul, muster, tiefe.getOrDefault(s, STAENDE));
                if (e.art() == Eintrag.Art.FLUSS) l.summe = new double[muster.laenge];
                leitungen.computeIfAbsent(s.name(), x -> new ArrayList<>()).add(l);
                leitung.put(s, l);
            }
        for (Map.Entry<Anschluss, Anschluss> v : von.entrySet()) if (v.getValue() != null) quelle.put(v.getKey(), leitung.get(v.getValue()));
        verdrahtet = true;
        return Collections.unmodifiableList(new ArrayList<>(hinweise));
    }

    /**
     * Das Gitter des Schreibers für einen Leser: das gleiche; sonst, bei einem einzigen, dieses; sonst das feinste, das das
     * Gitter des Lesers ganz deckt; sonst das erste (dann meldet {@link #verdrahten} einen Mangel).
     */
    private static Anschluss waehle(Anschluss l, List<Anschluss> qs) {
        if (qs.isEmpty()) return null;
        if (qs.size() == 1 || l.gitter() == null) return qs.get(0);
        for (Anschluss s : qs) if (s.gitter() != null && s.gitter().gleich(l.gitter())) return s;
        Anschluss best = null;
        double zelle = Double.POSITIVE_INFINITY;
        for (Anschluss s : qs) {
            if (s.gitter() == null || !deckt(s.gitter(), l.gitter())) continue;
            double z = s.gitter().flaeche() / s.gitter().zellen();
            if (z < zelle) { zelle = z; best = s; }
        }
        return best != null ? best : qs.get(0);
    }

    /** Deckt das Gitter q die Mitten aller Zellen von r (geprüft am Rand von r, über Länge und Breite, wenn die Lage verschieden ist)? */
    static boolean deckt(Gitter q, Gitter r) {
        int n = 16;
        for (int k = 0; k <= n; k++) {
            double a = k / (double) n;
            double[][] p = {{r.x(0) + a * (r.x(r.nx - 1) - r.x(0)), r.y(0)}, {r.x(0) + a * (r.x(r.nx - 1) - r.x(0)), r.y(r.ny - 1)},
                    {r.x(0), r.y(0) + a * (r.y(r.ny - 1) - r.y(0))}, {r.x(r.nx - 1), r.y(0) + a * (r.y(r.ny - 1) - r.y(0))}};
            for (double[] x : p) {
                double[] b = q.von(r, x[0], x[1]);
                if (!q.enthaelt(b[0], b[1])) return false;
            }
        }
        return true;
    }

    /** Ein Kreis der Antriebe im Intervall als Text, sonst null. */
    private String kreis() {
        int n = anmeldungen.size();
        int[] farbe = new int[n];
        for (int i = 0; i < n; i++) {
            List<Integer> pfad = new ArrayList<>();
            String k = suche(i, farbe, pfad);
            if (k != null) return k;
        }
        return null;
    }

    private String suche(int i, int[] farbe, List<Integer> pfad) {
        if (farbe[i] == 2) return null;
        if (farbe[i] == 1) {
            List<String> n = new ArrayList<>();
            for (int k = pfad.indexOf(i); k < pfad.size(); k++) n.add(anmeldungen.get(pfad.get(k)).modul.name());
            n.add(anmeldungen.get(i).modul.name());
            return String.join(" → ", n);
        }
        farbe[i] = 1;
        pfad.add(i);
        for (int w : antriebIndizes(i)) {
            String k = suche(w, farbe, pfad);
            if (k != null) return k;
        }
        pfad.remove(pfad.size() - 1);
        farbe[i] = 2;
        return null;
    }

    /** Indizes der Module, deren Felder das Modul i im Intervall liest. */
    private List<Integer> antriebIndizes(int i) {
        List<Integer> r = new ArrayList<>();
        for (Anschluss l : anmeldungen.get(i).liest) {
            if (!l.intervall()) continue;
            for (int k = 0; k < anmeldungen.size(); k++)
                for (Anschluss s : anmeldungen.get(k).schreibt)
                    if (s.name().equals(l.name()) && !r.contains(k)) r.add(k);
        }
        return r;
    }

    private Eintrag pruefe(String modul, String was, Anschluss s, List<String> maengel) {
        Eintrag e = katalog.eintrag(s.name());
        if (e == null) { maengel.add(modul + " " + was + " " + s.name() + ": steht nicht im Katalog"); return null; }
        if (!e.einheit().equals(s.einheit())) maengel.add(modul + " " + was + " " + s.name() + " in [" + s.einheit() + "], der Katalog sagt [" + e.einheit() + "]");
        if (e.ort() != s.ort()) maengel.add(modul + " " + was + " " + s.name() + " am Ort " + s.ort() + ", der Katalog sagt " + e.ort());
        return e;
    }

    /** Die angemeldeten Module in der Reihenfolge der Anmeldung. */
    public List<Modul> module() {
        List<Modul> l = new ArrayList<>();
        for (Anmeldung a : anmeldungen) l.add(a.modul);
        return l;
    }

    /** Kopplungsintervall eines Moduls (s). */
    public double kopplung(Modul m) { return anmeldung(m).kopplung; }

    /** Die Module, deren Felder m im Intervall liest (seit P5c): Sie rechnen vor m bis zum Ende seines Intervalls. */
    public List<Modul> antriebe(Modul m) {
        List<Modul> r = new ArrayList<>();
        for (int k : antriebIndizes(anmeldungen.indexOf(anmeldung(m)))) r.add(anmeldungen.get(k).modul);
        return r;
    }

    /** Namen der Felder, die über den Bus laufen. */
    public List<String> felder() { return new ArrayList<>(leitungen.keySet()); }

    /** Die Gitter, auf denen ein Feld über den Bus läuft (null bei einem Feld ohne Gitter). */
    public List<Gitter> gitter(String name) {
        List<Leitung> ls = leitungen.get(name);
        if (ls == null) throw new IllegalArgumentException("Kein Feld am Bus: " + name);
        List<Gitter> g = new ArrayList<>();
        for (Leitung l : ls) g.add(l.gitter);
        return g;
    }

    /** Zahl der Stände, die die Leitung eines Felds hält (die erste, wenn es mehrere Gitter hat). */
    public int tiefe(String name) { return leitungen(name).get(0).stand.length; }

    // ------------------------------------------------------------------ Schreiben

    /** Das Feld, in das der Schreiber den nächsten Stand füllt. Kein anderer sieht es, bis es abgelegt ist. */
    public Feld schreibfeld(Modul m, String name) { return schreibleitung(m, name, null).frei; }

    /** Wie {@link #schreibfeld(Modul, String)} für ein Feld, das der Schreiber auf mehreren Gittern ablegt. */
    public Feld schreibfeld(Modul m, String name, Gitter gitter) { return schreibleitung(m, name, gitter).frei; }

    /**
     * Nur für Flüsse: nimmt das Schreibfeld als Fluss über einen Schritt der Dauer dt in das Zeitmittel des laufenden
     * Kopplungsintervalls auf.
     */
    public void sammle(Modul m, String name, double dt) { sammle(schreibleitung(m, name, null), name, dt); }

    /** Wie {@link #sammle(Modul, String, double)} auf einem der Gitter des Schreibers. */
    public void sammle(Modul m, String name, Gitter gitter, double dt) { sammle(schreibleitung(m, name, gitter), name, dt); }

    private static void sammle(Leitung l, String name, double dt) {
        if (l.summe == null) throw new IllegalStateException(name + " ist kein Fluss");
        for (int i = 0; i < l.summe.length; i++) l.summe[i] += l.frei.hol(i) * dt;
        l.dauer += dt;
    }

    /**
     * Legt den Stand zur Zeit t ab. Bei einem Zustand ist es das Schreibfeld; bei einem Fluss das Zeitmittel des
     * Gesammelten (ohne {@link #sammle} das Schreibfeld selbst). Danach gibt {@link #schreibfeld} ein anderes Feld.
     */
    public void lege(Modul m, String name, double t) { lege(schreibleitung(m, name, null), name, t); }

    /** Wie {@link #lege(Modul, String, double)} auf einem der Gitter des Schreibers. */
    public void lege(Modul m, String name, Gitter gitter, double t) { lege(schreibleitung(m, name, gitter), name, t); }

    private static void lege(Leitung l, String name, double t) {
        if (l.summe != null && l.dauer > 0) {
            for (int i = 0; i < l.summe.length; i++) {
                l.frei.setz(i, l.summe[i] / l.dauer);
                l.summe[i] = 0;
            }
            l.dauer = 0;
        }
        l.frei.zeit = t;
        l.frei.prozessorNeuer = true;
        l.schloss.writeLock().lock();
        try {
            if (l.anzahl > 0 && t < l.juengster().zeit) throw new IllegalStateException(name + ": Stand zur Zeit " + t + " nach dem Stand zur Zeit " + l.juengster().zeit);
            Feld f = l.frei;
            if (l.anzahl < l.stand.length) {
                l.frei = l.stand[l.anzahl];
                l.stand[l.anzahl++] = f;
            } else {
                l.frei = l.stand[0];
                System.arraycopy(l.stand, 1, l.stand, 0, l.anzahl - 1);
                l.stand[l.anzahl - 1] = f;
            }
        } finally {
            l.schloss.writeLock().unlock();
        }
    }

    // ------------------------------------------------------------------ Lesen

    /** Ein leeres Feld, wie das Modul es angemeldet hat: sein Gitter, Zahlentyp und Schichten des Schreibers. */
    public Feld lesefeld(Modul m, String name) { return lesefeld(m, anschluss(m, name, null, false)); }

    /** Wie {@link #lesefeld(Modul, String)} für ein Feld, das das Modul auf mehreren Gittern liest. */
    public Feld lesefeld(Modul m, String name, Gitter gitter) { return lesefeld(m, anschluss(m, name, gitter, true)); }

    private Feld lesefeld(Modul m, Anschluss a) {
        Leitung l = leseleitung(a);
        return new Feld(l.eintrag, a.gitter() == null ? l.frei.gitter : a.gitter(), l.frei.zahl, l.frei.schichten);
    }

    /**
     * Liest das Feld zur Zeit t nach {@code ziel}: Zustände linear zwischen den beiden Ständen um t, Flüsse als Mittel
     * des Kopplungsintervalls, in dem t liegt. Liegt t außerhalb der gehaltenen Stände, gilt der nähere
     * ({@link #ausserhalb} zählt mit). Liest das Modul das Feld auf mehreren Gittern, gilt das Gitter von {@code ziel}.
     * Gibt die Zeit zurück, für die der Wert gilt.
     */
    public double lies(Modul m, String name, double t, Feld ziel) {
        Leitung l = leseleitung(anschluss(m, name, ziel.gitter, false));
        l.schloss.readLock().lock();
        try {
            if (l.anzahl == 0) throw new IllegalStateException(name + ": noch kein Stand abgelegt (Modul.beginn)");
            int k = l.anzahl - 1;
            while (k > 1 && l.stand[k - 1].zeit >= t) k--;
            Feld b = l.stand[k], a = k > 0 ? l.stand[k - 1] : b;
            double w = b.zeit > a.zeit ? (t - a.zeit) / (b.zeit - a.zeit) : 1;
            double toleranz = 1e-9 * Math.max(1, Math.abs(b.zeit));
            if (t < l.stand[0].zeit - toleranz || t > b.zeit + toleranz) ausserhalb.incrementAndGet();
            w = Math.max(0, Math.min(1, w));
            if (l.summe != null) w = w > 0 ? 1 : 0;
            Feld dort = ziel;
            Umrechnung u = null;
            if (b.gitter != null && ziel.gitter != null && !b.gitter.gleich(ziel.gitter)) {
                String ziellage = ziel.gitter.toString();
                synchronized (l.umrechnung) {
                    u = l.umrechnung.get(ziellage);
                    if (u == null) {
                        u = Umrechnung.bauen(b.gitter, ziel.gitter);
                        l.umrechnung.put(ziellage, u);
                    }
                }
                dort = b.gleiches();
            } else if (ziel.laenge != b.laenge) throw new IllegalArgumentException(name + ": Ziel hat " + ziel.laenge + " Werte statt " + b.laenge);
            if (w == 1) dort.kopiere(b);
            else if (w == 0) dort.kopiere(a);
            else if (dort.d != null && a.d != null) for (int i = 0; i < dort.laenge; i++) dort.d[i] = a.d[i] + w * (b.d[i] - a.d[i]);
            else if (dort.f != null && a.f != null) {
                float wf = (float) w;
                for (int i = 0; i < dort.laenge; i++) dort.f[i] = a.f[i] + wf * (b.f[i] - a.f[i]);
            } else for (int i = 0; i < dort.laenge; i++) dort.setz(i, a.hol(i) + w * (b.hol(i) - a.hol(i)));
            double zeit = a.zeit + w * (b.zeit - a.zeit);
            if (u != null) u.zustand(dort, ziel);
            ziel.zeit = zeit;
            ziel.prozessorNeuer = true;
            return zeit;
        } finally {
            l.schloss.readLock().unlock();
        }
    }

    /** Zeiten der gehaltenen Stände eines Felds, ältester zuerst; leer, solange keiner liegt (die erste Leitung). */
    public double[] staende(String name) { return staende(leitungen(name).get(0)); }

    /** Wie {@link #staende(String)} auf einem der Gitter des Schreibers. */
    public double[] staende(String name, Gitter gitter) {
        for (Leitung l : leitungen(name)) if (l.gitter != null && l.gitter.gleich(gitter)) return staende(l);
        throw new IllegalArgumentException(name + " läuft nicht auf " + gitter.name + " über den Bus");
    }

    private static double[] staende(Leitung l) {
        l.schloss.readLock().lock();
        try {
            double[] t = new double[l.anzahl];
            for (int i = 0; i < t.length; i++) t[i] = l.stand[i].zeit;
            return t;
        } finally {
            l.schloss.readLock().unlock();
        }
    }

    /** Wie oft außerhalb der gehaltenen Stände gelesen wurde – im geregelten Betrieb null. */
    public long ausserhalb() { return ausserhalb.get(); }

    private List<Leitung> leitungen(String name) {
        List<Leitung> l = leitungen.get(name);
        if (l == null) throw new IllegalArgumentException("Kein Feld am Bus: " + name);
        return l;
    }

    private Anmeldung anmeldung(Modul m) {
        for (Anmeldung a : anmeldungen) if (a.modul == m) return a;
        throw new IllegalStateException("Modul nicht angemeldet: " + m.name());
    }

    /**
     * Der Leseanschluss des Moduls für das Feld: der einzige oder der auf dem Gitter (bei mehreren). {@code streng}: das
     * Gitter muss gleich sein, auch wenn es nur einen gibt.
     */
    private Anschluss anschluss(Modul m, String name, Gitter gitter, boolean streng) {
        if (!verdrahtet) throw new IllegalStateException("Der Bus ist nicht verdrahtet");
        List<Anschluss> l = new ArrayList<>();
        for (Anschluss a : anmeldung(m).liest) if (a.name().equals(name)) l.add(a);
        if (l.isEmpty()) throw new IllegalStateException(m.name() + " hat " + name + " nicht als Lesefeld angemeldet");
        if (l.size() == 1 && !streng) return l.get(0);
        for (Anschluss a : l) if (a.gitter() != null && gitter != null && a.gitter().gleich(gitter)) return a;
        if (gitter == null) throw new IllegalStateException(m.name() + " liest " + name + " auf " + l.size() + " Gittern: das Gitter angeben");
        throw new IllegalStateException(m.name() + " hat " + name + " nicht auf " + gitter.name + " als Lesefeld angemeldet");
    }

    private Leitung leseleitung(Anschluss a) {
        Leitung l = quelle.get(a);
        if (l == null) throw new IllegalArgumentException("Kein Feld am Bus: " + a.name());
        return l;
    }

    private Leitung schreibleitung(Modul m, String name, Gitter gitter) {
        if (!verdrahtet) throw new IllegalStateException("Der Bus ist nicht verdrahtet");
        List<Leitung> ls = leitungen(name);
        if (ls.get(0).schreiber != m) throw new IllegalStateException(m.name() + " ist nicht der Schreiber von " + name);
        if (gitter == null) {
            if (ls.size() > 1) throw new IllegalStateException(m.name() + " schreibt " + name + " auf " + ls.size() + " Gittern: das Gitter angeben");
            return ls.get(0);
        }
        for (Leitung l : ls) if (l.gitter != null && l.gitter.gleich(gitter)) return l;
        throw new IllegalStateException(m.name() + " schreibt " + name + " nicht auf " + gitter.name);
    }
}
