# com.dan.longship – Schiffe der Wikingerzeit

Das Paket baut drei Schiffstypen aus Planken, Spanten, Mast und Segel und lässt sie fahren: unter Segel mit dem Wind, unter Riemen, beim Anlegen an einen Liegeplatz. Der Kern braucht nur Java 21: er erzeugt die Netze aus wenigen Hauptmaßen (Linienriss als Formel), rechnet Fahrt, Kurs, Segelstellung und Riemenschlag und liefert Säulen für eine Auftriebsrechnung. Der Ordner `gl/` zeichnet die Schiffe mit JOGL (OpenGL 4.3); Teer, Maserung, Nieten, Tuchbahnen und Schildbemalung entstehen im Fragment-Shader. Das Paket kennt weder Haithabu noch andere Pakete; das Wasser gibt der Benutzer über eine kleine Schnittstelle (`Water`) hinein, oder er setzt die Lage selbst (`externalPose`).

Zum Herauskopieren reicht `src/com/dan/longship/`. Nur `demo/LongshipDemo` braucht zusätzlich `FStyle.jar`.

**Stand: Timmendorf Phase 18** – bis dahin lag das Paket nur im Projekt Haithabu (Phase 7). Mit dem Umzug in den Renderer kam ein Segelmodell von außen dazu (`Longship.Rig`, siehe unten); ohne Modell fährt alles wie zuvor.

## Was es kann

- **Drei Typen als Datensätze** (`ShipType`):
  - Langschiff nach Haithabu 1: 30,9 × 2,7 m, 26 Riemenpaare, Schilde, Tierkopf, Segel 11 × 8,6 m.
  - Knorr (Handelsschiff): 22 × 6 m, hochbordig, offener Laderaum mit Ladung, drei Riemen an Bug und Heck, Segel 12 × 10,5 m.
  - Faering (Ruderboot): 6,6 × 1,5 m, zwei Riemenpaare, ohne Mast.

  Je Typ gibt es Tiefgang, Freibord, Sprung, Zahl der Plankengänge, Riemenlänge und -abstand, Rah- und Segelmaße, Fahrt unter Riemen und Verdrängung.
- **Rumpf** (`Hull`): Klinkerplanken mit Überlappung und Nietreihen (Außenhaut geteert, Innenhaut Eiche), Kiel, Steven (vorn Tierkopf oder Schnecke), Spanten und Querbalken, Decks oder Laderaum, Mast mit Wanten und Stagen, Seitenruder an Steuerbord, Schilde als eigenes Netz. Für die Auftriebsrechnung: `columns` (Säulen mit Fläche, Kiel- und Bordhöhe), `halfBeam`, `halfBreadthAt`.
- **Fahren** (`Longship`): Befehle in einer Warteschlange (`Command.sail`, `sailTo`, `rowTo`, `moor`, `waitFor`).
  - **Segeln:** Ein Rahsegel zieht raumschots am besten und hart am Wind gar nicht (über 110° zum Wind). Steht der Wind dagegen, greift das Schiff zu den Riemen (`sailStalled`). Die Rah dreht sich quer zum scheinbaren Wind. Segel setzen und bergen dauert rund zehn Sekunden.
  - **Fahrt und Kurs:** Die Fahrt ist träge nach der Verdrängung. Das Ruder wirkt erst mit Fahrt.
  - **Anlegen:** Das Schiff fährt einen Anlaufpunkt an, folgt der Linie zum Liegeplatz, stoppt mit den Riemen ab und macht fest (`Mode.FEST`).
  - **Riemen:** Die Riemen schwingen im Schlag, tauchen ein und heben sich wieder. Liegen sie eingezogen, ruhen sie auf den Querbalken.
  - **Rückmeldungen:** `onWake` meldet Kielwasser (je 4 m Fahrt) und Riemenschläge ins Wasser, `onStroke` meldet die Schläge (etwa für Geräusch oder Spritzer).
- **Segelmodell von außen** (seit Timmendorf Phase 18): `rig` nimmt eine `Longship.Rig` – Fahrt, Krängung und Abdrift auf einem Kurs und der höchste Winkel zum Wind. Haithabu verbindet so das Rahsegel aus `com.dan.sail` (`Sail.LONGSHIP`, `Sail.KNARR`): Das Schiff krängt nach Lee (`heelDeg`, geht ins Rollen ein), treibt ab (`leewayDeg`, seitwärts über Grund) und segelt so hoch, wie das Rahsegel trägt. Das Paket selbst kennt `sail` nicht.
- **Schwimmen:** Ohne Lage von außen folgt das Schiff dem `Water` an Bug, Heck und Seiten (Höhe, Stampfen, Rollen). Mit `externalPose = true` setzt der Benutzer `y`, `pitch` und `roll` selbst, etwa aus einer Auftriebsrechnung über `hull.columns`.
- **Grafik (gl):** `LongshipGl.draw(gl, ships, view, light)`. Die festen Netze liegen einmal je Typ im Grafikspeicher, die beweglichen Teile (Riemen, Rah, Segel, Schoten) werden je Schiff und Bild neu gefüllt. Die Schilde sind nur zu sehen, wenn nicht gerudert wird (die Pforten sind dann frei). Das Licht ist das gleiche wie in `com.dan.timber`: Sonne, Mond, Himmel, Dunst (auch aus einem Rundumbild), Nässe. Unter `Light.waterY` ist der Rumpf dunkel und nass, darüber liegt ein nasser Saum. Das Segel scheint von hinten durch.

## Benutzung

```java
Longship knorr = new Longship(ShipType.KNORR, "Knorr");
knorr.water = (x, z) -> meer.hoehe(x, z);
knorr.windFromDeg = 255; knorr.windSpeed = 6;
knorr.place(-400, 300, 60);
knorr.sailSet = knorr.sailTarget = 1;
knorr.commands.add(Longship.Command.sailTo(-60, 40, 15));
knorr.commands.add(Longship.Command.sail(0));                 // Segel bergen
knorr.commands.add(Longship.Command.moor(0, -40, 0));         // unter Riemen an den Liegeplatz
// je Bild:
knorr.step(dt);
LongshipGl gl = new LongshipGl();
gl.init(gl4);
gl.draw(gl4, List.of(knorr), view, light);
String hud = gl.info();                                       // „1 Schiffe · 9 800 Dreiecke“
```

Koordinaten: x Osten, y oben, Norden = −z. Der Kurs gilt in Grad von Nord im Uhrzeigersinn. Im Schiff liegt x an Steuerbord, y oben und z achteraus. `model()` liefert die Modellmatrix in Spaltenreihenfolge.

## Demo und Selbsttest

- `com.dan.longship.demo.LongshipDemo` hat drei Szenen:
  - `typen`: Die drei Schiffe fahren im Kreis, die Knorr unter Segel.
  - `anlegen`: Die Knorr segelt an, birgt das Segel und macht unter Riemen am Steg fest, wo der Faering schon liegt.
  - `nah`: Das Langschiff rudert, die Kamera blickt von schräg vorn.

  Regler gibt es für Wind, Dünung und Sonne. `LongshipDemo --bild datei.png szene breite höhe [sekunden]` rechnet ein Bild ohne Fenster.
- `com.dan.longship.demo.LongshipCheck` prüft:
  - Maße, Kiel und Windung der Netze; die Außenhaut zeigt nach außen.
  - Säulen und Wasserlinienfläche, Schilde, die Segelfläche gesetzt und aufgetucht, die Segeltabelle.
  - Eine Fahrt über 800 m raumschots und das Rudern gegen den Wind.
  - Das Anlegen auf 1 m und 6° genau, Riemenschläge und Auslage.
  - Gleiche Befehle ergeben gleiche Fahrt.

  Der Selbsttest endet mit „Alles in Ordnung.“.

## Grenzen

- Kein Kreuzen: Gegen den Wind wird gerudert. Ein Schiff ohne Riemen bleibt dann stehen.
- Ohne Segelmodell (`rig`) keine Abdrift und keine Krängung durch den Wind; das Rollen kommt dann nur vom Wasser.
- Keine Hindernisse: Die Befehle müssen freie Wege beschreiben, Stege und Ufer kennt das Paket nicht.
- Keine Besatzung. Die Riemen bewegen sich ohne Ruderer.
- Die Typen Haithabu 1 (um 985) und Knorr nach Haithabu 3 (um 1023) sind jünger als 950; sie stehen hier als Typen.
