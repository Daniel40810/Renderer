# com.dan.understory – Unterwuchs des Regenwalds

**Stand: Phase 6 von LituyaBay1958 (01.11.2026).** Was unter dem Kronendach eines Küstenregenwalds wächst oder liegt:

- Moospolster
- Farne
- Teufelskeule
- Beerensträucher
- Totholz mit Moos und jungen Bäumen darauf
- Nadelstreu
- Bartflechten

Jede Art steht dort, wo Licht und Feuchte ihr passen. Das Licht am Boden kommt nach Beer-Lambert aus dem Blattflächenindex des Kronendachs, die Feuchte aus der App. Die Netze sind prozedural in zwei Stufen. `gl/` zeichnet alles instanziert, mit Wind und Jahreszeit. Wo die Bäume stehen, weiß das Package nicht; die App gibt den Blattflächenindex und für Bartflechten die Ankerpunkte an den Ästen.

Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` zusätzlich FStyle. Zum Herauskopieren reicht `src/com/dan/understory/`.

## Was es kann

- **Arten** (`Kind`), je mit Licht- und Feuchteanspruch, höchster Dichte, Größe und Reichweite [G, nach Beschreibungen]:

  | Art | Licht am besten | Feuchte am besten | Dichte höchstens | Größe | Reichweite |
  |---|---|---|---|---|---|
  | Moospolster | 7 % | 0,85 | 0,22 je m² | 0,35–0,9 m | 60 m |
  | Farn (Schwert-, Frauenfarn) | 15 % | 0,70 | 0,15 je m² | 0,6–1,4 m Wedel | 80 m |
  | Teufelskeule | 12 % | 0,95 | 0,05 je m² | 1–3 m | 120 m |
  | Beerenstrauch (Heidelbeere, Lachsbeere) | 35 % | 0,60 | 0,09 je m² | 0,5–1,6 m | 100 m |
  | Totholz | (fast egal) | 0,70 | 0,004 je m² | 4–15 m | 250 m |
  | Nadelstreu | 4 % | 0,35 | 0,06 je m² | 1–3 m | 50 m |
  | Bartflechte | 30 % | 0,90 | Anteil der Anker | 0,2–0,6 m | 60 m |

  Eignung = exp(−½ · [(ln(Licht/opt)/Breite)² + ((Feuchte − opt)/Breite)²]). Licht am Boden = exp(−k · Blattflächenindex), k = 0,45 für die Nadelwälder Südostalaskas. Bei einem Blattflächenindex von 8 kommen 2,7 % des Lichts an.
- **Netze** (`PlantMesh.build(art, variante, stufe)`), Stufe 0 nah, Stufe 1 fern:
  - Moos: beuliges Kissen, oben gelblich (234 / 40 Dreiecke).
  - Farn: Trichter aus bogigen Wedeln mit Fiedern, Schwertfarn dunkel, Frauenfarn hell je Variante (424 / 112).
  - Teufelskeule: schräge Stöcke mit großen gelappten Blättern und einer roten Beerentraube (443 / 82).
  - Beerenstrauch: Zweige mit kleinen Blättern, blaue oder orange Beeren je Variante (729 / 64).
  - Totholz: liegender Stamm entlang +x, oben bemoost, morsche Stirnflächen, Stummel, junge Bäume darauf (266 / 48).
  - Streu: flacher, unregelmäßiger Fleck (26 / 10).
  - Bartflechte: Büschel hängender Bänder unter dem Anker (144 / 48).
- **Feld** (`Understory`): Kacheln zu 16 m. Je Art gibt es ein Gitter, mindestens so dicht wie verlangt; jeder Platz trägt eine Pflanze mit der Wahrscheinlichkeit Eignung × Dichte / Gitterdichte. Bartflechten hängen an den Ankern der App (`setAnchors`).
  - Je Bild wählt `update(auge, projektionBlick, neu)` nach Reichweite und Sichtkegel aus. Stufe 0 gibt es bis 35 % der Reichweite, ab 80 % wird gestreut ausgeblendet.
  - Gleicher Startwert, gleicher Unterwuchs.
  - Qualität `LOW` bis `ULTRA`: Faktor auf Reichweite (0,55 bis 1,35) und Dichte (0,6 bis 1).
- **Grafik** (`gl/UnderstoryGl`): je Art, Variante und Stufe ein Aufruf.
  - Wind: Wedel, Zweige und Flechten wiegen mit dem Quadrat der Höhe im Netz, Blätter zittern.
  - Licht: Sonne mit Schattenkarte; Licht unter dem Kronendach als Textur von oben (`Light.canopyTexture`) oder als Wert (`Light.canopy`); Himmel, Dunst; Blätter beidseitig und durchscheinend.
  - Teufelskeule und Beeren färben sich im Herbst und werden kahl (`setSeason(tag)`).

## Benutzung

```java
Understory.Site ort = new Understory.Site() {
    public float ground(double x, double z) { return gelaende.hoehe(x, z); }
    public double lai(double x, double z) { return kronendach.blattflaechenindex(x, z); }
    public double moisture(double x, double z) { return feuchte(x, z); }
};
Understory u = new Understory(List.of(Kind.values()), ort, 1958);
u.apply(Understory.Quality.HIGH);
u.setAnchors(aesteXYZ);                 // je 3 Werte: wo Bartflechten hängen können
UnderstoryGl ugl = new UnderstoryGl(u);
ugl.init(gl4);
// je Bild:
u.update(auge, projektionBlick, 16);
ugl.setWind(nachX, nachZ, mProS, boeigkeit);
ugl.setSeason(tagImJahr);
ugl.update(gl4);
ugl.draw(gl4, view, licht, t);          // licht.canopyTexture: Licht unter dem Kronendach (r, 0..1)
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Kind` | Arten, Ansprüche, Eignung, Beer-Lambert |
| `PlantMesh` | prozedurale Netze je Art, Variante, Stufe |
| `Understory` | Feld: Kacheln, Pflanzen nach Licht und Feuchte, Anker, Auswahl je Bild |
| `gl/UnderstoryGl`, `gl/understory.vert`, `gl/understory.frag`, `gl/understory_shadow.frag` | Zeichnen mit Wind, Licht, Jahreszeit, Schatten |
| `demo/UnderstoryDemo` | Waldboden 200 × 200 m mit Lichtung, Bach und trockenem Rücken (Stämme ohne Kronen, ihr Schatten als Licht unter dem Kronendach). Fenster mit Reglern; `--bild datei.png` zeigt oben die sieben Arten, darunter den Waldboden am Bach und die Lichtung, mit Zeiten |
| `demo/UnderstoryCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

`UnderstoryCheck` prüft:
- **Netze:** je Art 4 Varianten gültig, gleich bei gleichem Startwert, Stufe 1 leichter; Formen (Moos flach, Totholz liegt, Flechte hängt, Teufelskeule steht).
- **Beer-Lambert:** offen 1, Blattflächenindex 8 → 0,027.
- **Ansprüche in der richtigen Reihenfolge:** Licht Beeren > Farn > Moos; Feuchte Teufelskeule > Moos; Streu dunkel und trocken.
- **Gepflanzte Dichte gleich der erwarteten** auf 192 × 192 m, etwa Moos 5 753 erwartet, 5 757 gepflanzt.
- **Startwert:** derselbe pflanzt dasselbe, ein anderer anderes.
- **Auswahl:** Reichweite, Stufe 0 nur nah, Ausblenden 0 bis 1, Sichtkegel.
- **Bartflechten:** 3 606 an Ankern (erwartet 3 612), alle genau am Anker.

Phase 6 hat `UnderstoryDemo` außerdem mit Mesa (llvmpipe) gezeichnet (`docs/status/Vorschau_Phase6_Unterwuchs_Mesa.png` in LituyaBay1958).

## Seit welcher Phase

Phase 6 (Werkbuch 5.7). In LituyaBay1958 ab Phase 6a am Waldboden: Blattflächenindex aus der Dichte des Walds (`com.dan.forest`), Anker an den Ästen, Feuchte aus Bächen und Hang.

## Grenzen

- Keine Physik und keine Kollision; Totholz liegt auf der Höhe seines Fußes und ragt am Hang in die Luft oder in den Boden.
- Die Netze sind einfach (keine Texturen): Moos ist ein Kissen, keine Fasern; Bartflechten sind Bänder.
- Das Licht unter dem Kronendach ist ein Wert je Ort, keine echte Schattenkarte der Kronen; Sonnenflecken fehlen.
- Die Ansprüche sind nach Beschreibungen geschätzt, nicht gemessen [G].
- Der Unterwuchs wird auf dem Prozessor in Kacheln gepflanzt (wie `ForestField`), nicht auf der Grafikkarte wie `ForestGpu`.
