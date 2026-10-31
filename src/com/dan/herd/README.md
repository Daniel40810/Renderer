# com.dan.herd – Haustiere

**Stand: Timmendorf Phase 18** – unverändert aus Haithabu (Phase 10) in den Renderer gezogen.

Das Paket bringt Haustiere in eine Szene: Hühner, Ziegen, Schweine und Hunde. Es sind einfache Körper mit kurzen Bewegungen (Haithabu F10). Jedes Tier gehört zu einem Gehege und tut abwechselnd etwas Kurzes: stehen, fressen, ein paar Schritte gehen, liegen. Dabei hält es sich lose an die anderen. Kommt ein Mensch zu nahe, läuft es weg; der Hund kommt dagegen neugierig näher und setzt sich. Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet die Tiere mit JOGL (OpenGL 4.3, ein Netz je Art, eine Instanz je Tier); Beine, Kopf und Schwanz bewegt der Vertex-Shader. Zum Herauskopieren reicht `src/com/dan/herd/`; nur `demo/HerdDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Arten** (`Herd.Kind`) mit Maßen und Gangart [G]:

  | Art | Länge | Höhe | Gehen | Laufen | Schritte/s | Flucht |
  |---|---|---|---|---|---|---|
  | Huhn | 0,38 m | 0,40 m | 0,35 m/s | 2,2 m/s | 3,2 | 3 m |
  | Ziege | 1,05 m | 0,70 m | 0,55 m/s | 2,8 m/s | 1,6 | 7 m |
  | Schwein | 1,10 m | 0,62 m | 0,45 m/s | 2,0 m/s | 1,5 | 4 m |
  | Hund | 0,95 m | 0,58 m | 0,9 m/s | 4,0 m/s | 2,0 | kommt näher |

- **Verhalten** (`Herd.State`): stehen, fressen, gehen, laufen, liegen, sitzen, weg (im Stall).
  - Hühner picken in schnellen Stößen.
  - Ziegen und Schweine halten den Kopf lange am Boden und machen ab und zu einen Schritt.
  - Mittags liegen sie öfter.
  - Der Hund trabt zwischen seinen Wegpunkten, schnüffelt, setzt sich und bellt (`onBark`, für einen Klang in einem anderen Package).
- **Rufe** (seit Haithabu Phase 14, `onCall`): Hühner gackern (alle 25 s im Mittel), Ziegen meckern (alle 2 min), Schweine grunzen beim Fressen (alle 10 s); beim Weglaufen rufen sie warnend (Stärke 1). Der erste Vogel jedes Hühnerhofs ist der Hahn (`rooster()`): Er kräht in der Dämmerung (Sonnenhöhe −8° bis 12°, morgens wie abends) alle 40 s, auch aus dem Stall. Nachts ist es still [G].
- **Tag und Jahr:** Nachts sind die Hühner im Stall, Ziegen und Schweine liegen. Bei Regen über 2 mm/h gehen die Hühner hinein, bei mehr als 20 cm Schnee auch Ziegen und Schweine.
- **Gelände:** `ground` gibt die Höhe, `walkable` sperrt Wasser, Häuser und Zäune. Gehege (`Pen`) sind Kreise, der Hund hat dazu Wegpunkte.
- **Netze** (`AnimalMesh`): aus Ellipsoiden und Röhren, mit Teilen für Rumpf, Kopf, vier Beine und Schwanz.
  - Huhn mit Kamm und Kehllappen.
  - Ziege mit nach hinten gebogenen Hörnern und Bart.
  - Schwein der Wikingerzeit: klein, dunkel und borstig, mit langem Rüssel [G].
  - Spitzartiger Hund mit Stehohren und Ringelschwanz.
  - Fellfarben nach einem Ton je Tier (`coat`).
- **Grafik** (`gl/HerdGl`):
  - Beine im Trab: die diagonalen Paare schwingen im Gleichtakt.
  - Der Kopf senkt sich zum Fressen, der Schwanz wedelt.
  - Beim Liegen klappen die Beine ein, beim Sitzen kippt der Hund um die hintere Hüfte.
  - Licht wie die Flächen der App, dazu Wolkenschatten.

## Benutzung

```java
Herd herd = new Herd();
herd.ground = (x, z) -> terrain.height(x, z);
herd.walkable = (x, z) -> terrain.height(x, z) > level + 0.1 && !houses.contains(x, z);
herd.pens.add(new Herd.Pen("Hühnerhof", Herd.Kind.HUHN, x, z, 6, 7));
herd.disturbers.add(eye);                  // das Auge, je Bild nachgeführt
herd.sunAltDeg = sunAlt;                   // je Bild, dazu rain und snow
herd.step(dt);
herdGl.draw(gl, herd, view, light);
```

## Demo und Selbsttest

- `demo/HerdDemo`: der Hof von oben. Die Maus ist ein Mensch, ein Rechtsklick wechselt zwischen Tag und Nacht. `--bild datei.png` schreibt den Hof als Bild.
- `demo/HerdCheck` prüft ohne Fenster:
  - die Maße der Netze;
  - dass alle Tiere im Gehege und auf dem Boden bleiben und keines in den Tümpel läuft;
  - Ziegen und Hühner, die weglaufen;
  - den Hund, der kommt und sich setzt;
  - Nacht, Regen und Schnee;
  - die Rufe am Tag, den Hahn im Morgengrauen und die stille Nacht;
  - die Schrittfrequenz;
  - den Startwert.

  Endet mit „Alles in Ordnung.“

## Grenzen

- Keine Knochen, kein Fell: Die Beine sind starre Röhren, die um die Hüfte schwingen.
- Tiere weichen einander nur über das Gehen zur Gruppe aus; sie können sich kurz überlappen.
- Ställe sind nicht gebaut: „im Stall“ heißt unsichtbar.
- Der Hahn sieht aus wie die Hennen.
