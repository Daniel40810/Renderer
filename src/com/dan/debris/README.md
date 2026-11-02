# com.dan.debris – Treibgut

**Stand: Kern (LituyaBay1958 Phase 12).** Stämme mit und ohne Krone, Äste und Eisbrocken auf dem Wasser: Sie schwimmen mit dem Tiefgang ihrer Dichte, treiben mit der Strömung und ein paar Prozent des Windes, drehen sich, stoßen und verkeilen sich zu Teppichen, laufen auf Grund, werden von einer Welle mitgerissen, saugen sich über Monate voll und sinken. Für weit draußen und für Tage gibt es den Holzteppich als Feld. Das Package kennt keine App und kein anderes Package; Wasser (Tiefe, Strömung) und Wind kommen über kleine Schnittstellen, die App füllt sie etwa aus `wave.Bay`. Der Kern braucht nur Java 21, `demo/` zusätzlich `FStyle.jar`. Später für Rungholt, Sylt (Sturmflut), Hochwasser. Gezeichnet wird in der App (Phase 13).

Zum Herauskopieren reicht `src/com/dan/debris/`.

## Was es rechnet

- **Stück** (`Log`): liegender Zylinder (Länge, Durchmesser) in der Ebene, Ort, Achse, Geschwindigkeit, Drehrate. Arten (`Kind`): `TRUNK` entrindet 450 kg/m³, `CROWN` mit Krone 500 kg/m³ (dreifache Fläche im Wind, 1,6-fache im Wasser [G]), `BRANCH`, `ICE` 917 kg/m³.
- **Tiefgang:** Kreisabschnitt, dessen Fläche ρ/ρ<sub>w</sub> des Querschnitts ist (Holz 450: 46 % des Durchmessers, 45 % des Volumens unter Wasser).
- **Treiben:** Widerstand quer (C 1,0) und längs (0,1 davon [G]) an drei Stellen des Stamms, je gegen Wasser (bis zum Tiefgang) und Luft (Freibord); daraus Kraft und Drehung, halb implizit, mit Zusatzmasse. So reißt auch eine Welle mit 30 m/s die Stämme ohne winzige Schritte mit, ohne zu überschwingen. Im Gleichgewicht: Strömung plus α/(1 + α) des Windes, α = √(ρ<sub>a</sub> f / (ρ<sub>w</sub> t)) (Holz 450: 3,5 %; mit Krone 4,5 %).
- **Grund:** Wo das Wasser flacher ist als der Tiefgang, trägt der Grund den Rest (Reibung 0,6), an Land ganz: `GROUNDED`, `ASHORE`. Eine Welle hebt sie wieder an.
- **Stöße:** Kapseln (Strecke mit Halbmesser), Stoßimpulse längs der Normalen (Stoßzahl 0,2 [G]) mit Coulomb-Reibung quer (0,5), dazu auseinandergeschoben; vier Durchgänge, Nachbarn über ein Gitter. Impuls genau.
- **Vollsaugen:** ρ(t) = ρ<sub>sat</sub> − (ρ<sub>sat</sub> − ρ<sub>0</sub>) e<sup>−t/τ</sup>, 1 100 kg/m³, τ 150 Tage [G]: ein Stamm sinkt nach gut 9 Monaten. `gray()` für das Vergrauen über Jahre.
- **Teppich** (`Carpet`): je Zelle die bedeckte Fläche φ; treibt mit Strömung + α/(1 + α) Wind, höchstens 0,6 Bedeckung (dichte Packung [G]), der Rest staut sich; was ins Flache treibt, strandet. Fläche genau. `Debris.toCarpet` gibt Stämme an ihn ab.

## Benutzung

```java
Debris d = new Debris(new Debris.Water() {
    public double depth(double x, double z) { return bay.depthAt(x, z); }      // NaN: Land
    public double[] current(double x, double z) { return bay.velocityAt(x, z); }
});
d.wind = (x, z, t) -> new double[]{5, 2};
d.scatter(400, xa, za, xb, zb, Debris.Kind.TRUNK, 1958);
d.step(dt);                                   // in Teilschritten zu höchstens maxSub
for (Debris.Log l : d.logs()) zeichne(l.ends(), l.state, l.gray());

Carpet c = new Carpet(x0, z0, nx, nz, 40, d.water);
c.like(d, d.logs().get(0));                   // Windanteil und Tiefgang
d.toCarpet(c, l -> weitWeg(l));
c.run(3 * 86400);
```

## Prüfung (`demo/DebrisCheck`), Stand 02.11.2026

| Prüfung | Ergebnis | Soll |
|---|---|---|
| Tiefgang 0,5 m, 450 kg/m³ | 0,230 m, 0,4500 des Querschnitts | ρ/ρ<sub>w</sub> 0,45 |
| Strömung 1,0 / 0,5 m/s, ruhende Luft | 0,964 / 0,482 m/s, keine Drehung | c/(1 + α) |
| Westwind 10 m/s, quer und längs | 0,361 m/s | α 3,7 % → 0,361 m/s |
| Welle 30 m/s für 4 s | 28,91 m/s, kein Überschwingen | 28,92 |
| auflandige Strömung, Strand 1:20 | liegt auf Grund, Wasser unter dem Tiefgang | |
| Stoß zweier Stämme | Durchdringung 0 %, Impuls genau | |
| Teppich 150 Stämme im Wind | Fläche nach 600 s 1,05 | bleibt zusammen |
| Vollsaugen | sinkt nach 281 Tagen | τ ln(650/100) = 281 |
| Feld: Drift, Fläche, Stau | 0,561 m/s, genau, höchstens 0,600 | 0,561 m/s |
| zwei Läufe | gleich | |

## Demo

`demo/DebrisDemo.java` → Run File: Bucht mit Wirbel und Südwestwind, 400 Stücke eine halbe Stunde lang, daneben derselbe Wald als Teppich über drei Tage. Ohne Fenster: `DebrisDemo --bild datei.png [t]`.
