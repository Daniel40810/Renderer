# com.dan.vessel – Kutter, Anker und Kette

**Stand: Kern (LituyaBay1958 Phase 15), Abspielen und Autopilot (15a).** Fischkutter der 1950er als Starrkörper mit sechs Freiheiten auf beliebigem Wasser, mit einer Ankerkette aus Massenpunkten. Gebaut für die drei Troller von Lituya Bay 1958 (Edrie, Badger, Sunmore), die eine Welle von 15 bis 23 m an ihren Ankerplätzen erlebten. Das Package kennt kein anderes. Wasser, Welle und Grund kommen über die kleine Schnittstelle `Water`. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL. `demo/` braucht nur den eigenen Ordner `gl/` und JOGL. Später für Timmendorf (Niendorfer Hafen), Lighthouse und Kiel.

Zum Herauskopieren reicht `src/com/dan/vessel/`.

## Was es kann

- **Rumpf aus wenigen Maßen** (`Hull`): Länge, Breite, Seitenhöhe, Tiefgang.
  - Wasserlinienriss voll mittschiffs, vorn spitz, achtern bis auf den Spiegel eingezogen.
  - Spant als V mit flachem Unterwasserkörper, darunter ein durchgehender Kiel mit Vorfuß.
  - Sprung des Decks nach vorn und achtern, Steuerhaus mittschiffs.
  - Daraus ein Raster von Proben (Würfel, 30 cm bei 12 m Länge) sowie Verdrängung, Formschwerpunkt (KB), Wasserlinie und ihr Trägheitsmoment.
- **Starrkörper** (`Boat`):
  - Jede Probe trägt nach ihrem Anteil unter Wasser.
  - Widerstand gegen das bewegte Wasser: linear je Volumen (senkrecht stark), quadratisch auf der Außenhaut, längs schlank.
  - Die senkrechte Geschwindigkeit des Wassers kommt aus der Änderung der Oberfläche.
  - Wellenwiderstand über der Rumpfgeschwindigkeit, zusätzliche Masse des mitbewegten Wassers.
  - Daraus folgen Schwimmlage, Tauchen, Stampfen, Rollen und Kentern von selbst, auch wenn eine Welle das Boot 20 m anhebt und mit ihrer Strömung trägt.
- **Volllaufen und Sinken:**
  - Liegt das Deck unter Wasser, strömt durch Öffnungen Wasser ein, Q = C<sub>d</sub> A √(2 g h). Gekentert läuft es durch die Tür des Steuerhauses ein.
  - Das Wasser im Rumpf sammelt sich in den tiefsten Proben und läuft zur tiefen Seite (freie Oberfläche). Eine Lenzpumpe arbeitet dagegen.
  - Trägt der Rumpf nicht mehr, sinkt er und liegt auf dem Grund auf.
- **Grund:** Auflaufen und Stranden mit Reibung.
- **Maschine und Ruder:** Schub an der Schraube, Seitenkraft am Ruder nach Fahrt und Schraubenstrahl.
- **Ankerkette aus Massenpunkten** (`Chain`):
  - Glieder zu rund 2 m, XPBD mit kleinen Teilschritten, Nachgiebigkeit l/EA.
  - Gewicht unter Wasser, Widerstand quer und längs, Grund mit Reibung.
  - Der Zug je Glied kommt aus dem Lagrange-Faktor; die Kraft an der Klüse geht in jedem Teilschritt ins Boot.
  - Übersteigt der Zug die Bruchlast, reißt die Kette.
  - Der Anker hält bis zu seiner Haltekraft und slippt dann; festgehakt hält er alles, wie bei der Edrie.
  - `catenaryTension` gibt die Kettenlinie mit Grundberührung zum Vergleich.
- **Vorgaben** (`Kutter`): Edrie, Badger, Sunmore.
  - Belegt nach MILLER60: rund 40 Fuß, Ankertiefen 5 und 4 Faden, 40 Faden Kette der Edrie, Anker festgehakt.
  - Gestaltet [G]: Form, rund 21–25 t, GM 1,0 m, Kette 3/8″ Güte 30 (42 kN), 9 kN Schub.
- **Netze aus Code** (`Mesh`):
  - Rumpf aus den Spanten, unter Wasser kupferrot, schwarzer Wasserpass, weißes Freibord, grüner Schergang.
  - Deck aus Holz, Spiegel, Schanzkleid, Steuerhaus mit Fensterband.
  - Mast mit Topplicht, zwei aufgeholte Trollstangen, Ankerwinde.
- **Abspielen und Steuern (15a):** `Boat.pose(pos, quat)` setzt Lage und Drehung einer vorgerechneten Bahn, `orientation()` gibt die Drehung; `steuern(x, z)` ist ein Autopilot (Ruder voll bei 30° Abweichung). Befund 15a: das Ruder drehte verkehrt herum (die Seitenkraft am Heck zeigte nach Steuerbord); jetzt dreht Ruder nach Steuerbord den Bug nach Steuerbord, und `VesselCheck` zählt die Drehung über 180° hinaus.
- **`gl/VesselGl`:** Netze hochladen und mit der Modellmatrix des Boots zeichnen, Ketten als Linienzug (auch aus abgespielten Knoten), beliebige weitere Netze (auch durchscheinend mit Glanz). Licht wie `forest` und `buoyancy`; Ausgabe linear oder mit ACES.

## Benutzung

```java
Water meer = new Water() {                       // das eigene Wasser hineingeben
    public double height(double x, double z) { return welle.hoehe(x, z); }
    public double[] velocity(double x, double z) { return welle.stroemung(x, z); }
    public double bed(double x, double z) { return grund.hoehe(x, z); }
};
Boat edrie = Kutter.edrie();
edrie.settle(meer, x, z, 270);                   // in die Schwimmlage, Bug nach Westen
Chain kette = Kutter.edrieKette();               // 40 Faden 3/8″, Anker festgehakt
double[] k = edrie.hull.fairlead();
kette.lay(meer, ankerX, ankerZ, edrie.toWorld(k[0], k[1], k[2]));
edrie.attach(kette);
edrie.step(meer, dt);                            // je Bild; Teilschritte von 4 ms, die Kette läuft mit
float[] modell = edrie.model();                  // für VesselGl.draw(gl, edrie, view, light)
if (kette.broken()) …                            // kette.brokenTime(), kette.peak()
```

## Prüfung

`demo/VesselCheck` läuft ohne Fenster, 21 Prüfungen in rund 17 s:
- Die drei Troller sind 39–41 Fuß lang und wiegen 21–25 t.
- Schwimmlage auf 1 mm. GZ bei 2° gleich GM·sin φ (3 %), die Hebelkurve richtet bis über 60° auf.
- Rollperiode 3,15 s gegen die Handrechnung 3,15 s, Tauchen 1,73 gegen 1,71 s; in Ruhe trocken.
- Kette in Ruhe gegen die Kettenlinie auf 1 % (Zug an der Klüse und Länge auf dem Grund).
- Der Anker slippt über seiner Haltekraft; festgehakt reißt die Kette bei der Bruchlast.
- Kopflastig kentert das Boot; voll Wasser sinkt es auf den Grund.
- Mit voller Maschine läuft es 8,3 kn (Rumpfgeschwindigkeit 8,0 kn), und es dreht mit dem Ruder.
- **Die Edrie in der Einzelwelle** (Bucht in 5 Faden, Front 15 m):
  - Die Kette reißt, als das Boot mit der Welle steigt („The anchor chain snapped as the boat rose with the wave“, MILLER60).
  - Das Boot steigt 24 m und wird zum Ufer getragen.
  - Der Rückstrom trägt es aufrecht und schwimmend zurück.
  - Rechenzeit rund 0,1 s je Sekunde Welle.

`demo/VesselDemo` öffnet ein Fenster (Leertaste von vorn, 1/2/3 Blick, +/− Tempo, P anhalten). `--bild datei.png` schreibt einen Bogen mit vier Augenblicken und den Kurven: Hub, Wasser am Boot, Trimm und Zug an der Klüse mit der Bruchlast. Die Bucht der Demo (`demo/Bucht`) ist gerechnet [G]: Einzelwelle sech², Reflexion am Ufer, Auflauf mit Rückstrom.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Water` | Wasserstand, Strömung, Grund, Wind (Schnittstelle) |
| `Hull` | Form, Proben, Hydrostatik, Klüse, Schraube |
| `Boat` | Starrkörper, Auftrieb, Widerstand, Volllaufen, Sinken, Grund, Maschine, Ruder, Ketten |
| `Chain` | Kette aus Massenpunkten, Grund, Anker, Reißen, Kettenlinie |
| `Kutter` | Vorgaben Edrie, Badger, Sunmore |
| `Mesh` | Netz eines Kutters |
| `gl/VesselGl`, `vessel.vert`, `vessel.frag` | Zeichnen |
| `demo/Bucht`, `VesselCheck`, `VesselDemo` | Prüfbucht, Prüfung, Vorführung |
