# com.dan.render.schwimmer – die schwimmenden Körper im Bild

**Stand: S7b-3 (26.11.2026), Version 0.1** (Seegang Werkbuch 88; SF108 A). Bildmodul der Schicht `render`.
`Schwimmerbild` zeichnet die Netze der Schwimmer (Fähre, Kutter, Tonnen, Treibgut) im selben Licht wie Häuser und
Gehölze, mit deren Schatten, und zeichnet sich selbst in die Karte des Schattens von `render.bau`.

## Schicht

```
com.dan.render.schwimmer        core + core.gpu + LWJGL      Schwimmerbild (Bildteil), Shader
```

Das Package kennt keine Physik. Die Netze kommen als Zahlen (`physics.auftrieb.Schiffbau`, die Anwendung
`lauf.Flotte` reicht sie herein), die Lagen je Bild ebenso (`Schwimmerbild.lagen`).

## Ablage

| Puffer | Inhalt |
|---|---|
| Scheitel | je Scheitel 12 float: Lage im Körper (x vorn, y Backbord, z oben ab der Unterkante), Material; Normale, Nummer des Körpers; u, v, w |
| Material | je Material 8 float: Albedo (linear), Art (0 matt, 1 Lack, 2 Glas, 3 Fensterband, 4 Metall, 5 Holz); Glanz, Breite und Abstand der Fenster |
| Lagen | je Körper eine mat4 vom Körper in den Raum relativ zum Auge (x Ost, y oben, z Süd); je Bild auf dem Prozessor in double aus der Lage in der Welt gerechnet |

## Folge im Rahmen

Häuser (legen `Bau/Schatten` an), Bewuchs, **Schwimmer**, Gelände, Seegang, Wasser … Die Schwimmer zeichnen sich nach
den Häusern in die Karte des Schattens (Laden, nicht Löschen); Gelände und Wasser bekommen ihren Schatten so ohne
Änderung. Das Wasser verdeckt den Rumpf unter der Fläche über die Tiefe.

## Shader

| Teil | Aufgabe |
|---|---|
| `schwimmer.glsl` | Bindungen, Lage relativ zum Auge mit Erdkrümmung |
| `licht.comp`, `kugel.glsl` | Himmel und Boden als 9 Glieder nach Kugelflächenfunktionen (wie render.bewuchs) |
| `schatten.vert/.frag` | Körper in die Karten des Schattens (Abbildung aus `Bau/Schattenwerte`) |
| `schwimmer.vert/.frag` | Licht wie die Häuser (Lambert, Sonne und Mond mit der Durchlässigkeit der Luft, Himmel); Lack und Glas mit Glanz nach Schlick; Fensterband [G] |

## Grenzen

- Lichter seit S7e1 (`Lichterbild`, unten); ein Licht beleuchtet weder Rumpf noch Wasser, es hat nur Hof und Spiegelbild.
- Kein Wasser an Deck, keine Gischt am Bug (S8).
- Prüfung: `lauf.demo.SchwimmerCheck` Teil 4 (Prüfsteine 17–19), Attest `docs/status/S7b_Schwimmer.md`.

## Seit S7e1 (Seegang Werkbuch 99, SF148 A): Lichter

`Lichterbild` (0.1, Bildteil „Lichter“, `feuer.glsl`, `feuer.vert/.frag`) zeichnet die Lichter der Schwimmer als Punkte mit
Hof und ihr Spiegelbild im Wasser:

- Je Licht 11 double von der Anwendung (`lauf.Flotte.lichter`): Ort, Lichtstärke mit der Kennung, Farbe, Sektor (Mitte,
  halbe Breite; rundum ab π), Höhe des Wassers am Licht. Ob das Auge im Sektor steht, rechnet das Bild je Bild auf der CPU,
  mit weichem Rand von 2° [G].
- Helligkeit aus der Beleuchtungsstärke am Auge, E = I / d², mal der Durchlässigkeit der Luft exp(−3,912 d / Sicht)
  (Koschmieder) [G]; verteilt über den Raumwinkel als Kern (Gauß-Glocke) und Hof (1/(1 + r²/w²)², Anteil 0,5 %, bis L5e1 3 %) [G];
  vorbelichtet wie der Himmel (`sonneE.w`).
- Spiegelbild: das Licht an der ruhigen Fläche gespiegelt, Stärke nach Fresnel (n = 1,34, Schlick), ein senkrechter
  Streif, 8-mal höher als breit [G], am Punkt der Fläche auf dem Strahl zum Spiegelbild, um 2 % seines Abstands zum Auge
  vorgerückt, damit die Tiefe des Wassers ihn nicht verdeckt, wohl aber Rümpfe und Land davor [G].
- Folge: nach Himmel und Kimmung (der Himmel schreibt jeden Punkt ohne Tiefe neu); die Lichter testen die Tiefe und
  schreiben keine; Mischung additiv (vormultipliziert mit α = 0).
- Nacht: an, wenn die Sonne unter +2° steht, ganz ab −2° [G].
- Seit L5e1 (Befund 1: grüne und gelbe Feuer wurden weiß): Bei Nacht ist ein Feuer hundertfach über der Belichtung; die
  Filmkurve sättigt jede Schicht für sich. Die Farbe hält jetzt `render.frame` (Überstrahltes bei Nacht farbtreu
  gestaucht, `Belichtung.farbtreu`). Ein Kappen im Licht selbst (erster Versuch in L5e1b) senkte die Leuchtdichte unter
  die Schwelle der Stäbchen, und das Feuer blieb weiß. Hof und Streif laufen zum Rand ihres Vierecks auf null aus (vorher
  war bei Nacht die Kante zu sehen). Der Streif reicht im Bild nicht über das Wasser senkrecht unter dem Licht hinaus
  (Spiegelpunkte liegen zwischen Auge und Licht; Befund 4: bei fernen Lichtern stand er als Spitze über der Kimm).
- Prüfung: `lauf.demo.SeezeichenCheck` Teil 3 (Feuer von SA 13 an gegen aus ≥ 5 [G], grün im Bild, Takt über eine Periode
  gegen die Kennung), Bilder `S7e_*.png`.
