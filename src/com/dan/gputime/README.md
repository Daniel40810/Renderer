# com.dan.gputime – Abschnitte der Grafikkarten-Zeit

**Stand: LituyaBay1958 W6 Runde 6.** Misst, wie lange die Grafikkarte für benannte Abschnitte eines Bilds braucht, etwa „Spiegel“, „Knick“ oder „Schattenkarte“ innerhalb eines größeren Durchgangs. Dafür setzt das Package Zeitstempel (`GL_TIMESTAMP`) und wartet im Bildlauf nicht auf die Ergebnisse. Das Package kennt keine Szene. Zum Herauskopieren reicht `src/com/dan/gputime/`. Der Kern braucht nur Java 21, `gl/` braucht JOGL (OpenGL 3.3).

## Rechnung

| Schritt | Wie |
|---|---|
| Stempel | je Abschnitt ein Paar, am Anfang und am Ende; ein Ring von 4 Bildern, die Stempel werden wiederverwendet |
| Lesen | am Ende eines Bilds das älteste Bild im Ring, aber nur, wenn alle seine Stempel fertig sind. Sonst bleibt das alte Mittel, und `lost()` zählt das Bild |
| Mehrfach | ein Abschnitt, der im Bild mehrmals vorkommt, zählt als Summe; verschachtelte Abschnitte sind erlaubt |
| Fehlt | ein Abschnitt, der in einem Bild fehlt, zählt dort als 0 |
| Mittel | gleitend, `weight` 0,1 (rund 10 Bilder) |

## Klassen

| Klasse | Aufgabe |
|---|---|
| `GpuSpans` | `begin(name)`, `end(name)`, `endFrame()`, `ms(name)`, `toString()` („Spiegel 1,23 · Knick 0,45“), `enabled` |
| `GpuSpans.Clock` | Quelle der Stempel (anlegen, setzen, fertig?, ns) |
| `gl/GlClock` | die Uhr für JOGL: `use(gl)` je Bild, dann `glQueryCounter`. Nur im GL-Faden |
| `demo/GpuSpansCheck` | Prüfung mit einer erfundenen Uhr, ohne Grafikkarte |

## Einbau

```java
GlClock uhr = new GlClock();
GpuSpans teile = new GpuSpans(uhr);
// je Bild
uhr.use(gl);
teile.begin("Spiegel"); spiegelBild(gl); teile.end("Spiegel");
…
teile.endFrame();
status += "Teile (ms): " + teile;
```

## Grenzen

- Ein Stempel misst, wann die Grafikkarte die Stelle im Befehlsstrom erreicht. Laufen Arbeiten überlappend (Compute neben Zeichnen), ist die Summe der Abschnitte nicht die Bildzeit.
- Jeder Stempel kostet wenig, aber nicht nichts. Für Zeitvergleiche gilt deshalb dasselbe Paar an und aus im selben Lauf.
- Vergleiche zwischen Läufen streuen um mehrere ms, weil Takt und Wärme der Grafikkarte schwanken.
