# com.dan.surface – Materialien für Boden, Fels und Holz

**Stand: Phase 5 von LituyaBay1958 (01.11.2026).** Eine Materialbibliothek mit Farbe, Normale, Rauheit, Verdeckung und Höhe je Material. Ein Backwerkzeug macht aus PBR-Sätzen (ambientCG, Poly Haven) einheitliche Karten. `gl/` lädt sie gepackt in drei Texturfelder. Der Baustein `surface.glsl` tastet sie ohne sichtbare Wiederholung ab (Hex-Kachelung), seitlich dreiseitig, mischt bis zu drei Materialien nach Höhe und legt Nässe, Schnee und Staub darüber. Welches Material wo liegt, entscheidet die App.

Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` zusätzlich FStyle. Zum Herauskopieren reicht `src/com/dan/surface/`.

## Was es kann

- **Material** (`Material`): Kurzname, Name, Kachelgröße in Metern, Herkunft (Anbieter, Satz, Adresse, Lizenz) und offene Werte: `drehen` (Hex-Kacheln zufällig drehen; aus für Rinde, Holz, Tau), `dreiseitig`, Höhe in Metern, Tönung, Entsättigung, Aufhellung, Abdunkeln bei Nässe. Abgeleitete Materialien (`derived`) entstehen aus einem gebackenen anderen.
- **Katalog** (`Katalog.fjord()`): 14 Materialien für eine Fjordküste. 13 sind CC0-Sätze von ambientCG, Seetang ist aus Moos abgeleitet. Zahlen in Tabelle unten.
- **Backen** (`Bake.bake(material, zipOderOrdner, ziel, kante)`): liest `_Color`, `_NormalGL` oder `_NormalDX` (Grün wird umgedreht), `_Roughness`, `_AmbientOcclusion`, `_Displacement`. Verkleinert durch Mitteln, tönt im linearen Raum. Schreibt:
  - `a.png`: Farbe sRGB, Höhe im Alpha
  - `n.png`: Normale x, y (z rechnet der Shader)
  - `r.png`: Rauheit in Rot, Verdeckung in Grün
  - `material.txt`: Werte, Mittelwerte von Farbe, Rauheit und Höhe, Herkunft, Lizenz
- **Satz** (`MaterialSet`): liest einen Ordner mit gebackenen Materialien in der Reihenfolge einer Liste, meldet fehlende, rechnet den Speicherbedarf (`vram`).
- **Gepackt laden** (`gl/SurfaceGl.load(gl, set, kante, packen)`): drei `sampler2DArray`, eine Schicht je Material. Mip-Stufen auf der CPU (Farbe linear gemittelt, Normalen neu normiert).
  - Farbe + Höhe als BC7 (sRGB)
  - Normale als BC5
  - Rauheit + Verdeckung als BC5
  - Gepackt wird vom Treiber (einmal), das Ergebnis liegt als `gpu_a_<kante>.bin` usw. neben dem Material und wird beim nächsten Start direkt geladen. Ohne BPTC/RGTC lädt `SurfaceGl` ungepackt.
- **Ohne Wiederholung** (`surfHex`, CPU-Spiegel `HexTiling`): Hex-Kachelung nach Mikkelsen (2022). Drei Abtastungen je Bildpunkt mit Versatz (und Drehung) aus einem Ganzzahl-Hash (PCG), in Java und GLSL gleich. Gewichte mit Kontrast 6, Ableitungen vor allen Verzweigungen (`textureGrad`).
- **Dreiseitig** (`surfSampleD`): für Fels und Eis Projektion auf drei Ebenen, Gewichte |n|⁴, Normalen nach Whiteout. Andere Materialien nehmen die Ebene der größten Achse, damit Wände nicht verzerren.
- **Höhenmischung** (`surfBlend`): bis zu drei Materialien mit Gewichten; die Höhe im Alpha entscheidet, wer an der Grenze oben liegt.
- **Darüber:** `surfWet` (dunkler, glatter, je Material `naesse`), `surfSnow` (auf flachen Flächen zuerst), `surfDust` (Farbe der App).
- **Ferne:** Wird ein Bildpunkt größer als `uSurfFar` mal die Kachel, nimmt der Baustein nur den Mittelwert des Materials (`uSurfMean`). Das spart Abtastungen und flimmert nicht.

## Benutzung

```java
List<Material> kat = Katalog.fjord();
Bake.bake(Katalog.find(kat, "strandkies"), new File("download/texturen/tex_strandkies/Gravel041_2K-JPG.zip"),
          new File("daten/texturen/strandkies"), 2048);
MaterialSet set = new MaterialSet(new File("daten/texturen"), kat);
SurfaceGl surf = new SurfaceGl();
surf.load(gl, set, 1024, true);              // im GL-Faden; beim ersten Mal packt der Treiber
// je Bild, eigenes Programm der App
surf.bind(gl, prog, 4, 5, 6, 0.35);          // drei Textureinheiten, Fernschwelle
```

Im Pixelshader der App:

```glsl
#include "/com/dan/surface/gl/surface.glsl"
// L: Schichten (Reihenfolge der Liste), w: Gewichte, p: Weltpunkt in m, n: Normale, px: Bildpunktgröße in m
Surf s = surfBlend(ivec3(0, 1, 6), vec3(wKies, wSand, wMoos), p, n, length(fwidth(p)));
surfWet(s, 0, nass, n);
surfSnow(s, schnee, n);
vec3 N = s.normal;   // Weltnormale; dazu s.albedo (linear), s.rough, s.ao, s.height
```

`SurfaceGl.glsl()` liefert den Baustein als Text, falls die App kein `#include` auflöst.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Material` | ein Material mit Herkunft und offenen Werten |
| `Katalog` | Liste `fjord()` (14), `find` |
| `Bake` | Backen aus Zip oder Ordner, Ableiten, `material.txt` |
| `MaterialSet` | gebackene Materialien in Listenreihenfolge, Fehlende, Speicherbedarf |
| `HexTiling` | Hex-Kachelung auf der CPU, gleich dem Shader (für Prüfung und Bilder) |
| `gl/SurfaceGl` | Mip-Stufen, Packen im Treiber, Zwischenspeicher `gpu_*.bin`, Texturfelder, Uniforms |
| `gl/surface.glsl` | Baustein: `surfHex`, `surfSample(D)`, `surfBlend`, `surfWet`, `surfSnow`, `surfDust` |
| `demo/Probe` | gerechnete, kachelbare Probesätze im Format von ambientCG (für Prüfung und Vorschau ohne Daten) |
| `demo/SurfaceDemo` | Kontaktbogen: je Material Boden und Wand, rechts nass; unten zwei Kiesstrände (einfach und Hex). Regler für Sonne, Nässe, Schnee, Hex an/aus. `--texturen <ordner>`, `--kante n`, `--bild datei.png` (mit Zeiten) |
| `demo/SurfaceCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“; `--texturen <ordner>` nimmt den echten Strandkies |

## Katalog `fjord()`

| Kurzname | Material | Satz (ambientCG) | Kachel | Besonders |
|---|---|---|---|---|
| strandkies | Strandkies und Gerölle | Gravel041 | 1,6 m | |
| sand | Sand und Gletschermehl | Ground054 | 3,5 m | kühler, entsättigt |
| schiefer_verwittert | Schiefer, verwittert | Rock063 | 4 m | dreiseitig |
| schiefer_frisch | Schiefer, frischer Bruch | Rock058 | 4 m | dreiseitig, heller |
| moraene | Moräne, Schutt, Geröll | Ground108 | 2,2 m | |
| waldboden | Waldboden mit Nadeln und Moos | Ground037 | 2,1 m | |
| moos | Moos und Bartflechte | Moss002 | 1,5 m | |
| firn | Firn und Schnee | Snow014 | 3 m | |
| eis | Gletschereis | Ice003 | 5 m | dreiseitig, blau getönt |
| rinde | Rinde Sitka-Fichte und Hemlock | Bark014 | 1,2 m | fest, dreiseitig |
| holz_entrindet | Entrindetes Holz, Treibholz | Wood035 | 1 m | fest, silbergrau |
| seetang | Seetang und Kelp | aus moos | 1,5 m | olivbraun getönt |
| kutter_holz | Kutter: lackiertes Holz | PaintedWood009C | 1 m | fest |
| kutter_tau | Kutter: Tau | Rope001 | 0,5 m | fest |

Alle Sätze: CC0 1.0 (https://docs.ambientcg.com/license/), Seiten `https://ambientcg.com/view?id=<Satz>`.

## Prüfung

`SurfaceCheck` prüft auf gerechneten Probesätzen:
- Katalog: 14 Materialien, Kurznamen eindeutig, je Material Quelle und Lizenz
- Backen: vier Karten, Höhe im Alpha, Normale DirectX zu OpenGL (Abweichung 1/255), Tönung des Eises, Seetang aus Moos (Rot/Grün 0,50 → 0,76)
- `MaterialSet` mit fehlenden Materialien, Mip-Stufen mit kürzeren Normalen
- **1 km Kiesstrand ohne Wiederholung:** Helligkeit auf 64² und 8² Felder je Kachel gemittelt, Autokorrelation auf vier Linien, auf denen sich eine einfache Kachel genau wiederholt, bei 1 bis 10 Perioden. Einfach gekachelt 1,00 (sonst taugt das Maß nicht), hex 0,04 (Probe) bzw. 0,03 (echter Strandkies Gravel041); Schwelle 0,3. Seit Phase 6; das erste Maß (Phase 5) lief schräg, wo sich keine Kachel wiederholt, und gab mit echtem Kies auch einfach gekachelt nur 0,02.
- Budget: 14 Materialien à 2048² gepackt mit Mip-Stufen 235 MB (≤ 600 MB), à 1024² 59 MB

Phase 5 hat den Baustein außerdem mit Mesa (llvmpipe) gezeichnet: 14 Schichten à 512², gepackt BC7/BC5 beim Hochladen, in 0,5 s geladen; Kontaktbogen wie erwartet.

## Seit welcher Phase

Phase 5 (Werkbuch 5.10 und 8). Die App nutzt den Baustein ab Phase 5a im Geländeshader.

## Grenzen

- Die Hex-Kachelung kostet drei Abtastungen je Karte und Material; mit drei Materialien und dreiseitig bis zu 27 je Karte. Die Fernschwelle begrenzt das.
- Packen im Treiber ist langsam beim ersten Mal (Sekunden je Material bei 2048²) und so gut wie der Treiber. Ein eigener BC7-Packer fehlt.
- Höhe ist nur für die Mischung da, nicht für Parallaxe oder Verschiebung der Scheitel.
- Bis 16 Materialien je Satz (Größe der Uniform-Felder).
