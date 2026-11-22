# com.dan.physics.vegetation

Salzwiese als Physik (Werkbuch HalligHooge Teil A, 12.14; Datei 87 Plan, Datei 88 Bau). Stand P13b (Salzwiese 0.1,
VegetationModul 0.1): Pflanzen je Zone und Jahreszeit, daraus Rauheit und Bedeckung je Zelle am Bus. Eine Fassung auf der
Grafikkarte gibt es nicht (die Felder ändern sich einmal am Tag).

| Datei | Inhalt |
|---|---|
| `Salzwiese.java` | Wuchshöhe und Bedeckung je Zone (Pionierzone, untere, obere Salzwiese) im Sommer und Winter, Jahreszeit als Kosinus mit Tiefpunkt am Tag 20; Weide kürzt auf 0,4; z0 = 0,1 · Höhe; Manning-n nach Strickler n = (30 z0)^(1/6) / 26 zur Auskunft |
| `VegetationModul.java` | Modul `vegetation` am Bus, getaktet mit 300 s, rechnet einmal am Tag neu: Anteile der Zonen je Zelle aus den Zonen des Gebiets (beweidet, wo die Gestalt Fenne sagt); schreibt `surf.z0.vegetation` (geometrisches Mittel über den bewachsenen Teil, 0 unter dem Mindestanteil 0,5) und `veg.cover` (Bedeckung über die ganze Zelle) |
| `demo/VegetationCheck.java` | Kern ohne Lauf: Statistik der Überflutung an einer gerechneten Tide, Grenzen der Zonen, Pflanzen je Jahreszeit, Anteile je Zelle, Zonen in der Gestalt und zurück; Attest `docs/status/P13b_Vegetation.md` |

**Fenne (F155 A, Datei 89):** Hinter dem Sommerdeich erreicht die tägliche Tide die Fenne nicht; die Fenne hat deshalb keine Tidezone, sondern die Zone 4 „Fenne“: Pflanzen wie die obere Salzwiese, beweidet. Die Höhenzonen gelten nur auf dem Vorland (Klasse Salzwiese).

Die Zonen selbst (Byte Zone, Byte Höhe über MThw, 1 m, Kacheln wie die Gestalt) stehen in `com.dan.core.szene.Zonen`,
die Statistik der Überflutung (MThw, MSpThw, MNpThw, Dauer, Anteil der Tiden mit Wasser) in
`com.dan.core.gitter.Ueberflutung`. Gebaut werden die Zonen im Schritt `zonen` der Aufbereitung (`Zonenkarte`), aus der
Statistik des Laufs B1 (`com.dan.lauf.demo.VegetationLaufCheck --teil statistik`).

**Rückwirkung** (F154 A):

- hydro: `surf.z0` als Verbund (Maximum) aus `surf.z0.sediment` und `surf.z0.vegetation`; hydro liest ihn auf den Gittern
  in `rauheitGitter` (G3), Manning nach Strickler, n ≥ 0,015; wo z0 = 0, gilt die Packung.
- sediment: Schubspannung am Korn geteilt, u_Korn = u · √(1 − 0,9 · Bedeckung).
- aeolian: Fanglänge L_Fang / max(0,25; Bedeckung) auf bewachsenen Zellen.
- atmos: z0 aus `surf.z0`, sonst 1 mm.

```java
VegetationModul vm = new VegetationModul(List.of(g3), Zonen.lies(gebiet), Gestalt.lies(new File(gebiet, "gestalt")),
        new Salzwiese(), 300, Instant.parse("2013-12-01T00:00:00Z"));
bus.anmelden(vm);   // vor sediment; surf.z0 als Verbund anmelden
```

```
java -cp dist\Renderer.jar com.dan.physics.vegetation.demo.VegetationCheck
```

**Werte** (Annahmen, Datei 87, 3.2; zu prüfen gegen Aufnahmen aus dem Nationalpark in P13d):

| Zone | Höhe über MThw | Höhe Sommer / Winter | Bedeckung Sommer / Winter |
|---|---|---|---|
| Pionierzone (Queller, Schlickgras) | −0,40 … 0 m | 0,20 / 0,04 m | 0,40 / 0,10 |
| untere Salzwiese (Andelrasen) | 0 … +0,35 m | 0,15 / 0,08 m | 0,90 / 0,75 |
| obere Salzwiese (Rotschwingel, Strandflieder) | über +0,35 m | 0,30 / 0,15 m | 0,95 / 0,90 |
