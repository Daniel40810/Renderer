# com.dan.render.reference – Referenz Stufe 1

**Stand: P8c (16.11.2026), Version 0.2; nachgezogen seit F61 (P7f–P7l, P8c).** Bildmodul der Schicht `render`, kennt nur `core` (Werkbuch HalligHooge, Teil A, 1.2, 8.5 und 12.8; F4, F52–F54). Ein Pfadverfolger in Java, double, auf dem Prozessor: der Maßstab für das Echtzeitbild (Prüfstufe D), langsam, aber ohne Karte und auf jedem Rechner prüfbar.

| Klasse | Inhalt |
|---|---|
| `Referenz` | ein Bild aus einem `Blick`: Bildpunkt als Fläche, zwei unabhängige Hälften, alle Kerne; Ergebnis lineares sRGB in cd/m² |
| `Pfadverfolger` | Pfade vom Auge zurück durch Luft, Wasser, an Boden und Wasseroberfläche; Ereignisse zur Lichtquelle |
| `Wellen` | Wellenlängen je Pfad (Heldenwellenlänge, Wilkie u. a. 2014; Vorgabe 4, in `ReferenzCheck` 16) oder die drei Kanäle des Echtzeitbilds (Schalter aus F53 A) |
| `Medien` | Luft in Kugelschalen, Wassersäule; spektrales Tracking (Kutz u. a. 2017); Durchlässigkeit der Luft durch Quadratur, Ratio-Tracking zur Prüfung |
| `Phasen` | Rayleigh, Cornette-Shanks (Mie), Fournier-Forand (Schwebstoff, F54 A) |
| `Facette` | raue Wasseroberfläche nach Walter u. a. (2007) mit Cox und Munk; seit P8c je Treffer: α² aus dem Wind plus der Rest des Seegangs (`Szene.Treffer.sigma2`) |
| `Seegangsflaeche` (P8c, F77 A) | der örtliche Seegang wie im Bild (Stufe 2): dieselben Netze (`core.szene.Seegangsnetz`) und langen Wellen (`Seegangsoptik.neigungAm`), in double und ohne Filter nach Bildpunkten. Am Treffer die Neigung aller langen Wellen und als Neigungsvarianz der Rest max(0, mss − aufgelöst) |
| `Licht` | Sonnen- und Mondscheibe |
| `Szene`, `Ebenen` | Flächen der Szene; einfache Ebenen für die Prüfung (seit P7e mit `kugel`: der Boden als Kugel um die Mitte der Luft, für die Dämmerung; seit P10d mit `neigung`: die Normale der Wasserfläche gekippt wie in `Gelaendeszene` mit Seegang) |
| `Gelaendeszene`, `Schranke` | die Szene aus dem Gebiet (seit P7c): Gelände aus `Gelaende.hoehe` (1 m, mit Mindesthöhe), Wasser aus `Spiegelbild` (η_Bild, dieselbe Regel wie das Echtzeitbild), Material aus `Boden.pixel` mit der Nässe aus `water.wetAge`; gesenkt um d² / 2 R_eff. Geschnitten über eine Pyramide der Höchstwerte (Basis 1 024² Zellen zu 256 m, Ziegel zu 32 m unter den Stufen bis 10 m), darin in Schritten eines Viertels der Pixel und durch Halbieren auf 10⁻⁷ m |
| `Strahlgleichung` | gekrümmte Strahlen durch n(x), Runge-Kutta 4 |
| `Saeulentabelle` | backt `core.szene.Saeule` (seit F61 A 32 Stufen in der Tiefe, dicht im Flachen) |

**Seit P7f:** Mit `Referenz.teile` (P7h, F65 A) zählt jeder Beitrag zusätzlich nach seinem Weg (Fläche, Säule aus Sonne und Mond, Säule aus dem Himmel, Grund); `demo/FlaecheCheck` (P7g) prüft `core.szene.Flaeche` gegen die Facetten. **Seit P8c (F77 A):** Hat `Gelaendeszene.seegang` einen Seegang, neigt er die Fläche am Treffer um die langen Wellen und gibt den Rest der Neigungsvarianz dazu; ohne ihn (und in `Ebenen`) ist alles wie vorher. `lauf.demo.ReferenzCheck` hat eine sechste Ansicht (Brandung vor dem Japsand im Sturm) und `--name` für den Namen des Attests.

**Entscheidungen im Bau:**

- Sonne und Mond zählen nur über das Ereignis zur Lichtquelle; der direkte Blick des Auges zählt sie auch. So wird nichts doppelt gezählt; die Scheiben sind klein gegen jede Keule der Szene. Je Ereignis wird eine Quelle nach ihrer Stärke gezogen (bei Tag die Sonne, bei Nacht der Mond).
- **In der Luft** zählt das Ereignis zur Lichtquelle an jedem vorläufigen Stoß des Trackings (Gewicht σ_s / σ̄), nicht nur an der Streuung (seit P7c). Die Durchlässigkeit zur Quelle ist eine Quadratur (Gauß-Legendre in Abschnitten, ≤ 10⁻⁹ gegen eine feine Mittelpunktregel): Das Ratio-Tracking streut am Boden stark, weil dort die Majorante an der Auslöschung liegt.
- **Keule zur Sonne** (seit P7c): Am Boden, nach einer Streuung in der Luft und an der Wasserfläche (Spiegelung von oben, Brechung von unten) wird ein Viertel [G] der Richtungen aus einer Keule um die stärkste Quelle gezogen (Henyey-Greenstein, g = 0,8 [G]) und mit der Mischdichte gewichtet: Der Kranz um die Sonne beleuchtet stark und wird sonst selten getroffen.
- **Unter Wasser** kommt das Licht der Quelle durch die raue Fläche: zwei Richtungen mit Multiple Importance Sampling (Balance), eine über eine Mikronormale nach Beckmann (Punkt auf der Scheibe, gebrochener Strahl, BTDF nach Walter, geteilt durch die Dichte der Richtung), eine nach der Phasenfunktion bzw. Lambert. Der Weg Quelle → Fläche → Punkt im Wasser ist damit ganz geschätzt; das Ereignis an der Fläche von unten entfällt nach einem Ereignis im Wasser (vor P7c zählte es denselben Weg ein zweites Mal, bei rauem Wasser +91 %, `ReferenceCheck` Teil 6).
- Die Erdkrümmung steckt wie im Echtzeitbild in den Flächen (gesenkt um d² / 2 R_eff, k = 0,13); `Strahlgleichung` zeigt, dass ein gekrümmter Strahl über der Kugel denselben Punkt trifft (Prüfstein 5). Kimmung aus `air.n` kommt mit P10.
- Fournier-Forand als Tabelle (8 192 Stufen in θ, je Stufe gleichverteilt in cos θ): Ziehen und Auswerten sind dieselbe Verteilung. Die Dichte in der ersten Stufe (θ bis 6 · 10⁻¹² rad) ohne Auslöschung.
- Unter Wasser sucht ein Strahl die nächste Fläche nur 5 m [G] weit (`wasserWeite`); weiter fliegt er bei b ≈ 19/m mit der Wahrscheinlichkeit e^−95.
- In einer trockenen Zelle hat das Wasser keine Fläche; ein Strahl, der seitlich in den Wasserkörper läuft, geht hindurch wie im Echtzeitbild (gezählt).

**Demo und Selbsttest:** `demo/ReferenceCheck` ohne Gebiet, Prüfsteine 1–5 (Ofentest, Beer–Lambert und Durchlässigkeit, einfacher Rayleigh-Himmel, Spektrum, gekrümmte Strahlen) und Teil 6 zu den Schätzern aus P7c (Quadratur der Durchlässigkeit; Grund unter glattem und unter rauem Wasser gegen Formel bzw. Integral); Attest `docs/status/P7_Referenz_Kern.md`. `--schnell` rechnet ein Zehntel der Pfade. Gegen das Echtzeitbild: `lauf.demo.ReferenzCheck` (Prüfsteine 6–10).

**Tabellen der Wassersäule (P7d, F55 A):** `Saeulentabelle` backt mit Monte Carlo an einer Platte (Fournier-Forand, gerechnet mit a + b_b = 1) die Tabellen L, C, T, U, R für `core.szene.Saeule` (`saeule.bin`); Aufruf `java -cp dist/Renderer.jar com.dan.render.reference.Saeulentabelle src/com/dan/core/szene/saeule.bin [Photonen oben] [Photonen Grund]`, Vorgabe 200 000 und 100 000, deterministisch. `demo/SaeuleCheck` prüft sie (Energie, erster Stoß, Grund nach Lambert, Azimut, Tafel des Echtzeitbilds gegen eine eigene Platte; Lee u. a. 1999 zur Auskunft); Attest `docs/status/P7_Saeule.md`.

**Luft gegen das Echtzeitbild (P7e, F57 A, F58 A):** `demo/LuftCheck` misst den Himmel der Referenz gegen `core.szene.Scattering` (wie `luft.glsl`) an 10 Richtungen je Sonne, zerlegt in Einfachstreuung (`Referenz.einfach`: nur der erste Stoß), ganz in drei Kanälen und spektral (360–830 nm gegen die 15 Wellenlängen der Himmelsansicht); Boden als Kugel mit Albedo 0,08, ohne Nachthimmel. Sicht 30 km mit der Sonne 40°, 15°, 5°, 0°, −3°, Sicht 10 km mit 40°, 15°, 5°. Aufruf `java -cp dist/Renderer.jar com.dan.render.reference.demo.LuftCheck [--pfade n] [--nur 30|10] [--sonnen 40;15;5;0;-3]`, Vorgabe 200 000 Pfade; Attest `docs/status/P7_Luft.md`.

**Grenzen:** Die Schattung der Facetten nach Smith gibt Energie ab (Ofentest mit rauem Wasser: 0,4 % bei Windstille, 4 % bei 5 m/s, 10 % bei 12 m/s, viele Durchgänge durch die Fläche gerechnet); Mehrfachstreuung zwischen Facetten fehlt. Keine Polarisation, keine Fluoreszenz. Die Wassersäule mit 30 mg/l Schwebstoff unter Sonne konvergiert langsam (Streuung je Pfad rund das Elffache des Mittels: der gebrochene Sonnenstrahl ist nur selten in der Vorwärtsspitze des Pfads). Stufe 2 auf der Karte kommt mit P11.

**Quellen:** Wilkie u. a. (2014); Kutz u. a. (2017); Walter u. a. (2007); Veach (1997); Cox und Munk (1954); Fournier und Forand (1994), Mobley u. a. (2002); Cornette und Shanks (1992); Hillaire (2020).


## Seit P10b: gekrümmte Sehstrahlen (`Luftstrahl`)

`Referenz.luftstrahl` (null: aus): Sehstrahlen nahe der Waagerechten (± 0,5°) laufen nach der `Strahlgleichung` über der Kugel durch ein Profil n − 1 über der Höhe (dasselbe, das `render.air.Kimmung` bekommt). 2 048 Wege je Sehwinkel werden einmal gerechnet (Runge-Kutta, Ecken nach dem Pfeil von 1 mm, höchstens 400 m) und im gesenkten Raum der Szene als Streckenzug gegen die Flächen geschnitten, um die Sperrhöhe tiefer (F93 A). `gerade(d)` gibt die gerade Richtung zum ersten Treffer; von dort verfolgt die Referenz ihren Pfad wie immer. `Gelaendeszene` nimmt mit Halbwertszeit 0 die Nässe als Sättigung (`surf.wet`). Geprüft in `lauf.demo.KimmungBildCheck` (ohne Gebiet) und `ReferenzCheck --kimmung` (Prüfstein 13 von P10b).

**Messungen von P10d (Werkbuch HalligHooge Teil A, 12.11):** `demo/LuftCheck --nach-unten` (Messung d: Luftperspektive aus 300 m nach unten gegen die Referenz über der Kugel, Albedo 0,23 / 0,06 / 0, Attest `docs/status/P10_LuftNachUnten.md`), `demo/SaeuleCheck --leiter` (Messung e: Säule der Referenz gegen die Tafel in vier Stufen – glatt, rau, mit Luft und Himmel, spektral; `docs/status/P10_SaeuleLeiter.md`), `demo/FlaecheCheck --wellen` (Messung f: Fläche mit langen Wellen und Kräuselung nah am Auge, im Ofen und mit Himmel; `docs/status/P10_FlaecheWellen.md`). Alle zur Auskunft, ohne Grenze; je `--pfade n`.

**Seit P10d, Teil 2 (F102 A, F103 A, F104 A):** `Szene.truebung(p)`: die Trübung am Punkt, an dem ein Pfad ins Wasser eintritt; `Medien` rechnet dann Absorption und Streuung des Wassers je Bahn mit ihr (`Wellen.aWasserBei`, `bWasserBei`; bei gleicher Trübung bitgleich wie ohne, `demo/TruebungCheck`). `Gelaendeszene.sediment(Bodennetz, t, Schwebstoff, Trübung, dz)` gibt sie aus dem Netz des Sediments wie `truebungOrt` im Echtzeitbild (`Bodennetz.truebungAm`) und hebt den Boden mit `bed.dz` (`Bodennetz.dzAm`; die Schranken um die größte Hebung). `Seegangsflaeche.facetten` (Vorgabe an, F103 A): die langen Wellen als Facetten statt als gekippte Normale. `demo/FlaecheCheck --wellen` und `demo/SaeuleCheck --leiter` rechnen das Echtzeitbild nach F103 A / F104 A (mit Grenze 1 ± 0,10 bzw. ± 0,05) und zeigen den Stand bis P10c daneben.

**Seit P10d (F105 A):** `Ebenen` mit `kugel` legt auch das Wasser als Kugel an (von oben und von unten). `Wasseralbedotabelle` backt die Fläche von `core.szene.Wasseralbedo`: der Himmel (Mittel über den Azimut, Höhe 0,2–25°, ohne 5° um die Sonne, Auge 4,7 m, Sicht 30 km) über einer rauen Wasserkugel ohne Säule gegen Kugeln nach Lambert mit 0,06 / 0,23 / 0,40, je Höhe der Sonne und Wind; die gleichwertige Albedo stückweise linear. `demo/WasseralbedoCheck` (`--backen <datei>` legt sie ab; sonst die Prüfung: Rauschen, Wasser mit Säule gegen Fläche plus Säule aus der Tafel; Attest `docs/status/P10_Wasseralbedo.md`). Befund (Datei 47): Die gleichwertige Albedo ist bei tiefer Sonne schlecht bestimmt (neu gemessen bei Sonne 4° R 0,23 statt 0,59) und hängt an der Richtung; die Tafel ist darum seit F106 B nicht in Gebrauch, das Werkzeug bleibt für P11.


## Seit P11a: Stufe 2 auf der Karte (`gpu/`)

**Werkbuch HalligHooge Teil A, 8.5 und 12.12 (Dateien 50, 51); F107–F111 A.** `gpu/ReferenzKarte` rechnet denselben Pfadverfolger auf der Karte, in float und mit der Lage relativ zum Auge. Die Shader folgen den Klassen von Stufe 1 Datei für Datei: `gpu/kopf.glsl` (Bindungen, Werte, Zufall PCG, `Vek`, `Wellen`, `Licht`), `gpu/pfad.glsl` (`Phasen`, `Facette`, `Medien`, `Pfadverfolger`, Ablauf je Faden), `gpu/szene_ebenen.glsl` (`Ebenen`); der Kernel `gpu/pfad_ebenen.comp` bindet sie zusammen. Stufe 1 bleibt der Maßstab; an ihr ist nichts geändert außer Lesezugriffen für Stufe 2 (`Licht.richtung()`, `cosRadius()`, `randU()`, `faktor()`, `sonne()`, `Phasen.ffTafel()`).

| Thema | Stufe 1 (double) | Stufe 2 (float) |
|---|---|---|
| Höhe in der Luft | \|q\| − R | (x² + z² + (h_Auge + y)(2R + h_Auge + y) + t (2b + t)) / (\|q\| + R), ohne Auslöschung |
| Kugeln (Ebenen mit `kugel`) | (ro − rb)(ro + rb) | x² + z² + (y − h)(y + h + 2 (R + h_Auge)) |
| Fournier-Forand | cos θ je Stufe | 1 − cos θ = 2 sin²(θ/2); gesucht in F unter ½, in 1 − F darüber; Dichte je Stufe in double gerechnet |
| Scheibe von Sonne und Mond | cos des Radius | 1 − cos des Radius (in double gerechnet) |
| Facetten (Beckmann, Smith) | tan² = (1 − c²) / c² | tan² aus dem Kreuzprodukt |
| Versatz von Flächen | 10⁻⁴ m | 10⁻⁴ m + 4 · 10⁻⁷ · größte Koordinate |
| Majorante der Luft | mal 1,000001 | mal 1,0001 |
| Zufall | SplittableRandom je Zeile | PCG je Faden, Runde und Saat |

**Modi** (`ReferenzKarte.MODUS_*`): Bild (wie `Referenz.bild`, zwei Hälften, mit `teile` die vier Teile des Wassers), eine Richtung, freier Flug durch Wasser, Einfachstreuung (Stoßschätzer), Fournier-Forand, Tafeln. **Runden:** jede Rechnung beginnt mit einem Pfad je Faden und verdoppelt, solange eine Runde unter 30 ms bleibt (Ziel 60 ms; Windows bricht einen Aufruf nach 2 s ab).

**Prüfung:** `gpu/demo/ReferenceKarteCheck` (Prüfsteine 1–8 von P11a: Ofentest, Beer–Lambert, Rayleigh-Himmel, Tafeln, Grund unter rauem Wasser, Stufe 1 = Stufe 2 an Ebenen und im Bild, Geschwindigkeit, Speicher und Validierung); Attest `docs/status/P11a_ReferenzKarte.md`, `--schnell` ein Zehntel der Pfade ohne Attest. In der Arbeitsumgebung läuft der Kernel auf lavapipe (Mesa, Vulkan auf dem Prozessor).

**Befund P11a:** Die Säule unter der Sonne streut in beiden Stufen stark (Variationskoeffizient je Pfad 18–21 bei 30 mg/l, 82–85 % der Varianz aus dem Licht der Quelle durch die Fläche); Prüfstein 6 gibt diesen Szenen mehr Pfade.

**Grenzen (P11a):** nur die Szene `Ebenen` (ohne `neigung`); das Gebiet (Höhenfeld, Seegang, Trübung am Ort, gekrümmte Strahlen) kommt mit P11b.

## Seit P11a (Abnahme 19.11.2026): Stufe 2 ruht

Die Probe der Wellenfront (`gpu/wellenfront.glsl`, Kernel `gpu/wf_*.comp`, `ReferenzKarte.wellenfront`,
`gpu/demo/WellenfrontProbe`) schaffte gegen Stufe 1 auf 16 Fäden nur das 3- bis 6,5-Fache in der Luft (Prüfstein 7:
10-fach); nach F112 gilt B. Stufe 2 (großer Kernel und Wellenfront) bleibt geprüft im Code, ist aber nicht in Gebrauch.
Das Foto (P11b, `com.dan.lauf.Foto`) rechnet mit Stufe 1. Seit P11b hat `Referenz` den Schalter `abbrechen`.

**Seit P11d (Werkbuch HalligHooge Teil A, 12.12, Datei 60):** `demo/FlaecheCheck --glanz` (A24: Glitzern der tiefen Sonne, Echtzeit mit Smith gegen die Referenz, 1 ± 0,05 [G]; Attest `docs/status/P11d_Glanz.md`); `--wellen` rechnet die Spiegelung mit den vier Quantilen, `SaeuleCheck --leiter` das Himmelslicht durch die raue Fläche, beide wie das Echtzeitbild seit P11d. Messklassen von P11d1, nur zur Auskunft: `demo/P11dSaeule` (Säule nach Sonne und Himmel getrennt, `Referenz.teile`), `demo/P11dGlanz`, `demo/P11dFlaeche` (Spiegelung: drei Höhen, über alle Neigungen gemittelt, Quantile, Referenz), `demo/P11dGelaende` (ebener Boden nach Lambert gegen `gelaende.frag`).

## Seit P12c3: Häuser, Körper, Gestalt und Gehölze im Foto

**Werkbuch HalligHooge Teil A, 12.13, Datei 81 (F138 A, F141 A, F142 A, F143 A).** Das Foto zeigt jetzt, was das Bild
zeigt.

| Klasse | Inhalt |
|---|---|
| `Huellbaum` | Hüllkörperbaum über achsparallele Kästen (Teilen in der Mitte der längsten Achse, Blätter bis 4), nah zuerst |
| `Dreiecksbaum` | Häuser und Körper aus `core.szene.Bauten` (die Bereiche, die das Bild zeichnet: `Hausbild.bereiche`), relativ zum Auge, je Scheitel gesenkt; Möller–Trumbore in double. Material wie `haus.frag` im Mittel der Muster: Wand, Sockel, Fenster (Rahmen, Glas als Spiegel, Licht nachts nach dem Anteil des Bilds), Tür, Reet mit Moos und First, Ziegel, Körper nach Art und Nässe |
| `Kronen` | Gehölze als Ellipsoide mit Laub als Dichte (σ = 0,5 · Blattfläche je Volumen aus den Netzen des Bilds am Tag) und Stamm; Stoß im Laub ist ein Treffer **Blatt** (Normale auf der Kugel, Durchlass 0,35). Zufall aus dem Strahl, damit die Referenz wiederholbar bleibt |
| `Gestaltfeld` | Gestaltkarte wie `gelaende.frag` ohne Muster und ohne Ausblenden: Gestaltwasser als Anteil des Spiegels (`Treffer.spiegel`), Wege mit der mittleren Albedo des Belags |
| `Bauszene` | Gelände (mit Gestalt), Dreiecksbaum und Kronen: der nächste Treffer |

**Neu im `Pfadverfolger`:** Arten `BLATT` (Lambert nach beiden Seiten, Reflexion ρ, Durchlass τ · ρ; im Ofen ρ (1 + τ) = 1)
und `SPIEGEL` (Schlick f0, Beckmann α², darunter Lambert; Glas f0 0,043, Gestaltwasser f0 0,02 und α² 0,006 wie im
Bild); leuchtende Flächen (`Treffer.leuchtet`, `Wellen.leuchten`). Ein Boden mit `spiegel` > 0 wird aus der Luft mit
diesem Anteil zum Spiegel (unter der Flut bleibt er Boden, F134 A). Ohne die neuen Felder bleibt jeder Pfad wie vorher
(kein zusätzlicher Zufall).

**`lauf.Foto.referenz(…, Hausbild, Bewuchsbild)`** baut die `Bauszene`, wenn Häuser, Gestalt oder Bewuchs im Bild an
sind; sonst dieselbe Referenz wie vorher. Prüfung: `lauf.demo.ReferenzBauCheck` (Dreiecksbaum gegen alle Dreiecke,
Ofen mit Laub, Prüfsteine 8, 19, 9, 10). Attest `docs/status/P12c_ReferenzBau.md`, Bilder `P12c_Foto*.png`.

**Grenzen (P12c3):** Muster nur im Mittel (F143 A); Gehölze als Kronen, nicht als Netze; keine Halme; gekrümmte
Sehstrahlen (Kimmung) sehen nur das Gelände; Spiegel ohne Abschattung der Facetten; nur Stufe 1.
