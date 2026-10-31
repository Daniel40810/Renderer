# com.dan.rime – Raueis und Raureif

Eis, das sich an Objekten ansetzt: Raueis aus unterkühlten Nebeltröpfchen, das gegen den Wind als Fahne wächst, Raureif aus klaren Nächten und Klareis aus Eisregen. Das Package kennt keine Fichte und keinen Mast: das Wetter am Objekt kommt als `Rime.Lage`, das Objekt als `Rime.Ansatz`. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`.

Neu seit Brocken Phase 1 (F9 A) als Hülle; **seit Brocken Phase 10 ausgebaut:** Fangkoeffizient nach Finstad, nasses Wachstum, raue Oberflächen, Raten, Lasten, Federn und Fahnen als Form im Shader.

Zum Herauskopieren reicht `src/com/dan/rime/`.

## Was es kann

- **Raueis nach Makkonen (2000):** dM/dt = α1 · α2 · α3 · w · v · A mit Flüssigwasser w, Wind v und Querschnitt A (an der Fahne ihre Breite, am Klareis der Durchmesser mit dem Eis rundum); α2 = 1.
- **α1 nach Finstad u. a. (1988)** aus der Trägheitszahl K = ρw·d²·v / 9μD und φ = Re²/K: α1 = A − 0,028 − C·(B − 0,0454) mit den Fits A, B, C der Arbeit; gilt ab K = 0,17, darunter 0. Dünne Zweige fangen besser als dicke Stämme, große Tröpfchen besser als kleine, ein glatter Mast von 30 cm im Nebel fängt fast nichts.
- **Raue Oberflächen** (`Ansatz.rau`, seit Phase 10): Fels, Rinde, Fichtenzweige, Masten mit Kanten fangen wie Zylinder von höchstens 3 cm (`RAU`) [G] – auf der Luvseite wachsen die Federn als viele kleine Fänger.
- **α3 (nasses Wachstum)** [Näherung, G]: unter −3 °C friert alles, darüber bei großem Fluss bis 0,4 an der Nullgradgrenze; unter 0,8 wird es Klareis.
- **Dichte nach Macklin (1962):** ρ = 110 · R^0,76, R = −d·v / 2T, R höchstens 10 (rund 630 kg/m³).
- **Raten:** `mmJeStunde(d, rau, lage)` gibt das Wachstum am Anfang in mm/h, `alpha1(...)`, `alpha3(...)`, `stokes(...)` einzeln.
- **Form:** die Fahne wächst gegen den Wind (`richtung` = woher der Wind kommt) und wird dabei breiter (Breite = D + 0,3 · Dicke [G]); Klareis legt sich als Ring rundum.
- **Raureif** in klaren Frostnächten (0,2 mm/h [G], ohne Richtung), **Klareis** bei Tropfen ab 100 µm oder nassem Wachstum.
- **Tauen** nach Temperatur und Sonne; Fahnen brechen ab und melden es über `Abfall` (für den Klang).
- **Lasten:** `baumLast(zweig, zweigMeter)` – Masse je Meter am Zweig mal Länge der Zweige im Wind [G].
- **Presets:** `raueislage()`, `klareNacht()`, `eisregen()`, `tauwetter()`.
- **Offene Werte, seed, Qualitätsstufen:** alle Felder öffentlich; `seed` bestimmt, wann Fahnen abbrechen; `Quality` setzt den längsten Rechenschritt (600 bis 60 s).
- **Grafik (gl):** der Baustein `rime.glsl` für fremde Shader: `rimeCover`/`rimeLocal` (Bedeckung nach Luv, Art, Dicke; ab 5 cm greift es um die Kanten), `rimeFeder` (Federn statt glatter Schicht), `rimeOffset`/`rimeOffsetLocal` (Ecken gegen den Wind schieben: die Fahne als Form), `rimeApply`/`rimeApplyLocal` (Farbe und Rauheit). `RimeGl.bind` setzt die Uniforms aus einem Ansatz.
- **Demo:** sechs Stäbe von 5 mm bis 30 cm (der letzte rau) im Schnitt, Fahnen im Zeitraffer, rechts die **Raten** in mm/h nach Durchmesser und Wind (5 bis 20 m/s) und α1.

## Benutzung

```java
Rime rime = new Rime();
Rime.Ansatz zweig = new Rime.Ansatz(0.004, true);   // Fichtenzweig, rau
Rime.Lage lage = Rime.raueislage();
lage.wind = feld.speed; lage.fromDeg = feld.fromDeg; lage.tropfenUm = 20;
rime.step(zweig, lage, 3600);                         // eine Stunde
double mmh = Rime.mmJeStunde(0.004, true, lage);      // Rate jetzt
double kg = Rime.baumLast(zweig, 60);                 // Krüppelfichte
rime.onAbfall = (a, kgJeMeter) -> klang.eisFaellt(kgJeMeter);
// im Shader: #include "/com/dan/rime/gl/rime.glsl"
//   p += rimeOffset(n);  farbe = rimeApply(farbe, n, rauheit);
RimeGl.bind(gl4, program, zweig, lage.fromDeg);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Rime` | Kern: Lage, Ansatz, α1 (Finstad), α3, Rate, Dichte (Macklin), Raureif, Klareis, Tauen, Abfall, Lasten, Presets |
| `gl/RimeGl`, `gl/rime.glsl` | Baustein für fremde Shader: Bedeckung, Federn, Versatz gegen den Wind, Farbe und Rauheit |
| `demo/RimeDemo` | Sechs Stäbe im Schnitt, Fahnen im Zeitraffer, Tabelle der Raten; `--bild datei.png [stunden]` |
| `demo/RimeCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Grenzen

- Die Fits nach Finstad u. a. (1988) sind nach der Darstellung bei Makkonen (2000) übernommen [nicht im Wortlaut gelesen]; α3 ist eine Näherung [G], kein Wärmehaushalt nach Messinger.
- `RAU` (3 cm) und die Breite der Fahne (0,3 · Dicke) sind geschätzt [G]; Fahnen aus Federn fangen in Wirklichkeit unterschiedlich nach Wind und Form.
- Keine Sublimation bei Frost und trockener Luft: Eis bleibt, bis es taut oder abfällt.
- Die Form im Shader verschiebt Ecken vorhandener Netze; dünne Netze (ein Draht) bekommen keine eigene Geometrie.
