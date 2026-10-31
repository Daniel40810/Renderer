# com.dan.sky – Himmel und Atmosphäre

Das Paket rechnet den Himmel über einem Ort zu einer Zeit: Sonne, Mond und die fünf hellen Planeten nach Datum, Uhrzeit und Ort, die Streuung des Lichts in der Lufthülle mit Dunst nach Sichtweite, Dämmerung, Erdschatten und den natürlichen Nachthimmel. Dazu 8 920 Sterne, die Milchstraße, die Sternbildfiguren aus dem Projekt Sternbild, Lichtglocken ferner Städte über der Kimm, Regenbogen und Blitzleuchten. Aus demselben Zustand kommt das Licht für eine Szene: Sonne und Mond nach der Auslöschung, Himmelslicht, Dunst, die Helligkeit fürs Auge und die Grenzgröße der Sterne.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet denselben Himmel mit JOGL (OpenGL 4.3, ohne Compute-Shader). Das Paket kennt weder den Leuchtturm noch andere Pakete. Zum Herauskopieren reicht `src/com/dan/sky/`; nur `demo/SkyDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Sonne, Mond, Planeten:** Sonne nach VSOP87, Mond nach Meeus Kapitel 47 (beides als Kopie aus FMoon, dort gegen die Beispiele bei Meeus geprüft), Phase, Libration, Erdschein. Planeten nach den Bahnelementen von Standish (JPL), Helligkeit nach Meeus Kapitel 41. Präzession von J2000 auf das Datum, Strahlenbrechung an der Kimm (die Sonne geht abgeplattet unter).
- **Streuung:** Rayleigh (Luft), Mie (Dunst) und Ozon nach Hillaire 2020 mit Mehrfachstreuung: blauer Mittag, weißliche Aureole um die Sonne, Abendrot, Erdschatten und Venusgürtel, blaue Stunde durch das Ozon. Der Dunst kommt aus der meteorologischen Sicht (Koschmieder), 1 bis 220 km.
- **Nacht:** natürlicher Nachthimmel mit 22 mag/″² (Eigenleuchten der Luft, zur Kimm heller), Sterne bis 6,5 mag mit Farbe aus B−V, Auslöschung nach der Luftmasse, Funkeln (an der Kimm stärker) und einer Grenzgröße fürs Auge, die mit Mond und Dämmerung sinkt. Milchstraße nach Helligkeitsstufen. Lichtglocken von Städten über der Kimm (`LightDome`).
- **Mond:** Scheibe mit Phase und Sichelneigung aus der echten Lage von Sonne und Mond, Oberfläche aus FMoon (17 Maria, Strahlenkrater), Libration, Erdschein auf der Nachtseite. Die Helligkeit ist so geeicht, dass die ganze Scheibe genau das Mondlicht bringt, das auch aufs Wasser fällt.
- **Licht für die Szene:** `State` liefert Richtung und Farbe von Sonne und Mond am Auge, Himmelslicht auf eine waagerechte Fläche, Zenit-Strahldichte, Dunst je m, Anpassungsleuchtdichte und Blickfeld-Leuchtdichte (`viewLuminance`) für die Belichtung, den Anteil des Stäbchensehens (`scotopic`) für die Nachbearbeitung.
- **Wetter-Eingänge:** `visibilityKm`, `cloudCover` (bis ein Wolken-Paket zeichnet, graut ein Schleier den Himmel ein), `rainbow`, `flash(...)` in derselben Form wie die Schnittstelle `Flash`.
- **Rundumbild:** dasselbe Format, das `com.dan.ocean` für Spiegelungen liest (Azimut von Nord über die Breite, Höhe −90° bis +90°).

## Benutzung

```java
Sky sky = new Sky(System.currentTimeMillis(), 53.855, 8.079);
sky.eyeHeight = 10;                      // Augenhöhe in m (Kimm, Dunstweg)
sky.visibilityKm = 30;                   // Sicht
sky.domes.add(new Sky.LightDome("Bremerhaven", 137, 48, 1.0));

// je Bild
sky.epochMillis = uhrzeit;
Sky.State st = sky.step(dt);             // Licht rechnet bei Bewegung im Hintergrund nach
double[] sonne = st.sunColor;            // Bestrahlung am Auge (Szeneneinheiten, Sonne außerhalb 2,9)
double belichtung = Sky.State.exposureFor(Math.sqrt(st.adaptation * st.viewLuminance(blick, oben, 55, 16 / 9.0)), 0.85);

// zeichnen (im GL-Faden)
SkyGl gl = new SkyGl();
gl.init(gl4);
gl.update(gl4, st);                      // Tabellen
SkyGl.View v = new SkyGl.View();         // Matrizen, Bildgröße, Öffnung, Vorbelichtung
v.preExposure = belichtung;
gl.draw(gl4, st, v);                     // Himmel, Sterne, Planeten, Figuren
gl.drawPanorama(gl4, st, belichtung);    // ins gebundene Rundumbild
```

**Vorbelichtung:** Alle Helligkeiten kommen mit `preExposure` multipliziert heraus. Der Nachthimmel ist 10⁻⁸ so hell wie der Tag; ohne Vorbelichtung verschwände er im Halbfloat-Puffer. Eine Szene multipliziert ihr eigenes Licht (Sonne, Mond) mit derselben Zahl und belichtet danach mit 1.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Sky` | Eingänge, Zustand (`State`), Licht, Belichtungshilfen |
| `Atmosphere` | Rayleigh, Mie, Ozon, Sicht → Dunst |
| `Scattering` | Tabellen und Strahlengang auf dem Prozessor (wie die Grafikkarte) |
| `Celestial` | Präzession, Horizont, Brechung, Luftmasse |
| `Planets` | Merkur bis Saturn |
| `StarCatalog` | Sterne (`stars.bin`), Figuren (`figures.txt`), Sternfarbe |
| `MoonDisc` | Mondoberfläche, Beleuchtung, Eichung |
| `astro/` | Kopie des Rechenkerns aus FMoon |
| `gl/SkyGl` | Tabellen, Himmel, Sterne, Rundumbild, Selbsttest |
| `Mie` | seit Brocken Phase 7: Streuung an einer Kugel nach Lorenz und Mie (BHMIE nach Bohren und Huffman) |
| `Droplets` | seit Brocken Phase 7: Phasenfunktion eines Tröpfchenfelds als Farbtabelle (Glorie, Nebelbogen), Lage des Gespensts |
| `Halo` | seit Brocken Phase 7: Winkel von 22°-Ring und Nebensonnen an Eiskristallen |
| `gl/DropletGl` | seit Brocken Phase 7: die Tabelle als Textur für einen Nebel, im eigenen Faden gebaut |
| `demo/GloryDemo` | seit Brocken Phase 7: Glorien, Nebelbogen und Halo-Winkel als Blatt ohne Grafikkarte |

## Daten und Herkunft

- `stars.bin`: HYG-Datenbank v4.1 (astronexus, CC BY-SA 4.0), alle Sterne bis 6,5 mag.
- `milkyway.png`: aus den Helligkeitskonturen von d3-celestial (Olaf Frohn, BSD-3) gerastert.
- `figures.txt`: Sternbildfiguren aus dem Projekt Sternbild (BuiltinCatalog).
- `astro/`: Kopie aus FMoon (Meeus, „Astronomical Algorithms“).

## Demo und Selbsttest

- `com.dan.sky.demo.SkyDemo` zeigt den Himmel über einer glatten See mit Reglern für Tag, Uhrzeit, Zeitraffer, Sicht, Bedeckung und Regenbogen, Schaltern für Sterne, Planeten, Milchstraße und Sternbilder, „Blitz“ und vier Voreinstellungen. Ziehen dreht den Blick, das Rad ändert die Öffnung.
- Ohne Fenster rechnet `SkyDemo --bild datei.png [breite] [höhe] [JJJJ-MM-TT]` Morgen, Mittag, Abend und Nacht nebeneinander und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.sky.demo.SkyCheck` prüft den Rechenkern gegen Meeus, Präzession und Venus, Sonnenaufgang auf zwei Wegen, Streuung gegen die genaue Rechnung und den CIE-Normhimmel, Dämmerung, Nachthimmel, Mond- und Sonnenlicht, Grenzgröße, Katalog, Figuren, Milchstraße und die Uniforms; Ende „Alles in Ordnung.“.
- `SkyGl.selfCheck` liest die Tabellen der Grafikkarte zurück und meldet die größte Abweichung zum Prozessor.

## Grenzen

- Die Mehrfachstreuung ist die Näherung nach Hillaire (isotrop ab der zweiten Streuung). Tief in der Dämmerung (Sonne unter −12°) ist der Himmel dadurch etwas zu dunkel; dort übernimmt der Nachthimmel.
- Dunst liegt gleichmäßig mit 1,2 km Skalenhöhe. Bodennebel und Nebelbänke bringt `fog`, Wolken bringt `cloud`.
- Planeten ohne Lichtlaufzeit und Saturn ohne Ringneigung. Keine Kometen, kein Zodiakallicht, keine Polarlichter.
- Die Sterne stehen an ihrem Ort von J2000; die Eigenbewegung fehlt (in 26 Jahren höchstens 0,04°, bei 61 Cygni).

## Seit Glacier Phase 3: Südhimmel

- Vier Sternbildfiguren des Südhimmels in `figures.txt`: Kreuz des Südens (Cru), Zentaur (Cen, mit α und β als
  Zeiger), Kiel des Schiffs (Car, mit Canopus), Südliches Dreieck (TrA); jetzt 27 Figuren.
- Die Magellanschen Wolken fehlen in den Helligkeitsstufen von d3-celestial. `StarCatalog.magellanic(ra, dec)` gibt
  sie als weiche Flecken (Große Wolke 10° × 9° mit Balken, Kleine 5° × 3°) [G]; `SkyGl` malt sie beim Laden in die
  Milchstraßenkarte. Auf 50° Süd stehen beide und das Kreuz die ganze Nacht über der Kimm.
- Rückfluss nach Haithabu/Lighthouse in Phase 16.

## Seit Brocken Phase 7: Licht in Nebel und Eis

Was man im Nebel mit der Sonne im Rücken sieht, kommt aus der Streuung an den Tröpfchen; das Package rechnet sie, ein
Nebel (etwa `com.dan.fog.TerrainFog`) nimmt sie als Phasenfunktion. Das Package kennt keinen Nebel.

- **Mie (`Mie`):** Streuung an einer Kugel nach Lorenz und Mie, Rechenweg BHMIE (Bohren und Huffman 1983, Anhang A);
  geprüft gegen das Beispiel dort (Q_ext = Q_sca = 3,10543, Q_back = 2,92534). Brechungsindex von Wasser und Eis nach
  Cauchy, an Hale und Querry (1973) angepasst.
- **Tröpfchen (`Droplets`):** Halbmesser `radiusUm` (Median einer logarithmischen Normalverteilung) und Breite `width`
  (σ von ln r). Daraus eine Farbtabelle der Phasenfunktion über 0° bis 180° (`Droplets.N` = 2048 Werte je Kanal, auf 4π
  normiert, isotrop = 1): 25 Wellenlängen von 400 bis 700 nm, 17 Halbmesser, Farbe über die Normspektralwerte (Näherung
  nach Wyman, Sloan und Shirley 2013) nach linearem sRGB, gemittelt über die Sonnenscheibe. Gebaut in rund 0,5 s auf allen
  Kernen, danach aus dem Speicher. Darin:
  - die **Glorie**: farbige Ringe um den Gegenpunkt der Sonne; der erste rote Ring liegt bei 2,2° für 10 µm, allgemein
    nahe 24° / r (Laven 2005: 4,3° / 2,2° / 1,1° für 5 / 10 / 20 µm gegen 4,8° / 2,4° / 1,2°); ab rund 20 % Breite
    verblassen die Farben;
  - der **Nebelbogen**: ein breiter, fast weißer Bogen 36° bis 40° um den Gegenpunkt (38,1° für 10 µm), bei kleinen
    Tröpfchen enger;
  - vorn die Aureole (Beugung).
  Presets `nebelmeer()` (10 µm, 8 %: lebhafte Glorie), `stau()` (13 µm, 15 %), `talnebel()` (5 µm, 25 %).
- **Brockengespenst:** der Schatten des Betrachters im Nebel, mitten in der Glorie. `Droplets.antisolar`, `umbraLength`
  (ein Mensch, 0,25 m, wirft rund 54 m Kernschatten) und `spectreDeg` (Breite des Schattens im Nebel, 0 ohne Sonne im
  Rücken oder ohne Nebel im Kernschatten). Den Schatten selbst zeichnet der Nebel (`com.dan.fog`: Schattenwerfer).
- **Halos (`Halo`):** 22°-Ring an regellosen Säulen (rot innen 21,65°, blau 22,32°) und Nebensonnen an schwebenden
  Plättchen mit dem Index nach Bravais (im Azimut 21,6° an der Kimm, 24,4° bei 20°, 36,0° bei 40° Sonnenhöhe, ab rund 61°
  keine). Eingänge `Sky.halo` und `Sky.parhelia` (0..1), gemalt in `gl/sky.frag` (`halo(d)`) mit scharfem rotem
  Innenrand, dunklerem Inneren und dem weißen Schweif der Nebensonnen.
- **Grafik:** `gl/DropletGl` lädt die Tabelle als Textur (2048 × 1, RGBA16F, u = Streuwinkel / 180°); ein Shader liest
  `texture(uPhase, vec2(acos(dot(blick, sonne)) / π, 0.5)).rgb` statt Henyey-Greenstein.
- **Demo:** `demo/GloryDemo --bild datei.png [breite]`: vier Glorien (5, 10, 20 µm schmal, 10 µm breit) mit dem Ring nach
  Laven, Glorie und Nebelbogen ±50° um den Gegenpunkt, Kurven der Helligkeit und die Halo-Winkel. `SkyCheck` prüft Mie,
  Glorie, Nebelbogen, Normierung, Gespenst und Halos.

```java
Droplets tropfen = Droplets.nebelmeer();
DropletGl tropfenGl = new DropletGl();
tropfenGl.update(gl, tropfen);              // je Bild; baut im Hintergrund, wenn sich r oder σ ändern
licht.phaseTexture = tropfenGl.texture();   // an den Nebel (com.dan.fog.gl.TerrainFogGl.Light)
sky.halo = 0.6; sky.parhelia = 0.4;         // Eiskristalle im hohen Schleier
```

Grenzen: Die Tröpfchen sind Kugeln ohne Absorption (im Sichtbaren genau genug); die Glorie ist einfach gestreut, die
Vielfachstreuung im Nebel dämpft sie nur über das Verhältnis, das der Nebel ansetzt. Halos sind nach ihren Winkeln
gemalt, nicht durch Kristalle gerechnet; 46°-Ring, Bögen und Säulen fehlen. Die Glorie an Wolken (vom Flugzeug) zeichnet
`cloud` nicht.

## Seit Sylt Phase 13: grünes Leuchten

- `Sky.greenFlash` (0 aus, wie bisher): die Luft bricht Grün etwas stärker als Rot (Brechzahl der Luft bei 650, 550, 450 nm), das grüne Bild der Sonne steht an der Kimm rund 0,22′ über dem roten, das blaue 0,36′ darüber (es verlöscht auf dem langen Weg). `sky.frag` zeichnet die Sonnenscheibe dann je Farbkanal mit eigener Brechung; beim Untergang ist der letzte Rand grün. 1 ist die reine Brechung (grüner Rand 1 bis 2 s, im Fernglas oder Fernrohr), 2 bis 4 eine Luftspiegelung über warmem Wasser, die den grünen Blitz einige Sekunden lang macht [G].
- `GreenFlash`: Faktoren je Kanal, Versatz in Bogenminuten, sichtbarer Anteil der Scheibe je Kanal über einer Kimm. `SkyCheck` (Abschnitt Grünes Leuchten) prüft Versatz und Dauer.
