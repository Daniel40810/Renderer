# com.dan.physics.gischt – Bündel der Gischt

**Seit Seegang S8a (Seegang Werkbuch, Datei 104, SF158 A), Version 0.1.** `Buendel` ist `com.dan.spray.Parcel` aus Lighthouse,
Zeile für Zeile in die Schicht physics geholt: Ein Bündel Wasser in der Luft (Spritzwasser, Nebel), seine Tropfen als Formel aus
Bündel, Nummer und Alter – Schwerkraft und lineare Luftreibung, geschlossen gelöst. Dieselbe Rechnung steht in
`render/schwimmer/gischt.glsl`. Den Schwall am Bug wirft `com.dan.lauf.Bugschwall` (dort sind Körper und Bugschlag bekannt).

Prüfung: `lauf.demo.SeegangS8Check` Teil 1 (geschlossene Lösung gegen Runge–Kutta) und Teil 2 (Karte gegen Prozessor).
