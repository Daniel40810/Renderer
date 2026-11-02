# com.dan.succession – Störung und Wiederbewaldung

**Stand: Kern (LituyaBay1958 Phase 14, Zugriffe auf den Altbaum seit 14a).** Was eine Welle (oder ein Sturm, ein Hang, ein Gletscher) mit einem Wald macht und wie er in Jahrzehnten wiederkommt: ein Zellautomat auf dem Pflanzgitter des Walds (ein Platz je 6 m wie `forest.TreeStates`), je Platz ein Hauptbaum und einer im Unterstand. Das Package kennt keine App und kein anderes Package; Gelände, höchster Wasserstand und Altbestand kommen über kleine Schnittstellen, die Ausgabe geht Platz für Platz an eine `Ausgabe` (die App setzt daraus `TreeStates` mit Nachwuchs). Der Kern braucht nur Java 21. Später für Brocken (Windwurf, Borkenkäfer) und Glacier (Gletschervorfeld).

Zum Herauskopieren reicht `src/com/dan/succession/`.

## Was es rechnet

- **Wellenwurf** (`Wellenwurf`, Werkbuch 9.7): Kraft F = ½ ρ<sub>w</sub> C<sub>D</sub> A u² auf Stamm (bis zum Kronenansatz) und Krone (Fläche über die Kronenhöhe verteilt, ∝ H²), Biegemoment am Stammfuß gegen die Tragfähigkeit σ<sub>b</sub> π d³/32 (40 MPa, Sitka-Fichte) und gegen die Wurzelplatte (auf tiefem Boden stärker als der Stamm, an Hängen über 30° schwächer [G]). Folgen: steht, entrindet (schnelles Wasser mit Geröll, der Baum stirbt), gebrochen, umgelegt (entwurzelt, hangab), fort (umgelegt in tiefem Wasser oder ganz abgeräumt). Ab 20 m Tiefe und 20 m/s bis zum Fels abgeräumt [G]. Kritische Geschwindigkeit der ganz erfassten Krone 1,4 m/s: Wo das Wasser die Krone erreicht, bricht jeder Baum – daher die scharfe Spurlinie. Die Geschwindigkeit über Land kommt aus der Tiefe: u = Fr·√(g h), Fr 1,5 [G].
- **Störung** (`Succession.stoeren`): Band = Plätze an Land, über denen der höchste Wasserstand lag. Altbaum je Platz aus dem Bestand (Art, Höhe), sein Schicksal, Fallrichtung hangab, Boden danach (Fels, Mineralboden, Waldboden). Reste stehen oder liegen noch Jahre (entrindet 20–45, Stumpf 10–30, liegender Stamm 30–60 [G]).
- **Jahre** (`Succession.rechnen`):
  - Samen vom Altbestand außerhalb und von reifen Bäumen im Band, auf einem groben Gitter (4 × 4 Plätze) mit der Flugweite der Art ausgebreitet: drei Kastenfilter ≈ Gauß, dazu 15 % fünfmal so weit (Sturm, Vögel) [G].
  - Anwuchs auf freien Plätzen nach Samen, Boden und Licht (Lichtbäume brauchen Licht: Licht^(2·(1 − Toleranz))); Schattenbäume keimen auch im Unterstand.
  - Wuchs nach Chapman-Richards H(t) = H<sub>∞</sub>(1 − e<sup>−kt</sup>)<sup>p</sup>, über die Höhe aufs Alter zurückgerechnet, im Schatten gebremst; überwächst der Unterstand den Hauptbaum, tauschen sie.
  - Sterben nach der Lebensdauer, für Lichtbäume im Schatten mehr.
  - Fels verwittert zu Mineralboden (4 % je Jahr), Mineralboden unter Kronen wird Waldboden (3 %) [G].
  - Alles zufällig mit festem Startwert (PCG je Platz und Jahr): zwei Läufe gleich.
- **Arten** (`Art`, alle Felder öffentlich): Roterle (Pionier, 11 m nach 10 Jahren, lebt 90 Jahre, Samen weit), Sitka-Fichte (35 m nach 54 Jahren, mäßig schattentolerant), Westliche Hemlocktanne (Schattenbaum, keimt auf Waldboden) [G nach Lehrbuchwerten; Abgleich am DSM 2012 in LituyaBay1958 Phase 14a].
- **Ausgabe:** Verlauf je Jahr (Art und Höhe des Hauptbaums, Boden) im Speicher, 3 Bytes je Platz und Jahr; `zustand(jahr, Ausgabe)`, `platz(i, j, jahr)`, `statistik(jahr)` (Deckung, Höhen im Band und außerhalb, Anteile der Arten), `zeile(jahr)`; je Band-Platz k `lage(k)`, `folge(k)`, `altArt(k)` und `altHoehe(k)` (der Baum vor der Welle, etwa für Treibgut), `hoehe(k, jahr)`, `art(k, jahr)`, `bodenArt(k, jahr)`.
- **Abgleich in LituyaBay1958 (Phase 14a):** Kronenhöhe ArcticDEM − IfSAR-Gelände im Band 1958 gegen außerhalb, Jahr 53: die App setzt die Rate k aller Arten ×1,69 (die Vorgaben hier bleiben).

## Benutzung

```java
Succession su = Succession.covering(x0, z0, x1, z1, 6, List.of(Art.sitkaFichte(), Art.hemlock(), Art.roterle()), 1958);
su.froude = 1.5;
su.stoeren((x, z) -> terrain.height(x, z),            // Gelände
           (x, z) -> welle.hoechsterStand(x, z),      // NaN: trocken
           (x, z, s) -> bestand(x, z, s));            // Anteil der Art, Summe ≤ 1
su.rechnen(68);
su.zustand(54, (i, j, zustand, fall, art, hoehe) -> {
    states.set(i, j, zustand, fall);                                   // forest.TreeStates
    states.setGrowth(i, j, art, art < 0 ? 0 : hoehe / mittlereHoehe[art]);
});
System.out.println(su.zeile(54));
```

## Prüfung

`demo/SuccessionCheck` (ohne Fenster): Wuchskurve und Umkehrung; kritische Geschwindigkeit 1,42 m/s (9.7: ≈ 1,4); Sitka-Fichte 40 m bei 0,5 / 3 / 10 / 30 m Tiefe: steht, entrindet, gebrochen, fort, am steilen Hang umgelegt; Moment springt, wenn die Krone erfasst wird. Kastenfilter erhält die Menge. Ein gerechneter Hang über einem Fjord (2,4 × 1,5 km, Spurlinie 40–300 m) über 68 Jahre: scharfe Kante (Überlebende nur in unter 8 m Wasser dicht unter der Spurlinie), Fels nur in tiefer schneller Strömung, Roterle zuerst, Nadelbäume holen auf, Deckung wächst (81 % nach 68 Jahren), Kronenhöhe im Band nach 54 Jahren 0,36 von außerhalb, Fels wächst langsamer zu als Mineralboden, vom Rand her früher als innen, zwei Läufe gleich, Ausgabe je Platz. Rund 0,7 s für 23 000 Plätze und 68 Jahre.

Die Wiederbewaldung im Bild zeigt die App (`ant wiederbewaldung`, mit `forest`): ein Package darf kein anderes kennen.
