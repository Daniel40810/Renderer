# com.dan.physics.hydro.gpu – hydro auf der Karte

**Stand: P4d (12.11.2026).** Der Betrieb von hydro in float auf der Grafikkarte über `com.dan.core.gpu` (Vulkan), mit denselben Formeln wie die Referenz `com.dan.physics.hydro.Hydro` (Werkbuch HalligHooge Teil A, 12.5, Plan und Stand P4c). Seit P5c rechnet `KarteRechnung` das Modul `HydroModul` am Bus auf der Karte.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `HydroKarte` | ein Gitter auf der Karte; Einrichtung aus einem `Hydro`, `hoch()` und `herunter()` gleichen den Stand ab |
| `KetteKarte` | geschachtelte Gitter auf der Karte wie `Kette`, je grobem Schritt ein Befehlspuffer |
| `KarteRechnung` | seit P5c: `Rechnung` von `HydroModul` auf der Karte – je Stufe eine `HydroKarte` oder `KetteKarte`; gleiche Schritte je Kopplungsintervall; der Antrieb geht einmal je Intervall auf die Karte (`antriebJeSchritt = false`), die Summen der Flüsse sammelt `sammeln.comp` |

## Kernels

Je Schritt sechs Aufrufe über alle Kanten oder Zellen, Masse (die ersten vier) und Impuls (die beiden letzten) auch einzeln; alle binden dieselben 20 Puffer (`hydro.glsl`):

| Kernel | je | Aufgabe |
|---|---|---|
| `fluss.comp` | Kante | Q = u A(η_oben) mit dem alten u; Durchfluss am Rand; Bauwerke (Überfall, Siel, Ausgleich); gesperrte Kanten 0 |
| `faktor.comp` | Zelle | Anteil, auf den die abfließenden Flüsse begrenzt werden |
| `begrenzen.comp` | Kante | Fluss und u mit dem Anteil; Bilanz je Randkante (kompensiert) |
| `volumen.comp` | Zelle | V ← V + Δt Σ Q, η aus der Tabelle, Abgeschnittenes |
| `querschnitt.comp` | Kante | h_A stromauf im neuen Stand, q = Q / L |
| `impuls.comp` | Kante | Impuls wie `Hydro.impuls`: Gefälle (am Rand über `randAbstand`), Advektion, Coriolis, Wind, Luftdruck, Seegang, Smagorinsky, Reibung; gesperrte Kanten behalten u |
| `zeitschritt.comp` | Zelle | Grenzen von Δt in zwei Richtungen (Welle je Zelle, Speicher mit den Nachbarn; F32 A), je Gruppe gefaltet |
| `sammeln.comp` | Kante | seit P5c, nach jeder Masse, wenn `qSammeln()` gerufen ist: Σ Fluss · Δt je Kante, kompensiert (Kahan, zwei floats), für `water.q` |

Für die Schachtel (wie `Kette`, Werkbuch 12.5, Stand P4c):

| Kernel | je | Aufgabe |
|---|---|---|
| `aussengrenze.comp` | Zelle außen, Randkante fein | seit P4d, in jeder Masse des feinen nach `begrenzen`: Anteil je Zelle außen aus Zug und Volumen, dann Fluss und u hinein mit dem kleineren Anteil ihrer beiden Zellen (wie `Kette.aussenBegrenzen`) |
| `aussen.comp` | Zelle außen | nach jeder Masse des feinen: Summe der Randflüsse (verteilt wie der Wasserstand außen), dann V und η neu |
| `geist.comp` | Randkante fein | Wasserstand außen linear längs der Reihe der groben Zellen außen (nur nasse), Boden der Geisterzelle |
| `flaeche.comp` | Zelle, Kante fein | benetzte Fläche und Durchfluss im jetzigen Stand |
| `restriktion.comp` | grobe Zelle unter fein | η = Mittel der feinen, gewichtet mit der benetzten Fläche |
| `grobu.comp` | gesperrte grobe Kante | Fluss und u aus den feinen Durchflüssen |

`kopplung.glsl` trägt die Zuordnung der feinen Randkanten zu den groben Zellen außen und ihr Gewicht. Gesperrte Kanten haben im Puffer der Bauwerke die Nummer −2.

- **Tabellen:** die Packung unverändert (float16, je zwei in einem uint, gelesen mit `unpackHalf2x16`), Stufen relativ zur Basis wie `Subgrid.Packung` („so rechnet die Karte“).
- **u** liegt zweimal auf der Karte und wechselt je Schritt; der Satz der Kernels wechselt mit.
- **Ränder:** Wert und Boden der Geisterzelle je Randkante und halbem Schritt in einem geteilten Puffer; der Prozessor füllt ihn vor dem Abschicken aus den Seiten des `Hydro`, in der Schachtel `geist.comp` nach jeder Änderung der groben Zellen außen. Die gröbste Ebene der Schachtel hat fünf halbe Schritte zu Δt / 4 (zwei halbe Massen, ein Impuls).
- **Vor grob unter fein** rechnet `geist` den Rand des feinen Gitters neu (seit P4d): Die zweite halbe Masse des groben ändert die Zellen außen, und der Durchfluss der feinen Randkanten, aus dem `grobu` die gesperrten groben Kanten setzt, nimmt h_A stromauf aus der Geisterzelle. Mit dem alten Wert lag u dort bis 5,5·10⁻⁴ m/s neben der Referenz (Fall 10 über die Kette), danach 2,4·10⁻⁶ m/s.
- **Speicher:** `HydroKarte.speicherGeteilt` legt die Felder in den geteilten, vom Prozessor sichtbaren Speicher (für die Messung in Fall 14).
- **Statt NaN** (die Karte rechnet ohne Zusage für NaN): eine Stelle ohne Krone hat 1·10³⁰, eine fehlende Kante den Wert 3·10³⁸.

## Benutzung

```java
try (Geraet g = new Geraet()) {
    Hydro h = new Hydro(g3, ge.packung("G3"));               // Einrichtung wie in der Referenz
    h.bauwerke(ge.bauwerke("G3"));
    h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
    h.randwert[Hydro.WEST] = (t, x, y) -> 1.2 * Math.sin(2 * Math.PI * t / 44714);
    h.fuellen(0.5);
    try (HydroKarte k = new HydroKarte(g, g.konto("hydro", 4L << 30), h, 32)) {   // bis 32 Schritte je Abschicken
        k.lauf(3600, 5, 32);                                 // je 32 Schritte ein Δt nach CFL von der Karte
        k.herunter();                                        // η, V, u, Fluss, Bilanz ins Hydro
    }
}
```

## Prüfung

`com.dan.physics.hydro.demo.HydroCheck --karte` rechnet die Fälle der Prüfung A auf der Karte und dazu Fall 10, Referenz gegen Karte nach einem Schritt (Grenze A, 1·10⁻⁵), und Fall 14 (Zeit je Zelle und Schritt); Attest `docs/status/P4_HydroKarte.md`. Mit `--gebiet` kommen Fall 10 über die Kette (drei Ebenen, ein Schritt von G2), Fall 9 (eine Tide über G2 → G3 → G4) und die Zeiten des Laufs dazu, dazu das Status-Bild `docs/status/P4_Hydro.png`. Unter Windows mit `--enable-native-access=ALL-UNNAMED` (LWJGL).

Stand in der Arbeitsumgebung (Software-Gerät llvmpipe, P4d mit einer verkleinerten Kette: G2 ganz, G3 als Fenster 160 × 160, G4 200 × 200, 3 h mit 1 h Anlauf): 29 Prüfsteine ohne Verstoß; Fall 10 über die Kette, überflutet, G2 |Δη| 5,0·10⁻⁶ m, |Δu| 3,1·10⁻⁶ m/s (G3 und G4 darunter); Fall 9 Bilanz 3,6·10⁻⁹, nichts abgeschnitten. Auf der 680M (eine Periode über die ganze Kette, zweiter Lauf P4d): 13,7 ns je Zellschritt, Zeitraffer 11,6, ohne G4 25,0; G3 allein 14,5 ns mit den Feldern im Speicher der Karte, 14,8 ns im geteilten Speicher. Die Felder bleiben darum im Speicher der Karte.

## Grenzen

- float: Volumen und Bilanz auf etwa 10⁻⁷ relativ je Schritt; über eine Tide prüft Fall 9 (< 1·10⁻⁵).
- Die Ränder rechnet in `HydroKarte` der Prozessor vor jedem Abschicken; in `KetteKarte` steht die Zahl der Unterschritte jeder Ebene am Anfang des groben Schritts fest (bei zwei Ebenen wie `Kette`). Seit P5b kommt der grobe Schritt aus `dtKette`, abgeglichen wie `Kette.abgleich`; die Schritte der feineren Ebenen gelten dann für diesen Schritt und werden nicht noch einmal gerechnet.
- Ein Randabstand für alle Seiten mit Wasserstand (in der Schachtel sind sie gleich).
- Volumen auf der Karte in zwei floats (Hauptteil und Rest), dazu je Zelle das Bezugsvolumen V(η_b), η_b = max(0, oberste Stufe); η über der obersten Stufe als η_b + (V − V_b) / A, dazwischen von der näheren Stufe mit genauen Produkten (`etaAus2`, seit P5). Ohne das lag η in den tiefen Zellen von G0 auf 10⁻³ m daneben (ein float für 10¹¹ m³). Die Geometrie je Zeile (geographische Gitter) steht im Puffer `Zeilen` (Bindung 14, vorher nur f).
- Grenze A über die Kette gilt im überfluteten Stand. Fällt am Rand einer Schachtel Watt trocken, hängt u an den Begrenzungen (je Zelle und außen) und damit an der Menge in fast leeren Zellen; float und double trennen sich dort nach einem Schritt um bis zu 1,6·10⁻⁴ m/s (Fall 10 über die Kette, zur Auskunft), wie an jeder Nass-Trocken-Grenze nach vielen Schritten (Werkbuch 12.3).
