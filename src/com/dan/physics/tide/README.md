# com.dan.physics.tide – Antrieb der Gezeiten

**Stand: P5c (13.11.2026).** Grundlage ist das Werkbuch HalligHooge, Teil A, Abschnitte 7.2 und 12.6; nach F27 A rechnet hydro alle Gitter, tide liefert den Antrieb der Gezeiten. Nicht zu verwechseln mit dem Element-Paket `com.dan.tide` (schnelle Pegelquelle, bleibt als Vergleich).

## Schicht

```
com.dan.physics.tide    core + java.*    Modul am Bus
```

tide kennt kein anderes Modul. Die Daten kommen aus dem Gebiet: `tide/eot20.txt` (`core.gitter.Tidenfeld`, Aufbereitung Schritt `eot20`) und der Meeranteil von G0 (`nass.f32`).

## Felder

| Feld | Ort | Inhalt |
|---|---|---|
| `tide.bc` | Rand von G0 (`Ort.RAND`, Folge nach `Gitter.randZelle`: Süd, Ost, Nord, West gegen den Uhrzeigersinn) | Wasserstand an der Mitte der Geisterzelle: `mittel + r(t) · Σ f A cos(V + u − G)` aus den 17 Partialtiden von EOT20 (bilinear über nasse Punkte, sonst der nächste im Umkreis von 2 Punkten) mit Knotenfaktoren, dazu das umgekehrte Barometer −(p − 101 325 Pa) / (ρ g) aus `air.p` in der Randzelle, wenn der Antrieb läuft. Durchfluss 0. An einer Randzelle ohne Wasser NaN. |
| `tide.eq` | Zellen von G0 und G1 (F27 A) | `0,69 · r(t) · (η_eq(t) − η̄_eq(φ))`: η_eq aus Sonne und Mond (`core.astro.Gestirne`, Grad 2 und 3), η̄_eq der ständige Anteil, das Mittel über ein Jahr ab dem Start (stündlich, an vier Längen, je 0,1° Breite). |
| liest `air.p` | Zellen von G0, im Intervall | nur mit Antrieb (umgekehrtes Barometer) |

**Ständiger Anteil.** EOT20 am Rand enthält ihn nicht; im Inneren gäbe er eine feste Neigung von 6,6 cm über G0 (gemessen in P5c). η̄_eq stimmt mit der geschlossenen Form C · P₂(sin φ′) auf 0,1 mm (LaufCheck). Mit ihm fällt die 18,6-jährige Tide weg, die EOT20 ebenso wenig führt.

**Anlauf.** r(t) = ½ (1 − cos(π t / Anlauf)), Vorgabe 3 h [G]; am Anfang steht ein ruhender See bei `mittel` (Vorgabe 0 m NHN [G]; P5d misst es an den Pegeln). Das Barometer fährt mit dem Antrieb an (`physics.antrieb`).

## Benutzung

```java
Tide t = new Tide(Tidenfeld.lies(gebiet), Instant.parse("2026-10-12T16:00:00Z"), g0, ge.nass("G0"), 2, true, g0, g1);
t.kopplung = 300;              // wie hydro [G]
bus.anmelden(t);
```

`toString()` nennt die offenen Randzellen und woher ihre Werte kommen (G0 von Hooge: 911, davon 899 bilinear, 12 vom nächsten Punkt).

## Prüfung

In `com.dan.lauf.demo.LaufCheck` (Teil 2): `tide.bc` am Bus gegen eine eigene Rechnung an jeder offenen Randzelle zu jeder Zeit (≤ 10⁻⁶ m [G]), `tide.eq` gegen die Ephemeriden in allen Zellen von G0 und G1 (≤ 10⁻⁹ m [G]), der ständige Anteil gegen C · P₂(sin φ′) (≤ 1 mm [G]). Die Gezeiten selbst prüft P5d (`TideCheck`).

## Grenzen

- Die Randwerte sind nur so gut wie EOT20 auf dem Schelf (an der Küste kommt die Tide bei EOT20 10–35° zu früh; dort rechnet die Kette).
- Das Barometer gilt in der Randzelle, eine halbe Zelle innen (≤ 3 km).
- Die Gleichgewichtstide wirkt nur auf G0 und G1; über G2 spannt sie bis 4 mm (gemessen in P5c, F33).
