# P5c – LaufCheck

Attest für `com.dan.lauf`, Version 0.2. Stand 14.11.2026 05:53, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

18 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Katalog und Verdrahtung | Katalog: tide.eq (m, Zelle, schreibt tide, liest hydro), air.p auch von tide gelesen, je Feld ein Schreiber (seit P5e mit air.taux, air.tauy) | 42 Felder; tide.eq Eintrag[name=tide.eq, einheit=m, ort=ZELLE, raster=ZELLE, komponenten=1, schichten=1, art=ZUSTAND, verbund=KEINER, schreiber=[tide], anfang=, leser=[hydro]] | 42 Felder | ok |
| 1 Katalog und Verdrahtung | Lauf G0 / G1 / G2 mit antrieb, tide, hydro verdrahtet (Prozessor (double, 16 Kerne)) | keine Mängel, 0 Hinweise; 11 Felder: air.u10 auf ERA5; air.v10 auf ERA5; air.p auf ERA5; tide.bc auf G0; tide.eq auf G0, G1, G2; water.eta auf G0, G1, G2; water.h auf G0, G1, G2; water.u auf G0, G1, G2; water.v auf G0, G1, G2; water.wetAge auf G0, G1, G2; water.q auf G0, G1, G2 | keine Mängel, keine Hinweise | ok |
| 1 Katalog und Verdrahtung | Aufbau | 1,2 s (Gebiet lesen, Gitter, Einrichten, Verdrahten, Anfangsstand; der ständige Anteil der Gleichgewichtstide aus 8 760 Stunden) |  | – |
| 1 Katalog und Verdrahtung | tide | tide 0.1: Rand G0 mit 911 offenen Randzellen (899 bilinear, 12 vom nächsten Punkt, 0 ohne Werte), Gleichgewichtstide auf [G0, G1, G2], Anlauf 3.0 h, Mittel +0.000 m, umgekehrtes Barometer |  | – |
| 1 Katalog und Verdrahtung | Gegenproben: hydro ohne tide; hydro mit Antrieb ohne antrieb; tide mit Barometer ohne antrieb – es startet nichts | 3 von 3 erkannt | alle | ok |
| 2 tide | tide.bc am Bus gegen eine eigene Rechnung (Ort der Geisterzelle, EOT20 mit Knotenfaktoren, Anlauf, umgekehrtes Barometer aus ERA5 in der Randzelle), 911 offene Randzellen zu 72 Zeiten | /Δη/ bis 5,8e-15 m; NaN an offenen Randzellen 0 | ≤ 1e-6 m [G] | ok |
| 2 tide | tide.eq am Bus auf G0 und G1 gegen 0,69 · r(t) · (η_eq − η̄) aus den Ephemeriden, alle Zellen zu 13 Zeiten | /Δ/ bis 0,0e+00 m | ≤ 1e-9 m [G] | ok |
| 2 tide | Ständiger Anteil der Gleichgewichtstide η̄(φ) über 43–64° N gegen C · P₂(sin φ′) aus den Deklinationen desselben Jahres | /Δ/ bis 9,52e-05 m; C = -0,1849 m, η̄ von -0,1305 bis -0,0358 m (mal 0,69: Neigung 6,6 cm über G0) | ≤ 1 mm [G] | ok |
| 2 tide | Gleichgewichtstide über G2 (nicht angewandt, F27 A: nur gemessen) | größte Spanne zwischen den Zellen von G2 über den Lauf 6,32 mm (Schätzung im Plan: unter 1 mm [S]) |  | – |
| 3 antrieb | Luftdruck und Wind in hydro auf G0 (geographisch, vom Bus umgerechnet) zur Mitte des letzten Intervalls gegen ERA5 bilinear an den Zellmitten (Mittel der Stände am Anfang und Ende des Intervalls) | /Δp/ bis 5,8e-11 Pa, /Δu/ bis 1,1e-14 m/s (Wind bis 24,1 m/s) | ≤ 0,01 Pa, ≤ 1e-5 m/s [G] | ok |
| 3 antrieb | Luftdruck und Wind in hydro auf G2 (UTM, vom Bus umgerechnet) zur Mitte des letzten Intervalls gegen ERA5 bilinear an den Zellmitten (Mittel der Stände am Anfang und Ende des Intervalls), Wind um die Meridiankonvergenz gedreht | /Δp/ bis 5,8e-11 Pa, /Δu/ bis 1,4e-14 m/s (Wind bis 18,3 m/s), Betrag nach dem Drehen bis 1,8e-14 m/s | ≤ 0,01 Pa, ≤ 1e-5 m/s [G] | ok |
| 4 Takt | Reihenfolge je Intervall: antrieb, tide, dann hydro, das beide im Intervall liest; keine Lesung außerhalb der gehaltenen Stände | Beginn [antrieb 0.0 300.0, tide 0.0 300.0, hydro 0.0 300.0]; 216 Schritte, 0 außerhalb | antrieb, tide, hydro; 0 | ok |
| 5 Lauf G0 | G1 | G2 | Volumen je Stufe über 6,0 h: /V − V₀ − (Zufluss − Abfluss)/ / Bezug, Abgeschnittenes zählt als Fehler | Stufe 0 8,8e-15 (abgeschnitten 0 m³), Stufe 1 2,2e-15 (abgeschnitten 0 m³), Stufe 2 7,1e-15 (abgeschnitten 0 m³) | < 1e-5 (wie Fall 9) | ok |
| 5 Lauf G0 | G1 | G2 | kein NaN in η, V, u aller Gitter und in water.eta am Bus (G2) | 0 und 0 nicht endlich | keins | ok |
| 5 Lauf G0 | G1 | G2 | water.q (Zeitmittel, am Bus in float) auf G0 gegen die Masse: je Zelle /ΔV − Σ q · Länge · τ/ über das letzte Intervall, bezogen auf den Umsatz Σ /Fluss/ · Δt ihrer Kanten + /ΔV/ | höchstens 5,6e-08 (Zelle 100, 47) | ≤ 1e-5 [G] | ok |
| 5 Lauf G0 | G1 | G2 | water.q (Zeitmittel, am Bus in float) auf G1 gegen die Masse: je Zelle /ΔV − Σ q · Länge · τ/ über das letzte Intervall, bezogen auf den Umsatz Σ /Fluss/ · Δt ihrer Kanten + /ΔV/ | höchstens 5,4e-08 (Zelle 43, 63) | ≤ 1e-5 [G] | ok |
| 5 Lauf G0 | G1 | G2 | Pegel am Ende (2026-10-25T18:00:00Z), Modell gegen Beobachtung, m NHN | HELGOLAND_BINNENHAFEN (G1) +0,63 gegen +1,20 m; BÜSUM (G1) +0,77 gegen +1,17 m; HUSUM (G2) +0,84 gegen +1,17 m; PELLWORM_ANLEGER (G2) +0,74 gegen +1,05 m; WITTDÜN (G2) +0,61 gegen +1,10 m; HÖRNUM (G2) +0,58 gegen +1,24 m; DAGEBÜLL (G2) +0,76 gegen +1,26 m; LIST_AUF_SYLT (G2) +0,59 gegen +1,24 m – nach 6,0 h ab Ruhe noch nicht eingeschwungen; die Prüfung der Tide ist P5d |  | – |
| 5 Lauf G0 | G1 | G2 | Zeit | 6,0 h in 70 s, Zeitraffer 309,4, 98,6 ns je Zellschritt (7,077e+08 Zellschritte; mit antrieb, tide und den Prüfungen) |  | – |
| 6 Wiederholbar | Derselbe Lauf noch einmal über 1 h (Prozessor (double, 16 Kerne)): η aller Gitter | 0 von 3 Gittern verschieden | bitgleich | ok |
| 7 Tide allein | Lauf ohne Antrieb (Tide allein) verdrahtet und rechnet ein Intervall | Hinweise []; Felder [tide.bc, tide.eq, water.eta, water.h, water.u, water.v, water.wetAge, water.q]; Bilanz G0 1,5e-14 | keine Hinweise, ohne air.* | ok |
| 9 Windschub vom Bus (P5e) | Windschub aus ERA5 (iews, inss): verdrahtet (hydro liest air.taux, air.tauy), Kraft an der Oberfläche in hydro auf G0 (geographisch) und G2 (UTM, gedreht) gegen die eigene Rechnung, Bilanz über 15 min | Hinweise []; /Δτ/ G0 bis 4,9e-15, G2 bis 8,9e-16 N/m² (τ bis 6,85 bzw. 1,67 N/m²); Bilanz 1,9e-14 | keine Hinweise; ≤ 1e-08 N/m² [G]; < 1e-5 | ok |
| 9 Windschub vom Bus (P5e) | Windschub nach Charnock mit α aus ERA5: verdrahtet (hydro liest air.taux, air.tauy), Kraft an der Oberfläche in hydro auf G0 (geographisch) und G2 (UTM, gedreht) gegen die eigene Rechnung, Bilanz über 15 min | Hinweise []; /Δτ/ G0 bis 1,9e-12, G2 bis 3,7e-13 N/m² (τ bis 2,67 bzw. 0,96 N/m²); Bilanz 1,7e-14 | keine Hinweise; ≤ 1e-08 N/m² [G]; < 1e-5 | ok |
| 9 Windschub vom Bus (P5e) | Gegenprobe: Windschub aus ERA5 oder nach Charnock mit einem Wetterfeld ohne iews, inss, charnock | 2 von 2 abgelehnt | beide | ok |

Alles in Ordnung.
