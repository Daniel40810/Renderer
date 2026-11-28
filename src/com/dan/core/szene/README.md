# com.dan.core.szene – die gemeinsame Szene

**Stand: P8c (16.11.2026); nachgezogen seit F61 (P7f–P7l, P8b, P8c).** Teil des Kerns, nur `java.*` und `core` (Werkbuch HalligHooge, Teil A, 12.8, F52 A und F53 A). Was das Echtzeitbild (`render.air`, `render.terrain`, `render.water`) und die Referenz (`render.reference`) beide rechnen, steht hier: dieselbe Quelle, nicht nur dieselben Werte (Leitidee 2). Module derselben Schicht kennen einander nicht (Schichtenvertrag, 4); darum liegt die Szene im Kern.

| Klasse | Inhalt | vorher |
|---|---|---|
| `Atmosphere` | Lufthülle: Rayleigh, Mie aus der Sicht, Ozon, Radien (Hillaire 2020, Bruneton 2017) | `render.air.streuung` |
| `Scattering` | Tabellen und Strahlengang auf dem Prozessor (Prüfung von `render.air`); seit P7e erste Streuung genau, ab der zweiten J aus `Streuordnungen` (Ψ nach Hillaire nur noch mit `hillaire = true`), dazu `radianceSpektral` (15 Wellenlängen nach sRGB) und die Bilder von J für die Karte; seit P10d `durchWieTabelle` (die Durchlässigkeit, wie die Karte sie bilinear aus der Tabelle liest, aus den vier Stützstellen) | `render.air.streuung` |
| `Streuordnungen` (P7e) | Quelle der Mehrfachstreuung J(h, µ, µ_s, φ) je Wellenlänge, Rayleigh und Mie getrennt, nach Bruneton (2017) in sechs Streuordnungen mit echten Phasenfunktionen und dem Boden; Gitter 24 h (100 km · x³, dicht am Boden) × 32 µ × 40 µ_s (ab −0,2, tiefer 0) × 32 φ, in µ_s und h logarithmisch gelesen; die Durchlässigkeit liegt als optische Tiefe in den Tabellen (seit F59 A); drei Kanäle oder 15 Wellenlängen zu 22 nm, nach linearem sRGB je nm mit dem Licht dazwischen quadratisch im Logarithmus (`nachRgb`, `spektralTafel`); float16, abgelegt unter `~/.hallighooge/luft/` | neu |
| `Lichtquellen` | Sonne, Mond, Nachthimmel: Örter aus `core.astro`, Brechung, Stärken je Kanal; seit P7k (F68 A) `SONNE_FARBE`, Stärke und Farbe der direkten Sonne aus dem Spektrum der Referenz (rund 1,145 / 1,020 / 0,984 auf 133 100 lx); seit P10d `farbeNachLuft`: der Faktor nach der Luft für die Höhe der Quelle (das Spektrum je nm durch die Luft, Beiwerte wie die Referenz, durch die Durchlässigkeit, die die Karte aus ihrer Tabelle liest; je Bild für Sonne und Mond, rund 0,02 ms je Quelle) | `render.air.Himmel` |
| `Wasseralbedo` | seit P10d (F105 A): Albedo des Wassers für den Himmel je Kanal, Fläche aus `wasseralbedo.bin` (gebacken von `render.reference.Wasseralbedotabelle`: Referenz über einer rauen Wasserkugel ohne Säule gegen Kugeln nach Lambert, 11 Höhen der Sonne × 5 Winde) plus Säule aus `Saeule`; seit F106 B in der Anwendung nicht in Gebrauch (bei tiefer Sonne schlecht bestimmt, Datei 47) | `render.air.Himmel` (nur mit `wasserOptik`) |
| `Boden` | Albedo je Rauheitsklasse, Struktur aus dem Luftbild, Klassen in 1 m und 10 m; `pixel` für die Referenz | `render.terrain.Material` |
| `Wasseroptik` | Pope und Fry, Schwebstoff, Fresnel, Cox und Munk, Wellen der Kräuselung | `render.water.Optik` |
| `Saeule` (P7d) | Tabellen der Wassersäule (`saeule.bin`, gebacken von `render.reference.Saeulentabelle`): L, C, T, U, R über u, Tiefe und Winkel (seit F61 A 32 Stufen in x, x_j = 40 (j/31)⁴, dicht im Flachen); die Tafel für `wasser.frag` je Bild | neu |
| `Flaeche` (P7g, F63 A) | die wirksame Wasserfläche: was die Neigungen, die ein Bildpunkt nicht auflöst, im Mittel über die sichtbaren Flanken tun (Beckmann mit σ² nach Cox und Munk, Schattung nach Smith wie `render.reference.Facette`): wirksame Spiegelung, Höhe und Breite des Spiegelbilds, Durchlass; seit P11d (A26) dazu die Höhe an den Quantilen 1/8, 3/8, 5/8, 7/8 (acht Werte je Stelle); als Tafel für `wasser.frag` und `licht.comp` | neu |
| `Seegangsnetz` (P8b) | ein Gitter des Seegangs für das Bild: Hs, Tp, Richtung (als Vektor), mss, diss aus `waves.*` in zwei Ständen, linear in der Zeit; seit S3c-6 (SF31 A) `wertGemischt`: am Rand eines feineren Netzes über `RAND_MISCHUNG` = 500 m in das nächst gröbere; seit S3c-7 (SF33 A) nach `RAND_ART` (`-Dseegang.rand=aussen|innen|aus`, Vorgabe außen: im feineren nur das feinere, außerhalb vom Randwert in das gröbere; `gemischt`, `wertRand`, `aussenAbstand`) | neu |
| `Bodennetz` (P9b) | das Gitter des Sediments für das Bild: die optisch wirksame Trübung C_opt = Schlick + 0,05 · Sand (g/m³, aus `sed.c`) in zwei Ständen, und der Boden – Rippel (Höhe, Länge, Richtung als Vektor), Schlickanteil, Bodenänderung aus `bed.*` – in zwei Ständen mit eigenem Takt |
| `Seegangsoptik` (P8b, F76 A) | die 72 langen Wellen in 6 Bändern × 12 Richtungen, Tafel des JONSWAP und Gegentafel, Schaum; `neigung` (Varianz für Prüfstein 9). Seit P8c `werte` (die sechs Werte am Ort wie `seeWerte` im Shader) und `neigungAm` (die Neigung am Ort mit Phase, in double), damit die Referenz denselben Seegang sieht | neu |
| `Spiegel` | ein Gitter des Wassers: zwei Stände, Bauwerke, Schwellen (F47) | `render.water.Spiegel` |
| `Spiegelbild` | η_Bild für ein Auge und eine Zeit: Kopf des Shaders und dieselbe Regel in Java | `render.water.Wasserbild` |
| `spektrum.Spektrum` | 360–830 nm in 1 nm: CIE 1931, D65, Sonne (ASTM G173), Ozon, reines Wasser, Rayleigh nach Bodhaine, RGB → Spektrum nach Mallett und Yuksel | neu |

**Geprüft beim Umzug:** `AirCheck` 19, `FrameCheck` 28, `WaterCheck` 15 und `TerrainCheck` 13 Prüfsteine ohne Verstoß; `WaterCheck` und `TerrainCheck` geben vor und nach dem Umzug Zeichen für Zeichen dieselbe Ausgabe (bis auf Zeiten).

**Daten** (`spektrum/spektrum.txt`): CIE 1931 2° und D65 aus `colour-science` 0.4.7; ASTM G173-03 aus `pvlib` 0.16.1; reines Wasser nach Pope und Fry (1997), außerhalb 380–727,5 nm nach IOCCG (2018), aus github.com/raphidoc/aw (MIT); Ozon nach Serdyuchenko u. a. (2014) in 10 nm aus Bruneton (2017, BSD); Basis nach Mallett und Yuksel (2019) aus `colour-science`.

**Grenzen:** Mie ohne Gang mit der Wellenlänge (Ångström 0) wie im Echtzeitbild; Randverdunklung der Sonne grau; Nachthimmel mit der Form des Sonnenspektrums [G].

## Seit P12a: Bauten

`Bauten` beschreibt die Häuser eines Gebiets als Körper ohne Grafik (Ablage `bau/gebaeude.bin`, Schritt `gebaeude` der
Aufbereitung): Dreiecke mit Normale, Lage auf der Fläche (u, v, w für Fenster, Backstein und Halme) und Art (Wand, Dach),
je Haus Boden, Traufe, First, Dachart (Reet, Ziegel, flach), Albedo von Dach und Wand und die gemessene Farbe des Dachs
im DOP20. Raum wie die Clipmap des Geländes (x = Ost − e0, z = n0 − Nord). `Bauten.Bauer` zerlegt ebene Ringe
(`Zerlegung`: Normale nach Newell, Ohrenschneiden). Gelesen von `render.bau`, später von `render.reference` (P12c).

## Seit P12b: Gestalt

`Gestalt` ist die Karte der Gestalt (Ablage `gestalt/`, Schritt `gestalt`): Kacheln (`Kachel`: Südwestecke, 2 Byte je
Bildpunkt, Zeile 0 im Norden) mit Art (`FENNE` … `HOLZ`) und Wasserstand als Byte (`stand`, `standByte`:
w/50 − 1 m NHN, 0 = keiner). `bei(ost, nord)` gibt beides am Ort. Die Körper der Gestalt sind `Bauten` in
`bau/koerper.bin` (`Bauten.KOERPER`, Arten `HOLZ`, `STEIN`, `BETON`, `BUSCH`); `Bauten.zusammen` hängt sie an die Häuser.

## Seit P12c: Gehölze

`Gehoelz` sind die Gehölze eines Gebiets (Ablage `bau/gehoelz.bin`, Schritt `gehoelz`): je Gehölz Lage im Raum der
Bauten (x = Ost − e0, z = n0 − Nord), Boden (m NHN), Höhe über dem Boden, acht Halbmesser der Krone (Ost, dann gegen
den Uhrzeigersinn), Art (`ESCHE` … `HUNDSROSE`), Fläche. `kroneIn(a)` gibt den Halbmesser in einer Richtung.

**Zonen der Salzwiese (P13b).** `Zonen`: je Bildpunkt (1 m, Kacheln zu 500 m wie die Gestalt) Byte Zone (0 keine, 1 Pionierzone, 2 untere, 3 obere Salzwiese, 4 Fenne ohne Tidezone seit F155 A) und Byte Höhe über MThw (b / 100 − 1,28 m, 0 kein Wert); Ablage `vegetation/zonen/zonen.txt` und `E<ost>_N<nord>.rg8`. `zone(über, −0,40, +0,35)` teilt nach der Höhe; `aufGestalt(Gestalt)` gibt eine Gestalt mit den Arten 6 (Pionierzone), 7 (untere), 8 (obere Salzwiese), 9 (Fenne unten oder Pionier) für das Bild; `ohneZone(art)` führt sie auf 2 und 1 zurück.

**Schlick (F158 A).** `aufGestalt` macht Salzwiese der Karte unter der Pionierzone (Zone 0 mit Höhe) zur Art 13 `ART_SCHLICK`: im Bild Gelände ohne Gras und ohne Halme; `ohneZone(13)` ist wieder Salzwiese.

## Seit P13c (Brandung, Werkbuch 12.14)

| Klasse | Inhalt |
|---|---|
| `Kanten` | Böschungen der Brandung je 5 m: Fuß, Richtung zur See, Krone, tan α, γ_f; Ablage `brandung/kanten.txt` |
| `Brecherform` | Formen der Brecher im Querschnitt, unverändert aus `com.dan.surf.BreakerShape`; Typ nach Battjes |
| `Brandung` | je Bild Brecher als Netz, Auflauf nach EurOtop 2018, Bündel der Gischt; ohne Zustand, ohne Grafik |
| `Seegangsoptik` | dazu Höhe der langen Wellen, Kammform, Brechen, Schaumköpfe (Φ, Φ⁻¹), `D_KAL` |

Seit S5b-4 (Seegang Werkbuch 46, SF51 A): `Fusswellen` (neu, 0.1) sammelt die Kämme der Fläche am Fuß je Abschnitt
(von `render.seegang.Fusssuche`): derselbe Kamm unter 0,3 T_m01, nach dem Durchgang fest, eine Vorhersage, die die
nächste Suche nicht wiederfindet, fällt weg; Sprung der Zeit über 5 s oder rückwärts: leer; älter als 2 s: die
Brandung nimmt ihre eigene Folge. `Brandung.fuesse`: Wo das Netz zeichnet, nimmt ein Abschnitt seine Wellen aus der
Folge – Aufschlag = Durchgang + `laufzeit` (2 √(h_Fuß/g) / tan α), Höhe 2 |Z| · H_s / H_s,Fuß (Rang aus der Fläche,
Verteilung nach Rayleigh wie bisher), Volumen des Überlaufs mit T_m01 statt Tp als Abstand der Wellen
(`volumen(…, tWelle)`). Jenseits der Clipmap und ohne Folge unverändert (Byte für Byte geprüft).
`Oberflaeche.tm01(x, y)`: T_m01 am Ort (Referenz der Suche).
Seit S5b-4c: `Fusswellen.Folge.hsGezeichnet` (H_s der gezeichneten Wellen am Fuß); `folge` gibt nichts, wo die Stufe
weniger als 0,25 H_s zeichnet (`GEZEICHNET_MIN`), `stand(kante)` für Prüfungen. `Brandung` skaliert 2 |Z| mit
H_s / H_s,gezeichnet.

Seit S5b-5 (Seegang Werkbuch 50): **Lippen im Netz.** Ein Abschnitt, der den Kämmen der Fläche folgt, zeichnet die
Lippe jeder Welle, die an einem seiner Enden sichtbar ist: Typ aus ξ (nur Sturz und Reflexion; den Schwall zeichnet die
Fläche selbst), Höhe gezeichnet 2 |Z'| (höchstens 0,78 h + 0,05), Lage des Kamms nach der Zeit τ bis zum Aufschlag
u = g tan α τ² / 4 seewärts der Wasserlinie, Brechpunkt wie bisher (H / 0,78 bzw. 0,3 H über dem Hang); nach dem
Aufschlag die Walze (aus der Lippe übergeblendet), dann taucht sie ab. Vor dem Brechpunkt taucht die Lippe in
`lippeAn` Tp auf, nach der Walze in `lippeAus` s ab (unter Wasser und Grund) [G]. Die Form steht an beiden Enden des
Abschnitts; **die Enden werden mit dem Nachbarn geteilt** (`lippenNachbarn`): Ort an der Wasserlinie, Richtung, η, h,
tan α, Tp und – wenn der Nachbar denselben Kamm kennt (gegenseitig der nächste, gemessen am erwarteten Unterschied der
Ankunft längs der Kante, höchstens `lippeFang` · Tp daneben) – Aufschlag und Höhe gemittelt, von beiden Seiten gleich
gerechnet. So passen Nachbarn aneinander ohne Kacheln. Nachbarn: einmal je `Kanten` aus der Lage der Füße
(eine Länge weiter längs t, höchstens 0,4 Längen daneben, gegenseitig). Gezeichnet erst nach allen Abschnitten;
`lippenZahl`, `lippenSumme`, `lippenProtokoll` (`LIPPE_KOPF`), `formenGerechnet`. Jenseits des Netzes unverändert.

Seit S5b-5b (Seegang Werkbuch 51, Befund am Bild der Anwendung 24.11.: „wieder kachelig“): Die Füße der Kanten sind
der steilste Punkt je Zelle von 5 m (Kantenkarte), oft im Zickzack oben und unten am Hang und 3 bis 9 m auseinander;
die feste Nachbarschaft „eine Länge weiter längs t“ fand dort nur ein Drittel der Nachbarn, die übrigen Enden standen
frei – Kacheln. Jetzt: **Nachbarn je Bild aus den Wasserlinien** der Abschnitte mit Lippe (`ketten`: n·n' ≥ 0,3,
höchstens `ketteMax` = 12 m, nicht zu quer, die kürzesten zuerst, je Seite einer); die Richtung am geteilten Ende quer
zur Verbindung der Wasserlinien; **derselbe Kamm nach dem Durchgang am Fuß** (die Laufzeit zur Wasserlinie streut mit
h und tan α der einzelnen Abschnitte um Sekunden); ein Ende, das kein Nachbar mit derselben Welle teilt, ist
unsichtbar (die Lippe taucht ab); ein geteiltes Ende taucht erst auf, wenn beide Füße den Kamm sicher kennen
(`lippeKennt` 0,15 s nach dem späteren Durchgang, dann `lippeEin` 0,5 s) [G]. `nachBild` und `Lippenpruefung` (Auswertung je Bild) für Prüfungen; im Protokoll
dazu der Ort der Enden (`LIPPE_KOPF` = 16).


## Spiegelbild: Saum (seit C1b, A52)

`Spiegelbild.SAUM` (Vorgabe an, `-Dwasser.saum=aus`): eine trockene Zelle zeigt am Rand zu einer nassen Seitenzelle deren Spiegel, wenn die Kante offen ist und unter dem Spiegel liegt. Siehe `render/water/README.md`.

## Seit Seegang S3a: die Oberfläche des Seegangs, Referenz (Oberflaeche 0.1)

Aus den Amplituden des Nahfelds (`physics.waves.nah.Nahfeld`, Varianz je Zelle, Band und Richtung) baut `Oberflaeche`
die Fläche zu einer Zeit, in double, als Referenz für die Karte (S3b) und das Netz (S3c). Seegang Werkbuch, Datei 9.

| Klasse | Inhalt |
|---|---|
| `Dispersion` | σ² = g k tanh kh mit Strömung, c_g, Blockierpunkt; Kopie aus `Waves` und `Nahfeld` (SF3 A) |
| `Profilpuffer` | je Stützstelle eine Hülle G_bq(τ) = Σ c_m e^{iΔ_m τ}: viele Teilwellen im Band, gleiche Energie in ω, zufällige Phasen; G und G' an 4096 Stellen, kubisch nach Hermite; Perioden 1,8–13 h |
| `Phasenfeld` | je Stützstelle die Phase Φ der Mitte des Bands und die Laufzeit der Gruppe T = ∂Φ/∂ω über Tiefe und Strömung: kleinste Quadrate zu ∇Φ = k(h, U) e_θ und ∇T = e_θ/(c_g + U_θ) (Poisson, Kosinus- bzw. Sinus-Transformation); fein mit dem Rand aus grob; `angleichen` an einen anderen Stand |
| `Seegangsform` | Schiefe und Vorneigung nach Ruessink u. a. (2012), Beiwert der zweiten Ordnung, Höhen nach Battjes–Groenendijk (2000), gebundene lange Welle nach Longuet-Higgins und Stewart (1962) |
| `Oberflaeche` | die Synthese: Z = Σ √(2E) G(T − t) e^{i(Φ − ωt)}, waagrechte Verschiebung nach Gerstner (λ_eff gegen das Falten), Form η = Re Z' + Re(c Z'²), Stufen fein, grob und Fernfeld (72 Wellen der Stufe 2) mit Überblendung; `punkt` (Lagrange), `euler` (fester Ort), `lage` (Normale, Jacobi-Determinante), `buch` (Varianz und mss für das Bild) |

Prüfung: `lauf.demo.OberflaecheCheck` (13 Teile, 38 Prüfsteine), Attest `docs/status/S3_Oberflaeche.md`.

## Seit Seegang S3b: Phasenfeld mit Gewicht, Schranke der Wellenzahl

Seegang Werkbuch, Datei 10. Die Karte dazu ist `render.seegang.SeegangKarte`.

| Klasse | Neu |
|---|---|
| `Phasenfeld` | `rechne(…, e)`: mit der Varianz je Zelle und Stützstelle zählt jede Kante mit 0,01 + Ê (Ê relativ zur größten Energie der Stützstelle), gelöst mit konjugierten Gradienten (Jacobi), Start aus der Lösung ohne Gewicht (Befund B33: ohne Gewicht 25 % Fehler in \|∇Φ\| gegen k(h), mit Gewicht 3 %); `kMin` je Band, die kleinste Wellenzahl der Dispersion im Feld; `schritte` |
| `Oberflaeche` | Schwelle (SF23 A): eine Stützstelle zählt über 10⁻³ m0 (`E_REL`) oder 10⁻³ der Neigung (`S_REL`), bis zum Doppelten eingeblendet (`faktor`); `aktiv`: die Stützstellen, die irgendwo zählen (die Karte nimmt dieselbe Liste); Kennwerte je Zelle; `kUnten()`, die Auflösung misst sich an max(\|∇Φ\|, kMin) (Befund B34) |
| `Seegangsform` | `HTR_MIN` = 0,05: H_tr / H_rms nicht unter 0,05 (wie die Tafel der Karte) |

Seit S5b-5c (Seegang Werkbuch 52, Befund L3e: „immer noch“ kachelig; im Bild von oben Splitter): **`Brandung.lippen`
ist aus (Vorgabe).** Die Nähte zwischen Nachbarn sind geschlossen (Prüfstein grün), aber die Lippe zerfällt in kurze
Stücke: Neigung und Fußhöhe der Abschnitte streuen, die Laufzeit vom Fuß zur Wasserlinie springt zwischen Nachbarn um
Sekunden (Warft: Median 1,3 s, 95 % 7 s bei Tp 5,6 s), so ist die Lippe nur über kurze Stücke zugleich sichtbar. Die
Prüfungen schalten sie für sich ein. Neuer Plan in Datei 52.

Seit S5b-5d (Seegang Werkbuch 53, Weg A aus Datei 52): `spuren` – derselbe Kamm bei den Nachbarn nach dem
Durchgang an den beiden **Füßen**, erwarteter Unterschied aus der Lage der Füße längs der mittleren Laufrichtung (bei
flachem Hang liegen die Füße bis 40 m vor der Wasserlinie; der Unterschied längs der Wasserlinie traf dort oft nicht);
`glaetten` – je Welle längs ihrer Spur Aufschlag und Höhen über ±`glattBreite` (3) Abschnitte glätten (gleitender
Median, dann Dreiecksmittel) [G]; die Lage zu glätten (`glattLage`) bleibt aus, sie brachte an den echten Kanten mehr
kurze Stücke. `Lippenpruefung` misst dazu die **Stücke** (zusammenhängend sichtbare Länge, f ≥ 0,5). Die Lippe bleibt in
der Anwendung aus; WalzeCheck schaltet sie zur Prüfung ein.

**S5b-5 abgeschlossen ohne Lippe (Seegang Werkbuch 55, Daniel 24.11. 21:23: „C“).** `Brandung.lippen` bleibt aus;
der Code (Ketten, Spuren, Glätten, `Lippenpruefung`) bleibt für einen späteren eigenen Schritt (Lippe an der
Uferlinie aus dem Gelände, Datei 54 Weg D). Die Prüfungen geben die Werte der Lippe nur noch als Auskunft.

Seit S5b-6 (Seegang Werkbuch 55, Plan Datei 45): **`Reflexion` (neu, 0.1) – die Phase der Reflexion.** Das Nahfeld gibt
an Flächen zu trockenen Zellen K_r² in die gespiegelte Richtung zurück; die Synthese zeichnete die reflektierte
Stützstelle aber mit eigener Hülle und eigener Phase, vor einer Wand stand keine Welle. Jetzt: `partner` (aus den Wänden
einer Stufe: je reflektierte Stützstelle die einlaufende mit dem größten Fluss hinein, nur wenn diese selbst mehr in die
Wände läuft), `richtungen` (Richtung genau gespiegelt), `teilen` (dieselbe Hülle, `Profilpuffer.teilen`), `konstanten`
(Φ und T so verschoben, dass an den Wänden Φ_r = Φ_e und T_r = T_e im gewichteten Mittel: Bauch an der Wand [G]; im
feinen Feld mit festem Rand nur der Rest, vom Rand bis zur Mitte eingeführt [G]). Partner fest je gebauter Fläche, aus
der groben Stufe [G]. Prüfung: `lauf.demo.OberflaecheCheck` Teil 14 (Bauch an der Wand, K_r aus Bauch und Knoten).

Seit S5b-6b (Seegang Werkbuch 56/57): **`Reflexion` 0.2 – Bauch je gerades Wandstück.** An Hooge traf die eine Konstante
je Paar nur einen kleinen Teil der Wände (Kohärenz fein 0,11–0,32, L3h): Wände in verschiedener Lage brauchen verschiedene
Konstanten (2 k_x Δx). Jetzt: `partner(grob, fein)` zählt auch die Wände des feinen Gitters (Energie davor aus der groben
Stufe; im Landunter stehen die Warften oft nur dort); Gewicht einer Wandfläche √(E_e E_r) · cos (nur, wo das Nahfeld
zurückwirft); `wand(Stufe, Phasenfeld, fein)` ersetzt `konstanten`: gerade Stücke (Flächen derselben Achse und Seite,
lückenlos, ≥ 3 Zellen und ≥ L/2 [G]) bekommen je ihren Versatz von Φ und T (Bauch an der Fläche), der längs der Strahlen
der reflektierten Welle ins Feld läuft (quer geglättet, σ = L/2 [G]); alle übrigen Strahlen tragen die eine Konstante je
Paar wie in S5b-6. Ein Versatz, der längs des Strahls abklingt (erster Versuch: weiche Bedingung im Lösen), änderte die
Wellenzahl um 10 % (Teil 15). Neu `Phasenfeld.gradient(bq, c)` (k und 1/(c_g + U) je Zelle). `Bilanz`: Kohärenz vorher
(beste Konstante), nachher (alle Flächen), in den Stücken, Anteil der Stücke am Gewicht. Grenze: An Treppen schräger oder
runder Wände ist die Spiegelung an der Achse des Gitters (Nahfeld) nicht die wahre Reflexion; dort gibt es keinen
gemeinsamen Bauch (runde Warft: 36 % des Gewichts in geraden Stücken). Prüfung: `OberflaecheCheck` Teil 15.

Seit S5b-6b2 (Seegang Werkbuch 58–61): **`Reflexion` 0.3.** L3i zeigte an Hooge auch in den geraden Stücken keinen Bauch
(fein 0,08 / 0,25). Ursachen: die Rampe des feinen Felds stieg bis zur Mitte (jetzt `RAMPE_ZELLEN` = 12 [G]); ein Versatz je
Stück (jetzt je Fläche, längs des Stücks stetig abgewickelt). Dazu `spiegeln`: vor den Flächen gerader Stücke in derselben
Zeile (Spalte) bis 1 L voll, bis 2 L aus [G] Φ_r = 2 Φ_e(F) − Φ_e(x), T_r = 2 T_e(F) − T_e(x) (F an der Fläche; auch die
trockene Zelle dahinter) – das Spiegelbild der einlaufenden, bei jedem Winkel; das stehende Muster hängt dort nur noch an
der einlaufenden. Grenze: Wo das Phasenfeld der einlaufenden selbst vom Modell abweicht (Kompromiss B26 bei Tiefe quer zur
Welle), weicht auch das Muster ab (Teil 15, Tiefe längs der Wand 3 → 6 m: K_r 0,70 / 0,76 bekannt rot).

Seit S6b-2 (Seegang Werkbuch 67/68, SF61 A, SF62 A): **`Oberflaeche.Punkt` mit dem Antrieb des Auflaufs.** `punkt` rechnet
dazu den tiefengemittelten Durchfluss je Welle q = (ω / k) η k̂ über Stützstellen und Fernfeld (`qx`, `qy`), die gebundene
lange Welle nach Longuet-Higgins und Stewart, die im Bild aus bleibt (`lwAntrieb`; Durchfluss mit c_g), η₂ mit c, und den
Stand des Antriebs (`etaAntrieb` = η ohne die lange Welle des Bilds, mit der des Antriebs). Die übrigen Felder sind
unverändert. Referenz für `render.seegang.Randwelle`. An trockenen Punkten (nur Fernfeld) wird die lange Welle groß (A7).

Seit S6c-1b (Seegang Werkbuch 78, SF85 A): **`Oberflaeche.punktRand(x, y, t, p, xm, ym, hR)`**, der Punkt für den Antrieb
eines Lösers ohne Dispersion an einem geraden Rand durch (xm, ym) mit der Tiefe hR: je Stützstelle Phase und Laufzeit längs
des Rands gestreckt (Φ′ = Φ_m + s (Φ − Φ_m), s = (ω/√(g hR)) / |∇Φ_m|; T′ = T_m + (T − T_m) / (|∇T_m| √(g hR))), Durchfluss
je Welle mit √(g hR), das Fernfeld ebenso. So hat jede Welle im Löser ihre Richtung aus der Synthese. Am Bezugspunkt
gleich `punkt` bis auf den Durchfluss. Referenz für `see_antrieb` mit `RANDRICHTUNG`.

Seit S7e1 (Seegang Werkbuch 99, SF148 A): **`Kennung`** (0.1), die Kennung eines Feuers als Funktion der Zeit, aus der
Schreibweise der Seekarte (`Fl(2) G 9s`, `Q`, `VQ`, `LFl`, `Iso`, `Oc(2)`, `Mo(A)`, `F`): `hell(t)` zwischen 0 und 1, mit
weichem Rand von 0,05 s [G]. Blitz 0,5 s, langer Blitz 2 s, Q 1 s Periode (0,3 s an), VQ 0,5 s (0,2 s an), Morse-Punkt
0,5 s, Strich 1,5 s, Pause zwischen den Blitzen einer Gruppe 1 s [G]. `blitzeJePeriode()` zählt an der Funktion (Prüfung),
`rgb()` gibt die Farbe (linear).
