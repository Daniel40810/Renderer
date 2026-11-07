# com.dan.precip – Niederschlag

Das Paket lässt es regnen, wie es physikalisch regnet. Jeder Tropfen um das Auge ist ein Körper mit Größe, Geschwindigkeit und Schwingung, der mit seinem Luftwiderstand durch den böigen Wind fällt und auf Wasser, Fels oder Holz aufschlägt. Die Dichte ist die echte: Um das Auge schweben die größeren Tropfen in voller Dichte (bei 5 mm/h alle ab 1,2 bis 1,3 mm), die kleineren und die ferneren kommen aus Schichten mit derselben Verteilung, dahinter die Vorhänge der Schauer bis 8 km. Die Belichtung ist eine echte Größe: Ein Bildpunkt sieht einen Tropfen nur so lange, wie der Tropfen über ihn zieht. Das Licht im Tropfen kommt aus der Strahlenoptik einer Wasserkugel; daraus entstehen Glanz im Gegenlicht und Regenbogen von selbst. Auf dem Wasser laufen echte Kapillar- und Schwerewellen, spektral genau gelöst.

Seit LituyaBay1958 W7b (WF25 A). `com.dan.rain` bleibt unverändert für andere Szenen.

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet mit JOGL (GL 4.3, Compute-Shader). Das Paket kennt weder Himmel noch Wolken noch Meer: Licht, Rundumbild und Schattenkarte reicht die App, das Wellenfeld gibt es als Textur heraus. Zum Herauskopieren reicht `src/com/dan/precip/`.

## Physik (mit Quellen; [G] = gestaltet)

| Was | Wie | Quelle |
|---|---|---|
| Größenverteilung | N(D) = N0 e^(−ΛD), Λ = a R^b; Landregen, Niesel, Schauer; N0 so abgeglichen, dass die Verteilung genau R ergibt (für Landregen 0,85–0,89 des Literaturwerts) | Marshall und Palmer 1948, Joss u. a. 1968 |
| Fallgeschwindigkeit | bis 0,6 mm die Messung, ab 0,8 mm die Formel, dazwischen gemischt; auf 1,9 % genau gegen die Messung; dünne Luft (ρ0/ρ)^0,4 | Gunn und Kinzer 1949, Brandes u. a. 2002, Foote und du Toit 1969 |
| Bewegung | dv/dt = g − g \|v − w\| (v − w) / vt², halbimplizit (stabil); Böen als Ornstein-Uhlenbeck (6 s), Wirbel 1,5 m / 0,7 s [G] | – |
| Form | Achsenverhältnis 1,0048 + 0,0057 D − 2,628 D² + 3,682 D³ − 1,677 D⁴ (D in cm) | Beard und Chuang 1987 |
| Schwingung | f = √(8σ / ρr³) / 2π (2 mm: 122 Hz); Ausschlag bis 6 % [G] | Rayleigh 1879, Szakáll u. a. 2009 |
| Einschlag auf Grund | spritzt, wenn K = Oh Re^1,25 > 57,7 (trocken ab 0,69 mm, nass ab 0,55 mm) | Mundo u. a. 1995 |
| Einschlag auf Wasser | fließt ein (D < 0,6 mm oder We < 40), Strahl (D < 2,6 mm oder We < 900), Krone mit Spitzen, Tröpfchen, Bläschen; Höhen und Zahlen [G] | Engel 1966, Rein 1993, Pumphrey und Elmore 1990 |
| Wellen | ω² = g k + (σ/ρ) k³, jede Mode genau als gedämpfter Schwinger; Druckstoß m v des Tropfens (Cauchy–Poisson, ohne Faktor); Dämpfung 2νk² · 3 + 0,35/s [G]. Gegen die Messung: Ring eines 2,8-mm-Tropfens nach 0,8 s 0,10 mm bei 15 cm (gemessen 0,1 mm bei 13 cm), 2,2 mm: 0,044 mm (0,05) | Lamb 1932, Craeye, Sobieski, Bliven und Guissard 1999 |
| Einschläge aufs Wasser | nach N(D) v(D) – große fallen schneller und kommen öfter an (mittlere Größe 0,80 statt 0,64 mm bei 5 mm/h); gezogen aus N, behalten mit v / v(dmax) | – |
| Rauigkeit | stationäre mittlere quadratische Steigung ⟨\|∇h\|²⟩ = 1/(2π) ∫ N v (I/ρ)² ∫ k⁵ e^(−k²σ²) / (4γω²) dk dD (gegen das Feld geprüft); 5 mm/h: rms 0,081, so rau wie 0,7 m/s Wind; 98 % davon unter einem 8-mm-Raster. Fleckig: unter 16 mm tragen sie 20 % der Fläche (um die jungen Einschläge), die Ringe 34 % (gemessen, [G] als Potenz in R) | Cox und Munk 1954 (Vergleich) |
| Optik | Strahlen durch eine Wasserkugel, Fresnel für beide Schwingungsrichtungen, Ordnungen 0 bis 3, Brechzahl je Wellenlänge, CIE 1931 → linear sRGB; Hauptbogen 42,1°, Nebenbogen 50,9° | Quan und Fry 1995, Wyman u. a. 2013 |
| Sicht, Radar | Auslöschung 2 · π/4 ⟨D²⟩ n; Z = N0 · 720 / Λ⁷ (5 mm/h: 34,3 dBZ, Marshall–Palmer 34,2) | Atlas 1953 |

## Was es kann

- **Tropfen** (`drops.comp`): je Stufe 4 000 bis 48 000 Tropfen in einem Kasten von ±2,5 bis ±4,5 m, fest in der Welt. Sie fallen, folgen Böen (kleine schnell, große träge), schwingen, schlagen auf und kommen oben wieder; ihre Einschläge stehen in einem Buch (bis 8 192 je Bild).
- **Belichtung**: `shutter` in Sekunden (1/250 voreingestellt). Mit 1/30 s ist Regen kaum zu sehen (Film), mit 1/2000 s stehen die Tropfen. `timeScale` verlangsamt alles; die Belichtung gilt in der langsamen Zeit.
- **Zeichnen der Tropfen** (`drop.vert/frag`): die Spur in der Belichtung, Deckung = Überlapp / Spurlänge, über die Spur genau die Fläche des Tropfens; kleiner als ein Bildpunkt auf ihn verteilt. Fern zeigt ein Tropfen ∫ L p dΩ, nah das Bild in der Linse (die Welt dahinter auf dem Kopf, Spiegelung am Rand, Glanzpunkte), abgeplattet und schwingend.
- **Schichten** (`layers.frag`): bis 60 m Schalen mit Zellen, in jeder ein Tropfen nach der Verteilung, der mit seiner Fallgeschwindigkeit fällt; wo viele Tropfen auf einen Bildpunkt kommen, ihr Mittel. Im Kasten nur die kleinen unter dmin.
- **Vorhänge** (`curtain.frag`): ab 60 m bis zur Szene, zum Rand der Karte oder zur Wolkenuntergrenze; σ = N0 π / (2 Λ³); Streuung mit derselben Phasenfunktion, mit der Sonne am Ort aus einer Schattenkarte (wo die Sonne unter den Wolken auf Regen fällt, steht der Regenbogen).
- **Wellen** (`ripple_*.comp`, `fft.comp`): Feld von 256² bis 1024² Zellen zu 6 bis 2 mm (1,5 bis 3 m; seit Runde 5c fein statt weit, weil die Steigung des Regens in den Wellen von 3 bis 16 mm sitzt), ringsum geschlossen, fest in der Welt; die echten Einschläge des Kastens und zufällige für alle anderen Tropfen ab 0,3 mm. Textur (∂h/∂x, ∂h/∂z, h, ∂h/∂t) für das Wasser; `com.dan.ocean` nimmt sie mit `detailMode = 1` (im Fenster echt, draußen zwei gedrehte Abfragen). Was das Feld nicht trägt (unter dem Raster) und was das Wasser wegmittelt (Mip-Stufe, Ferne), gibt `rippleRoughness()`, `rippleVariance()` und `rippleShare()` heraus: `com.dan.ocean` (`Light.detailRoughness/Variance/Share`) legt es als Neigung einzelner Facetten (Anteil p, Varianz/p) – die Varianz bleibt erhalten, zwischen den Einschlägen bleibt der Spiegel glatt.
- **Spritzer** (`splash.comp`, `splash.vert/frag`): Kronen, Strahlen, Bläschen, Tröpfchen (fliegen mit Luftwiderstand, Spur wie die Tropfen).
- **Wald**: Unter einem Kronendach (`Ground.canopy`) fällt `throughfall` (0,3) frei durch, der Rest nach Verdunstung (`interception` 0,25) kommt als große Tropfen (3 bis 5 mm) vom Dach [G].
- **Grund** (`Precip.Ground`): Höhe, Wasser oder nicht, Kronendach; Raster von 64² um das Auge. Ohne Grund überall Wasser auf `waterLevel`.

## Benutzung

```java
Precip p = Precip.moderate();              // drizzle(), heavy(), shower(), of(mm/h, Dsd)
p.curtain = (x, z) -> wolken.regen(x, z);  // 0..1, null = überall gleich
p.ground = meinGrund;                      // Höhe, Wasser, Kronendach; null = Wasser auf waterLevel
// je Bild
p.windSpeed = 8; p.windFromDeg = 240; p.gust = 0.3; p.eyeX = …; p.shutter = 1 / 250.0;
p.step(dt);

PrecipGl g = new PrecipGl();
g.init(gl4);
g.aim = new double[]{fwd[0], fwd[2]};      // Wellenfenster vor dem Auge
g.update(gl4, p.snapshot(), licht);         // vor dem Wasser
wasser.detail(g.rippleTexture(), g.rippleSize(), g.rippleStrength(), g.rippleCenter());
g.draw(gl4, view, 1.0);                     // nach der Szene, ins HDR-Ziel mit Tiefe; view.depthTexture = Kopie der Tiefe
```

`PrecipGl.Light`: Sonne und Mond als Bestrahlung, Himmel oben und am Horizont, Grund als Strahldichte, Rundumbild (optional), Sonne am Auge, Schattenkarte der Wolken (optional), Dunst. Ausgabe vormultipliziert, linear, vorbelichtet.

## Aufbau

| Datei | Aufgabe |
|---|---|
| `Drops` | Verteilung, Fallgeschwindigkeit, Luftwiderstand, Form, Schwingung, Kennzahlen, Wellen |
| `DropOptics` | Phasenfunktion der Wasserkugel (RGB je 0,25°), Bogenwinkel, Bild in der Linse |
| `Splash` | Art des Einschlags und Spritzer |
| `Ripples` | spektrales Wellenfeld, genauer Schritt, Rechnung auf dem Prozessor zur Prüfung |
| `Precip` | Eingänge, Böen, Karte, Raster des Grunds, Stufen, Momentaufnahme |
| `gl/PrecipGl` | alles auf der Grafikkarte, Selbsttest |
| `gl/precip_common.glsl` | die Physik von `Drops` Zeile für Zeile |

## Prüfung

- `demo/PrecipCheck` (ohne Grafikkarte, 60 Prüfungen, Ende „Alles in Ordnung.“): Regenmenge, Radar, Wasser in der Luft, Sicht, Einschläge nach N v, Fallgeschwindigkeit gegen Gunn und Kinzer, dünne Luft, Luftwiderstand und Böen, Beard und Chuang, Rayleigh, Splash-Grenzen, langsamste Welle (1,71 cm, 23,1 cm/s), kleinste Gruppengeschwindigkeit (17,8 cm/s), genaue Schwingung des Wellenfelds, ruhige Mitte des Rings, Ringe gegen Craeye u. a. 1999, Rauigkeit gegen das Feld, Anteil der Fläche, Brechzahl, Bogenwinkel und Farbfolge, Energie je Ordnung, Alexanders Band, Lauf.
- `PrecipGl.selfCheck`: Tropfen gegen dieselbe Rechnung in Java (± 0,13 mm nach 10 Schritten), Wellenfeld gegen `Ripples.Field` (0,4 %), Umgebung (gleichmäßig 1 → 0,997).
- `demo/PrecipDemo --bild datei.png [breite]`: acht Proben (Regen vor dem Wald, Gegenlicht, auf dem Wasser, Tropfen nah 1/2000 s, 1/30 s, Schauer, Regenbogen, Zeitlupe) und der Selbsttest.

## Grenzen (ehrlich offen)

- Die Beugung ist nicht gerechnet; ihre Hälfte der Auslöschung bleibt im Bildpunkt. Überzählige Bögen kleiner Tropfen (Airy) fehlen.
- Die Linse im Tropfen nimmt eine Kugel; die Abplattung ändert nur den Umriss.
- Das Wellenfeld ist linear; den Krater großer Tropfen und den Strahl zeichnet `splash`, nicht das Feld (die lineare Mulde ist rund dreimal flacher als der Krater nach Engel 1966; die Ringe stimmen). Die Facetten der Rauigkeit liegen zufällig, nicht an den Einschlägen des Felds; die Kerne der Krater (Kurtosis 280) zeichnet niemand. Außerhalb des Fensters klingt die Kachel über 40 m aus; ob es dort regnet, weiß das Wasser nicht.
- Schichten sind Schalen um das Auge (Parallaxe beim Gehen stimmt nicht ganz); steil nach oben oder unten fehlen sie.
- Zufällige Einschläge gibt es nur im Wellenfenster auf Wasser; Spritzer auf Grund nur im Kasten.
- Wind unter dem Kronendach ein Fünftel [G]; Tropfen vom Dach fallen mit Fallgeschwindigkeit.

## Seit LituyaBay1958 W8b Runde 2

- **Feines zweites Wellenfeld** (`PrecipGl.fine`, Vorgabe aus; `Quality.fineRes`, `fineCell`: MEDIUM 512 × 1,5 mm, HIGH 512 × 1,2 mm, ULTRA 1 024 × 1 mm): ein kleineres Fenster nah am Auge, das genau die Wellen zwischen π/rippleCell und π/fineCell trägt – zusammen mit dem groben Feld dasselbe Spektrum (Kaskade ohne Doppelzählung), nah bis in die Kapillarwellen von 2 bis 3 mm. Eigene Einschläge ins Fenster (die zufälligen nach der Verteilung, die echten aus dem Kasten), eigene Dämpfung je Welle wie das grobe Feld. `fineTexture()`, `fineSize()`, `fineCenter()`, `fineRoughness()` (was unter seinem Raster bleibt).
- **Schichten ziehen mit dem Wind** (`layersDrift`, Vorgabe an): das Gitter jeder Schale um den Wind quer zur Bildmitte versetzt, je Schale derselbe Versatz – ohne Scherung zwischen benachbarten Bildpunkten (am Bildrand höchstens 1 − cos(φ/2) Abweichung der Komponente).
- **Schritte im Vorhang** (`curtainStepShare`, Vorgabe 1): Anteil der Schritte je Stufe; der Vorhang tastet je Bild versetzt ab, vor einer zeitlichen Glättung reicht die Hälfte.
- **Karte der Schauer** (`curtainTexture()`, `curtainRect()`) als Maske für das Wasser (siehe `com.dan.ocean`).
