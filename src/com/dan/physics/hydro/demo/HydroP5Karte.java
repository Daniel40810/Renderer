package com.dan.physics.hydro.demo;

import static com.dan.core.pruef.Attest.f;
import static com.dan.physics.hydro.demo.HydroCheck.A;

import com.dan.core.gitter.Gebiet;
import com.dan.physics.hydro.Hydro;
import java.io.File;

/** Fall 10 über G0 (Werkbuch HalligHooge Teil A, 12.6, Plan P5a): Grenze A auf dem geographischen Gitter. */
final class HydroP5Karte {
    private HydroP5Karte() { }

    /**
     * G0 aus dem Gebiet (mit Subgrid), Wasserstand an allen offenen Seiten A sin(ω_M2 t) mit 1 m und 1 h Anlauf, nach 2 h
     * ein Schritt auf der Karte und auf dem Prozessor aus demselben Stand (wie Fall 10).
     */
    static void fall10G0(String gebiet) throws Exception {
        Gebiet ge = Gebiet.lies(new File(gebiet));
        Hydro h;
        try {
            h = new Hydro(ge.gitter("G0"), ge.packung("G0"));
        } catch (java.io.IOException e) {
            A.info("Fall 10 über G0", "G0 ohne Subgrid im Gebiet (Aufbereitung --schritte backen ab P5): " + e.getMessage());
            return;
        }
        for (int s = 0; s < 4; s++) {
            h.rand[s] = Hydro.Rand.WASSERSTAND;
            h.randwert[s] = (t, x, y) -> Math.sin(HydroP5.M2 * t) * (t >= 3600 ? 1 : 0.5 * (1 - Math.cos(Math.PI * t / 3600)));
        }
        h.fuellen(0);
        h.lauf(7200, 1e9);
        HydroCheck.einSchritt(h, f("G0 (%s, Fläche je Zeile %.1f … %.1f km²), 1 m Tide an allen Seiten nach 2 h", ge.gitter("G0"), ge.gitter("G0").flaeche(0) / 1e6, ge.gitter("G0").flaeche(ge.gitter("G0").ny - 1) / 1e6));
    }
}
