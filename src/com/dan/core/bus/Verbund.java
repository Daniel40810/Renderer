package com.dan.core.bus;

import com.dan.core.feld.Eintrag;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.gitter.Gitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Bildet ein Feld aus den Beiträgen mehrerer Module (Werkbuch HalligHooge Teil A, 13.1, F18 A). Der Schichtenvertrag
 * erlaubt je Feld einen Schreiber. Wo zwei Module an derselben Größe arbeiten – der Boden ändert sich durch Wasser
 * ({@code sediment}) und durch Wind ({@code aeolian}) –, legt jedes seinen Teil unter eigenem Namen ab
 * ({@code bed.dz.sediment}, {@code bed.dz.aeolian}), und dieses Modul des Kerns schreibt das Feld {@code bed.dz}:
 * nach der Regel des Katalogs die Summe oder das Maximum.
 * <p>
 * Der Verbund ist ein Modul wie jedes andere: Er liest die Beiträge zur Startzeit seines Intervalls und legt das
 * Ergebnis an dessen Ende ab. Das Feld hinkt den Beiträgen also um ein Kopplungsintervall nach; bei Größen, die sich
 * langsam ändern (Boden, Rauheit), ist das ohne Belang. Angemeldet werden nur die Beiträge, deren Module im Lauf
 * sind; einer genügt.
 */
public final class Verbund implements Modul {
    private final Eintrag eintrag;
    private final List<Eintrag> beitraege = new ArrayList<>();
    private final Gitter gitter;
    private final Feld.Zahl zahl;
    private final double kopplung;
    private final List<Feld> gelesen = new ArrayList<>();

    /**
     * @param feld     Name des Verbunds im Katalog, etwa {@code bed.dz}
     * @param kopplung Kopplungsintervall in Sekunden Modellzeit
     * @param module   die Module, deren Beiträge im Lauf sind, etwa {@code "sediment", "aeolian"}
     */
    public Verbund(Katalog katalog, String feld, Gitter gitter, Feld.Zahl zahl, double kopplung, String... module) {
        eintrag = katalog.verlange(feld);
        if (eintrag.verbund() == Eintrag.Verbund.KEINER) throw new IllegalArgumentException(feld + " ist im Katalog kein Verbund");
        if (module.length == 0) throw new IllegalArgumentException(feld + ": kein Beitrag");
        for (String m : module) beitraege.add(katalog.verlange(feld + "." + m));
        this.gitter = gitter;
        this.zahl = zahl;
        this.kopplung = kopplung;
    }

    @Override public String name() { return "core.verbund." + eintrag.name(); }

    @Override public void anmelden(Anmeldung a) {
        a.kopplung(kopplung).schreibt(eintrag.name(), eintrag.einheit(), eintrag.ort(), gitter, zahl);
        for (Eintrag b : beitraege) a.liest(b.name(), b.einheit(), b.ort(), gitter);
    }

    @Override public void beginn(double t0, Bus bus) {
        gelesen.clear();
        for (Eintrag b : beitraege) gelesen.add(bus.lesefeld(this, b.name()));
        bilde(t0, t0, bus);
    }

    @Override public void schritt(double t0, double t1, Bus bus) { bilde(t0, t1, bus); }

    private void bilde(double lesezeit, double t, Bus bus) {
        for (int k = 0; k < beitraege.size(); k++) bus.lies(this, beitraege.get(k).name(), lesezeit, gelesen.get(k));
        Feld ziel = bus.schreibfeld(this, eintrag.name());
        boolean summe = eintrag.verbund() == Eintrag.Verbund.SUMME;
        for (int i = 0; i < ziel.laenge; i++) {
            double w = gelesen.get(0).hol(i);
            for (int k = 1; k < gelesen.size(); k++) w = summe ? w + gelesen.get(k).hol(i) : Math.max(w, gelesen.get(k).hol(i));
            ziel.setz(i, w);
        }
        bus.lege(this, eintrag.name(), t);
    }
}
