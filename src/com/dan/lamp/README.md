# com.dan.lamp – Leuchten

Laternen, Poller und Mastleuchten mit echten Lichtströmen und Lichtstärkeverteilungen, Dämmerungsschalter und Halbnacht, Fensterlicht nach Uhrzeit und Nutzung, die Spiegelung der Lichter auf nassem Pflaster und auf dem Wasser. Für Timmendorf (Promenade, Seebrücke), später für Heidelberg und den Kneiphof (Gaslaternen).

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/lamp/`.

**Stand: Timmendorf Phase 12** (F18 A) – angelegt in Phase 11; seit Phase 12 eigene Lichtfarben (`Lamp.color`, `Lamp.point` für fremde Punktlichter wie die Bomben eines Feuerwerks) und `LampGl.limit` (zweiter Puffer für die Lichtpunkte aller Leuchten einer Stadt).

## Was es kann

- **Leuchte** (`Lamp`): Ort, Richtung zur Straße, Lampenart, Lichtstrom (lm), Betriebswirkungsgrad, leuchtende Fläche. Vier Verteilungen, jede so normiert, dass ∫ I dΩ den Lichtstrom ergibt: `STREET` (voll abgeschirmt, wirft längs der Straße, Spitze bei 65° vom Nadir wie IES Typ II), `ROUND` (Pilz- und Laternenleuchte, rund 8 % nach oben), `DOWN` (Poller), `GLOBE` (Kugel). `candela(richtung)`, `illuminance(p, n)`, `headLuminance(auge)`.
- **Lampenarten mit Spektrum** (`Spectra`): LED 3 000 K und 4 000 K (blaue Pumpe und Leuchtstoff), Amber-LED, Natriumdampf-Hochdruck (selbstabsorbierte D-Linie, um 2 200 K) und -Niederdruck (589 nm allein), Quecksilberdampf (Linien 405/436/546/578 nm), Halogen, Glühlampe, Gasglühlicht (Auerstrumpf, grünlich). Über CIE 1931 zu linearem sRGB; Farbtemperatur nach McCamy. Lichtausbeute und Anlaufzeit je Art [G nach Datenblättern].
- **Schalten:** Dämmerungsschalter mit Hysterese (an unter 35 lx, aus über 70 lx [G]), Halbnachtschaltung, Anlauf (Natriumdampf 90 s bis 63 %), Ausfall und Flackern.
- **Alle Leuchten** (`Lamps`): Gitter von 25 m, je Zelle nur Leuchten, deren Licht dort über 0,05 lx liegt; `illuminance`, `groundLux`. `important(auge, n)`: die n Leuchten mit dem größten I/d² für die Grafikkarte. Bausteine `row` (Reihe längs eines Wegs), `pier` (Seebrücke: Poller beidseits, Masten in der Mitte, Laternen am Kopf) und `gasLanterns` (Altstadt).
- **Fensterlicht** (`Windows`): Anteil brennender Fenster je Nutzung (Wohnen, Ferienwohnung, Hotel, Laden, Gastronomie, Büro) und Stunde [G, geformt nach der Zeitverwendungserhebung 2012/13]; jedes Fenster mit fester Schwelle und eigener Verschiebung um bis zu ±40 min, damit die Lichter nacheinander schalten. Farbe (2 700 / 3 000 / 4 000 K) und Leuchtdichte (5–40 cd/m², Schaufenster 100–300 cd/m²) fest je Fenster.
- **Nässe und Spiegelung** (`Wet`): GGX mit höhenkorrelierter Smith-Abschattung und Schlick-Fresnel (F0 0,02 für den Wasserfilm). Flächen Asphalt, Pflaster, Platten, Planken, Sand mit trockener und nasser Albedo und Rauigkeit – nass dunkler und glatter. Die langen senkrechten Lichtstreifen auf nasser Straße kommen von selbst (Prüfung: 6 : 1). Meer mit Rauigkeit aus dem Wind nach Cox und Munk.
- **Grafikkarte** (`gl/LampGl`): die wichtigsten Leuchten als Speicherpuffer (std430, Bindung 7); fremde Shader nehmen `LampGl.header()` und rufen `lampIrradiance(p, n)` und `lampSpecular(p, n, v, α, f0)` aus `lamp.glsl` – dieselben Verteilungen wie in Java. `drawHeads`: die Köpfe als Lichtpunkte, deren Fläche genau I/d² trägt.
- **Paketvertrag:** offene Felder, `seed`, Qualitätsstufen `LOW` bis `ULTRA` (32, 64, 128, 256 Leuchten auf der Karte).

## Benutzung

```java
Lamps l = new Lamps();
l.row(x0, z0, x1, z1, 0, 22, 4.0, Lamp.Type.LED_3000K, Lamp.Distribution.ROUND, 3200, 1);   // Promenade
l.pier(landX, landZ, kopfX, kopfZ, 3.0, 4.0);                                              // Seebrücke

// je Bild
l.step(dt, himmel.lux(), uhr.stunde());
LampGl gl = new LampGl();  gl.init(gl4);
gl.prepare(l, auge);  gl.upload(gl4);
int prog = program(LampGl.header() + pflasterShader, ...);   // lampIrradiance, lampSpecular
gl.bind(gl4, prog);
gl.drawHeads(gl4, view);

// Fenster
boolean hell = Windows.lit(Windows.Use.HOLIDAY, haus, fenster, 21.5, seed);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Lamp` | Eine Leuchte: Verteilung, Schalten, Lichtstärke, Beleuchtungsstärke |
| `Lamps` | Alle Leuchten, Gitter, Auswahl für die Karte, Bausteine Reihe, Seebrücke, Gaslaternen |
| `Spectra` | Spektren der Lampenarten, CIE 1931, lineares sRGB, Farbtemperatur |
| `Windows` | Fensterlicht nach Nutzung und Uhrzeit |
| `Wet` | GGX-Spiegelung, nasse Flächen, Meer nach Cox und Munk |
| `gl/LampGl` | Speicherpuffer, Baustein `lamp.glsl`, Lichtpunkte `lamp_head.vert/.frag` |
| `demo/LampPicture` | Promenade bei Nacht auf der CPU (Standbild ohne Grafikkarte) |
| `demo/LampDemo` | Fenster mit Reglern für Uhrzeit und Nässe; `--bild datei.png` schreibt ein Standbild |
| `demo/LampCheck` | Prüfung ohne Fenster (Lichtstrom, Verteilung, Straße nach DIN EN 13201, Spektrum, Schaltung, Fenster, Nässe, Darstellung), endet mit „Alles in Ordnung.“ |

## Grenzen

- Verteilungen geformt nach den IES-Typen, keine gemessenen LDT-/IES-Dateien (ein Leser könnte folgen).
- Keine Schatten; `lampIrradiance` sieht durch Wände.
- Fenster leuchten flach (kein Raum dahinter); die Spiegelung der Fenster im nassen Pflaster rechnet erst die Szene.
- Tagesgänge der Fenster gestaltet, nicht gemessen.

## Seit welcher Phase

- **Timmendorf Phase 13:** Befund aus Phase 12 behoben – `lamp_head.frag` bekam ein zweites `#version` vorangestellt, die Lichtpunkte starteten nicht; `LampGl.sources()` und eine Prüfung in `LampCheck`.
- **Timmendorf Phase 12:** `Lamp.color`, `Lamp.rgb()`, `Lamp.point(…)`, `LampGl.limit`; die Vorführung mit LED 4 000 K an der Promenade.
- **Timmendorf Phase 11:** angelegt (F18 A): Leuchten, Spektren, Schalten, Fensterlicht, nasses Pflaster und Wasser, Grafikkarte.
