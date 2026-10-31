# com.dan.fog – Seenebel und Nebel über Gelände

Eine flache Nebelschicht über dem Wasser, die in Bänken über die See zieht, mit Licht von Sonne, Mond und Himmel, dazu Nebelschallsignale mit Kennung. Das Package braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/fog/`.

## Was es kann

- **Sicht nach Koschmieder:** Im Kern einer Bank ist der Auslöschungskoeffizient σ = 3,0 / V (5 % Kontrast). `visibilityAt(x, y, z)` gibt die waagerechte Sicht am Ort, `transmittance(a, b)` die Durchlässigkeit eines Wegs.
- **Bänke, die ziehen:** ein Rauschfeld in der Fläche (drei Oktaven Wertrauschen, leicht verwirbelt), das mit 80 % des Winds zieht. Der Anteil bedeckter Fläche (`coverage`) wird über eine Eichtabelle getroffen: 20/45/70 % gewollt ergeben 24/47/71 % gemessen.
- **Obergrenze:** im Mittel `top` Meter über dem Wasser, von Ort zu Ort ±25 %, weich auslaufend. Darüber ist klare Luft; ein Turm von 30 m ragt aus einem Nebel von 18 m heraus.
- **Untergrenze (seit Haithabu Phase 3):** `base` Meter über dem Wasser, darunter ist es klar, mit einem weichen Übergang von 2 · max(1,5 m, 0,3 · base). Damit hebt sich Strahlungsnebel am Morgen als Schicht vom Wasser ab, bevor er sich auflöst. 0 lässt alles wie bisher.
- **Schwaden:** Rauschen in der Dichte, das mit der Höhe versetzt ist.
- **Licht im Nebel:** Sonne und Mond mit Vorwärtsstreuung (Henyey-Greenstein, g = 0,75) und einem Anteil vielfach gestreuten Lichts, gedämpft durch den Nebel über dem Punkt; dazu der Himmel. Gegenlicht leuchtet, tief im Nebel wird es grau.
- **Streufeld für fremde Lichter:** `scatter(x, y, z)` in 1/m, damit ein Leuchtfeuer seinen Strahl im Nebel zeigen kann (Nebeltröpfchen streuen fast alles).
- **Nebelsignale:** `Signal` mit Kennung wie im Leuchtfeuerverzeichnis: `Horn Mo(R) 30s`, `Horn(2) 20s`, `Tyfon 120s`. Ein Signal tönt, solange die Sicht an seinem Ort unter 2 sm liegt, und meldet jeden Ton mit Dauer über `onHorn`.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)` (Seenebel bis Bft 3, Fetzen bis 5, ab 7 reißt ihn der Wind auf), dazu `haze()`, `banks()`, `dense()`, `shallow()`.
- **Reproduzierbar:** gleicher `seed`, gleiche Bänke. **Stufen:** `Quality.LOW` bis `ULTRA` regeln die Schritte des Blicks (8 bis 32) und die Karte der Bänke (256² bis 768²).

## Benutzung

```java
Fog fog = Fog.banks();
fog.visibilityM = 200;          // Sicht im Kern einer Bank
fog.coverage = 0.6;             // 60 % der Fläche
fog.top = 50;                   // Obergrenze über dem Wasser
fog.windSpeed = 3; fog.windFromDeg = 240;
fog.signals.add(new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0));
fog.onHorn = (x, y, z, strength, seconds, s) -> playHorn(seconds);
fog.step(dt);                    // je Bild
double v = fog.visibilityAt(eyeX, eyeY, eyeZ);

// Grafik: Karte der Bänke, dann ein Bilddurchgang über die fertige Szene (Tiefe als Kopie)
fogGl.update(gl, fog.snapshot(), eye);
fogGl.draw(gl, fog.snapshot(), view, light);   // Mischung ONE, SRC_ALPHA
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Fog` | Kern: Eingänge, Presets, Dichte, Sicht, Durchlässigkeit, Eichung der Bedeckung, Ziehen, Signale |
| `Signal` | Kennung lesen, Takt der Töne, tönt es gerade |
| `gl/FogGl` | Karte der Bänke (RG16F), Blick durch den Nebel als Bilddurchgang, Selbsttest |
| `gl/fog_common.glsl` | das Feld wie `Fog.density` (gleicher Ganzzahl-Hash) |
| `gl/fog.frag`, `fog_map.frag`, `check.frag` | Blick, Karte, Selbsttest |
| `demo/FogDemo` | Fenster mit Reglern; `--bild datei.png [breite]` schreibt sechs Lagen und den Takt dreier Signale |
| `demo/FogScene` | eigene Szene ohne Dreiecke: See, Turm, Pfähle bis 2 km |
| `demo/FogCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |
| `TerrainFog` (mit `Ground`, `ColdPool`) | seit Brocken Phase 6: Nebel über Gelände (Nebelmeer, Talnebel, Stau am Hang) |
| `gl/TerrainFogGl`, `gl/terrain_fog*.glsl/.frag` | seit Brocken Phase 6: Karte und Bilddurchgang dazu |
| `demo/TerrainFogDemo` | seit Brocken Phase 6: Schnitte ohne Grafikkarte |
| `gl/terrain_fog_march.glsl`, `gl/terrain_fog_up.frag` | seit Brocken Phase 7: der Blick als Funktion, Hochrechnen aus halber oder drittel Auflösung |

## Demo und Selbsttest

- `com.dan.fog.demo.FogDemo` zeigt See, Turm und Pfähle im Nebel. Die Pfähle stehen in 60 m bis 2 km; daran sieht man die Sicht. Am Turm tönt `Horn Mo(R) 30s`.
- `com.dan.fog.demo.FogCheck` prüft Koschmieder, Obergrenze, Eichung, weiche Ränder, das Ziehen, Presets, seed, die Kennungen und dass Signale nur im Nebel tönen.
- `FogGl.selfCheck` rechnet die Dichte an 64 Orten auf der Grafikkarte und vergleicht mit dem Prozessor.

## Grenzen

- `Fog` ist eine Schicht über einer ebenen See; Gelände kennt er nicht. Über Land liegt er in derselben Höhe über dem Wasserspiegel. Für Nebel, der das Gelände kennt, gibt es seit Brocken Phase 6 `TerrainFog` (unten).
- Licht von Lampen im Nebel rechnet das Package nicht selbst. Es gibt sein Streufeld her, ein Leuchtfeuer muss damit seinen Strahl zeichnen.
- Vielfachstreuung ist genähert (fester Anteil, gedämpftes Himmelslicht), nicht gerechnet.
- Die Tonlängen der Signale (Punkt 1 s, Strich 3 s, Gruppe 2 s, Tyfon 5 s) sind gestaltet; Periode und Muster kommen aus der Kennung. Den Klang macht ein anderes Package.

## Seit Brocken Phase 5: Wind von außen

- Schnittstelle `Wind` (nur JDK): `double[] at(x, y, z)` gibt den Wind am Ort als {Ost, oben, Süd} in m/s; die App verdrahtet etwa `fog.wind = feld::windAt`.
- Gesetzt, ziehen die Bänke mit 80 % des Winds am Ort `windAnchor` (etwa die Mitte der Szene oder das Auge); `drift()` gibt Richtung und Geschwindigkeit. Der Nebel über Gelände (Stau, Hangnebel, Kaltluft) folgt in Brocken Phase 6.
- Ohne `wind` (null) bleibt alles wie bisher.

## Seit Brocken Phase 6: Nebel über Gelände (`TerrainFog`)

Drei Teile, die sich addieren; dazu Schwaden, die mit dem Wind ziehen.

- **Nebelmeer:** Hochnebel unter einer Inversion zwischen `seaBase` und `seaTop` (m über dem Bezug, nicht über dem Boden), die Obergrenze wogt um ± `seaRough`. Kuppen ragen heraus, Täler liegen darin. Das Ziel kommt aus der Lage (`seaBaseTarget`, `seaTopTarget`; NaN = kein Meer). Am Tag **löst es sich von unten auf**: die Untergrenze steigt mit `burnRate` (260 m/h [G]) · `heating`, oben trocknet es langsam ab; nachts sinkt die Untergrenze zum Ziel zurück (3 h). Dünner als 25 m, vergeht es (15 min). `step(dt)` rechnet das, `settle()` setzt es sofort.
- **Talnebel:** wo ein Kaltluftsee liegt (Schnittstelle `TerrainFog.ColdPool`), füllt Nebel ihn bis zum Anteil `valleyFill` seiner Tiefe, über dem Boden gemessen.
- **Stau am Hang:** liegt eine Wolkendecke mit der Basis `capBase` auf dem Berg, reicht sie am Luvhang tiefer: um `liftPerMs` (110 m [G]) je m/s Aufwind 40 m über Grund (Schnittstelle `Wind`), höchstens `capBelt`. Die Decke zeichnet die App mit ihren Wolken; hier entsteht nur der Gürtel darunter (Hangnebel).
- **Karte:** Boden (Schnittstelle `TerrainFog.Ground`; verschachtelt, damit kein zweites `Ground` neben dem von `forest` steht), Kaltluft und Gürtel über `mapRect` (`buildMap(n)`); außerhalb gibt es nur das Nebelmeer. `mapVersion` erhöhen, wenn sich Gelände, Kaltluft oder Wind geändert haben.
- **Abfragen:** `density`, `visibilityAt`, `transmittance`, `describe()`; die Dichte rechnet `gl/terrain_fog_common.glsl` Zeile für Zeile gleich.
- **Grafik:** `gl/TerrainFogGl` baut die Karte im eigenen Faden (RGBA32F, 128² bis 256² nach `TerrainFog.Quality`) und zeichnet einen Bilddurchgang über die fertige Szene (Tiefe als Kopie, Mischung `ONE, SRC_ALPHA`). Abgetastet wird nur das Stück zwischen der tiefsten und höchsten Stelle mit Nebel, mit Schritten, die vom Eintritt an wachsen (der erste rund 3 m), bis zur Kimm (`View.maxDistance`, 80 km); die Erdkrümmung der Szene (`View.curvature`) wird zurückgerechnet. `selfCheck` vergleicht 64 Orte mit dem Prozessor.
- **Demo:** `demo/TerrainFogDemo --bild datei.png` zeichnet Schnitte durch einen Testberg (Nebelmeer am Morgen, nach zwei und vier Stunden Sonne, Talnebel, Stau mit Westwind), ohne Grafikkarte. `FogCheck` prüft alle Teile.

```java
TerrainFog nebel = new TerrainFog();
nebel.ground = gelaende::height;
nebel.cold = (x, z) -> windfeld.coldPoolAt(x, z, 0);
nebel.wind = windfeld::windAt;
nebel.mapRect = new double[]{x0, z0, 12_800};
// je Bild aus der Lage
nebel.seaBaseTarget = 450; nebel.seaTopTarget = 850;   // Inversion, sonst NaN
nebel.heating = sonne;                                 // −1 … 1
nebel.step(dt);
terrainFogGl.update(gl, nebel);
terrainFogGl.draw(gl, nebel.snapshot(), view, light);
```

Grenzen: Der Nebel ist ein Feld nach Regeln, keine Strömung; das Nebelmeer fließt nicht über Pässe, der Talnebel nicht hangab. Der Gürtel unter der Decke hängt an der Spalte über dem Boden und kann über einem Tal schweben. Licht im Nebel wie bei `Fog` (vielfach gestreut genähert), unter einer Decke nur gedämpft (`Light.sunUnderCap`).

## Seit Brocken Phase 7: Licht im Nebel und kleinere Auflösung

- **Auflösung:** Der Bilddurchgang rechnet nach der Stufe in voller (`ULTRA`), halber (`HIGH`) oder drittel Auflösung
  (`MEDIUM`, `LOW`; `Quality.scale`) einen festen Bildpunkt je Block in ein eigenes Ziel (Streulicht und Entfernung) und
  rechnet ihn kantentreu hoch (`gl/terrain_fog_up.frag`): die vier nächsten Texel nach Lage und danach, wie gut ihre
  Entfernung zu der passt, die die eigene Fläche dort hätte. Passt keiner (Kante einer Kuppe vor dem fernen Meer) oder
  springt die Durchlässigkeit zwischen ihnen um mehr als 0,3 (das Ufer des Nebels am Hang, flach gesehen), wird der
  Bildpunkt selbst gerechnet – nur an Kanten, rund 2 % des Bilds. `View.scale` setzt die Auflösung von Hand. Auf Mesa
  kostet der Durchgang in drittel Auflösung rund ein Viertel der vollen.
- **Phasenfunktion von außen:** `Light.phaseTexture` (u = Streuwinkel / 180°, RGB auf 4π normiert), etwa die Tröpfchen
  nach Mie aus `com.dan.sky.Droplets`; dann zeigt der Nebel Glorie, Nebelbogen und Aureole. 0 = Henyey-Greenstein wie
  bisher. Einfach gestreutes Licht (Anteil 0,3) folgt der Tabelle, vielfach gestreutes (0,12) bleibt gleichmäßig.
- **Schattenwerfer:** `Light.casters` (bis 8 Kapseln, `TerrainFog.CASTER_STRIDE` = 7 Werte: Achse, Halbmesser);
  `TerrainFog.person(x, y, z, größe)` gibt einen stehenden Menschen (Rumpf und Kopf). In der Schattensäule fehlt das
  einfach gestreute Sonnenlicht; ihr Rand wird mit dem Abstand um s · tan(Sonnenhalbmesser) weich (Kernschatten eines
  Menschen rund 54 m). Steht das Auge im Kopf, liegt der Schatten genau im Gegenpunkt, mitten in der Glorie: das
  Brockengespenst. `TerrainFog.sunShadow` rechnet dasselbe auf dem Prozessor, `selfCheck` vergleicht.
- **Schatten des Geländes:** `Light.sunHeightTexture` mit `sunHeightRect`: eine Karte (Kanal G) der Höhe, ab der die Sonne
  über das Gelände scheint. Darunter fehlt das direkte Licht im Nebel, und der vielfach gestreute Anteil sinkt auf 30 %:
  der Schatten eines Bergs auf dem Nebelmeer. Die Karte rechnet die App.

```java
TerrainFogGl.Light l = new TerrainFogGl.Light();
l.phaseTexture = tropfenGl.texture();                         // com.dan.sky.gl.DropletGl
l.casters = TerrainFog.person(auge[0], grund, auge[2], 1.83); // das Auge im Kopf
l.sunHeightTexture = schattenKarte; l.sunHeightRect = rechteck;
terrainFogGl.draw(gl, nebel.snapshot(), view, l);
```

Grenzen: Die Schatten gelten nur dem direkten Licht; vielfach gestreutes Licht füllt sie nicht von der Seite her auf.
Der Mond nimmt die Phasenfunktion, wirft aber keine Schatten. An sehr dünnen Kanten (einzelne Bildpunkte) kann das
Hochrechnen um einen Texel danebenliegen.
