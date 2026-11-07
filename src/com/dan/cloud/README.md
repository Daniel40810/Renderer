# com.dan.cloud – Wolken

Das Paket rechnet Wolken über dem Meer: Schönwetter-Cumulus bis zum Gewitterturm mit Amboss, Schichtwolken bis zur geschlossenen grauen Decke, darüber ein hoher Schleier. Die Bedeckung stellt man in Achteln ein, wie sie ein Beobachter von unten sieht; die Wolken ziehen mit dem Wind und wandeln langsam ihre Form. Dazu kommen der Schatten auf dem Wasser und auf der Szene, ein Regenmaß unter dichten Stellen und ein Faktor für die Augenanpassung unter bedecktem Himmel.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet die Wolken mit JOGL (OpenGL 4.3, ohne Compute-Shader) als Raymarching in halber oder viertel Auflösung. Das Paket kennt weder den Leuchtturm noch andere Pakete, auch nicht `com.dan.sky`: das Licht kommt als Zahlen herein. Zum Herauskopieren reicht `src/com/dan/cloud/`; nur `demo/CloudDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Arten:** Cumulus, Stratocumulus, Stratus, Nimbostratus, Cumulonimbus (`CloudType`) mit Basis, Dicke, Türmen, Zellgröße und Dichte nach dem Wolkenatlas der WMO, Basis über See eher niedrig.
- **Form:** Wetterkarte (512², Zellen in Gruppen mit Lücken, große Flecken für Schichten), Grundform aus Perlin-Worley (128³), Fransen aus Worley (32³), nach Schneider (Horizon Zero Dawn, 2015). Haufen haben eine flache Basis und werden nach oben schmaler; Gewittertürme bleiben bis oben dicht, der Amboss sitzt nur über starken Türmen und reicht eine Drittelzelle darüber hinaus.
- **Bedeckung in Achteln:** Eine Eichtabelle (`Clouds.areaFraction`) setzt die Achtel in den Flächenanteil der Karte um, gemessen mit `observedCoverage` an 256 Orten (Richtungen bis 70° vom Zenit, optische Dicke ab 1). Die Prüfung misst an anderen Orten nach: alle Arten auf ±0,15 Achtel.
- **Licht:** Henyey-Greenstein mit zwei Keulen (Silberrand vorn, helle Seite hinten), Mehrfachstreuung in vier Oktaven (Wrenninge 2015), ab der zweiten mit durchmischter Farbe (halb Sonne, halb Himmel), Pulvereffekt an den Rändern, Himmelslicht von oben und Meerlicht von unten. Das Licht des Gestirns kommt für acht Höhen (0 bis 14 km) herein, so wird die Kuppe eines Turms abends noch rot, wenn die Basis schon im Erdschatten liegt. Nachts ist der Mond das Licht.
- **Dunst:** Wolken verblassen mit der Entfernung (Dunst mit 1,2 km Skalenhöhe wie in `com.dan.sky`); eine geschlossene Decke bleibt bis zur Kimm grau, statt durchsichtig zu werden.
- **Zug:** Drift mit dem Wind, Formwandel über die Zeit (`evolve`). `step(dt)` im Lauf, `setTime(t)` für Standbilder; beide ergeben denselben Stand.
- **Schatten:** Schattenkarte 256² über 24 km um das Auge, auf Texel gerastet (kein Flimmern beim Fahren). `Clouds.shadow` rechnet denselben Wert auf dem Prozessor (Licht am Turm, Prüfungen).
- **Hohe Schicht:** dünner Schleier in 6,5 km mit Fasern, eigene Bedeckung 0..1.
- **Stufen:** `FLAT` (2.5D, eine Fläche), `MEDIUM` (1/4 Auflösung, bis 72 Schritte), `HIGH` (1/2, bis 96), `ULTRA` (1/2, bis 128). Die Schrittweite wächst mit der Entfernung, leere Luft wird mit größeren Schritten übersprungen; zitternde Startpunkte und die Wiederverwendung des letzten Bilds glätten das Rauschen.

## Benutzung

```java
Clouds clouds = Clouds.fair();             // 3/8 Cumulus; overcast(), storm()
clouds.oktas = 5;
clouds.type = CloudType.STRATOCUMULUS;
clouds.highCover = 0.3;
clouds.windSpeed = 12;                     // in Wolkenhöhe
clouds.windFromDeg = 260;
clouds.quality = Clouds.Quality.MEDIUM;

// je Bild
clouds.step(dt);
Clouds.Snapshot s = clouds.snapshot();
double licht = Clouds.shadow(s, x, z, richtungZurSonne);   // 0..1 an einem Ort auf Meereshöhe

// zeichnen (im GL-Faden)
CloudGl gl = new CloudGl();
gl.init(gl4);
CloudGl.Light l = new CloudGl.Light();     // vorbelichtet: Richtung, Farbe in 8 Höhen, Himmel, Meer, Dunst
gl.update(gl4, s, l, auge);                // Schattenkarte
gl.draw(gl4, s, l, view);                  // nach dem Himmel, vor Meer und Szene, ins gebundene HDR-Ziel
gl.drawPanorama(gl4, s, l, auge);          // ins gebundene Rundumbild (für Spiegelungen)
int schatten = gl.shadowTexture();         // R16F, Rechteck aus gl.shadowRect()
```

**Vorbelichtung:** Wie bei `com.dan.sky` kommen alle Lichtwerte mit der Belichtung multipliziert herein. `brightnessFactor()` sagt, wie viel dunkler ein bedeckter Himmel ist (für die Augenanpassung).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `CloudType` | Arten mit ihren Maßen |
| `Noise` | Rauschtexturen (Prozessor, reproduzierbar, kachelbar) |
| `Clouds` | Eingänge, Eichung, Dichte, Schatten, Regen, Bedeckung von unten |
| `gl/CloudGl` | Texturen, Schattenkarte, Raymarching, Wiederverwendung, Auflegen, Rundumbild, Selbsttest |
| `gl/cloud_common.glsl` | Dichte Zeile für Zeile wie `Clouds.density` |

## Demo und Selbsttest

- `com.dan.cloud.demo.CloudDemo` zeigt die Wolken über einer glatten See mit einem schlichten eigenen Himmel. Regler für Bedeckung, Art, hohe Schicht, Wind, Sonnenhöhe und -richtung, Sicht und Stufe, Zeitraffer, „0 → 8/8“ (die Bedeckung läuft langsam hin und her) und vier Voreinstellungen. Unten steht die gemessene Zeit der Wolken auf der Grafikkarte.
- Ohne Fenster rechnet `CloudDemo --bild datei.png [breite]` zwei Reihen (Cumulus 0/8 bis 8/8; die fünf Arten und einen Abend) und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.cloud.demo.CloudCheck` prüft Rauschen (Prüfsummen, kachelbar), die Bedeckung von unten für alle Arten, Höhen und Amboss, den Zug mit dem Wind, Schatten, Regen, Helligkeit und die Uniforms; Ende „Alles in Ordnung.“.
- `CloudGl.selfCheck` liest die Schattenkarte zurück und vergleicht 64 Punkte mit dem Prozessor (Median und Ausreißer an Wolkenrändern).

## Grenzen

- Eine Schicht zur Zeit (plus der hohe Schleier). Cumulus unter einer Altostratus-Decke oder zwei Stockwerke Stratocumulus gibt es nicht.
- Kein Licht zwischen Wolken (Lichtstrahlen, „God rays“).
- Blitzlicht (seit Phase 10, `Light.flashPos`, `flashRgb`, `flashCount`, `flashDiffusion`) wird beim Auflegen am mittleren Ort der Wolke im Blick gerechnet: Licht, das durch die Wolke wandert (Diffusion), und Licht von unten. Es zieht nicht nach (ein Blitz dauert nur wenige Bilder), wirft aber keine Schatten in der Wolke.
- Regen fällt noch nicht sichtbar; `Clouds.rain` liefert das Maß für Phase 7.
- Die Schattenkarte deckt 24 km; dahinter gilt der Rand.

## Seit Glacier Phase 3: Lentikularis

`Clouds.lens` (0..1) und `Clouds.lensFromDeg`: Altocumulus lenticularis im Lee eines Gebirges als bis zu drei
Linsenlagen ab 4,6 km (Abstand 320 m, Stapel). Längs des Winds liegen die Linsen auf den Kämmen einer stehenden
Leewelle (Wellenlänge 9 km), quer dazu als Mandeln aus einem ganzzahligen Rauschen je Kamm (bitgleich in Java und
GLSL). Die Linsen ziehen nicht mit dem Wind. `Clouds.lensTau` gibt die optische Dicke ohne Grafikkarte; Schatten
(`shadow`, `shadow.frag`), Hauptbild und Rundumbild (`clouds.frag`) rechnen sie mit. Die Prüfung misst Anteil,
Stapel, Ortsfestigkeit und die Streckung quer zum Wind. Rückfluss in Phase 16.

## Seit Brocken Phase 3: Wolken im Gebirge

- **Basis von außen:** `Clouds.baseHeight` (m über dem Meer, NaN = die der Art) hebt die untere Schicht über das Gelände oder legt sie an den Berg; Dicke, Türme und Amboss bleiben die der Art. `Snapshot.base()` und `top()` geben die Grenzen.
- **Schatten in der Höhe:** `Clouds.shadowHeight` legt die Fläche fest, für die die Schattenkarte rechnet (0 = Meeresspiegel wie bisher). Die Karte ist jetzt RG16F: Kanal R wie bisher, Kanal G nur hohe Schicht und Linsen, für Punkte über der unteren Schicht (das Nebelmeer: der Gipfel in der Sonne). `Clouds.shadow(s, x, y, z, l)` und `upperShadow` rechnen dasselbe am Punkt.
- **Wolken vor dem Gelände:** `CloudGl.View.depthTexture` (Tiefe der Szene, 0 = ohne). Mit Tiefe zeichnet die App die Wolken nach der Geometrie: der Strahl endet am Gelände, und das Auflegen nimmt je Bildpunkt nur die Texel, deren Entfernung zur Geometrie zu seiner passt (Hochrechnen nach der Tiefe, Kanal G des Entfernungsbilds). Die ferne Decke liegt auch über fernem Gelände und ist von oben zu sehen. Ohne Tiefe bleibt alles wie bisher.
- Kurze Wege durch die Wolke (Nebel vor dem Hang, das Auge in der Wolke) laufen mit mindestens sechs Schritten; vorher fiel der einzige Schritt je nach Zittern hinein oder heraus.
- Grenzen: das Auflegen nach der Tiefe arbeitet in der Auflösung der Wolken (1/4 auf „Mittel“); an feinen Graten bleibt ein Saum. Die Dicke der Schicht kommt weiter aus der Art.

## Seit LituyaBay1958 W7: Wolken am Gelände

Bisher kannte die Schicht nur eine Basis (seit Brocken Phase 3). Jetzt kann sie das Gelände kennen: `Clouds.terrain` (`Clouds.Terrain`: Höhen n × n über ein Quadrat, Meer 0). Daraus rechnet das Paket einmal eine **Strömungskarte** (`Oro`, 256², Gelände über `terrainSmooth` = 700 m geglättet, Steigung, Gipfel) und mit ihr vier Dinge, die es über dem Meer nicht gibt:

| Eingang | Was | Wie |
|---|---|---|
| `bank`, `bankBase`, `bankTop`, `bankReach` | **Wolkenbank am Hang** | zwischen Unter- und Obergrenze, bis `bankReach` über dem geglätteten Gelände, nur wo es steigt (Steigung ab 3 %, voll ab 14 %), am Luvhang dichter (Steigung in Windrichtung), mit Lücken aus der Wetterkarte, die langsam am Berg entlangziehen; Form wie Stratocumulus |
| `cap`, `capBase`, `capReach` | **Kappe über den Gipfeln** | glatte Haube um alles, was das Kondensationsniveau `capBase` erreicht: vom Niveau bis `capReach` über die geglättete Hülle der Gipfel (Kanal A der Karte, seit Runde 1b geglättet), zum Rand der Hülle weich und flacher, 400 m nach Lee versetzt |
| `convection` | **Quellwolken über Land** | das Feld der Wetterkarte über hohem Land angehoben, über dem Meer abgesenkt: Haufen bauen sich über dem Gebirge auf, über kaltem Meer bleibt es klar (die Achtel gelten im Mittel, nicht mehr überall) |
| `lee` | **Lücken im Lee** | das Feld im Lee der Kämme abgesenkt (Föhnlücke) |

Bank und Kappe stehen am Berg (die Luft strömt durch), ihre Form zieht mit dem Wind. Dichte, Schatten (`shadow`, `upperShadow`, Kanal G nimmt seitdem die Kappe mit), Bedeckung (`coverage`) und Regen (`rain`) rechnen sie mit, Java und GLSL Zeile für Zeile gleich (`oroCover`, `oroShift`, `cloudShape`). Der Strahl läuft von der untersten Höhe der Bank bis über die Kappe (`Snapshot.shellLow`, `lowHigh`, `shellHigh`).

Dazu für Szenen im Gebirge:
- `CloudGl.shadowSize`, `shadowN`, `shadowCenter`: Schattenkarte über die ganze Szene statt 24 km um das Auge (LituyaBay1958: 72 km, 384², Mitte der Szene), damit Schatten auf Hängen in 30 km Entfernung liegen.
- `CloudGl.historyBlend`: Anteil des letzten Bilds (0,85 wie bisher); im schnellen Zeitraffer weniger, sonst Schlieren.
- `CloudGl` zeichnet auch dann, wenn nur Bank oder Kappe da sind (0/8 Schicht).

```java
clouds.terrain = new Clouds.Terrain(x0, z0, 66_000, 1024, hoehen);   // m über dem Meer, Meer 0
clouds.bank = 0.9; clouds.bankBase = 120; clouds.bankTop = 760;      // Regen am Berg: Wolken hängen tief in den Hängen
clouds.cap = 0.85; clouds.capBase = 3900;                            // Kappe über dem Gipfel
clouds.convection = 0.9;                                             // Quellwolken über der Kette, über dem Meer kaum
cloudGl.shadowSize = 72_000; cloudGl.shadowN = 384; cloudGl.shadowCenter = new double[]{cx, cz};
```

`CloudCheck` prüft an einem Testberg (Gauß, 4 600 m): Bank am Luvhang dicht, im Lee dünn, über dem Meer keine; Kappe über dem Gipfel ab dem Kondensationsniveau, nicht darunter und nicht 8 km daneben; ihr Schatten in Kanal G; Quellwolken über Land (Berg 0,82 → 0,97, Meer 0,46 → 0,20), Lücken im Lee; ohne Stärken gleich der Schicht (Abweichung 0 an 400 Orten).

Grenzen: Die Strömung ist eine Regel nach der Steigung des geglätteten Geländes, keine gerechnete Strömung (für die gibt es `com.dan.wind`). Die Kappe kennt keine Bannerwolke im Lee eines einzelnen Gipfels. Ohne `terrain` (oder ohne Stärken) bleibt alles wie bisher.

## Seit LituyaBay1958 W7 Runde 1b/1c: Wolken in der Ferne, im Dunst und im Spiegel

Alles abschaltbar, die Vorgaben lassen andere Szenen, wie sie waren.

| Eingang | Vorgabe | Was |
|---|---|---|
| `CloudGl.Light.hazeKeep`, `hazeColor`, `hazeToward`, `hazeAway` | 0 | Ferne Wolken gehen in den Dunst über statt durchsichtig zu werden (1). Die Farbe des Dunsts quer zum Gestirn, zu ihm hin und von ihm weg (Strahldichte knapp über der Kimm, vorbelichtet), dazwischen quadratisch nach dem Azimut. Über Gelände nötig: sonst schien der Hang durch eine Wolke in 25 km (Proben 702, 706) |
| `CloudGl.teleMax` | 1 | Mit langer Brennweite ist ein Bildpunkt in der Ferne klein; die Schritte wachsen dann um bis zu diesen Faktor langsamer, die Zahl der Schritte bis 2× (aus der Projektion, Bezug 60° und 540 Zeilen) |
| `Clouds.smoothNoise` | false | Grundform und Feinheit mit stetiger Ableitung zwischen den Texeln mischen (Anteile 3f² − 2f³, auf der Grafikkarte über versetzte Koordinaten, Quilez; Java gleich in `Noise.sample3`). Stark vergrößert zeigte das lineare Mischen nach dem Schwellen der Form die Texelkanten als Gitter aus feinen senkrechten Streifen und waagrechten Bändern (200 mm auf 25 km: ein Texel der Feinheit über fünf Bildpunkte). `CloudCheck`: Knicke an den Texelgrenzen 15,6× → 1,1× so stark wie dazwischen |
| `CloudGl.geometryHistoryTest` | false | Beim zeitlichen Mischen das letzte Bild nur nehmen, wo es dieselbe Geometrie sah (Entfernung zur Szene auf 10 %, Himmel bleibt Himmel; die Entfernungen werden dafür im Wechsel in zwei Puffern gehalten). Sonst legte das zitternde Bild an Kämmen und Baumspitzen vor fernen Wolken deren Licht als hellen Saum vor die Geometrie |
| `CloudGl.View.slot`, `geometryOnly` | 0, false | Eine zweite Ansicht mit eigener zeitlicher Mischung (bis `SLOTS` = 4), etwa das Spiegelbild im Wasser; `geometryOnly` legt nur über Geometrie auf (der Himmel und die Deckkraft des Ziels bleiben, im Spiegel trägt das Rundumbild den Himmel samt Wolken). Das Ziel ohne Tiefe binden, die Tiefe als Textur geben |

```java
licht.hazeKeep = 1; licht.hazeColor = quer; licht.hazeToward = zurSonne; licht.hazeAway = vonDerSonne;
cloudGl.teleMax = 4; cloudGl.geometryHistoryTest = true; clouds.smoothNoise = true;
// Spiegelbild: Ziel ohne Tiefe, gespiegelte Projektion, Auge unter dem Spiegel
CloudGl.View v = new CloudGl.View(); v.viewProj = vpR; v.invViewProj = invR; v.eye = augeUnten;
v.depthTexture = spiegelTiefe; v.slot = 1; v.geometryOnly = true;
cloudGl.draw(gl, snapshot, licht, v);
```

Die Kappe seit Runde 1b: Kanal A der Strömungskarte ist jetzt die geglättete Hülle der Gipfel (das Höchste der Nachbarschaft, danach zweimal geglättet). Ohne Glätten blieben die Zellkanten als senkrechte Wände in der Kappe stehen. `CloudCheck`: auf einer Linie quer durch den Rand der Kappe größter Sprung je 10 m 1,5 % der Bedeckung.

Grenzen: Die Wolken bleiben in der Ferne weich (halbe Auflösung, zeitliche Mischung); Quellwolken haben keine harten Blumenkohlkanten. Ein Feld aus vielen Haufen, das man auf 25 km von der Seite sieht, wirkt grau und dicht, nicht wie einzelne Türme.

**Seit LituyaBay1958 W8b:** `CloudGl.Light.gradation` (a, b, Normierung, Stärke; Vorgabe aus) – das Licht, das eine dicke Decke nach unten streut, nach der Blickrichtung abgestuft wie der CIE-Normhimmel (`com.dan.sky.CieSky`): zum Zenit heller, zur Kimm dunkler, bei gleicher Bestrahlungsstärke am Boden. Gilt für die Vielfachstreuung, das Umgebungslicht in der Wolke und die ferne Decke, nur für ein Auge unter der Basis.
