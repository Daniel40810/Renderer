# com.dan.jelly – Ohrenquallen

Ohrenquallen (*Aurelia aurita*) für eine Bucht der Ostsee: ein Feld von Tieren um den Blick, das mit der Jahreszeit kommt und geht, im Wind treibt, bei Seegang tiefer geht und im flachen Wasser strandet. Dazu eine Darstellung, die jede Qualle als Schirm mit Rand, vier Gonaden, Mundarmen und Kanälen durch die Wasseroberfläche zeigt. Angelegt in Timmendorf Phase 17 für den Blick von der Seebrücke.

Der Kern braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/jelly/`.

## Was es kann

- **Feld um den Blick:** eine Kachel (70 bis 170 m nach der Stufe) folgt dem Blickpunkt; wer hinaus treibt, kommt gegenüber wieder herein. Über Land und in zu flachem Wasser bleibt ein Tier verborgen, bis es die nächste Runde macht.
- **Ehrliche Dichte:** `density` ist Tiere je m² auf der Höhe des Jahres. Deckelt die Stufe die Zahl, wird die Kachel kleiner, nicht das Feld dünner.
- **Jahreszeit:** `seasonFactor()` aus `dayOfYear` und `waterTemp` – ab Mai, am meisten Ende August, weg im November, unter 6 °C keine. `meanDiameter()` wächst von 3 cm im Mai auf 22 cm im August.
- **Schlag:** `pulseRate(d, T)` (kleine und warme schlagen schneller), `contraction(phase)` (schnell zusammen, langsam auf); der Schirm zieht sich um bis zu 18 % zusammen.
- **Trift und Tiefe:** Oberflächenstrom 3 % des Winds plus `currentX/Z`, eigenes Schwimmen nach Größe und Schlag; bei Seegang (`hs`, Wind über 8 m/s) gehen sie tiefer. `seen(depth)` sagt, wie viel man noch sieht (Hälfte in der Sichttiefe `visibility`).
- **Strandung:** im flachen Wasser bleibt eine Qualle liegen, trocknet in `strandedLife` (Vorgabe 6 h) und meldet sich über `Stranding`.
- **Presets:** `none()`, `few()`, `summer()`, `bloom()` und `beaufort(n)`.
- **Qualitätsstufen:** `Quality.LOW` bis `ULTRA` (300 bis 3 000 Tiere).

## Benutzung

```java
Bloom b = Bloom.summer();
b.bed = terrain::height;                  // Grund (m)
b.surface = (x, z) -> ocean.heightAt(x, z); // Spiegel mit Wellen
b.dayOfYear = 235; b.waterTemp = 19;
b.windSpeed = 5; b.windFromDeg = 270;
b.onStranding = (x, z, d) -> System.out.println("gestrandet bei " + x + ", " + z);
b.step(dt, blickX, blickZ);               // je Bild

JellyGl gl = new JellyGl();               // nach dem Wasser, mit Tiefentest, ohne Tiefe zu schreiben
gl.init(gl4);
gl.draw(gl4, b, view, light);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Bloom` | Kern: Feld, Jahreszeit, Schlag, Trift, Tiefe, Strandung, Presets; Schnittstellen `Bed`, `Surface`, `Stranding` |
| `Bloom.Jelly` | eine Qualle mit offenen Feldern |
| `gl/JellyGl` | je Qualle ein waagerechtes Viereck knapp über dem Spiegel, Shader `jelly.vert`, `jelly.frag` |
| `demo/JellyDemo` | Blick von oben auf 3,2 × 2 m, Regler für den Tag im Jahr; `--bild datei.png [tag]` ohne Fenster |
| `demo/JellyCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Die Darstellung

Das Muster entsteht im Shader: milchiger Körper, zum Rand dichter (die Glocke wird zur Seite dicker gesehen), beim Schlag noch dichter; acht Radialkanäle mit feiner Verzweigung; vier ringförmige Gonaden um 0,36 vom Mittelpunkt, nach innen offen, violett bis rosa je Tier; vier gekräuselte Mundarme; ein Saum kurzer Tentakel. Was man sieht, kommt durch die Oberfläche: Durchlass nach Fresnel (Schlick, n = 1,33), Abschwächung mit der Tiefe nach `seen`, Farbe des Wassers über die Auslöschung je m (Vorgabe westliche Ostsee, grünlich) auf dem Weg des Lichts hinab und des Blicks hinauf (gebrochen). Gestrandete liegen als glänzender Gallert auf dem Grund und werden beim Trocknen flacher.

## Grenzen

- Die Qualle ist flach: kein Körper in drei Dimensionen, keine langen Tentakel. Von der Seebrücke und vom Steg reicht das; mit dem Auge unter Wasser nicht.
- Die Zahl der Tiere je m² ist frei gewählt [G]; belegt sind nur Zeit und Größe im Jahr (westliche Ostsee: Möller 1980, Hansson 1997, Lucas 2001) und der Schlag nach der Größe (McHenry und Jed 2003).
- Keine Schwärme in Streifen (Langmuir-Zirkulation), kein Nesseln, keine Polypen.
- Die Kachel wiederholt sich nicht sichtbar, solange der Blick in ihr bleibt; sehr weite Blicke zeigen nur den nahen Teil.

## Demo und Selbsttest

- `com.dan.jelly.demo.JellyDemo` zeigt das Feld von oben mit einem Regler für den Tag.
- `com.dan.jelly.demo.JellyCheck` prüft seed, Jahreszeit, Größe, Schlag, Presets, Tiefe bei Seegang, Trift im Westwind, Strandung, Dichte, Kachel und Rechenzeit.
- `ant check-packages` übersetzt `jelly` einzeln: Kern gegen das JDK, `gl/` gegen JOGL, `demo/` gegen FStyle.
