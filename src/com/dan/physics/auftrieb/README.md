# com.dan.physics.auftrieb

Schwimmende Körper mit sechs Freiheiten (Seegang Werkbuch, S7; SF104–SF112 A). Stand **S7a** (Auftrieb 0.1): die Körper
als Referenz in doppelter Genauigkeit auf dem Prozessor, mit Kette, Leinen, Fendern und Antrieb. Das Package kennt kein
Archiv und keine Karte; das Wasser kommt über die Schnittstelle `Wasser` herein. In S7b liest `lauf.Flotte` es von der
Karte (`render.seegang`, Punkte unter dem Rumpf), und `render.schwimmer` zeichnet die Körper.

Kopie aus den Element-Packages `vessel` (Starrkörper aus Proben, Kette) und `buoyancy` (Tonnen, Trockenfallen) nach
SF3 A; beide bleiben, wie sie sind.

## Schicht

```
com.dan.physics.auftrieb        nur java.*                Form, Proben, Hydrostatik, Koerper, Kette, Festmacher, Welle, Druckprofil, Vorgaben
com.dan.physics.auftrieb.demo   dazu core (Attest)        AuftriebCheck
```

## Achsen

- **Welt:** x nach Ost, y nach Nord, z nach oben (m).
- **Körper:** x nach vorn (0 mittschiffs), y nach Backbord, z nach oben ab der Unterkante (Kiel).
- Kurs in Grad von Nord im Uhrzeigersinn; Krängung positiv = Steuerbord tief; Trimm positiv = Bug hoch.

## Physik

| Teil | Was |
|---|---|
| `Form` | Rumpf (Einrumpf aus Maßen: Riss mit parallelem Mittelschiff und Superellipsen, Spant mit Aufkimmung und Kimmradius, aufgeholter Boden, Kiel, Sprung, Aufbauten, die beim Eintauchen zu einem Teil tragen [G]); Drehkörper (Tonnen); Ellipsoid (Ente); Quader; liegender Zylinder |
| `Proben` | Würfel der Kante d über der Form, je Zelle 8³ (dünne Körper 16³) Stellen: Volumen, Schwerpunkt, untere Kante, Höhe, Anteil, der trägt, und die freie Fläche je Richtung (±x, ±y, ±z) aus den Teilsäulen der Stellen. So ist die angeströmte Fläche die projizierte, ohne Treppen |
| `Hydrostatik` | die Handrechnung aus der Form, fein und unabhängig von den Proben: V, LCB, KB, A_W, LCF, I_T, I_L; GM, GM_L |
| `Koerper` | Starrkörper mit sechs Freiheiten, siehe unten |
| `Kette` | Massenpunkte, XPBD, Grund, Anker hält oder slippt (`vessel.Chain`); `kettenlinie` zum Vergleich |
| `Festmacher.Leine`, `Festmacher.Fender` | Leine nur auf Zug (EA, Dämpfung) zu einem Poller; Fender drückt gegen eine Wand (Kaje) |
| `Welle` | lineare Wellen über ebenem Grund (Airy, endliche Tiefe), regelmäßig oder JONSWAP mit cos²ˢ, in Frequenzbändern wie das Nahfeld; Pegel als Funktion der Zeit, Strom, Wind, Grund. Für die Prüfungen und als Bezug |
| `Druckprofil` | die Druckhöhe in der Tiefe aus zwei Summen je Band an der Oberfläche (S0, S1), siehe unten |
| `Vorgaben` | die Schwimmer von Hooge |

### Der Körper

- **Druck nach Froude–Krylov (SF105 A):** Jede Probe sieht in ihrer Tiefe z den Spiegel pegel + ψ(z) mit
  ψ(z) = Σ a cosh(k (z + h)) / cosh(k h) cos φ und trägt mit dem Anteil darunter: ρ g V f senkrecht, −ρ g V f ∇ψ
  waagerecht. Für lange Wellen ist das der Auftrieb unter der Fläche wie in `vessel`; kurze Wellen heben einen tiefen
  Rumpf weniger.
- **Zusatzmasse** je Freiheit (längs, quer, hoch im Kurs; um x, y, z im Körper). Schiffe aus Streifen [G]: hoch
  Σ ρ π b² / 8 · (1 + ¼ ln(b / 2T)), quer Σ ρ π T² / 2, Stampfen und Gieren mal x², alles mal L / (L + B); längs 5 % der
  Masse, Rollen 15 % der Trägheit. Tonnen, Treibgut, Ente als Anteile [G]. Sie wirkt auf die Beschleunigung gegen das
  Wasser (Morison) und schwindet mit der Eintauchung (trocken keine). Im flachen Wasser wäre sie größer; das fehlt.
- **Strahlungsdämpfung** als Anteil der kritischen für Tauchen, Rollen und Stampfen [G], gegen die Bewegung der Fläche (Ebene
  durch die senkrechte Geschwindigkeit des Wassers an den eingetauchten Proben); Rollen dazu quadratisch [G].
- **Widerstand** gegen Strom und Orbitalgeschwindigkeit auf die angeströmten freien Flächen der Proben, c_D längs, quer und
  senkrecht [G]; **Wellenwiderstand** beim Fahren (Fn⁶, wie `vessel`) [G]; **Wind** auf Flächen von vorn und von der Seite
  an einem Druckpunkt [G].
- **Grund (SF112 A):** Proben unter dem Gelände drückt eine Feder (Einsinken unter dem eigenen Gewicht 5 mm [G]) mit Dämpfer
  zurück, Reibung nach Coulomb (0,5 [G]). Schiffe fallen trocken; ein Kutter legt sich auf die Kimm.
- **Antrieb:** Schub an der Schraube, wenn sie im Wasser ist; Ruder nach Fahrt und Schraubenstrahl; Autopilot `steuern`.
- **Integration:** Leapfrog in Teilschritten ≤ 4 ms. Die Drehung über den Drehimpuls im Körper, die freie Drehung in Teilen
  um die Hauptachsen (Dullweber, Leimkuhler und McLachlan 1997): ohne Dämpfung hält das die Energie ohne Drift (Prüfstein
  13: 4e-5 über 600 s).

### Druckhöhe aus Bändern

Die Karte gibt in S7b je Punkt und Frequenzband b an der Oberfläche S0_b = Σ a cos φ und S1_b = Σ a (k − k_b) cos φ (k_b
die mittlere Wellenzahl des Bands nach der Energie). In erster Ordnung um k_b ist

```
ψ(z) ≈ Σ_b A(k_b, z) · (S0_b + D_b(z) · S1_b),   A = cosh(k (z + h)) / cosh(k h),   D = (z + h) tanh(k_b (z + h)) − h tanh(k_b h)
```

Bei acht Bändern wie im Nahfeld liegt der Fehler bis 3 m Tiefe unter 0,5 % (nur S0: 2–5 %). Entscheidung S7a nach
Ermessen: diese zwei Summen je Band statt der Druckhöhe in drei Tiefen (Plan, Datei 86, 3.3).

## Die Schwimmer von Hooge (`Vorgaben`)

| Schwimmer | Maße | Proben | Masse | GM [G] | T_z | T_φ |
|---|---|---|---|---|---|---|
| Fähre „Hilligenlei“ | 38,3 × 10,0 m, T 1,6 m (AIS) | 2862 zu 0,75 m | 591 t | 2,50 m | 4,7 s | 5,2 s |
| „Adler-Express“ | 42 × 8 m, T 1,2 m [G] | 2344 zu 0,75 m | 228 t | 1,30 m | 4,0 s | 5,7 s |
| „Hauke Haien“ | 33,6 × 6,0 m, T 1,4 m (AIS) | 3606 zu 0,6 m | 168 t | 0,70 m | 3,6 s | 5,9 s |
| Krabbenkutter (Vorbild WRE 6) | 16,0 × 4,8 m, T 1,6 m | 2930 zu 0,4 m | 66 t | 0,55 m | 3,2 s | 5,3 s |
| Spierentonne [G] | Ø 0,5 m, 5 m, T 3,0 m | 1226 zu 0,1 m | 0,6 t | 0,30 m | 3,6 s | 5,0 s |
| Spitztonne [G] | Ø 1,6 m, T 1,0 m | 580 zu 0,2 m | 2,1 t | 0,26 m | 2,5 s | 3,0 s |
| Leuchttonne [G] | Ø 2,4 m, 5,1 t (`buoyancy.Buoy`) | 360 zu 0,3 m | 5,1 t | 0,44 m | 2,6 s | 3,3 s |
| Balken, Fischkiste, Stamm [G] | 4 × 0,3 × 0,3 m; 0,8 × 0,5 × 0,35 m; Ø 0,4 × 3 m | 160–480 zu 0,1 m | 0,03–0,2 t | – | – | – |
| Eiderente [G] | 0,60 × 0,26 × 0,22 m | 256 zu 0,05 m | 2,2 kg | 0,05 m | 0,4 s | 0,7 s |

Die Form der Schiffe ist gestaltet nach den Fotos in `HalligHooge\docs\img` (Fähre1–3, Kutter1–3).

## Prüfung

```bash
java -cp dist\Renderer.jar com.dan.physics.auftrieb.demo.AuftriebCheck            # alle Teile, rund 4 min
java -cp dist\Renderer.jar com.dan.physics.auftrieb.demo.AuftriebCheck --teil 12  # ein Teil
```

14 Teile (Prüfsteine aus Datei 86, 3.8), Attest `docs/status/S7a_Auftrieb.md`: Schwimmlage und Ruhe aller Vorgaben,
Eigenperioden, Ausschwingen, lange und kurze Welle, Druckhöhe, Seitensee (Auskunft), Kettenlinie, Tonne im Strom, Fähre an
Leinen im Wind, Trockenfallen, Energie, bitgleich; dazu Proben, Kosten und Fahrt als Auskunft.

## Grenzen

- Die Zusatzmasse hängt nicht von der Frequenz und nicht von der Wassertiefe ab [G]; im flachen Watt wäre sie größer.
- Keine Beugung und keine abgestrahlten Wellen: Ein Schiff glättet die Welle in seinem Lee nicht.
- Kein Volllaufen und Sinken (gibt es in `vessel`; für Hooge nicht gebraucht).
- Balken (GM < 0) schwimmen über Eck, Stämme ohne Rückstellung; beides ist so gewollt.
- Im Seegang mit vielen Teilwellen ist `Welle` als Wasser teuer (Kutter mit 24 Teilwellen 3,7 ms je Teilschritt); in der
  Anwendung kommt das Wasser von der Karte (S7b).

## Seit S7b (Seegang Werkbuch 88): Wasser von der Karte, Netze, Version 0.2

- **`Bandwasser`** (neu): `Wasser` aus den Summen je Band, die die Karte je Säule eines Körpers liefert
  (`core.szene.Wasserpunkte`). Die Bänder der Fläche liegen zu höchstens 6 Bändern des Körpers zusammen [G]; je Band
  ψ(z) = A(k_b, z) (Re C0 + D_b(z) Re C1), dazu (η − Re Z) A(k_p, z) für die Form der Fläche, so dass ψ(0) die Fläche des
  Bilds ist. Zwischen der Stelle der Säule und der Probe und über den Verzug der Karte dreht der Körper die Summen je Band
  um e^{i (κ_b · δ − ω_b δt)}. Pegel, Strom, Wind und Grund setzt die Anwendung.
- **`Proben.saeule`**: Säulen aus Blöcken von Zellen, höchstens 0,8 m breit [G]; je Säule eine Stelle der Karte.
  `Koerper.saeulenWelt` gibt ihre Orte in der Welt. `Wasser.welle(saeule, …)` und `Wasser.grund(saeule, …)` (Vorgabe:
  ohne Säule, darum rechnet S7a bitgleich wie vorher).
- **`Koerper.wasserTakt`** [G]: die Welle an einer Probe gilt so lange, solange die Probe nicht mehr als 2 cm steigt;
  0 (Vorgabe) wie S7a. Die Flotte nimmt 12 ms.
- **`Schiffbau`** (neu): Netze aus der Form (Rumpf aus denselben Spanten, Deck, Spiegel, Kiel, Aufbauten), Zubehör und
  Anstrich nach den Fotos in `HalligHooge\docs\img` [G]; Tonnen gedreht, Treibgut und Enten.
- `Vorgaben.spierentonne(d)`, `spitztonne(d)`, `leuchttonne(d)`: Tonnen mit gröberen Proben für die Szene.


## Seit S7b-4, S7c (Seegang Werkbuch 89)

- **Punkte der Karte** (`Koerper.kartenRaster`, `kartenpunkt`, `kartenWelt`): Säulen in Zellen eines Rasters zusammengefasst;
  `Bandwasser.zuordnung` dreht die Summen eines Punkts je Band mit κ_b auf die Stelle der Säule. Vorgabe: je Säule ein Punkt.
- **Kielwasser** im Wasser des Körpers: `Bandwasser` liest Höhe und Wellenzahl des Kielwassers am Punkt und lässt es mit
  seiner eigenen Wellenzahl abklingen; das Glied der Form gilt ohne es.
- **`Bugschlag`** (neu): Schlag nach Ochi (1964), wenn der Boden ein Zehntel der Länge hinter dem Bug erst austaucht und
  dann mit mehr als 0,093 √(g L) gegen das Wasser eintaucht.
- `Koerper.geschwindigkeit(vx, vy, vz)`: setzt die Geschwindigkeit (eine Fähre, die in Fahrt erscheint).
- Die Physik der Körper ist unverändert; `AuftriebCheck` bleibt bitgleich bis auf Zeiten.

**Seit S7d7 (Seegang Werkbuch 95, SF138 A): Auftrieb 0.3 – Rumpf quer und Ruder**

- Befund L5d6 1: Mit Ruder 0,2 verlor die Fähre die Hälfte ihrer Fahrt. Der Widerstand wirkte längs der ganzen Strömung mit
  der angeströmten Fläche aller Seiten; jedes Schrägfahren kostete Widerstand mit der ganzen Seitenfläche, und die Fähre
  drehte bei kleinen Ruderlagen sogar falsch herum.
- `Koerper.querstrom` (für Schiffe, `Vorgaben.steuern`): längs ½ ρ c_D A |u_x| u_x, quer zur Längsachse ½ ρ c_D A |u_⊥| u_⊥
  mit u_⊥ = (u_y, u_z), dazu der Quertrieb ½ ρ c_Q A |u_x| u_y mit c_Q = π T/L (1 + 0,4 C_B B/T) nach Clarke.
- Ruder: 2,8 je rad, höchstens 2,0, Widerstand 0,013 plus induziert (Streckung 1,5) [G]; bis S7d6 2π je rad unbegrenzt.
- Geeicht im Container: bei Ruder 0,2 Fähre −5 %, Kutter −1 % Fahrt; Drehkreis bei vollem Ruder 4,8 und 4,7 Längen
  (Richtwerte ≤ 10 %, 3–5 L [G]). Tonnen und Kleines rechnen wie bisher.

**Seit S7d8 (Seegang Werkbuch 97, SF144 A): Führung, die sich dreht**

- `Festmacher.Fuehrung.vSoll`: die Geschwindigkeit, die der Punkt quer zur Linie haben soll; der Dämpfer wirkt gegen die
  Abweichung davon. Der Lotse dreht damit die Fähre auf der Stelle (die Linie durch ihre Mitte dreht sich mit 1,5 °/s).
  Ohne `vSoll` (0) wie bisher; `AuftriebCheck` 55 von 55.

**Seit S7e1 (Seegang Werkbuch 99, SF149 A, SF151 A): Pricke, Fähre nach Foto**

- `Vorgaben.pricke()`: Birkenstamm 5 m, Quader 0,12 × 0,12 m, wenige Proben (die Flotte stellt sie nur; mit den üblichen
  Proben kostete der Bau 322 ms je Pricke). Netz in `Schiffbau`: Stamm und 14 Reiser als Besen, aufwärts an Backbord,
  abwärts an Steuerbord.
- `Schiffbau`, Fähre: das Netz nach Fotos der Hilligenlei – Portal über dem Autodeck, Fahrgastraum mit 7 Bullaugen je
  Seite, Brücke, Mast, ockerfarbener Schornstein mit Bändern, Rampe. Nur das Netz; Rumpf, Gewicht und Proben wie bisher.
  Seit L5e1 (Befund 2: vier schmale Beine je Seite sahen aus wie ein Tisch) die Stützen des Portals wie im Foto: vorn
  schräg, vordere Mitte schräg, Mitte breit, achtern eine schräge Strebe vor der Endwand, dazu das Schanzkleid des
  Oberdecks; neu im Baukasten `strebe` (schräger Balken, im Schnitt x–z ein Viereck).

## Seit S7e2 (Seegang Werkbuch 101, SF150 A, SF151 A): Gleiten, Treibgut, Antrieb – Koerper 0.4

- **`Gleiten` 0.1 (neu):** dynamischer Auftrieb am Boden eines Knickspants nach Savitsky (1964), nur für Rümpfe mit
  `Koerper.gleiten` (Adler-Express). Je Teilschritt aus der Fahrt durchs Wasser C_V, der Tiefe des Kiels am Spiegel und dem
  Trimm: benetzte Kiel- und Kimmlänge (Sprühwurzel), λ, C_L0 und C_Lβ, Druckpunkt; der Anstellwinkel aus der Geschwindigkeit
  des Bodens im Druckpunkt (dämpft Tauchen und Stampfen). Mit dem Anteil w = glatt(C_V − 0,5) [G] ersetzt w · L den Auftrieb
  der Proben (Kraft und Moment): L steht senkrecht zum Kiel in der lotrechten Ebene durch den Kiel und greift längs im
  Druckpunkt, quer dort an, wo der Auftrieb der Proben sitzt (der tiefere Boden trägt den geneigten Rumpf, dynamische
  Querstabilität [G]). Der Wellenwiderstand klingt für Gleiter über F_n 0,4–0,7 aus, der Widerstand der Proben senkrecht
  mit (1 − w). Unter C_V 0,5 bitgleich wie ohne.
- **Adler-Express:** GM 3,5 statt 1,3 m [G] (mit 1,3 m kenterte er im Container schon mit Ruder 0,3 bei 8 m/s; nach
  Schätzung der Gewichte KG rund 2,8–3,3 m über Kiel). Bei 24 kn im Gleichgewicht τ 1,60°, Hub +0,06 m, Widerstand 80 kN.
- **Treibgut (`Vorgaben.treibgut`):** Balken, Fischkiste, Stamm mit Proben der Kante `TREIB_D` 0,15 m [G]; die Flächen gegen
  den Wind aus der Ruhelage (`Vorgaben.flaechen`: senkrecht projiziert über und unter dem Pegel, von vorn und von der
  Seite, Strahlen durch Zellen von 1 cm), nicht als fester Anteil; der Balken liegt auf der Kante (45°). Die Ente mit
  `ENTE_D` 0,08 m. Seit L5e2 (Faden der Flotte 2,58 ms je Teilschritt) rechnen Treibgut und Enten in der Szene mit
  Teilschritten von `SCHRITT_KLEIN` 1/60 s [G]; im `Schiffbau` hat die Ente Hals, Kopf (beim Erpel mit schwarzer Kappe) und
  Schnabel, nur im Netz.
- **Antrieb in der Welt:** `Koerper.antriebOst`, `antriebNord` (N, am Schwerpunkt) und `kursSoll` mit `kursZeit` (Drehen auf
  einen Kurs, gedämpft 0,7) – für die Eiderenten.
- Prüfung: `demo.GleitCheck` (Prüfstein 34 am Gleitprisma 15 × 3 m, β 12°, 13 m/s gegen Savitskys Gleichgewicht ± 10 %;
  Adler-Express bei 24 kn als Auskunft; langsam bitgleich; hart Ruder in voller Fahrt < 20°), Attest `S7e_Gleiten.md`;
  `demo.TreibCheck` (Prüfstein 32: Drift in Strom und Wind gegen das Gleichgewicht aus den Flächen ± 10 %), Attest
  `S7e_Treibgut.md`.
