# com.dan.physics.atmos

Boden, Wärme und bodennahe Luft (Werkbuch HalligHooge Teil A, 7.5 und 12.11). Stand P10b Teil 1 (Atmos 0.2,
AtmosModul 0.2): die Rechnung in doppelter Genauigkeit auf dem Prozessor und das Modul am Bus. Eine Fassung auf der
Grafikkarte gibt es nicht (ein Kopplungsintervall für 280 000 Zellen kostet Zehntelsekunden). Die Kimmung im Bild
kommt mit P10b Teil 2.

| Datei | Inhalt |
|---|---|
| `Luft.java` | Stoffwerte der feuchten Luft; Sättigungsdampfdruck nach IAPWS-IF97; Brechzahl nach Ciddor (1996) in der Fassung des NIST (A21–A41), als n und als n − 1 |
| `Schicht.java` | Bodennahe Schicht nach Monin und Obukhov: Funktionen nach Businger und Dyer (Dyer und Hicks 1970), integriert nach Paulson (1970); Lösung aus den Werten der Oberfläche oder aus den Flüssen; Profile von Wind, Temperatur, Feuchte; z0 über Wasser nach Charnock |
| `Profil.java` | Temperatur, Feuchte, Druck und Brechung n − 1 in 64 Höhen (0, dann 1 cm bis 100 m geometrisch); n(z) und dn/dz dazwischen linear, darüber das Gefälle der Normalatmosphäre (k = 0,13) |
| `Atmos.java` | Die Referenz: Energiebilanz der Oberfläche je Zelle (Newton), Wärmeleitung im Boden (5 Schichten bis 0,5 m, implizit), flaches Wasser als durchmischte Schicht, tiefes Wasser mit der Temperatur des Meers, Nässe der obersten Schicht (`surf.wet`), Temperatur der Oberfläche (`surf.T`), Profile je Säule (`air.n`), Bilanz der Energie |
| `demo/AtmosCheck.java` | Prüfsteine 1–8 und 13 aus 12.11; Attest `docs/status/P10a_Atmos.md` |

Die Prüfsteine 9–11 (Lichtstrahlen durch das Profil) stehen in `com.dan.lauf.demo.KimmungCheck`
(Attest `docs/status/P10a_Kimmung.md`): Sie brauchen `render.reference.Strahlgleichung`, und ein Physikmodul kennt die
Grafik nicht.

```java
Atmos at = new Atmos(nx, ny, 10);            // 10 × 10 Zellen je Säule
// h (Wassertiefe) und z0 je Zelle, das Wetter je Säule setzen (oder at.wetter(...) für alle), dann
at.anfang(283.15);
at.schritt(300);
// at.ts (surf.T), at.wet (surf.wet), at.profile[s].r (air.n: n − 1 in 64 Höhen), at.rn, at.hf, at.le, at.gb
```

```
java -cp dist\Renderer.jar com.dan.physics.atmos.demo.AtmosCheck                 alle Teile, mit Attest
java -cp dist\Renderer.jar com.dan.physics.atmos.demo.AtmosCheck --teil 5        ein Teil, ohne Attest
java -cp dist\Renderer.jar com.dan.lauf.demo.KimmungCheck
```

**Eingang.** Je Zelle die Wassertiefe `h` (m) und die Rauheit `z0` (m, über Land). Je Säule: Wind in 10 m, Temperatur
und spezifische Feuchte in 2 m, Druck, Sonnen- und Gegenstrahlung am Boden (W/m², nach unten), Temperatur des Meers.
Die Strahlung kommt vom Wetter (F90 A: aus ERA5, mit Wolken), nicht aus dem Sonnenstand.

**Für P10b.** `air.n` muss als n − 1 in das Feld (float trägt n selbst nicht: Die Stufen unterscheiden sich um 10⁻¹¹
bis 10⁻⁶). Der Feldkatalog kennt Strahlung und Wassertemperatur noch nicht. Der Grenzstrahl der unteren Spiegelung
streift die Fläche im untersten Zentimeter; ab welcher Höhe das Bild die Fläche als undurchsichtig nimmt (Wellen),
ist dort zu entscheiden.

**Quellen.** Ciddor (1996), Applied Optics 35, 1566; Stone und Zimmerman, NIST Engineering Metrology Toolbox,
„Index of Refraction of Air“ (Gleichungen und Prüfwerte, nachgelesen am 18.11.2026); Dyer (1974), Boundary-Layer
Meteorology 7, 363; Paulson (1970), Journal of Applied Meteorology 9, 857; van de Griend und Owe (1994), Water
Resources Research 30, 181.

**Gesetzt [G].** Bodenschichten 1, 3, 6, 12, 28 cm; wassergesättigter Sand überall (2,2 W/(m K), 2,95 MJ/(m³ K)),
unten kein Wärmefluss; Albedo nasser Sand 0,20, trockener 0,35, Wasser 0,08; Emissionsgrad 0,97; Wasser ab 1 mm, ab
0,30 m mit der Temperatur des Meers; Nässe als Sättigung des obersten Zentimeters (Porenanteil 0,4); Widerstand der
Oberfläche 10 s/m, unter 15 Vol.-% steigend (Zahlen aus dem Gedächtnis); Rauheit für Wärme z0/10 über Land, gleich z0
über Wasser; Charnock 0,018; Schichtung in 10 m höchstens ζ = 1, über ζ = 1 φ = 6; Wind mindestens 0,5 m/s; zwei
Wechsel zwischen Schichtung und Bilanz je Schritt; Brechzahl bei 550 nm, CO₂ 430 µmol/mol; Höhen der Tabelle.

**Grenzen.** Kein Regen, kein Eis; teils nasse Zellen zählen ganz als Wasser; das Grundwasser kennt nur den
Wasserstand des Fensters, keinen Priel nebenan; die Stoffwerte des Bodens hängen nicht von der Nässe ab; über 10–20 m
ist das Profil die Fortsetzung der bodennahen Schicht; Businger und Dyer gelten gemessen bis etwa ζ = −2, bei Flaute
über warmer Fläche rechnet die Schicht weiter draußen.


## Seit P10b: Entwässerung und Modul am Bus

**Entwässerung (Atmos 0.2, F92 A; `mitEntwaesserung`, im Modul an).** Unter trockengefallenem Sand sinkt das
Grundwasser (`grundwasser[]`, m unter der Oberfläche) mit `sinken` = 2 cm/h [G], höchstens bis zum Wasserstand nebenan
(`ueber[]` = Höhe der Zelle über dem Wasserstand); steigt der, steigt es sofort mit. Die oberste Schicht strebt zur
Sättigung im Gleichgewicht darüber, nach van Genuchten (1980): S_e = [1 + (α h)ⁿ]^−(1 − 1/n), h bis zur Mitte der
Schicht; α = 3,5 1/m [G, Feinsand], n = 2,68, θ_r/θ_s = 0,045/0,43 (Carsel und Parrish 1988, Sand). Die Rate kommt aus
der Leitfähigkeit nach Mualem und van Genuchten, K = K_s S_e^½ [1 − (1 − S_e^(1/m))^m]², K_s = 8,25 · 10⁻⁵ m/s
(Formel und K_s aus dem Gedächtnis), auf der nasseren Seite genommen und implizit gerechnet:
W′ = (W + r W_Ziel) / (1 + r), r = ρ_w K Δt / W_max. Die Verdunstung zieht davor ab. Das weicht vom Wortlaut von F92 A
ab („Zeitkonstante“, „Höhe über dem Wasserstand“): Gegen den Wasserstand allein fiele bei Niedrigwasser das ganze Watt
trocken.

| Datei | Inhalt |
|---|---|
| `AtmosModul.java` | Modul `atmos` am Bus, getaktet mit 300 s: liest `water.h`, `water.wetAge`, `water.eta` auf seinem Gitter (G3) und `air.u10`, `air.v10`, `air.p`, `air.t2`, `air.q2`, `air.sw`, `air.lw`, `water.sst` auf den Säulen (Gitter `<G3>s`, je `teiler` × `teiler` Zellen eine); schreibt `surf.wet`, `surf.T` (Zelle) und `air.n` (Säule, 64 Höhen, n − 1). Nass ist eine Zelle mit `wetAge` ≥ 0 und h ≥ 1 mm; der Wasserstand nebenan ist das Mittel von `water.eta` über tiefem Wasser im Gitter [G]; die Höhe kommt aus `grund` (m NHN). Am Beginn: Boden und Oberfläche mit der Luft, Wasser mit dem Meer, Grundwasser und Nässe aus `water.wetAge` |

Der Nachlauf aus einem Archiv steht in `com.dan.lauf` (`Nachlauf`), die Prüfung im Gebiet in
`com.dan.lauf.demo.AtmosLaufCheck`; `demo.AtmosCheck` prüft seit P10b als Teil 14 die Entwässerung ohne Gebiet.

Quellen dazu: van Genuchten, M. Th. (1980), Soil Sci. Soc. Am. J. 44; Mualem, Y. (1976), Water Resour. Res. 12;
Carsel, R. F., Parrish, R. S. (1988), Water Resour. Res. 24.

## Seit P10c: Regen (Atmos 0.3, AtmosModul 0.3; F97 A)

`Atmos.regen` (je Säule, kg/(m² s)): Was in einem Schritt fällt, füllt zu dessen Beginn die oberste Schicht, höchstens
bis zur Sättigung; der Rest versickert [G]. Ohne Regen rechnet der Schritt bitgleich wie 0.2. `AtmosModul.mitRegen` liest
`air.rain` (vom Antrieb aus `tp` von ERA5); Regen unter `regenAb` = 0,1 mm je Stunde [G] zählt nicht – die Reanalyse
meldet an vielen Stunden Spuren, die den Sand sonst dauernd feucht hielten. Wärme des Regens und Pfützen fehlen.
`demo.AtmosCheck` prüft den Regen als Teil 15.
