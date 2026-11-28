package com.dan.lauf;

import com.dan.physics.auftrieb.Festmacher;
import com.dan.physics.auftrieb.Koerper;

/**
 * Der Lotse einer Fähre nach Fahrplan (Seegang Werkbuch, S7d, Datei 89, SF115 A): Er kennt ihren Weg ab dem Anleger
 * ({@link Schwimmerangabe#route}, bis {@link #WEG_MAX} m) und den Fahrplan (Ankunft, Abfahrt, täglich, UTC) und sagt je
 * Zeit, wo sie sein soll; in der Zeit der Szene stellt er Ruder und Hebel ({@code Koerper.ruder}, {@code hebel}).
 * <ul>
 * <li><b>Kommt:</b> von {@code Ankunft − Weg/FAHRT_PLAN − ANLEGEN_S} (seit S7d5) bis zum Anleger, mit {@link #FAHRT} [G] auf ein Ziel zwei Längen voraus
 * auf dem Weg; im letzten Stück langsamer bis 0,4 m/s; ist der Bug am Anleger (unter {@link #FEST_ABSTAND} m), macht die
 * Flotte sie fest (Leinen wie {@code anleger}).</li>
 * <li><b>Liegt:</b> von der Ankunft bis zur Abfahrt an den Leinen.</li>
 * <li><b>Fährt ab:</b> Leinen los, {@link #RUECK_S} s rückwärts mit halber Kraft, dann mit Ruder hart Steuerbord herum auf
 * den Weg und mit Fahrt hinaus [G]. Seit S7d8 (Seegang Werkbuch 97, SF144 A, Befund L5d7 4: im Bogen 22 m am Kutter vorbei):
 * rückwärts mit der Führung auf der Linie bis {@link #DREH_AB} m vor den Anleger, dort stoppen, mit der Führung (Querschub an
 * Bug und Heck) auf der Stelle auf den Weg drehen ({@link #DREH_RATE} °/s), erst dann Fahrt aufnehmen [G].</li>
 * <li><b>Weg:</b> sonst ruht sie am Ende des Wegs.</li>
 * </ul>
 * Auf den letzten {@link #FUEHRUNG_AB} m und beim Rückwärts hält eine Führung quer zur Linie des Anlegers Bug und Heck auf der
 * Linie, wie zwei Strahlruder ({@link Festmacher.Fuehrung}, höchstens {@link #QUERSCHUB} N je Ende) [G]: das Ruder allein greift
 * bei 1–2 m/s kaum (die Fähre trieb in der ersten Rechnung 50 m neben den Anleger).
 * Bei Wind über {@link #WIND_MAX} m/s oder einem Pegel am Anleger unter {@link #PEGEL_MIN} m NHN fährt sie nicht ab und
 * kommt nicht [G] (die Fähre fährt nach Tide und Wetter; auf den ersten 5 km ist das Fahrwasser 4–6 m tief).
 * <p>
 * Seit S7d2 (Datei 90, SF117 A) wahlweise <b>nach der Tide</b> wie in Wirklichkeit: je Hochwasser am Pegel ein Umlauf,
 * Ankunft und Abfahrt gegen seine Zeit (in {@code Hooge.szenen}: {@code tide -2:00 +1:30} [G]), nur bei Tag (Ankunft ab
 * {@link #TAG_VON}, Abfahrt bis {@link #TAG_BIS} s des Tags, UTC) [G]. Die Hochwasser gibt die Flotte aus der Pegelreihe des
 * Szenarios ({@link #hochwasser}); vor dem ersten und nach dem letzten geht es mit {@link #TIDE_S} weiter. Ohne Hochwasser
 * liegt sie.
 */
final class Lotse {
    public static final double FAHRT = 5.1, WEG_MAX = 5000, VORAUS = 2, RUECK_S = 60, WIND_MAX = 14, PEGEL_MIN = -1.0, FEST_ABSTAND = 8;
    /**
     * Seit S7d5 (Seegang Werkbuch 93, SF130 A): Der Plan rechnet mit dieser Fahrt über Grund und mit so viel Zeit fürs Anlegen
     * ab {@link #BREMS_AB} m vor dem Anleger [G]. Seit S7d7 (Seegang Werkbuch 95, SF138 A, Befund L5d6 1: das Ruder bremste
     * sie, nicht das flache Wasser) 4,5 m/s und 180 s: Mit dem neuen Steuern fährt sie durchs Wasser bis 6 m/s; 0,6 m/s
     * bleiben gegen den Strom [G]. Unterwegs fährt sie nach dem Plan ({@link #steuere}). Seit S7e1 (Seegang Werkbuch 99, SF146 A)
     * {@code ANLEGEN_S} 95 s statt 180 s: In L5d8 brauchte sie ab BREMS_AB 92 s bis fest (Datei 98, Befund 2) [G nach der Messung].
     */
    public static final double FAHRT_PLAN = 4.5, ANLEGEN_S = 95;
    /** Seit S7d7: ab hier bremst sie auf den Anleger zu (Soll 0,4 + s/60 m/s erreicht {@link #FAHRT}); langsamste Fahrt nach dem Plan [G]. */
    public static final double BREMS_AB = (FAHRT - 0.4) * 60, PLAN_MIN = 2.0;
    /** Seit S7d7: die Zeit des Fahrplans zum Schritt (ms, UTC; NaN ohne Uhr), von der Flotte vor {@link #steuere} gesetzt. */
    double ms = Double.NaN;
    /** Führung quer zur Linie des Anlegers ab diesem Abstand (m) und Querschub je Ende (N) [G]. */
    public static final double FUEHRUNG_AB = 160, QUERSCHUB = 40e3;
    /**
     * Seit S7d8 (SF144 A): die Abfahrt – rückwärts bis die Mitte so weit (m) vor dem Anleger liegt, höchstens mit V_RUECK m/s;
     * dort drehen mit dieser Rate (°/s), bis der Kurs auf DREH_FERTIG ° auf den Weg zeigt; Zeit vom Losmachen, bis sie mit Fahrt
     * auf dem Weg ist, für den Plan (s) [G]. Seit S7d8 auch: die verlangte Fahrt glättet ihren Fehler gegen den Plan über
     * mindestens GLATT_S s (Befund L5d7 5: kurz vor {@link #BREMS_AB} sprang sie auf volle Fahrt) [G].
     */
    public static final double DREH_AB = 80, V_RUECK = 1.5, DREH_RATE = 1.5, DREH_FERTIG = 8, ABLEGEN_S = 240, GLATT_S = 20;
    /** Seit S7e1 (Befund L5d8 3: gedreht 97 statt 80 m vor dem Anleger): Verzögerung, mit der sie vor dem Drehen bremst (m/s²) [G]. */
    public static final double RUECK_BREMS = 0.05;

    /** Seit S7d2: Umläufe nach der Tide nur bei Tag (s des Tags, UTC) [G]; Länge einer Tide (s, M2). */
    public static final double TAG_VON = 5 * 3600, TAG_BIS = 20 * 3600, TIDE_S = 12.4206 * 3600;

    enum Phase { KOMMT, LIEGT, FAEHRT_AB, WEG }

    /**
     * Seit S7e2 (Seegang Werkbuch 101): Werte je Schiff, Vorgabe wie die Fähre – Fahrt (m/s), Fahrt des Plans (m/s), Zeit fürs
     * Anlegen ab {@link #bremsAb} (s), Hebel bei voller Fahrt, kleinster Pegel am Anleger (m NHN); bei Sturm weg (sonst liegt
     * es); Zeitzone des Fahrplans (null: UTC).
     */
    double fahrt = FAHRT, fahrtPlan = FAHRT_PLAN, anlegenS = ANLEGEN_S, hebelVoll = 0.9, pegelMin = PEGEL_MIN;
    boolean sturmWeg;
    java.time.ZoneId zone;
    /** Seit S7e2: ab hier bremst es auf den Anleger zu (m), wie {@link #BREMS_AB} mit der eigenen Fahrt. */
    double bremsAb() { return (fahrt - 0.4) * 60; }

    /** Seit S7e2: der Weg der Abfahrt (sonst derselbe wie der Ankunft), wie {@link #weg}, {@link #s}, {@link #laenge}. */
    double[][] wegAb;
    double[] sAb;
    double laengeAb;

    final double[][] weg;
    final double[] s;
    /** Ankunft und Abfahrt: fest s des Tags (UTC), nach der Tide s gegen das Hochwasser. */
    final double laenge, an, ab;
    final boolean nachTide;
    /** Seit S7d2: Hochwasser am Pegel (ms seit 1970, UTC), aufsteigend. */
    volatile double[] hochwasser = new double[0];
    /** Ruhewasser in der Phase FAEHRT_AB: seit wann (s der Szene), ob die Leinen los sind. */
    double abSeit = Double.NaN;
    boolean los;
    /**
     * Seit S7d8: Stufe der Abfahrt (0 rückwärts, 1 drehen, 2 in Fahrt), Mitte und Kurs der Linie beim Drehen (Grad), Zielkurs,
     * Zeit des letzten Schritts (s der Szene).
     */
    int abStufe;
    /** Seit S7d8: das Soll (m/s), mit dem sie {@link #BREMS_AB} erreicht hat (NaN: noch nicht). */
    double bremsSoll = Double.NaN;
    double[] drehMitte;
    double drehLinie = Double.NaN, drehZiel = Double.NaN, tVor = Double.NaN;
    /** Führung an Bug und Heck (erst beim ersten Steuern angelegt). */
    Festmacher.Fuehrung bugF, heckF;

    /** Die Führung an oder aus; an: auf die Linie des ersten Stücks des Wegs (über den Anleger hinaus). */
    void fuehren(Koerper k, boolean an) {
        double dx = weg[1][0] - weg[0][0], dy = weg[1][1] - weg[0][1], l = Math.hypot(dx, dy);
        fuehren(k, an, weg[0][0], weg[0][1], dx / l, dy / l);
        for (Festmacher.Fuehrung f : new Festmacher.Fuehrung[] {bugF, heckF}) { f.vSoll[0] = 0; f.vSoll[1] = 0; }
    }

    /** Seit S7d8 (SF144 A): die Führung an oder aus, auf die Linie durch (px, py) mit der Richtung (ex, ey) (Länge 1). */
    void fuehren(Koerper k, boolean an, double px, double py, double ex, double ey) {
        if (bugF == null) {
            double[] ka = k.form.kasten();
            double m2 = k.masse / 2, steif = m2 * Math.pow(2 * Math.PI / 30, 2), daempf = 2 * 0.7 * Math.sqrt(steif * m2);
            bugF = new Festmacher.Fuehrung(new double[] {0.8 * ka[1], 0, 0.5 * ka[5]}, steif, daempf, QUERSCHUB);
            heckF = new Festmacher.Fuehrung(new double[] {0.8 * ka[0], 0, 0.5 * ka[5]}, steif, daempf, QUERSCHUB);
        }
        if (an && !k.festmacher().contains(bugF)) { k.festmacher().add(bugF); k.festmacher().add(heckF); }
        for (Festmacher.Fuehrung f : new Festmacher.Fuehrung[] {bugF, heckF}) {
            f.an = an;
            f.p0[0] = px; f.p0[1] = py;
            f.e[0] = ex; f.e[1] = ey;
        }
    }

    Lotse(double[][] route, double[] fahrplan) { this(route, fahrplan, false); }

    Lotse(double[][] route, double[] fahrplan, boolean nachTide) {
        this.nachTide = nachTide;
        int n = route.length;
        double[] sk = new double[n];
        int m = n;
        for (int i = 1; i < n; i++) {
            sk[i] = sk[i - 1] + Math.hypot(route[i][0] - route[i - 1][0], route[i][1] - route[i - 1][1]);
            if (sk[i - 1] >= WEG_MAX) { m = i; break; }
        }
        weg = java.util.Arrays.copyOf(route, m);
        s = java.util.Arrays.copyOf(sk, m);
        laenge = Math.min(WEG_MAX, s[m - 1]);
        an = fahrplan[0];
        ab = fahrplan[1];
        wegAb = weg;
        sAb = s;
        laengeAb = laenge;
    }

    /** Seit S7e2: ein eigener Weg der Abfahrt (bis {@link #WEG_MAX} m); sein erstes Stück sollte das der Ankunft sein. */
    void wegAb(double[][] route) {
        int n = route.length, m = n;
        double[] sk = new double[n];
        for (int i = 1; i < n; i++) {
            sk[i] = sk[i - 1] + Math.hypot(route[i][0] - route[i - 1][0], route[i][1] - route[i - 1][1]);
            if (sk[i - 1] >= WEG_MAX) { m = i; break; }
        }
        wegAb = java.util.Arrays.copyOf(route, m);
        sAb = java.util.Arrays.copyOf(sk, m);
        laengeAb = Math.min(WEG_MAX, sAb[m - 1]);
    }

    /** Sekunde des Tags (UTC) zur Zeit in ms seit 1970. */
    static double tagS(double ms) { return ((ms / 1000.0) % 86400 + 86400) % 86400; }

    /**
     * Seit S7d2: Ankunft und Abfahrt (ms, UTC) des Umlaufs, in dem die Zeit ms liegt (vom Beginn des Kommens bis zum Ende des
     * Abfahrens); null: keiner (dann ist sie weg, oder sie liegt, wenn nach der Tide kein Hochwasser bekannt ist).
     */
    double[] umlauf(double ms) {
        double dauer = planZeitI(laenge), vor = 1000 * dauer, nach = 1000 * (planZeitI(laengeAb) + ABLEGEN_S + 60);  // seit S7d5 nach FAHRT_PLAN, seit S7d7 mit BREMS_AB, seit S7d8 mit ABLEGEN_S, seit S7e2 je Schiff
        java.util.List<double[]> kandidaten = new java.util.ArrayList<>();
        if (!nachTide && zone != null) {                                       // seit S7e2: in der Ortszeit
            java.time.LocalDate d0 = java.time.Instant.ofEpochMilli((long) ms).atZone(zone).toLocalDate();
            for (int d = -1; d <= 1; d++) {
                java.time.LocalDate dd = d0.plusDays(d);
                double a0 = dd.atTime(java.time.LocalTime.ofSecondOfDay((long) an)).atZone(zone).toInstant().toEpochMilli();
                double a1 = dd.atTime(java.time.LocalTime.ofSecondOfDay((long) ab)).atZone(zone).toInstant().toEpochMilli();
                kandidaten.add(new double[] {a0, a1});
            }
        } else if (!nachTide) {
            double tag0 = Math.floor(ms / 86400e3) * 86400e3;
            for (int d = -1; d <= 1; d++) kandidaten.add(new double[] {tag0 + d * 86400e3 + 1000 * an, tag0 + d * 86400e3 + 1000 * ab});
        } else {
            double[] hw = hochwasser;
            if (hw.length == 0) return null;
            java.util.List<Double> alle = new java.util.ArrayList<>();
            for (double h : hw) alle.add(h);
            for (int j = 1; j <= 4; j++) { alle.add(hw[hw.length - 1] + j * 1000 * TIDE_S); alle.add(hw[0] - j * 1000 * TIDE_S); }
            for (double h : alle) {
                double a0 = h + 1000 * an, a1 = h + 1000 * ab;
                if (tagS(a0) < TAG_VON || tagS(a1) > TAG_BIS || Math.floor(a0 / 86400e3) != Math.floor(a1 / 86400e3)) continue;
                kandidaten.add(new double[] {a0, a1});
            }
        }
        for (double[] k : kandidaten) if (ms >= k[0] - vor && ms < k[1] + nach) return k;
        return null;
    }

    /** Die Phase nach Fahrplan zur Zeit ms (UTC); ohne gutes Wetter liegt sie. */
    Phase phase(double ms, boolean wetter) {
        if (!wetter) return sturmWeg ? Phase.WEG : Phase.LIEGT;             // seit S7e2: ein Schiff von weit her kommt dann nicht
        double[] u = umlauf(ms);
        if (u == null) return nachTide && hochwasser.length == 0 ? Phase.LIEGT : Phase.WEG;
        if (ms < u[0]) return Phase.KOMMT;
        if (ms < u[1]) return Phase.LIEGT;
        return Phase.FAEHRT_AB;
    }

    /** Seit S7d7: Zeit nach dem Plan vom Weg w (m vor dem Anleger) bis fest (s). */
    static double planZeit(double w) { return w > BREMS_AB ? (w - BREMS_AB) / FAHRT_PLAN + ANLEGEN_S : ANLEGEN_S * Math.max(0, w) / BREMS_AB; }

    /** Seit S7e2: wie {@link #planZeit} mit den Werten dieses Schiffs. */
    double planZeitI(double w) { double ba = bremsAb(); return w > ba ? (w - ba) / fahrtPlan + anlegenS : anlegenS * Math.max(0, w) / ba; }

    /** Seit S7e2: wie {@link #planWeg} mit den Werten dieses Schiffs. */
    double planWegI(double tRest) { double ba = bremsAb(); return tRest > anlegenS ? ba + fahrtPlan * (tRest - anlegenS) : ba * Math.max(0, tRest) / anlegenS; }

    /** Seit S7d7: Weg nach dem Plan (m vor dem Anleger) tRest s vor der Ankunft. */
    static double planWeg(double tRest) {
        return tRest > ANLEGEN_S ? BREMS_AB + FAHRT_PLAN * (tRest - ANLEGEN_S) : BREMS_AB * Math.max(0, tRest) / ANLEGEN_S;
    }

    /** Abstand vom Anleger auf dem Weg, wo sie nach Fahrplan zur Zeit ms ist (m). */
    double sSoll(double ms, Phase p) {
        double[] u = umlauf(ms);
        return switch (p) {
            case KOMMT -> u == null ? laenge : Math.min(laenge, planWegI((u[0] - ms) / 1000));                  // seit S7d5, seit S7d7 mit BREMS_AB
            case FAEHRT_AB -> u == null ? laengeAb : Math.min(laengeAb, Math.max(0, fahrtPlan * ((ms - u[1]) / 1000 - ABLEGEN_S)));   // seit S7d8; seit S7e2 auf dem Weg der Abfahrt
            case LIEGT -> 0;
            case WEG -> laengeAb;
        };
    }

    /**
     * Ort auf dem Weg im Abstand sw vom Anleger: Ost, Nord, Richtung des Wegs nach außen (Grad). Unter 0 geradeaus über den
     * Anleger hinaus gedacht (die Linie, auf der sie anlegt).
     */
    double[] punkt(double sw) { return punkt(sw, false); }

    /** Seit S7e2: wie {@link #punkt(double)}, mit ab auf dem Weg der Abfahrt. */
    double[] punkt(double sw, boolean abW) {
        double[][] weg = abW ? wegAb : this.weg;
        double[] s = abW ? sAb : this.s;
        double laenge = abW ? laengeAb : this.laenge;
        if (sw < 0) {
            double dx = weg[1][0] - weg[0][0], dy = weg[1][1] - weg[0][1], l = Math.hypot(dx, dy);
            return new double[] {weg[0][0] + sw * dx / l, weg[0][1] + sw * dy / l, (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360};
        }
        // seit S7e2: hinter dem Ende des Wegs geradeaus weiter in seiner letzten Richtung (bis S7e1 das Ende selbst: wer früher
        // dort war als im Plan, kreiste um den Punkt, bis er verschwand – beim Adler-Express im Container fünf Minuten)
        double hinter = Math.max(0, sw - laenge);
        sw = Math.min(laenge, sw);
        int i = 1;
        while (i < s.length - 1 && s[i] < sw) i++;
        double l = s[i] - s[i - 1], u = l > 0 ? (sw - s[i - 1]) / l : 0;
        if (hinter > 0 && l > 0) u += hinter / l;
        double dx = weg[i][0] - weg[i - 1][0], dy = weg[i][1] - weg[i - 1][1];
        return new double[] {weg[i - 1][0] + u * dx, weg[i - 1][1] + u * dy, (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360};
    }

    /** Abstand vom Anleger des nächsten Punkts auf dem Weg zu (x, y). */
    double sIst(double x, double y) { return sIst(x, y, false); }

    /** Seit S7e2: wie {@link #sIst(double, double)}, mit ab auf dem Weg der Abfahrt. */
    double sIst(double x, double y, boolean abW) {
        double[][] weg = abW ? wegAb : this.weg;
        double[] s = abW ? sAb : this.s;
        double best = Double.POSITIVE_INFINITY, sb = 0;
        for (int i = 1; i < s.length; i++) {
            double ax = weg[i - 1][0], ay = weg[i - 1][1], sx = weg[i][0] - ax, sy = weg[i][1] - ay, l2 = sx * sx + sy * sy;
            // seit S7e2 hinter dem letzten Punkt weiter auf seiner Verlängerung (wie {@link #punkt(double, boolean)})
            double u = l2 > 0 ? Math.max(0, Math.min(i == s.length - 1 ? Double.POSITIVE_INFINITY : 1, ((x - ax) * sx + (y - ay) * sy) / l2)) : 0;
            double qx = ax + u * sx - x, qy = ay + u * sy - y, d = qx * qx + qy * qy;
            if (d < best) { best = d; sb = s[i - 1] + u * Math.sqrt(l2); }
        }
        return sb;
    }

    /**
     * Ruder und Hebel für einen Schritt der Szene zur Zeit ts (s der Szene) in der Phase p; bug: Ort des Anlegers (Ost,
     * Nord) für das Anlegen. Gibt true, wenn sie anlegen kann (Bug unter {@link #FEST_ABSTAND} m vom Anleger, langsam).
     */
    boolean steuere(Koerper k, Phase p, double ts, double[] anleger) {
        double[] o = k.lage(), v = k.geschwindigkeit();
        double fahrt = Math.hypot(v[0], v[1]);
        double L = k.form.kasten()[1];
        double[] bug = k.punktWelt(L, 0, 0);
        double wz = k.drehgeschwindigkeit()[2];
        switch (p) {
            case KOMMT, LIEGT -> {
                // auf dem Weg voraus, zuletzt auf der Linie über den Anleger hinaus (so kommt sie längs an) [G]
                double si = sIst(bug[0], bug[1]);
                double[] z = punkt(si - Math.max(40 + 10 * fahrt, Math.min(VORAUS * 2 * L, si)));
                k.steuern(z[0], z[1]);
                fuehren(k, si < FUEHRUNG_AB);
                k.ruder = Math.max(-1, Math.min(1, k.ruder + 6 * wz));          // Gierdämpfung [G]
                double soll = Math.max(0.4, Math.min(this.fahrt, 0.4 + si / 60));
                // seit S7d7 (SF138 A): nach dem Fahrplan – so schnell, dass sie BREMS_AB zur Zeit Ankunft − ANLEGEN_S erreicht,
                // mindestens PLAN_MIN, höchstens FAHRT. Seit S7d8 (Befund L5d7 5): der Fehler gegen den Plan über mindestens
                // GLATT_S s ausgeglichen (bis S7d7 (si − BREMS_AB) / rest: kurz vor BREMS_AB sprang das Soll auf volle Fahrt);
                // im Bremsen nicht schneller als das Soll, mit dem sie BREMS_AB erreicht (bis S7d7 bis FAHRT: noch einmal voller Hebel
                // bei BREMS_AB) [G]
                double[] u = Double.isNaN(ms) ? null : umlauf(ms);
                if (u != null) {
                    double rest = (u[0] - ms) / 1000 - anlegenS, ba = bremsAb();
                    if (si > ba) {
                        double plan = fahrtPlan + (si - ba - fahrtPlan * rest) / Math.max(rest, GLATT_S);
                        soll = Math.min(soll, Math.max(PLAN_MIN, plan));
                        bremsSoll = soll;
                    } else {
                        if (Double.isNaN(bremsSoll)) bremsSoll = soll;
                        soll = Math.min(soll, bremsSoll);
                    }
                }
                k.hebel = Math.max(-1, Math.min(1, hebelVoll * soll / this.fahrt + 0.6 * (soll - fahrt)));
                double d = Math.hypot(bug[0] - anleger[0], bug[1] - anleger[1]);
                if (d < FEST_ABSTAND) k.hebel = Math.min(k.hebel, -0.3 * fahrt);
                return d < FEST_ABSTAND && fahrt < 1.0;
            }
            case FAEHRT_AB -> {
                if (Double.isNaN(abSeit)) { abSeit = ts; abStufe = 0; tVor = Double.NaN; }
                double dts = Double.isNaN(tVor) ? 0 : Math.max(0, Math.min(1, ts - tVor));
                tVor = ts;
                double ex = weg[1][0] - weg[0][0], ey = weg[1][1] - weg[0][1], el = Math.hypot(ex, ey);
                ex /= el;
                ey /= el;
                double hk = Math.toRadians(k.kurs()), vh = v[0] * Math.sin(hk) + v[1] * Math.cos(hk);   // Fahrt längs des Kurses
                if (abStufe == 0) {
                    // seit S7d8 (SF144 A): rückwärts mit der Führung auf der Linie, bis die Mitte DREH_AB m draußen ist, dort stoppen
                    double aus = (o[0] - weg[0][0]) * ex + (o[1] - weg[0][1]) * ey, va = v[0] * ex + v[1] * ey;
                    // seit S7e1: nach außen höchstens √(2 a s) vor DREH_AB (vorher 0,25 · s: sie lief rund 17 m nach)
                    double vs = Math.max(0, Math.min(V_RUECK, Math.sqrt(2 * RUECK_BREMS * Math.max(0, DREH_AB - aus))));
                    k.ruder = 0;
                    // der Bug zeigt zum Land: rückwärts ist nach außen; Hebel gegen den Kurs
                    double dir = Math.signum(Math.sin(hk) * ex + Math.cos(hk) * ey);
                    k.hebel = Math.max(-1, Math.min(1, dir * (0.9 * vs / FAHRT + 2.5 * (vs - va))));   // seit S7e1 Verstärkung 2,5 (vorher 0,8) [G]; FAHRT der Fähre auch für andere [G]
                    fuehren(k, true);
                    if ((aus >= DREH_AB - 3 && Math.abs(va) < 0.15) || ts - abSeit > RUECK_S + 120) {
                        abStufe = 1;
                        drehMitte = new double[] {o[0], o[1]};
                        drehLinie = k.kurs();
                        double si = sIst(o[0], o[1], true);
                        double[] z = punkt(si + VORAUS * 2 * L, true);
                        drehZiel = (Math.toDegrees(Math.atan2(z[0] - o[0], z[1] - o[1])) + 360) % 360;
                    }
                    return false;
                }
                if (abStufe == 1) {
                    // seit S7d8 (SF144 A): auf der Stelle drehen – die Führung zieht Bug und Heck auf eine Linie durch die Mitte,
                    // die sich mit DREH_RATE auf den Zielkurs dreht (der kürzere Weg); die Fahrt längs des Kurses bleibt 0
                    double rest = ((drehZiel - drehLinie) % 360 + 540) % 360 - 180;
                    double schritt = Math.signum(rest) * Math.min(Math.abs(rest), DREH_RATE * dts);
                    drehLinie = (drehLinie + schritt + 360) % 360;
                    double hl = Math.toRadians(drehLinie);
                    fuehren(k, true, drehMitte[0], drehMitte[1], Math.sin(hl), Math.cos(hl));
                    double w = Math.abs(rest) > 1e-9 ? -Math.signum(rest) * Math.toRadians(DREH_RATE) : 0;   // gegen den Uhrzeigersinn positiv
                    for (Festmacher.Fuehrung f : new Festmacher.Fuehrung[] {bugF, heckF}) {
                        double[] pf = k.punktWelt(f.punkt[0], f.punkt[1], f.punkt[2]);
                        f.vSoll[0] = -w * (pf[1] - drehMitte[1]);
                        f.vSoll[1] = w * (pf[0] - drehMitte[0]);
                    }
                    k.ruder = 0;
                    k.hebel = Math.max(-0.3, Math.min(0.3, -0.8 * vh));
                    double dk = ((drehZiel - k.kurs()) % 360 + 540) % 360 - 180;
                    if (Math.abs(rest) < 1e-9 && Math.abs(dk) < DREH_FERTIG) abStufe = 2;
                    else return false;
                }
                fuehren(k, false);
                double si = sIst(o[0], o[1], true);
                double[] z = punkt(si + VORAUS * 2 * L, true);
                double soll = (Math.toDegrees(Math.atan2(z[0] - o[0], z[1] - o[1])) + 360) % 360;
                double dk = ((soll - k.kurs()) + 540) % 360 - 180;
                if (Math.abs(dk) > 60) {
                    k.ruder = 1;                                                // herum über Steuerbord, vom Land weg [G]
                    k.hebel = 0.5;
                } else {
                    k.steuern(z[0], z[1]);
                    k.ruder = Math.max(-1, Math.min(1, k.ruder + 6 * wz));
                    k.hebel = Math.max(-1, Math.min(1, hebelVoll + 0.6 * (this.fahrt - fahrt)));
                }
                return false;
            }
            default -> {
                k.ruder = 0;
                k.hebel = 0;
                if (bugF != null) fuehren(k, false);
                return false;
            }
        }
    }
}
