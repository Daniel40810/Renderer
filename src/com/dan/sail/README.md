# com.dan.sail – Segel und Segelboote

Segelboote nach der Physik: das Segel als Tragflügel im scheinbaren Wind, Kiel und Schwert als Tragflügel im Wasser, Rumpfwiderstand, Krängung gegen das aufrichtende Moment, Polaren und Bewegung über die Zeit; Wende, Halse, Kentern und eine Regatta mit Startverfahren, Bahn und Wegerecht.

**Stand: Timmendorf Phase 18** (Rahsegler der Wikingerzeit); sail II seit Phase 8, Nachtrag Phase 9: `Boat.downwindAllowed` – die Regatta lässt den Gennaker vor dem Start im Sack (Befund aus Phase 8).

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/sail/`.

## Was es kann

- **Takelagen als Datensätze** (F11 A): Optimist, ILCA 7, 420er, Hobie 16, J/70 mit Gennaker, Fahrtenyacht 34 ft, Gaffelschoner, Viermastbark Passat, Kite (`Sail.RIGS`).
  - Seit Phase 8 mit Segelplan (`Sail.Plan`): Hochtakelung, Gaffel (Schoner), Rahsegel (Passat: trägt erst ab β ≈ 35°, am Wind nicht höher als rund 65°), Kite (Schirm im Windfenster, Kraft am Trapezhaken, im Flug mehr Wind als am Fahrer).
  - Luftwiderstand von Crew, Rumpf und Rigg (`windage`): bremst Kats und Kites bei viel scheinbarem Wind.
  - Klassenmaße nach den Klassenvorschriften.
  - Formbeiwerte für Wellenwiderstand und Gleiten [G], an Literaturwerte angepasst (siehe Prüfung).
- **Aerodynamik:** Auftrieb und Formwiderstand über dem scheinbaren Windwinkel β, getrennt für Groß mit Fock und für Gennaker oder Spinnaker [G nach dem Verlauf bei Hazen 1980].
  - induzierter Widerstand C_L²/(π·AR) über dem Spiegelbild der Wasseroberfläche
  - Krängung dreht den Wind in die Segelebene
  - Entlasten über die Flachheit (`flat`)
- **Hydrodynamik:** Reibung nach ITTC 1957 mit Formfaktor, Wellenwiderstand über der Froude-Zahl (gleitende Rümpfe heben sich heraus), Zuschlag für Krängung.
  - Kiel oder Schwert als Flügel nach Helmbold, mit Abdrift, induziertem Widerstand und Strömungsabriss
- **Gleichgewicht:** Vortrieb = Widerstand, Querkraft = Seitenkraft der Anhänge, krängendes = aufrichtendes Moment.
  - Das aufrichtende Moment kommt vom Rumpf (W·GM·sin φ, beim Kat höchstens W·B/2) und von der Crew auf der Kante.
  - Wo es nicht reicht, wird entlastet; über der größten Krängung der Klasse geht es nicht.
- **Polaren:** `polar(takelage, wind)` gibt je Windwinkel die schnellste Kombination aus Flachheit und Vorwindsegel. `vmg` liefert die besten Kreuz- und Vorwindwinkel, `tackAngle` den Wendewinkel, `vmgCached` dasselbe aus einem Speicher.
  - Seit Phase 8 wird die Flachheit stetig gesucht (`best`: größte Flachheit, bei der das Moment reicht, dann goldener Schnitt), nicht mehr in 9 Stufen: die Zacken der Polare sind weg (Befund am Hobie 16 aus Phase 7).
- **Bewegung:** `step(boot, dt)` rechnet Fahrt (mit mitbewegtem Wasser), Krängung (Trägheit, Dämpfung), Abdrift, Kurs und Lage über Grund mit Strömung.
  - Der Rudergänger steuert `targetHeadingDeg` mit der Drehrate der Klasse.
  - Der Trimm wird alle 0,5 s nachgeführt. Das Boot läuft auf seine Polare zu.
- **Manöver seit Phase 8:**
  - Krängung mit Vorzeichen (`rollDeg`, + Steuerbord unten): sie läuft bei der Wende durch null. Die Crew sitzt in Luv und hängt nur so weit aus, wie es nötig ist.
  - Der Baum (`boomDeg`) folgt der Schot; kommt der Wind achterlich von seiner Seite, schlägt er über (Halse, `onGybe` mit Heftigkeit) und stößt das Boot auf die neue Seite.
  - Kentern: Jollen über 55°, Kats über 35° (das aufrichtende Moment des Kats nimmt ab, sobald ein Rumpf fliegt). Gekenterte Boote liegen auf der Seite, treiben und werden nach `rightingTime` aufgerichtet (`onRight`). `capsize(boot)` lässt ein Boot von außen kentern (Patzer).
  - Reflex der Crew: krängt das Boot über seine Grenze, fiert sie die Schot, ein Kind (`skill` 0,5) langsamer und weniger. So kentert der Kat nicht beim Anfahren, wohl aber in einer harten Bö.
  - Schoten fieren von außen (`ease`), Fahrt aufbauen: treibt das Boot hoch am Wind quer, fällt der Rudergänger ab.
- **Regatta seit Phase 8** (`Regatta`, F13 A): Bahn auf und ab (Startlinie zwischen Startschiff und Pin, Luvtonne, Leetor), Startverfahren 5–4–1–0 mit Einzelrückruf, Taktik je Boot (unter der Linie halten, auf Zeit an die Linie, Wende an der Anliegelinie oder am Rand des eigenen Felds, Halse auf dem Vorwindkurs, Tonnen runden, Ziel). Wegerecht nach den Wettfahrtregeln 10 bis 12 vereinfacht (Backbordbug weicht, Luv weicht, klar achteraus weicht). Abdeckung: jedes Segel nimmt den Booten in seiner Lee bis 30 % Wind.
- **Ereignisse:** `onTack` (Wende), `onLuff` (das Segel killt), `onCapsize`, `onStart`, seit Phase 8 `onGybe` und `onRight`; die Regatta meldet Signale, Tonnen, Ziel und Ausweichmanöver.
- **Form:** `BoatShape` baut aus dem Zustand eines Boots die Dreiecke:
  - Rumpf, Kiel und Ruder, beim Kat zwei Rümpfe mit Trampolin
  - Mast und Baum; der Baum steht nach Lee
  - Groß und Fock mit Bauch und Verwindung, gesetzt der Gennaker
  - die Crew auf der Luvkante; das ganze Boot krängt nach Lee
  - seit Phase 8: die Crew hängt aus oder steht im Trapez (Kat, 420er), Kinder sind kleiner; der Baum aus `boomDeg`, schlagendes Tuch (`time`); gekenterte Boote liegen auf der Seite; Rahsegler mit vier Masten, sechs Rahen je Rahmast, Klüverbaum und Besan (gesetzt oder gepackt, `furled`); Gaffelschoner; Kite mit Brett, Fahrer und Schirm im Windfenster; Farben je Boot
- **Eingänge:** `Wind`, `Water` (Höhe, Strömung), `Cloth` (Profiltiefe aus `cloth` nahe der Kamera, F10 A).
- **Presets:** `calm()`, `breeze()`, `fresh()`, `travemuenderWoche()` (Seewind aus Nordost [G]).
- **Paketvertrag:** offene Felder, `seed`, Qualitätsstufen `LOW` bis `ULTRA` (Feinheit der Polaren, Unterschritte), `strength()`.

## Benutzung

```java
Sail s = Sail.travemuenderWoche();
Sail.Boat b = new Sail.Boat(Sail.J70);
b.headingDeg = 0;
b.targetHeadingDeg = 40;            // der Rudergänger dreht an den Wind
s.step(b, dt);                      // Fahrt, Krängung, Abdrift, Lage
double[][] p = s.polar(Sail.J70, 12 * Sail.KN);   // {TWA, Fahrt, Krängung, Abdrift, Flachheit, Gennaker}
double[] v = s.vmg(Sail.J70, 12 * Sail.KN);       // Kreuzwinkel, VMG, Fahrt; Vorwindwinkel, VMG, Fahrt

// seit Phase 8: eine Regatta
Regatta r = new Regatta(s, Regatta.Course.fromMarks(startschiffX, startschiffZ, luvX, luvZ, 160, 2), seed);
r.add(Sail.J70, 20);
r.listener = new Regatta.Listener() { public void onSignal(String was, double t) { /* Horn */ } };
r.start(300);                       // Ankündigung jetzt, Start in 5 min
r.step(dt);                         // Taktik, Wegerecht, Segeln, Tonnen, Ziel
String hud = r.describe();
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Sail` | Kern: Takelagen, Beiwerte, Kräfte, Gleichgewicht, Polaren, Bewegung, Ereignisse |
| `Regatta` | Bahn, Startverfahren, Taktik, Wegerecht, Abdeckung, Reihenfolge (seit Phase 8) |
| `BoatShape` | Dreiecke eines Boots aus seinem Zustand (für `gl/` und Bilder ohne Grafikkarte) |
| `gl/SailGl` | Boote mit JOGL, beidseitig, Durchscheinen der Segel; Shader `sail.vert`, `sail.frag` |
| `demo/SailDemo` | links ein Boot in 3D (Java2D), rechts die Polare mit den ORC-Punkten der J/70; Regler für Klasse (alle neun), Wind, Kurs; `--bild datei.png`; `--regatta datei.png [boote] [wind]` zeichnet ein Feld J/70 auf der Bahn von oben |
| `demo/SailCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

Gegen veröffentlichte Werte:

- **J/70 gegen das ORC-One-Design-Zertifikat** (data.orc.org, `j70.od` 2019): Kreuz-VMG, Fahrt bei 90° und Vorwind-VMG bei 8, 12 und 16 kn auf 12 %. Der Wendewinkel liegt auf 10 % bei den 75° des Zertifikats.
- **Optimist und 420er gegen ILCA 7** über die RYA Portsmouth Numbers 2025 (1640, 1107, 1100): die Zeit auf einer Bahn auf und ab bei 10 kn auf 12 %.
- **Hobie 16 und Fahrtenyacht** in Bereichen [G, zu prüfen]. Die Fahrtenyacht bleibt unter ihrer Rumpfgeschwindigkeit.

Außerdem:

- Krängung gegen die Handrechnung sin φ = M / (W·GM)
- der Kat höchstens W·B/2
- Abdrift aus der Querkraft
- die Bewegung über die Zeit erreicht die Polare (auf 8 %)
- eine Wende meldet `onTack`, kostet Fahrt und das Boot läuft wieder an
- beschränkt, wiederholbar, im Wind stehend bleibt das Boot stehen

Seit Phase 8 (50 Prüfungen):

- Passat bei 16 kn höchstens 6 bis 11 kn, am Wind nicht unter 60° [G]; Gaffelschoner wendet über mehr als die J/70; Kite 12 m² bei 16 kn: Kreuzwinkel 45° bis 66°, bis 18 bis 30 kn [G]
- Polare des Hobie 16 bei 16 kn steigt von 40° bis zur Spitze ohne Einbruch
- Halse: der Baum geht über, ein `onGybe`; Wende: die Krängung wechselt das Vorzeichen
- Bö von 10 auf 22 kn: die geübte Crew fiert und bleibt aufrecht, die ungeübte kentert, wird aufgerichtet und läuft wieder; die J/70 kentert auch in 30 kn nicht
- Regatta: 10 J/70, zwei Runden auf 600 m, alle im Ziel, Signale 5–4–1–0, der Erste in der Zeit der Polaren plus Manöver (bis 45 % darüber), alle wenden, Ausweichmanöver nach den Regeln, höchstens drei Berührungen je Boot, gleiche Reihenfolge bei gleichem Startwert; Abdeckung in Lee

## Grenzen

- Die Beiwerte von Gaffel, Rahsegel und Kite sind gestaltet [G] und nur gegen Bereiche geprüft, nicht gegen Messungen; der Kite fliegt nicht wirklich (sein Flug steckt in einem Faktor).
- Wellenwiderstand und Gleiten sind zwei Formbeiwerte je Klasse, keine Rumpfserie. Seegang bremst noch nicht.
- Der Wind gilt am Boot, ohne Höhenprofil; die Abdeckung der Regatta ist ein Kegel, keine Strömung um das Segel.
- Regatta: kein Raum an der Tonne (Regel 18), keine Strafkringel; an den Tonnen kommt es zu Berührungen (die Rümpfe werden dann auseinandergeschoben). Kein Wechsel der Windrichtung, auf den die Taktik antwortet.
- Die Halse eines Kielboots ist zahm; Patenthalsen werfen nur Jollen und Kats wirklich auf die Seite.
- `BoatShape` ist gestaltet: Linien des Rumpfs und Schnitt der Segel sind nicht die echten der Klassen.

## Seit welcher Phase

- **Timmendorf Phase 1:** Hülle nach `muster`.
- **Timmendorf Phase 7:** Aerodynamik, Hydrodynamik, Gleichgewicht, Polaren, Bewegung über die Zeit, Takelagen für Optimist, ILCA, 420er, Hobie 16, J/70 und Fahrtenyacht; `BoatShape`, `gl/SailGl`; Polaren gegen ORC und Portsmouth geprüft. In `buoyancy` kam dazu der `SailHull` (Rumpf, der unter dem Segelmoment krängt).
- **Timmendorf Phase 8:** Segelpläne (Gaffel, Rahsegel, Kite), Luftwiderstand, stetige Flachheit (Polare ohne Zacken), Krängung mit Vorzeichen, Halse mit Baum, Kentern und Aufrichten, Reflex der Crew, Fahrt aufbauen, `Regatta` mit Startverfahren, Taktik, Wegerecht und Abdeckung; `BoatShape` für alle Takelagen mit Crew im Trapez; `gl/SailGl` nimmt fremde Dreiecke (Schiffe) mit und die Zeit für schlagendes Tuch.
- **Timmendorf Phase 9:** `Boat.downwindAllowed`; die Regatta setzt es vor dem Start auf false (kein Gennaker im Startverfahren).
- **Timmendorf Phase 17:** `Regatta.resume(time, gun, finished, race)` setzt eine gespeicherte Wettfahrt fort („Weiter wie zuletzt“); die Signale vor der Laufzeit kommen nicht noch einmal.
- **Timmendorf Phase 18** (Rückfluss nach Haithabu): zwei Rahsegler der Wikingerzeit als Datensätze, `Sail.LONGSHIP` (Langschiff nach Haithabu 1, Rahsegel 94,6 m²) und `Sail.KNARR` (Knorr nach Haithabu 3, 126 m²) in `Sail.VIKING` (nicht in `RIGS`). Sie tragen ab rund 55° am wahren Wind, raumschots bei 16 kn Wind 8 kn (Langschiff) und 7 kn (Knorr) [G]. Haithabu nimmt Fahrt, Krängung und Abdrift daraus für die Schiffe aus `longship`.
