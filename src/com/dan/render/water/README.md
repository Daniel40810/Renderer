# com.dan.render.water – das Wasser

**Stand: P9b (17.11.2026, Trübung am Ort), davor P8c (16.11.2026), Version 0.1; Grundstufe aus P6d, Spiegelung des Geländes aus P6e, nachgezogen seit F61 (P7f, P7g, P8b).** Bildmodul der Schicht `render` (Werkbuch HalligHooge, Teil A, Abschnitte 8.1 und 12.7, F42 A). Ein Spiegel aus `water.eta` der Gitter G4, G3, G2 und G1, je Ort das feinste, in der Zeit linear zwischen den zwei Ständen des Busses. Kräuselung aus dem Wind nach Cox und Munk. Optik der Grundstufe: Fresnel, Spiegelung des Geländes im Bild und sonst des Himmels, Glitzern, Wassersäule mit Schwebstoff, Grund im Flachen.

## Schicht

```
com.dan.render.water        core + core.gpu + LWJGL      Wasserbild (Bildteil), Shader; Spiegel, Spiegelbild und Wasseroptik seit P7a in core.szene
com.dan.render.water.demo   dazu FStyle                  WaterCheck, WasserAuszug, probe.comp, boden.frag
```

Liest aus dem Bildziel, was `render.air` ablegt (`Himmel/Durchlaessigkeit`, `Himmel/Ansicht`, `Himmel/Werte`), über `luft.glsl` im Kern. Zeichnet nach dem Gelände und vor dem Himmel: Reihenfolge im Rahmen `List.of(gelaende, wasser, himmel)`. Das Gelände kennt es nicht; es liest Farbe und Tiefe dessen, was vor ihm gezeichnet ist.

## Klassen und Shader

| Teil | Aufgabe |
|---|---|
| `Wasserbild` | Bildteil. `spiegel(Spiegel)` setzt ein Gitter; `zeit` (Modellzeit des Bilds), `u10`, `v10`. Vorbereiten: neue Stände über einen Zwischenpuffer (drei Plätze) auf die Karte, Kopf mit `vkCmdUpdateBuffer`. Zeichnen: Kopie von Farbe und Tiefe darunter, ein Dreieck über das ganze Bild. `etaBild(x, z)`: die Regel auf dem Prozessor |
| `core.szene.Spiegel` | ein Gitter in UTM: zwei Stände (η, nass), Bauwerkskanten aus `bauwerke.txt`, Schwellen der Kanten aus der Packung (`schwellen`, seit F47); `stand(t, eta, wetAge, h)` vom Bus |
| `core.szene.Wasseroptik` | Werte und Formeln: Pope und Fry, Schwebstoff, Fresnel, Cox und Munk, die 48 Wellen der Kräuselung (vorher `Optik`) |
| `core.szene.Spiegelbild` | η_Bild für ein Auge und eine Zeit: der Kopf des Shaders (`kn`, `kf`) und dieselbe Regel auf dem Prozessor (`etaBild`); seit P7a, damit die Referenz denselben Spiegel liest |
| `wasser.glsl` | η_Bild, Suche des Treffers, Optik |
| `wasser.vert` / `.frag` | je Bildpunkt Treffer, Leuchtdichte, Tiefe |
| `demo.WaterCheck` | Prüfung ohne Fenster |
| `demo.WasserAuszug` | kleiner Auszug eines Gebiets für die Prüfung in einer Arbeitsumgebung |

## Festlegungen

- **η_Bild:** im feinsten gesetzten Gitter, das den Ort hat. Ist die eigene Zelle zur Zeit t nicht nass, ist dort kein Wasser. Sonst bilinear zwischen den vier Zellmitten. Ein Nachbar zählt mit seinem Wert, wenn er nass ist, im Gitter liegt und kein Bauwerk (Deich oder Siel) ihn von der eigenen Zelle trennt und das Wasser nicht über die Kante fällt; über Eck reicht einer der beiden Wege über eine Seitenzelle. Sonst zählt er mit dem Wert der eigenen Zelle. **Überfall (seit F47 A, Werkbuch 12.7, Abnahme P6):** Sind beide Zellen nass und steht das Unterwasser weniger als 2/3 so hoch über der Schwelle der Kante wie das Oberwasser (vollkommener Überfall nach der Wehrformel; Unterwasser unter der Schwelle eingeschlossen), trennt die Kante wie ein Bauwerk; im Bild steht dort eine Stufe im Wasser. Die Schwelle ist der tiefste Punkt der Kante aus der Subgrid-Packung (Basis plus erste Stufe), je Zelle für West- und Südkante als float-Bits neben der Art (`Arten`: drei Werte je Zelle). Ohne Packung trennen nur Bauwerke. Gerechnet in float mit `precise`, ohne Division: Karte und Prozessor rechnen bitgleich.
- **Zeit:** je Zelle a + s (b − a), s in float wie `Bus.lies`. Nass zur Zeit t heißt: (s < 1 und nass in A) oder (s > 0 und nass in B). Nass ist wetAge > 0; im ersten Stand eines Laufs h > 0.
- **Wasser im Bild:** wo der Strahl den gekrümmten Spiegel trifft (feste Stelle über η_Bild, ≤ 8 Schritte, Erdkrümmung mit Refraktion wie das Gelände) und das Gelände dahinter liegt. An jeder Pixelmitte des 1-m-Geländes (dort liegen die Scheitel der feinsten Stufe) also genau, wo η_Bild über dem Feingelände liegt.
- **Optik:** Kanäle 680, 550 und 440 nm [G]. Brechzahl 1,34. Reines Wasser nach Pope und Fry (1997). Schwebstoff 30 mg/l [G] mit a*(443) = 0,04 m²/g und Neigung 0,0123/nm [S] sowie b_b* = 0,012 m²/g [S]. Die Wassersäule und der Grund (seit P7d, Werkbuch 12.8, F55 A) kommen aus der Tafel von `core.szene.Saeule`: Mehrfachstreuung nach Fournier-Forand (b_b/b = 0,019), gebacken an einer Platte, über u = b_b/(a + b_b) je Kanal, die Tiefe (a + b_b) D und die Winkel unter der Fläche, mit dem Azimut zur Sonne in erster Ordnung (L + 2C cos φ). Licht kommt von Sonne, Mond und Himmel (`licht.comp`: Himmelsansicht durch Fresnel). Der Grund geht nach Lambert ein, mit der Albedo ρ = π L_u / E aus dem Bild darunter: L_g = ρ T U / (1 − ρ R). Die Nässe des Grunds bringt das Gelände mit.
- **Spiegelung (F45 A):** Der gespiegelte Strahl wird im Bild darunter verfolgt: 64 Schritte, je 15 % länger, ab 0,2 m [G]. Ein Treffer liegt hinter dem Gelände im Bild, um höchstens die Länge des Schritts und 1 m [G]; danach wird viermal halbiert. Zum Bildrand hin geht das Gewicht auf 0 [G]. Ohne Treffer spiegelt sich der Himmel. `Wasserbild.spiegeln` schaltet ab.
- **Kräuselung (F42):** σ² = 0,003 + 5,12 · 10⁻³ U₁₀. Verteilt auf 48 Wellen von 5 mm bis 1 m, gleiche Varianz je Band, Richtungen nach cos² um den Wind [G]. Was ein Bildpunkt auflöst (Wellenlänge über vier Bildpunkten), ist Geometrie. Der Rest geht als Statistik ins Glitzern; die Summe bleibt σ².

## Seit P7f

- **Teile (P7f):** `Wasserbild.teil` zeichnet einen Teil des Wassers allein (1 Spiegelung des Himmels, 2 Glanz, 3 Säule, 4 Grund, 5 Gelände im Spiegel; 0 alles), nur zum Messen gegen die Referenz (`lauf.demo.ReferenzCheck`).
- **Wirksame Fläche (P7g, F63 A):** Die Neigungen, die ein Bildpunkt nicht auflöst, wirken im Mittel über die sichtbaren Flanken: Sie spiegeln schwächer und einen höheren Himmel und lassen mehr Licht aus dem Wasser (`core.szene.Flaeche`, drei Proben über die Breite des Spiegelbilds). Schalter `licht.w` im Kopf; aus: die Formeln an der Normalen der aufgelösten Kräuselung, wie `WaterCheck` sie prüft.
- **Säule (P7d, F61 A):** die Tafel aus `core.szene.Saeule` über das Spektrum, 32 Stufen in der Tiefe.

## Stufe 2: Seegang (P8b, Werkbuch 12.9, F76 A)

**Trübung am Ort (P9b, Werkbuch 12.10).** Mit einem Netz des Sediments (`Wasserbild.boden(Bodennetz)`, aus `sed.c` im
Archiv) rechnet die Säule je Bildpunkt mit der Konzentration C_opt am Ort statt mit dem Schwebstoff der Szene. Die Tafel
der Säule gilt für eine Konzentration; deshalb liegen hinter der wirksamen Fläche Tafeln für sechs Stufen
(`Wasserbild.stufe(k)` = 30 · 2,512^(k−2) g/m³: 4,75 bis 475 [G]). `wasser.frag` (`truebungOrt`, Puffer an Bindung 11)
liest C_opt bilinear über die Zellen mit Wasser, linear in der Zeit, und mischt linear in ln C zwischen zwei Stufen, jede
mit ihrer Dämpfung K = a_w + (a_p + b_b) · C / C_Szene (`saeule`). 30 g/m³ ist eine Stufe: Ein Netz mit überall
30 g/m³ gibt das Bild ohne Netz. Außerhalb des Netzes gilt der Schwebstoff der Szene, zum Rand hin über 25 Zellen
überblendet [G]. `Wasserbild.truebung = false` schaltet auf den alten Weg (Prüfung). Die Referenz kennt die Trübung am
Ort noch nicht (F83 A: P10).

Mit Netzen des Seegangs (`Wasserbild.seegang(Seegangsnetz)`, aus `waves.hs`, `.tp`, `.dir`, `.mss`, `.diss` im Archiv;
`com.dan.lauf.Bild` setzt sie aus `Bild.Quelle.seegang…`) rechnet `seegang.glsl` je Bildpunkt:

- **Werte am Ort:** im feinsten Netz (G3w vor G2w), bilinear zwischen den Zellmitten, linear zwischen zwei Ständen. Die Richtung als Einheitsvektor (wohin, im Gitter).
- **Lange Wellen:** ein fester Satz von 72 Wellen in 6 Bändern von 0,07 bis 0,6 Hz, je Band 12 Richtungen (`core.szene.Seegangsoptik`). Je Ort bekommt ein Band die Varianz Hs²/16 mal den Anteil eines JONSWAP (γ 3,3) im Band um die örtliche Tp, verteilt nach cos¹⁶((θ − θ_m)/2) auf seine Wellen. Der Satz bleibt überall gleich, nur die Höhen ändern sich: Die Phase ist stetig, die Wellen werden im Lee kleiner und laufen, wohin der Seegang sie trägt. Wie die Kräuselung nur Neigung (Normale), keine Verschiebung.
- **Glitzern:** dazu max(0, mss − aufgelöst) als Restvarianz; die Kräuselung aus dem Wind bleibt wie in Stufe 1.
- **Schaum:** Bedeckung W = min(1, diss / 20 W/m²) [G] als Muster aus Rauschen (3 m und 0,8 m), das mit 1 m/s in Richtung der Wellen wandert; Albedo 0,6 [G], Lambert unter Sonne, Mond und Himmel.

Ohne Netz (oder `Wasserbild.seegang = false`) ist alles wie Stufe 1; mit Hs, mss, diss = 0 ebenso (Prüfstein 8 von P8b in
`com.dan.lauf.demo.SeegangCheck`). `seeprobe.comp` und `Seegangsprobe` rechnen die langen Wellen auf der
Karte und dem Prozessor (Prüfstein 9). Live gibt es keinen Seegang aus dem Spektrum (Vorgabe aus P8a): live bleibt Stufe 1.

## Seit P13c: Brandung im Wasser (Werkbuch 12.14, F159 A, F161 A)

Mit Seegang und `Wasserbild.brandung` (Vorgabe an; `-Dlauf.brandung=aus`: wie bis P13b, bitgleich):

- **Höhe der langen Wellen:** derselbe Satz von 72 Wellen als Höhe ζ = Σ a sin φ (`seeAmplituden`, `seeHoehe`), Wellen unter
  1 mm Amplitude und unter zwei Bildpunkten zählen nicht. Kämme spitz, Täler flach nach dem Kammanteil im Flachen
  (`seeKamm`, Tiefe h aus dem Bild darunter). Der Strahl trifft die Fläche η_Bild + ζ_s: zwischen der Höhe der höchsten
  Kämme und der tiefsten Täler in 16 Schritten, dann 5-mal halbiert. Die Normale kommt aus derselben Höhe.
- **Weißwasser:** wo ein Kamm ζ_s / Kammanteil > γ h (McCowan, 0,78) erreicht, jetzt und eine halbe und anderthalb
  Perioden zurück (abklingend über zwei Perioden) [G].
- **Schaumköpfe:** Bedeckung W = diss / 22,7 W/m² (kalibriert, `Seegangsoptik.D_KAL`), auf den Kämmen der langen Wellen,
  dazu Flecken (3 m, 0,8 m) und ab Bft 8 Streifen längs des Winds (20 × 1,5 m). Was ein Bildpunkt nicht auflöst, geht als
  Wahrscheinlichkeit ein; im Mittel ist der Schaum überall W (A35).
- Prüfung: `Brandprobe` (`brandprobe.comp`) gegen `Seegangsoptik`, `render.brandung.demo.BrandungCheck`, `lauf.demo.BrandungBildCheck`.
- Die Referenz bleibt eben mit Facetten (P10d) und sieht weder Höhe noch Schaumköpfe.

## Seit S3c: das Netz der Clipmap (Seegang Werkbuch, SF7 A)

Legt `render.seegang` eine Clipmap ab (`Seegang/Netz` und die Puffer dazu, Bindungen 14–17), zeichnet das Wasser dort
ein Netz statt der Suche je Bildpunkt; das Dreieck über das Bild (`wasser.frag`) schweigt in diesem Bereich (`imNetz`,
`netz.glsl`) und zeichnet nur außerhalb, wie bisher. `Wasserbild.netz = false` schaltet zurück.

- **`netz.comp`:** je Stufe und Stelle der Scheitel: ruhende Stelle x₀ plus ξ, Höhe η_Bild(x₀) plus η, Erdkrümmung wie
  der Spiegel; wo kein Wasser ist, seit S3c-5 der Spiegel des nächsten nassen Nachbarn (unten). Im äußeren Band (12 Stellen [G]) jeder Stufe außer der letzten
  geht der Scheitel bilinear in die gröbere über (auch η_Bild); am Rand liegt er auf ihren Kanten.
- **`netz.vert`:** je Stufe (n − 1)² Vierecke aus `gl_VertexIndex`; die feineren reichen über die Stellen 0 … n − 2, die
  gröbere spart ihr Inneres aus, aber nicht den äußersten Ring (dort decken beide; sonst fielen an der T-Naht durch
  Rundung einzelne Bildpunkte durch: N2).
- **`netz.frag`:** kein Wasser an P (η_Bild) → weg; das Gelände verdeckt über die Tiefe. Normale und Aufgelöstes aus der
  Stufe mit d_L ≥ fuss / 2 (nicht feiner als die des Scheitels, zwischen zwei Stufen linear); Restvarianz fürs Glitzern
  max(0, mss − Σ k² E g²); Schaum nach SF25 A: Weißwasser, wo η der Fläche γ h · Kammanteil überschreitet (nur jetzt),
  Schaumköpfe auf ihren Kämmen (`schaumKoepfe` mit η und der gezeichneten Varianz). Dann `wasserOptik` wie bisher.
- **`wasser_optik.glsl`:** die Optik ab dem Treffer, aus `wasser.frag` herausgelöst und mit `netz.frag` geteilt.

Prüfung: `lauf.demo.SeegangNetzCheck` (N1 Scheitel gegen Java, N2 keine Lücken in drei Ansichten, N3 ohne Wellen
dieselbe Tiefe wie ohne Netz; Attest `docs/status/S3c_Netz.md`).

**Seit S3c-5 (Seegang Werkbuch 19/20):**

- **B47, trockene Scheitel:** `netz.comp` gibt einem trockenen Scheitel den Spiegel des nächsten nassen Nachbarn im
  Abstand s = max(Zelle, d_L) (erst die Seitennachbarn, dann über Eck, von mehreren der höchste), sonst den Pegel des
  Kopfs (`Spiegelbild.etaScheitel` rechnet dasselbe in Java). Die Art des Scheitels (1 nass, 0,5 nah am Wasser, 0 weit)
  steht im zweiten vec4 des Scheitels.
- **B47, Fuß am Bildpunkt:** Weicht der Fuß des Netzes um mehr als 2 cm [G] von η_Bild am Bildpunkt ab (Kanten zwischen
  zwei Spiegeln, trockene Scheitel), sucht `netz.frag` die Fläche η_Bild + η der Welle auf dem Strahl selbst (feste Stelle
  wie `suche`, ab η_Bild am Netz, sonst ab den Spiegeln der vier Nachbarn, der höchste zuerst) und schreibt deren Tiefe.
  Trockene Bildpunkte weit vom Wasser fallen sofort weg. N3b in `SeegangNetzCheck`: Teich auf +1,5 m hinter einem
  trockenen Ring mit Bauwerken, gegen den Strahl in Schritten von 5 cm.
- **B48, Abbruch über die Tiefe:** Liegt das Gelände vor dem Bildpunkt (Tiefe darunter), bricht `netz.frag` vor der Optik
  ab (`Wasserbild.abbruch`, Kopf `seeMehr.w`; aus mit `-Dwasser.abbruch=aus`). Bitgleich (N6b).
- **B49a:** Zeichnet das Netz, gilt im Weg ohne Netz für das Weißwasser dieselbe Regel (nur jetzt, SF25 A).
- **SF28 A:** Zeichnet das Wasser das Netz, legt es `Wasser/Netz` (`NETZ_GEZEICHNET`) mit dem inneren Bereich in UTM in
  `z.werte`; `render.brandung` liest den Schlüssel.

**Seit S3c-6 (Seegang Werkbuch 22):**

- **Fuß in `netz_fuss.glsl`:** η_Bild, Wegfallen und B47 aus `netz.frag` herausgelöst, geteilt mit dem Vorlauf.
- **N6, Vorlauf der Tiefe** (`Wasserbild.vorlauf`, Vorgabe aus, an mit `-Dwasser.vorlauf=an`): `netz_vorlauf.frag`
  rechnet je Bildpunkt nur den Fuß, schreibt die Tiefe und als Farbe (`R32UI`) die Nummer des Dreiecks (`vId` aus
  `netz.vert`); danach schattiert `netz.frag` ohne Prüfung der Tiefe nur den Sieger (Schub `nsch.x`). Bitgleich (N6c).
- **N6, Zähler** (`Wasserbild.zaehlen`, `zaehlung()`): je Bildpunkt die Aufrufe im Vorlauf, in `netz.frag`, mit Fuß und
  bis zur Optik (Binding 20, Schub `nsch.y`); nur für den Befund.
- **SF31 A:** `seegang.glsl` mischt am Rand eines feineren Netzes des Seegangs über 500 m (`SEE_RAND`) in das nächst
  gröbere, das den Ort deckt; `Seegangsoptik.werte` ebenso (N9).

**Seit S3c-7 (Seegang Werkbuch 24):**

- **SF33 A, Übergang außen:** `seeWerte` nach der Art in `seeZ[0].z` (`Seegangsnetz.RAND_ART`, `-Dseegang.rand`):
  außen (Vorgabe) gilt im feineren Netz nur das feinere, außerhalb geht es über 500 m von dessen Randwert
  (`seeWerteRand`) in das gröbere über; innen wie S3c-6; aus ohne Übergang. N9 je Art.
- **Zähler** in `netz_zaehler.glsl` (8 je Bildpunkt): dazu B47 im Netz und der alte Weg (`wasser.frag`, Binding 15):
  Aufrufe, im Netz nach der Suche verworfen, bis zur Optik (`Wasserbild.zaehlung`, Felder 8–11).

**Seit S3c-8 (Seegang Werkbuch 26, SF34 A):**

- **Maske des Netzes:** `netz.frag` schreibt als zweites Ziel (`R32UI`) die Nummer des Dreiecks, wo es im inneren
  Bereich zeichnet (`imNetz`); `wasser.frag` verwirft diese Bildpunkte vor seiner Suche (Binding 16, Schub `nsch.w`).
  Vorher suchte der alte Weg auch dort (rund 1,5 ms je Bild mit viel Wasser, L2j). Im äußersten Ring zeichnen beide
  wie bisher. Bitgleich (N6d); aus mit `-Dwasser.maske=aus`.

## Grenzen der Grundstufe

- Gespiegelt wird nur, was im Bild ist; was hinter dem Auge oder unter dem Bildrand liegt, ersetzt der Himmel. Die gespiegelte Farbe trägt keine Luftperspektive zwischen Wasser und Treffer.
- Der Grund steht ohne Brechung an seinem Bildpunkt und wird nicht unscharf.
- Das Gelände wirft keinen Schatten auf das Wasser.
- Bis P10c ging das Licht des Himmels als gleichmäßig in den Kegel unter der Fläche ein, und die Fläche war für das Licht hinein glatt; seit P10d (F104 A) nicht mehr (siehe unten); ρ des Grunds aus dem Bild darunter gilt, wo das Gelände von Sonne und Himmel beleuchtet ist (im Schatten wird der Grund mit dunkler).
- Das Auge ist über dem Wasser.
- Kein Seegang: P8 ersetzt die Kräuselung durch `waves.mss` und das örtliche Spektrum.
- Kein gerechneter Schwebstoff: das kommt mit P9.

## Prüfung

Seit P6e liest `WaterCheck` die Höhen der Pixel (Prüfstein 5) aus dem Stapel mit der Mindesthöhe der Bauwerke, wenn das Gebiet eine hat (wie das Gelände im Bild und hydro). Mit den Ständen von hydro prüft `lauf.demo.BildCheck` die Wasserlinie (Teil 2).

```bash
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.render.water.demo.WaterCheck [--gebiet D:\Daten\HalligHooge\gebiet] [--wasser D:\Daten\HalligHooge\_p6\wasser]
java -cp dist\Renderer.jar com.dan.render.water.demo.WasserAuszug D:\Daten\HalligHooge\gebiet D:\Daten\HalligHooge\_p6\wasser
```

Teile:

1. **Daten:** ruhende Spiegel mit Gefälle 10⁻⁴ über G3 und G2. Nass ist, was vom Rand über Kanten ohne Bauwerk erreicht wird und mehr als 0,1 mm trägt.
2. **Wasserlinie (Prüfstein 5):** η_Bild auf der Karte gegen Java an allen 4 Mio. Pixelmitten des Fensters. Benetzter Anteil je teilweise nasser Zelle gegen die Pixel des Stapels unter η der Zelle (seit F46 A; die Tabelle A(η) mit 12 Stufen steht zur Auskunft daneben). Kein Wasser in einem trockenen Ring.
3. **Zeit (Prüfstein 6):** η_Bild zu fünf Zeiten gegen Java. Wechsel des Intervalls.
4. **Optik (Prüfstein 7):** Fresnel bei 0°, 53° und 85°. Transmission durch 1 m reines Wasser. Neigungsvarianz der Wellen.
5. **Dicht (Prüfstein 3):** Ersatzgelände 3 m unter NHN, in drei Ansichten. Dazu die Spiegelung einer dunklen Wand 200 m vor dem Auge im glatten Wasser: Unterkante des Spiegelbilds gegen die Spiegelebene; ohne Spiegelung keine Wand.
6. **Aufräumen**, Validierung (Prüfstein 9).

Gelände und Himmel sind dort ein Ersatz mit bekannten Werten. Attest `docs/status/P6_Wasser.md`.

**Seit P10d (Werkbuch HalligHooge Teil A, 12.11, Datei 44/45):**

- **Spiegelung nah am Auge (F103 A, Messung f):** Die Stärke der Spiegelung und der Durchlass kommen aus der wirksamen Fläche mit der ganzen Neigungsvarianz (gezeichnet und Rest, `s2Ganz`) um die mittlere Normale; die gezeichnete Normale bestimmt nur, welcher Himmel gespiegelt wird, und das Glitzern. Bis P10c spiegelte jede Stelle nach Fresnel zu ihrer gekippten Normale – auf einer ebenen Fläche ohne Verdeckung nah am Auge im Sturm bis 1,5-mal zu hell. `kraeuselungMit` gibt dafür die aufgelöste Varianz zurück.
- **Licht in die Säule (F104 A, Messung e):** Die Sonne und der Mond gehen durch die wirksame Fläche hinein (Durchlass für ihre Richtung). Das Himmelslicht verteilt `licht.comp` je Bild nach seiner Richtung unter der Fläche auf die acht Stufen μ_s der Tafel und rechnet daraus je Tafel (Grundtafel, Stufen der Trübung) und Kanal L des Himmels und ln T des Himmels; `wasser.frag` liest sie statt `S_LH`, `S_TH` (Puffer „Himmelslicht“ 24 352 Byte). Java dazu: `core.szene.Saeule.wertStufen`, `himmelStufe`.
- `RAND_ZELLEN` steht jetzt in `core.szene.Bodennetz` (die Referenz braucht ihn auch).

**Seit P11d (Werkbuch HalligHooge Teil A, 12.12, Datei 60):**

- **Glitzern (A24):** `glanz` in `wasser.glsl` rechnet mit Schattung und Verdeckung nach Smith, G1(v) · G1(l) um die Normale mit α = √rest (`smithG1`, wie `core.szene.Flaeche.g1` und `render.reference.Facette`). Ohne sie war das Glitzern bei flachem Blick und tiefer Sonne 1,4- bis 5-mal zu hell; mit ihr stimmt es gegen die Referenz auf 0,8 % (`render.reference.demo.FlaecheCheck --glanz`).
- **Spiegelung des Himmels (A26):** vier Höhen an den Quantilen 1/8, 3/8, 5/8, 7/8 der Verteilung des Spiegelbilds zu je ¼ (`flaecheTeil(mu, s2, 4)`) statt drei Höhen nach Gauß; die Verteilung ist an der Kimm abgeschnitten und schief, Gauß war 5–16 % zu dunkel. Die Tafel der wirksamen Fläche hat dafür acht Werte je Stelle; Säule und Fläche gehen in zwei Aufrufen von `vkCmdUpdateBuffer` auf die Karte (zusammen über 65 536 Byte).
- **Himmelslicht in die Säule (A23, A27):** `licht.comp` lässt es durch die raue Fläche (Durchlass der Tafel bei der Varianz des Winds, `bild.z`) wie die Sonne seit F104 A; bis P11d durch die glatte.

## Saum der Wasserlinie (seit C1b, Werkbuch HalligHooge 137, A52)

Eine trockene Zelle zeigt am Rand zu einer nassen Seitenzelle deren Spiegel, wenn die gemeinsame Kante weder ein Bauwerk trägt noch über dem Spiegel liegt (Schwelle aus der Subgrid-Packung). Je Ort zählen der nächste Nachbar nach Ost oder West und nach Nord oder Süd (wie die bilinearen Nachbarn), von zweien der höhere Spiegel. Das Gelände schneidet die Wasserlinie dann auf 1 m wie in einer nassen Zelle; vorher brach sie an der Zellgrenze ab (Treppen, trockene Flecken in Zellform). `wasser.glsl` und `core.szene.Spiegelbild` rechnen dieselbe Regel (nur Vergleiche, bitgleich); `-Dwasser.saum=aus` schaltet sie ab (Netz.z.y = 0). `WaterCheck` Teil 0 prüft die Regel auf dem Prozessor; Prüfstein 3 (dicht) läuft ohne Saum.

**Seit S4a (Seegang Werkbuch 35):** Das Netz bindet als 21 das Schaumfeld (`Seegang/Schaum`, render.seegang.Schaumfeld;
ohne Feld ein leerer Kopf). `netz.frag` liest es an der ruhenden Stelle bilinear (fein, sonst grob) und nimmt dort den
Anteil min(1, f) statt der Statistik aus P13c (Schaumköpfe und Brechen); über 32 Zellen am Rand jedes Rasters geht es
weich über. Der Weg ohne Netz (`wasser.frag`) bleibt bei der Statistik.

**Seit S4b (Seegang Werkbuch 36):** `wasser_optik.glsl` hat drei Stellschrauben, die nur das Netz setzt: `schaumAlb`
(Albedo des Schaums, sonst see.w), `blasen` (Aufhellung der Säule unter frischem Schaum), `glatt` (Dämpfung der
unaufgelösten Kräuselung). `netz.frag` rechnet aus der Menge f des Felds die Frische min(1, f), ein zelliges Muster
(Worley F2 − F1 auf 0,4 m: frisch deckend, alternd ein Netz an den Zellgrenzen), die Albedo frisch 0,55 bis alt 0,2,
Blasen 2 · Frische³, Glattwasser bis 0,6 [G]. Ohne Feld ändert sich nichts.

## Auflauf im Wasser (seit S6c-2, Seegang Werkbuch 82, SF93 A)

Shader, die vor `wasser.glsl` `SWASH_BINDUNG` setzen (netz.comp 19, netz.frag und netz_vorlauf.frag 22, wasser.frag 17),
lesen das Bild des Auflaufs (`Wasserbild.SWASH_BILD`, Lage im Kopf: `swLage`, `swAchse`, `swN`). Im Rechteck ist η_Bild der
Stand des Lösers, wo die Zelle mindestens 3 cm Wasser trägt (darunter zeichnet das Gelände den Film), bilinear wie die
Spiegel (ein Nachbar mit weniger Wasser zählt mit der eigenen Zelle); über 8 m an W, S und N geht er in η_Bild der Spiegel
über (`swGewicht`). `netz.comp` nimmt dort die Verschiebung der Clipmap mit (1 − Gewicht) (der Löser trägt die Welle),
`netz.frag` die Neigung des Lösers und Weißwasser an steilen Fronten (Neigung 0,15 bis 0,45) [G]. Die bisherige Regel heißt
`etaBildSpiegel`; ohne Bild oder mit `swashAn` = false (`-Dwasser.swash=aus`) bitgleich wie bisher (WaterCheck,
`core.szene.Spiegelbild` kennen den Auflauf nicht).

Seit S6c-2b (Seegang Werkbuch 83, Befund L4o: an der Böschung ein helles Band mit dunklen Zähnen):
- `swFlaeche`: im Rechteck ist die Fläche des Lösers überall bestimmt – mit mindestens 3 cm Wasser ihr Stand, sonst 0,1 m
  unter ihrem Grund (`SW_UNTER`). Bilinear dazwischen taucht das Netz an der Wasserlinie stetig unter das Gelände; vorher
  sprangen die Scheitel zwischen dem Stand des Lösers und dem Pegel der Hydro (oder dem Spiegel eines Nachbarn), das gab
  senkrechte Wände. `swNetz` (im Band an W, S, N gemischt mit den Spiegeln der Hydro) nehmen `netz.comp` (Scheitel) und
  `netz_fuss.glsl` (Bildpunkte, Suche längs des Strahls ohne Nachbarn); gezeichnet wird, wo der Löser bilinear ab 2,5 cm
  trägt (`SW_NETZ_ZEIGEN`). η_Bild (`etaBild`) im Rechteck ebenso.
- Weißwasser im Rechteck an der Front: wo Stand und Tiefe zugleich steil sind (das kleinere von |∇η| und |∇h| ab 0,08,
  voll bei 0,3), mit dem Muster und der Albedo des Schaumfelds (Frische 0,7 · Stärke). Vorher aus |∇η| allein – auf dem
  dünnen Film einer Böschung 1:2,5 ist das die Böschung selbst, und die ganze Böschung wurde weiß (L4o); |∇h| allein ist am
  ruhenden Ufer die Böschung (L4q: heller Streifen am Fuß). Schaumfeld und Statistik der Brandung im Rechteck × (1 −
  Gewicht): Sie gehören zu den Wellen der Synthese, die dort der Löser ersetzt (L4r: große, glatte Flecken auf 2–3 m
  tiefem Wasser im Rechteck).

## Seit S6e (Seegang Werkbuch 85, A21): die Fläche des Lösers als B-Spline

- `swFlaeche` (wasser.glsl) nimmt Stand und Wasser als **quadratischen B-Spline über 3 × 3 Zellen** um die nächste Mitte (bis S6d bilinear).
  - Bilinear lag die Linie, an der das Netz das Gelände schneidet, auf den Diagonalen der Zellen. An den 1-m-Zellen der Flutkante war das eine Treppe (L4y).
  - Der B-Spline ist in der Neigung stetig; er glättet über ± 1,5 Zellen.
  - Die Neigung des Wassers (`gh`, Front) kommt aus der Ableitung des Splines.
