# Renderer

![Abendsonne, Sturmsee, Erdblitz, Leuchtfeuer, Fluss, Wikingerhäuser, Baum und Promenade bei Nacht – alles aus diesem Repository gerendert](docs/img/hero.png)

**Renderer** ist eine Bibliothek aus 51 Element-Paketen für Naturszenen in Java: Himmel, Wolken, Meer, Brandung, Regen,
Nebel, Gewitter, Bäume, Gras, Fels, Flüsse, Eis, Tiere, Menschen, Schiffe, Holzbauten, Strandkörbe, Licht und Feuer. Jedes Paket
rechnet mit echten physikalischen Größen (Beaufort, IALA, Marshall–Palmer, Briggs, CIE 1931, …), kommt im Kern mit
reinem Java 21 aus und zeichnet sich im Ordner `gl/` mit JOGL (OpenGL 4.3).

Die Pakete stammen aus den Szenenprojekten *Lighthouse*, *Haithabu*, *Geiranger*, *Timmendorf*, *Rungholt*, *Glacier*,
*Sylt* und *Brocken* und sind hier so gesammelt, dass jedes für sich herauskopiert werden kann.

> Alle Bilder in dieser README wurden mit den Demos aus diesem Repository erzeugt (`--bild`, siehe unten) –
> ohne Grafikkarte, mit Mesa-Software-OpenGL.

---

## Inhalt

- [Galerie](#galerie)
- [Pakete](#pakete)
- [Der Paketvertrag](#der-paketvertrag)
- [Bauen und starten](#bauen-und-starten)
- [Demos, Standbilder und Selbsttests](#demos-standbilder-und-selbsttests)
- [Verzeichnisse](#verzeichnisse)

---

## Galerie

<table>
<tr>
<td width="50%"><a href="src/com/dan/sky/README.md"><img src="docs/img/sky.png" alt="sky"></a><br><b>sky</b> – Morgen, Mittag, Abend, Nacht über der See</td>
<td width="50%"><a href="src/com/dan/cloud/README.md"><img src="docs/img/cloud.png" alt="cloud"></a><br><b>cloud</b> – Bedeckung 0/8 bis 8/8 und fünf Wolkenarten</td>
</tr>
<tr>
<td><a href="src/com/dan/ocean/README.md"><img src="docs/img/ocean.png" alt="ocean"></a><br><b>ocean</b> – offene See bei Bft 2, 6 und 10</td>
<td><a href="src/com/dan/surf/README.md"><img src="docs/img/surf.png" alt="surf"></a><br><b>surf</b> – Brecher, Weißwasser und Auflauf am Strand</td>
</tr>
<tr>
<td><a href="src/com/dan/lightning/README.md"><img src="docs/img/lightning.png" alt="lightning"></a><br><b>lightning</b> – Erd-, Wolken- und Spinnenblitz, Ablauf und Donner</td>
<td><a href="src/com/dan/beacon/README.md"><img src="docs/img/beacon.png" alt="beacon"></a><br><b>beacon</b> – Leuchtfeuer, Gürtellinse und Kennungen</td>
</tr>
<tr>
<td><a href="src/com/dan/fog/README.md"><img src="docs/img/fog.png" alt="fog"></a><br><b>fog</b> – Dunst, Nebelbänke, Seenebel und Nebelsignale</td>
<td><a href="src/com/dan/rain/README.md"><img src="docs/img/rain.png" alt="rain"></a><br><b>rain</b> – Niesel bis Wolkenbruch</td>
</tr>
<tr>
<td><a href="src/com/dan/wetglass/README.md"><img src="docs/img/wetglass.png" alt="wetglass"></a><br><b>wetglass</b> – Tropfen, Läufer, Beschlag und Salz auf Glas</td>
<td><a href="src/com/dan/spray/README.md"><img src="docs/img/spray.png" alt="spray"></a><br><b>spray</b> – Spritzwasser und Gischt bei Bft 5, 8 und 11</td>
</tr>
<tr>
<td><a href="src/com/dan/river/README.md"><img src="docs/img/river.png" alt="river"></a><br><b>river</b> – Strömung, Wellen und Schaum um Steine</td>
<td><a href="src/com/dan/ice/README.md"><img src="docs/img/ice.png" alt="ice"></a><br><b>ice</b> – Gletscher: Front, Eisfall, Zunge mit Spalten</td>
</tr>
<tr>
<td><a href="src/com/dan/timber/README.md"><img src="docs/img/timber.png" alt="timber"></a><br><b>timber</b> – wikingerzeitliche Häuser, Zäune und Palisaden</td>
<td><a href="src/com/dan/longship/README.md"><img src="docs/img/longship.png" alt="longship"></a><br><b>longship</b> – Schiffe der Wikingerzeit unter Segel</td>
</tr>
<tr>
<td><a href="src/com/dan/forest/README.md"><img src="docs/img/forest.png" alt="forest"></a><br><b>forest</b> – prozeduraler Wald im Herbst, mit Schatten</td>
<td><a href="src/com/dan/tree/README.md"><img src="docs/img/tree.png" alt="tree"></a><br><b>tree</b> – Bestand aus Unreal-Assets mit Detailstufen</td>
</tr>
<tr>
<td><a href="src/com/dan/ground/README.md"><img src="docs/img/ground.png" alt="ground"></a><br><b>ground</b> – Gras, Blumen, Steine und ein Trampelpfad</td>
<td><a href="src/com/dan/reed/README.md"><img src="docs/img/reed.png" alt="reed"></a><br><b>reed</b> – Schilf und Gras im Wind</td>
</tr>
<tr>
<td><a href="src/com/dan/road/README.md"><img src="docs/img/road.png" alt="road"></a><br><b>road</b> – Feldwege, Straße und Autobahn im Gelände</td>
<td><a href="src/com/dan/lamp/README.md"><img src="docs/img/lamp.png" alt="lamp"></a><br><b>lamp</b> – Promenade bei Nacht, nass und trocken</td>
</tr>
<tr>
<td><a href="src/com/dan/cliff/README.md"><img src="docs/img/cliff.png" alt="cliff"></a><br><b>cliff</b> – Steilwand: Bänder, Klüfte, Nässe, Schnee</td>
<td><a href="src/com/dan/fall/README.md"><img src="docs/img/fall.png" alt="fall"></a><br><b>fall</b> – Wasserfall von vorn, von der Seite, im Wind, bei Frost</td>
</tr>
<tr>
<td><a href="src/com/dan/flock/README.md"><img src="docs/img/flock.png" alt="flock"></a><br><b>flock</b> – Möwen im Aufwind am Turm</td>
<td><a href="src/com/dan/figure/README.md"><img src="docs/img/figure.png" alt="figure"></a><br><b>figure</b> – Menschen: Kleidung nach Monat, Gang, Gruppe</td>
</tr>
<tr>
<td><a href="src/com/dan/sail/README.md"><img src="docs/img/sail.png" alt="sail"></a><br><b>sail</b> – Segelboot mit Polare</td>
<td><a href="src/com/dan/seal/README.md"><img src="docs/img/seal.png" alt="seal"></a><br><b>seal</b> – Seehunde in ihren Haltungen</td>
</tr>
<tr>
<td><a href="src/com/dan/sky/README.md"><img src="docs/img/glory.png" alt="glory"></a><br><b>sky</b> – Glorie, Nebelbogen und Mie-Streuung</td>
<td><a href="src/com/dan/firework/README.md"><img src="docs/img/firework.png" alt="firework"></a><br><b>firework</b> – Feuerwerk mit Farben aus dem Spektrum</td>
</tr>
<tr>
<td><a href="src/com/dan/dune/README.md"><img src="docs/img/dune.png" alt="dune"></a><br><b>dune</b> – Flugsand, Kammfahnen und Rippel</td>
<td><a href="src/com/dan/weathering/README.md"><img src="docs/img/weathering.png" alt="weathering"></a><br><b>weathering</b> – Nässe, Rost, Algen und Salz am Turm</td>
</tr>
<tr>
<td><a href="src/com/dan/smoke/README.md"><img src="docs/img/smoke.png" alt="smoke"></a><br><b>smoke</b> – Rauchfahnen in stabiler Abendluft</td>
<td><a href="src/com/dan/tide/README.md"><img src="docs/img/tide.png" alt="tide"></a><br><b>tide</b> – Pegel Husum: ein Wattbecken läuft voll und leer</td>
</tr>
<tr>
<td><a href="src/com/dan/wave/README.md"><img src="docs/img/wave.png" alt="wave"></a><br><b>wave</b> – Welle nach einem Einschlag, Auflauf und Reflexion</td>
<td><a href="src/com/dan/wake/README.md"><img src="docs/img/wake.png" alt="wake"></a><br><b>wake</b> – Kelvin-Muster hinter Boot und Fähre</td>
</tr>
<tr>
<td><a href="src/com/dan/beachchair/README.md"><img src="docs/img/beachchair.png" alt="beachchair"></a><br><b>beachchair</b> – Strandkörbe: Ostsee und Nordsee, Lehne, Fußstützen, abends zu</td>
<td></td>
</tr>
</table>

---

## Pakete

Alle Pakete liegen unter `src/com/dan/`. Jedes hat eine eigene, ausführliche README – der Name in der Tabelle führt dorthin.

### Himmel und Wetter

| Paket | Inhalt |
|---|---|
| [`sky`](src/com/dan/sky/README.md) | Sonne, Mond und Planeten nach Datum und Ort, Streuung in der Atmosphäre, Dämmerung, 8 920 Sterne, Milchstraße, Regenbogen, Glorie, grüner Blitz |
| [`cloud`](src/com/dan/cloud/README.md) | Cumulus bis Gewitterturm, Schichtwolken, Schleier; Bedeckung in Achteln, Wolkenschatten |
| [`fog`](src/com/dan/fog/README.md) | Seenebel in Bänken, Nebel über Gelände, Nebelschallsignale mit Kennung |
| [`rain`](src/com/dan/rain/README.md) | Tropfenstreifen, Regenvorhänge, Ringe und Krönchen auf dem Wasser, Sichtweite im Regen |
| [`lightning`](src/com/dan/lightning/README.md) | Leitblitz, Hauptschläge, Wolkenleuchten und Donner mit Laufzeit |
| [`weather`](src/com/dan/weather/README.md) | Wetterregie: Wind, Böen, Bedeckung, Regen, Sicht, Luftdruck, Dünung – nur Zahlen |
| [`wind`](src/com/dan/wind/README.md) | Windfeld über einem Höhenfeld mit Rauigkeit und Schichtung |
| [`season`](src/com/dan/season/README.md) | Das Jahr an einem Ort: Schnee, Eis, Reif, Stand der Pflanzen |
| [`rime`](src/com/dan/rime/README.md) | Raueis, Raureif und Klareis an Objekten |

### Meer und Küste

| Paket | Inhalt |
|---|---|
| [`ocean`](src/com/dan/ocean/README.md) | Windsee aus einem Spektrum, Dünung, Schaumköpfe und -straßen, Ringwellen |
| [`wave`](src/com/dan/wave/README.md) | Flachwasserlöser: eine Welle läuft, läuft auf, wird zurückgeworfen |
| [`surf`](src/com/dan/surf/README.md) | Welle von der offenen See bis zum Strand: Shoaling, Brechen, Auflauf |
| [`spray`](src/com/dan/spray/README.md) | Spritzwasser, Sprühnebel und Gischtfahnen an Körpern |
| [`wake`](src/com/dan/wake/README.md) | Kielwasser, Bugwelle und Schwell |
| [`tide`](src/com/dan/tide/README.md) | Pegel aus zehn Partialtiden, Überflutung eines Geländes |
| [`mudflat`](src/com/dan/mudflat/README.md) | Das Watt als Material: Nässe, Sediment, Spuren |
| [`dune`](src/com/dan/dune/README.md) | Flugsand, Dünen und Windrippel |
| [`buoyancy`](src/com/dan/buoyancy/README.md) | Tonnen und Schiffe, die auf beliebigem Wasser schwimmen |
| [`wetglass`](src/com/dan/wetglass/README.md) | Regentropfen auf einer Scheibe, jeder eine kleine Linse |
| [`weathering`](src/com/dan/weathering/README.md) | Nässe, Rost, Algen, Salz und Moos aus Masken und Rauschen |

### Land, Wasser und Pflanzen

| Paket | Inhalt |
|---|---|
| [`cliff`](src/com/dan/cliff/README.md) | Felswände als Material und Feinform |
| [`fall`](src/com/dan/fall/README.md) | Wasserfälle über Fels |
| [`river`](src/com/dan/river/README.md) | Flüsse mit Strömung, Wellen, Schaum und Treibgut |
| [`ice`](src/com/dan/ice/README.md) | Eis als Material (Absorption nach Beer–Lambert) und Gletscher als Körper |
| [`fracture`](src/com/dan/fracture/README.md) | Bruch und Starrkörper für Eis, Fels, Beton, Klei und Lockergestein |
| [`ground`](src/com/dan/ground/README.md) | Bodendecke: Gräser, Blumen, Steine, Erde, niedergetreten |
| [`reed`](src/com/dan/reed/README.md) | Schilf, Binsen und Gras im Wind |
| [`tree`](src/com/dan/tree/README.md) | Bäume, Sträucher und Gräser aus Unreal-Assets, mit Detailstufen |
| [`forest`](src/com/dan/forest/README.md) | Prozedurale Bäume und Wälder mit Wind, Jahreszeit und Laubfall |
| [`road`](src/com/dan/road/README.md) | Pfad, Feldweg, Straße, Autobahn – Trasse, Fahrbahn, Verkehr |
| [`route`](src/com/dan/route/README.md) | Wegenetze, kürzeste Wege, Ströme von Fußgängern und Fahrzeugen |
| [`rail`](src/com/dan/rail/README.md) | Gleise und Züge |

### Lebewesen

| Paket | Inhalt |
|---|---|
| [`figure`](src/com/dan/figure/README.md) | Menschen, die gehen, stehen, sich bücken, mit Kleidung nach Monat |
| [`flock`](src/com/dan/flock/README.md) | Möwen im Schwarm und Watvögel |
| [`herd`](src/com/dan/herd/README.md) | Hühner, Ziegen, Schweine und Hunde |
| [`seal`](src/com/dan/seal/README.md) | Seehunde auf der Sandbank |
| [`jelly`](src/com/dan/jelly/README.md) | Ohrenquallen in einer Ostseebucht |

### Bauten, Schiffe, Licht und Feuer

| Paket | Inhalt |
|---|---|
| [`timber`](src/com/dan/timber/README.md) | Wikingerzeitliche Häuser, Zäune, Bohlenwege, Palisaden und Brücken |
| [`beachchair`](src/com/dan/beachchair/README.md) | Strandkörbe aus Geflecht, Holz und Markisenstoff (Ostsee rund, Nordsee eckig), mit Stellung, Verschluss und Schatten |
| [`longship`](src/com/dan/longship/README.md) | Drei Schiffstypen der Wikingerzeit unter Segel und Riemen |
| [`sail`](src/com/dan/sail/README.md) | Segelboote nach der Physik, Polaren, Wende, Halse, Regatta |
| [`cloth`](src/com/dan/cloth/README.md) | Flaggen, Wimpel, Segel und Markisen als Tuch nach XPBD |
| [`beacon`](src/com/dan/beacon/README.md) | Leuchtfeuer mit Kennung, Drehlinse und Strahl im Regen |
| [`lamp`](src/com/dan/lamp/README.md) | Laternen und Fensterlicht, Spiegelung auf nassem Pflaster |
| [`hearth`](src/com/dan/hearth/README.md) | Herdfeuer, Fackeln, Schmiedeessen und Hausbrand |
| [`smoke`](src/com/dan/smoke/README.md) | Rauchfahnen nach Briggs |
| [`firework`](src/com/dan/firework/README.md) | Feuerwerk mit Steighöhen, Sternen, Spektralfarben und Knall |

### Infrastruktur

| Paket | Inhalt |
|---|---|
| [`kino`](src/com/dan/kino/README.md) | Kinomodus für Regiebücher: Breitwand, Vor- und Abspann, Untertitel, Überblendung |
| [`soundscape`](src/com/dan/soundscape/README.md) | Erzeugter Ton ohne Audiodateien: Wind, See, Regen, Möwen, Donner, Nebelhorn |
| [`store`](src/com/dan/store/README.md) | Presets und Zustand speichern, mit oder ohne Oracle |
| [`muster`](src/com/dan/muster/README.md) | Vorlage für ein neues Element-Paket |

---

## Der Paketvertrag

Alle Pakete folgen denselben Regeln. Die Vorlage dafür ist [`com.dan.muster`](src/com/dan/muster/README.md).

- **Drei Schichten.** Der Kern importiert nur `java.*` (Java 21). `gl/` darf zusätzlich JOGL nutzen, `demo/` zusätzlich `FStyle.jar`.
- **Kein Paket kennt ein anderes.** Was von außen kommt, kommt als Zahl oder über eine kleine eigene Schnittstelle (z. B. `Scatter`, `WindField.Ground`).
- **Zum Herauskopieren** reicht der Ordner `src/com/dan/<paket>/`. Shader liegen als Ressourcen im Paket und werden mit `getResourceAsStream` gelesen.
- **Offene Werte.** Alle Einstellungen sind öffentliche Felder und zur Laufzeit änderbar; dazu Presets (z. B. `beaufort(n)`).
- **Reproduzierbar.** Gleicher `seed`, gleiches Ergebnis.
- **Qualitätsstufen** `LOW`, `MEDIUM`, `HIGH`, `ULTRA`.
- **Demo und Selbsttest.** Jede Demo schreibt mit `--bild datei.png` ein Standbild ohne Fenster; jede `…Check`-Klasse prüft das Paket ohne Grafikkarte und endet mit „Alles in Ordnung.“
- **Isolationsprüfung.** Das Ant-Target `check-packages` ([`tools/PaketPruefung.java`](tools/PaketPruefung.java)) übersetzt jedes Paket einzeln – Kern gegen das JDK, `gl/` gegen JOGL, `demo/` gegen FStyle – und prüft Importe und Shader. Es läuft nach jedem Übersetzen.

---

## Bauen und starten

**Voraussetzungen:** JDK 21. Die Bibliotheken liegen in `lib/`:

| Datei | Zweck |
|---|---|
| `jogl-all.jar`, `gluegen-rt.jar` | JOGL 2.6.0 für die Ordner `gl/` |
| `*-natives-windows-amd64.jar` | Native Teile für Windows x64 |
| `FStyle.jar` | Bedienelemente für die Demos |

Für Linux oder macOS legt man die passenden Natives aus JOGL 2.6.0 daneben, z. B.
`jogl-all-natives-linux-amd64.jar` und `gluegen-rt-natives-linux-amd64.jar` (Maven Central, `org.jogamp.jogl` / `org.jogamp.gluegen`).

### Mit NetBeans oder Ant

Das Repository ist ein NetBeans-Projekt. Öffnen und bauen genügt; auf der Kommandozeile:

```bash
ant jar
```

```bash
ant check-packages
```

### Ohne Ant

```bash
find src -name '*.java' > sources.txt
```

```bash
javac -encoding UTF-8 -cp "lib/*" -d build/classes @sources.txt
```

Beim Starten muss `src` mit auf dem Klassenpfad stehen, damit die Shader und Assets gefunden werden:

```bash
java -cp "build/classes:lib/*:src" com.dan.ocean.demo.OceanDemo
```

(Unter Windows `;` statt `:` als Trenner.)

---

## Demos, Standbilder und Selbsttests

Jedes Paket hat unter `demo/` eine Demo mit Fenster und eine `…Check`-Klasse.

```bash
java -cp "build/classes:lib/*:src" com.dan.sky.demo.SkyDemo
```

```bash
java -cp "build/classes:lib/*:src" com.dan.sky.demo.SkyDemo --bild himmel.png
```

```bash
java -cp "build/classes:lib/*:src" com.dan.sky.demo.SkyCheck
```

Die weiteren Argumente für `--bild` (Breite, Blick, Beaufort, Datum …) stehen im Javadoc der jeweiligen Demo und in der README des Pakets.

**Ohne Grafikkarte:** Unter Linux laufen die Demos auch mit Mesa (llvmpipe) in einem virtuellen Bildschirm. So sind die Bilder dieser README entstanden:

```bash
xvfb-run -a -s "-screen 0 2560x1600x24" java -cp "build/classes:lib/*:src" com.dan.ocean.demo.OceanDemo --bild docs/img/ocean.png
```

---

## Verzeichnisse

```
Renderer/
├── src/com/dan/<paket>/     51 Element-Pakete: Kern, gl/ (JOGL + Shader), demo/ (Demo + Check), README.md
├── lib/                     JOGL 2.6.0, FStyle.jar, NetBeans-CopyLibs
├── tools/
│   ├── PaketPruefung.java   Isolationsprüfung (ant check-packages)
│   └── tree/                Wandler für Baum- und Gras-Assets (USD, FBX, glTF → .dtree)
├── docs/img/                Bilder dieser README
├── nbproject/, build.xml    NetBeans-/Ant-Projekt
└── manifest.mf
```
