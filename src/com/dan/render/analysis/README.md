# com.dan.render.analysis – der Messblick

**Stand: P11c3 (19.11.2026), Version 0.1.** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, 12.12, Dateien 55
und 57). Es legt über das fertige Bild, was das Wasser tut: Partikel, die mit der Strömung ziehen, die Strömung als
Textur auf dem Wasser (LIC) und Isolinien von Wasserstand, Tiefe oder Trübung. Es ist eine `core.gpu.Auflage`:
render.frame ruft sie nach der Filmkurve, damit Linien und Spuren ihre Farbe behalten, wie hell die Szene auch belichtet
ist. Seit P11c3 dazu der Schnitt: Durchfluss, Profil und Bilanz.

## Schicht

```
com.dan.render.analysis        core + core.gpu + LWJGL      Isolinien, Messblick
com.dan.render.analysis.demo   dazu FStyle                  AnalysisCheck
```

Die Felder kommen von außen (`Messblick.staende`, `zeit`); das Modul kennt weder Bus noch Archiv. In `lauf` holt
`Messquelle` sie aus einer `Bild.Quelle` (Strömung aus `hydro_G3`, P11c1), `Buehne.messblick(boolean)` schaltet ihn.

## Klassen und Shader

| Datei | Aufgabe |
|---|---|
| `Isolinien` | Marching Squares über den Mitten der Zellen, Lücken aus NaN, Sattel nach dem Mittel, Stücke zu Linien verbunden, Douglas und Peucker; Begleitfeld je Punkt (der Wasserstand, auf dem die Linie liegt) |
| `Messblick` | die Auflage: Feld der zwei Stände (u, v, η, h je Stand) in einem geteilten Puffer (drei Plätze), Partikel, LIC, Isolinien in einem eigenen Faden aus dem näheren Stand |
| `messblick.glsl` | bilinear zwischen den Mitten, zwischen den Ständen (`MIT_FELD`) oder aus dem Feldbild mit vier Zugriffen (`MIT_FELDBILD`); Farbleiter; Zufall (PCG) |
| `feldbild.comp` | das Feld zum Gewicht w je Zelle in ein Bild (RGBA32F: u, v, η, h), einmal je Bild, wenn sich Stand oder Gewicht ändern |
| `partikel.comp` | Runge-Kutta 2 (Mittelpunkt) in Teilschritten (höchstens eine halbe Zelle), Spur aus 8 Punkten, neu setzen auf nassen Zellen |
| `spur.vert`, `linie.vert`, `linie.frag` | Spuren und Isolinien als Strecken, Tiefe der Szene getestet, nicht geschrieben, um 3 · 10⁻⁴ zum Auge gezogen [G], vormultipliziert gemischt |
| `lic.comp` | LIC (Cabral und Leedom 1993), 2 × 2 Bildpunkte je Zelle, Kasten über 16 halbe Bildpunkte in beide Richtungen [G]; neu, wenn ein Stand kommt oder das Gewicht um ein Achtel wandert, verteilt auf mehrere Bilder in Streifen in ein zweites Bild (`STREIFEN` = 4 bis W5, seit W5 `licStreifenZahl` = 8, A43 aus W2b: die LIC war der größte Brocken der langen Bilder im Messblick); schreibt je Bildpunkt η, h, LIC, Betrag (RGBA16F) |
| `voll.vert`, `lic.frag` | die LIC auf der Wasserfläche: Ort aus der Tiefe der Szene, nur wo nass und nahe am Wasserstand; ein Zugriff (linear) auf das Bild der LIC; Farbe nach dem Betrag der Geschwindigkeit |
| `Querschnitt` | seit P11c3: Schnitt von A nach B als Treppe über die Ecken des Gitters; Durchfluss genau aus `water.q` (Summe über die Kanten der Treppe, nach links positiv); Profil (Grund von außen, Wasserstand, Geschwindigkeit quer, Trübung) je halbe Zelle; Bilanz aus Q(t) über ein Zeitfenster |
| `demo.AnalysisCheck` | Prüfsteine 17 (Partikel: gleichförmig, starrer Wirbel, neu setzen) und 18 (Isolinien: Ebene, Kegel, Lücken, Douglas und Peucker); seit P11c3 Teil 3 Querschnitt (Durchfluss gegen q · n · L, geschlossener Weg, Profil, Bilanz einer Sinuswelle) |

Kosten auf der 680M (Lauf C2d, 1920 × 1080): 0,5–1,4 ms je Bild im Median mit allen Ebenen und laufend neuer LIC, 0,3–0,9 ms, wenn die LIC nur bei neuem Stand rechnet (Partikel 0,24, Linien 0,04–0,25, LIC auf dem Bild 0,28–0,43, ein Streifen der LIC 0,39–0,66 ms).

Einstellungen [G]: 20 000 Partikel, Spur 120 s Szenenzeit, Lebensdauer 600–1 800 s, trocken unter 5 cm, Farbleiter bis
1,5 m/s, Stufen der Tiefe 0,5 / 1 / 2 / 3 / 5 / 10 / 15 / 20 m, der Trübung 5 / 10 / 20 / 50 / 100 / 200 / 500 g/m³, des
Wasserstands rund zehn runde Stufen über die Spanne.

## Prüfung

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.analysis.demo.AnalysisCheck        # P11c2_Analysis.md
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.lauf.demo.MessblickCheck --szenen <Datei>  # P11c2_Messblick.md, Bilder
```

## Grenzen

- hydro ist tiefengemittelt: eine Geschwindigkeit je Zelle, kein Profil.
- Partikel und LIC zeigen die Strömung, sie rechnen keinen Transport.
- Linien liegen auf dem Wasserstand des Felds, nicht auf den Wellen des Bilds; hohe Kämme können sie verdecken.
- Die Isolinien springen mit dem näheren Stand (alle 150 s Szenenzeit), statt zwischen den Ständen zu wandern.
- Live (Lauf am Bus) liefert die Quelle noch keine Strömung: dort nur Isolinien.
- Die LIC hat 10 m je Bildpunkt (G3 zu 20 m): nah am Auge grob.
- Der Nachlauf beginnt in Ruhe: am ersten Stand eines Archivs gibt es keine Strömung, in den ersten Stunden schwingt sie ein.
