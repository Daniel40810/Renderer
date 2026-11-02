# com.dan.dustcloud – Staubwolke

**Stand: Kern (LituyaBay1958 Phase 12).** Die Staubwolke eines Felssturzes, eines Abbruchs oder eines Steinbruchs: eine Schicht staubiger Luft, schwerer als die Luft darüber, die hangab fließt, sich in der Ebene und über dem Wasser ausbreitet, Luft einmischt und dabei dünner wird, vom Wind genommen wird und ihren Staub nach Korngröße absetzt. Für Licht und Sicht gibt sie die optische Dicke und die Sichtweite. Das Package kennt keine App und kein anderes Package; Gelände und Wind kommen über kleine Schnittstellen. Der Kern braucht nur Java 21, `demo/` zusätzlich `FStyle.jar`. Später für Sylt (Abbruch), Steinbruch, Vulkan. Die Darstellung auf der Grafikkarte (Volumen, Mie-Streuung, Sonne als Scheibe) kommt mit dem Ereignis (Phase 13).

Zum Herauskopieren reicht `src/com/dan/dustcloud/`.

## Was es rechnet

Je Zelle: Mächtigkeit h, Geschwindigkeit u und je Kornklasse das Staubvolumen je Fläche q<sub>k</sub> = h c<sub>k</sub>. Die Wolke ist um g′ = g R c schwerer als die Luft (R = (ρ<sub>s</sub> − ρ<sub>a</sub>)/ρ<sub>a</sub> ≈ 2 200):

- **Schwerestrom:** Flachwassergleichungen mit g′ h = g R Q (Q = Σ q<sub>k</sub>), HLL in erster Ordnung (stoßerfassend, keine negative Mächtigkeit), Grund als Quelle −g R Q ∇b, Reibung C<sub>d</sub> |u| u (0,004 [G]). Die Nase einer Schleuse läuft wie das Kastenmodell nach Huppert und Simpson (Fr 1,19 bis √2, Benjamin); der Kopf ist flacher als im Versuch [G].
- **Einmischung** nach Parker (1987): E = 0,075 / √(1 + 718 Ri<sup>2,4</sup>), Ri = g′h/|u|²; die eingemischte Luft bringt den Impuls des Windes mit.
- **Wind:** Die Wolke nähert sich dem Wind mit τ = τ<sub>0</sub> (1 + g′h/|W − u|²), τ<sub>0</sub> = 20 s [G]: Eine dichte Wolke läuft als Schwerestrom auch gegen den Wind, eine verdünnte zieht mit ihm.
- **Sinken** je Klasse nach Ferguson und Church (2004), für kleine Körner Stokes: 2 µm 0,3 mm/s, 10 µm 8 mm/s, 50 µm 0,18 m/s, 200 µm 1,7 m/s. Abgesetzter Staub zählt je Zelle, getrennt nach Land und Wasser.
- **Quellen:** `Source` (Kreis, Zeit, Staub- und Wolkenstrom, Geschwindigkeit) oder `inject` je Schritt von außen (etwa aus `landslide`, verdrahtet in der App).
- **Licht:** optische Dicke Σ 3 q<sub>k</sub>/d<sub>k</sub> (Q<sub>ext</sub> = 2), Sichtweite 3,912/β nach Koschmieder.
- **Bilanz:** Luft + Land + Wasser + über den Rand = eingebracht, genau. Ränder offen nur hinaus, Grund NaN ist Wand. Parallel über die Zeilen, zwei Läufe gleich.

## Benutzung

```java
DustCloud c = new DustCloud(x0, z0, nx, nz, 25, DustCloud.rockDust());   // 2/10/50/200 µm
c.wind = (x, z, t) -> new double[]{6, 0};
c.build(new DustCloud.Ground() {
    public double height(double x, double z) { return gelaende(x, z); }   // über Wasser der Spiegel
    public boolean water(double x, double z) { return see(x, z); }
});
c.source(new DustCloud.Source(x, z, 200, 0, 30, 2000, 3e7, 0, 25));    // 30 s lang
c.run(300);
double tau = c.opticalDepth(x, z), sicht = c.visibility(x, z), oben = c.top(x, z);
double[] bilanz = c.budget();   // Luft, Land, Wasser, Rand, abgeschnitten, eingebracht (m³ Feststoff)
```

## Prüfung (`demo/DustCheck`), Stand 02.11.2026

| Prüfung | Ergebnis | Soll |
|---|---|---|
| Stokes 50 µm / 5 µm | 0,200 m/s / 2,00 mm/s | Werkbuch 9.2: rund 0,2 m/s, 2 mm/s |
| Ferguson-Church 5 / 50 / 200 µm | Stokes genau / 90 % / 1,74 statt 3,21 m/s | Trägheit bremst große Körner |
| ruhende Schicht 100 m, 50 µm, 300 s | 0,5797 in der Luft | e<sup>−wt/h</sup> = 0,5793 |
| Schleuse 500 × 100 m, g′ 21,7 m/s² | Nase nach 120 s bei 3 745 m | Kastenmodell 3 798–4 240 m (Fr 1,19–√2) |
| mit Einmischung | langsamer, 23 % mehr Wolke | verdünnt |
| Hang 22° ins Wasser | hangab über das Wasser, hangauf 120 m | |
| Bilanz | 1,000000, nichts abgeschnitten | genau |
| verdünnte Wolke im Wind 10 m/s | 9,83 m/s | Wind |
| dichte Wolke im selben Wind | läuft 610 m gegen den Wind | Schwerestrom |
| Sicht c 1e-6, 10 µm | 13,0 m | 3,912/0,3 |
| zwei Läufe | gleich | |

## Demo

`demo/DustDemo.java` → Run File: Felssturz an einer Fjordflanke, 30 s Staub; von oben die optische Dicke und die Ablagerung, der Schnitt durch die Rinne, die Bilanz und die Sicht. Ohne Fenster: `DustDemo --bild datei.png [t]`.
