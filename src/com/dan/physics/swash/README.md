# com.dan.physics.swash

Auflauf und Rücklauf als dünner Wasserfilm (Seegang Werkbuch, S6; SF52–SF59 A). Stand **S6a** (Swash 0.1): der Löser
als Referenz in doppelter Genauigkeit auf dem Prozessor; seit **S6b-1** derselbe Löser auf der Karte (Vulkan, float). Seit
**S6b-2** setzt `lauf.Auflauf` das Rechteck am Blick und treibt es aus der Synthese an (`render.seegang.Randwelle`); das
Package kennt davon nichts. Das Bild (Film, nasser Sand, Spülsaum) folgt mit S6c.

Seit **Swash 0.2** (Seegang Werkbuch 69/70, SF65 A): Courant 0,4 statt 0,3 (`cfl`), für die Referenz und damit für
die Karte in den Prüfungen. Die Prüfsteine aus S6a halten dieselben Werte bis auf die vierte Stelle (im Container
geprüft). Gewählt war 0,45: Damit lief die Karte mit dem Zeitschritt der Referenz bei Carrier und Greenspan nach 66 s
über – eine dünne Zelle am zurückweichenden Ufer (h 4e-5 m, knapp über `hGeschw`) bekam in float 50 m/s (`vMax`), die
Referenz an derselben Stelle (h 1e-5 m) keine Geschwindigkeit. Mit 0,4 bleibt die Karte bei der Referenz; bei 60 Bildern
je s sind es so oder so 2 Schritte je Bild. `SwashKarte.tempoZeilen()` gibt das Tempo je Zeile für Prüfungen.

Seit **S6b-3** (Seegang Werkbuch 71/72):

- `SwashKarte.hoch()` schreibt das Tempo je Zeile aus dem Stand der Referenz in `tempo` (F1). Vorher las das erste Bild
  nach dem Setzen einen leeren Puffer oder das Tempo des vorigen Rechtecks.
- `gpu/swash_saum.comp` (neu, Bindung 14) und `SwashKarte.saum(Ablauf)` / `saumLesen()`: je Zeile der höchste Grund unter
  einer Zelle mit mindestens 1 cm Wasser, relativ zum Ruhewasser. Für die Messung des Auflaufs Welle für Welle.
- `SwashKarte.randZufluss(seite)`: Zufluss über eine Seite je Zeile bzw. Spalte seit `hoch`, aus dem letzten `runter`
  (für den Überlauf über eine offene Seite).
- `demo.SwashKarteCheck` Teil 9 (SF68 A): rund 1 s Vorlauf der Karte, dann 200 gemessene Schritte (vorher 8 und 32).

Seit **S6b-3b** (Seegang Werkbuch 73):

- `SwashKarte.zeileLesen(j)` (Diagnose: eine Zeile des Zustands ohne den ganzen) und `versickert(i, j)` (versickertes
  Wasser der Zelle seit `hoch`, aus dem letzten `runter`).
- Container (Datei 73): Auflauf am Steilhang (1:2,9, unregelmäßig, H_m0 0,55 m, Tp 5,3 s) gegen EurOtop: 0,83 / 0,94 /
  1,00 bei 0,25 / 0,125 / 0,0625 m; am glatten Hang 1:2,5 regelmäßig 1,25 / 1,48 / 1,56 m. Der Löser nähert EurOtop mit
  feinerem Gitter; bei 0,25 m liegt er am Steilhang rund 17 % darunter (SF74).

Seit **Swash 0.3** (Seegang Werkbuch 74, SF74 A): **Front im Teilgitter** nach Bollermann, Chen, Kurganov und Noelle
(2013), in der Referenz (`teilgitter`, Vorgabe an) und auf der Karte (`I_TEIL`):

- Der Grund ist in jeder Zelle eine Ebene mit den Steigungen g_x, g_y (minmod); sein Umfang über die Zelle S = |g_x| + |g_y|.
- Eine trockene Zelle hat an ihren Kanten den linearen Grund (b ± g/2) statt der Stufe: Wasser läuft hinein, sobald es
  die Kante erreicht, nicht erst die Mitte der Zelle.
- Eine nasse Zelle an der Front (ein Nachbar trocken, oder h &lt; S/2) hat einen ebenen Spiegel w über dem linearen Grund;
  reicht das Wasser nicht über die Zelle, steht es in einem Keil, w = b − S/2 + √(2 h S), und die hohe Kante ist trocken.
- Quellterm mit der Tiefe q = ½(h₋² − h₊²)/(b₊ − b₋), wo eine Kante trocken ist (sonst ½(h₋ + h₊) wie bisher): So hält der
  Druck an den Kanten dem Gefälle auch im Keil die Waage.
- `fuellen` füllt in Ruhe nach derselben Regel (`tiefeInRuhe`: im Keil das Volumen über der Ebene) und setzt vorher den
  Grund der Randzellen (`grundRand`) wie `raender`. Der See bleibt in Ruhe (SwashCheck Teil 1: 4e-15 m/s).
- Die Karte rechnet dieselben Kanten in `kantenZelle` (swash_gemein.glsl) und legt sie ab statt der Steigungen: `steig`
  hat seit 0.3 drei vec4 je Zelle (Kanten in x, in y, Steigungen der Geschwindigkeit). `swash_saum` misst an der Front die
  Kontur im ebenen Spiegel (w − 1 cm, höchstens die obere Kante der Zelle in x).
- `spiegel(i, j)`: der Spiegel einer Zelle (an der Front im Keil w).
- Container (Datei 74): unregelmäßiger Seegang am Steilhang 1:2,9 gegen EurOtop bei 0,25 m 0,93 statt 0,83 (0,125 m:
  1,00); am Hang 1:10 0,94 statt 0,92. SwashKarteCheck neu Teil 11 (Steilhang gegen EurOtop ± 10 %) und Teil 12 (Strand
  1:12 gegen Stockdon ± 20 %, SF75 A).
- `swash_stufe` (L4i): die Steigungen der Geschwindigkeit an den Kanten aus dem Zustand im geteilten Speicher, trockene
  Zellen ohne Steigungen; Teil 9 0,764 ms je Schritt (L4h 0,995).
- `swash_rand` (Datei 74, F6): Hat ein Platz des Antriebs im vierten Wert 1, nimmt die Randzelle die erste Zelle innen
  (für `lauf.Auflauf`, Seiten S und N). Die Prüfungen schreiben dort 0; für sie ändert sich nichts.

Seit **Seegang Werkbuch 75** (SF77 A): SwashKarteCheck Teil 12 kurzkämmig in 2D (`jonswapLauf2D`): 64 m breit, je
Komponente eine Richtung aus cos^2s(θ/2) mit s 10 (σ_θ rund 26°), Wände an S und N, Antrieb mit u_n und u_t aus √(g/h0) η;
R2 % aus den Maxima von 12 Zeilen gegen Stockdon ± 20 %. Als Auskunft Stau (Saum im Mittel), S_inc und S_IG (4 σ über bzw.
unter 0,05 Hz) gegen Stockdon und der langkämmige Fall in 1D. Container (0,5 m): 1D +53 %, 2D +47 %; Stau +54–60 %, S_IG
in 1D +72 %, in 2D +26 %. Der Stau hängt an der Kontur des Saums (1 cm 0,44 m, 3 cm 0,32 m, 5 cm 0,25 m; Stockdon 0,28 m).

Seit **Seegang Werkbuch 76** (SF81 A, SF82 A):

- `swash_saum` nimmt die Kontur aus der Schiebekonstanten (`SwashKarte.saum(ab, kontur)`; `saum(ab)` wie bisher 1 cm).
- SwashKarteCheck Teil 12: Wasserlinie bei 5 cm (wie Studien mit SWASH), Grenze ± 30 % (Streuung von Stockdon u. a.
  2006: 38 cm RMS bei 144 cm). Die Referenz mit 1 cm als Auskunft.
- SwashKarteCheck Teil 13 (neu): Überlauf im Kanal in 1D (glatt, Hang 1:2,4, Krone 0,5 m breit, dahinter ein Becken;
  JONSWAP senkrecht in 3 m Tiefe) gegen EurOtop 2018 (Mittelwert) innerhalb eines Faktors 3; zwei Fälle wie am Fuß von
  Deckwerk (H_m0 0,52 m, R_c 0,98 m) und Deich (0,57 m, 0,92 m). Container: 2,76 und 1,99.

| Datei | Inhalt |
|---|---|
| `Swash.java` | Löser: Flachwasser in Flussform auf einem Rechteck (lokal gedreht), HLL, MUSCL (minmod), Heun, hydrostatische Rekonstruktion nach Audusse u. a. (2004), nass/trocken; Kern aus `wave.BayGrid` übernommen (SF3 A, Kopie). Dazu Reibung nach Manning je Zelle, Versickern nach Darcy (Green und Ampt 1911) je Zelle über dem Grundwasser, Begrenzung des Abflusses je Zelle (Bollermann u. a. 2013), Seiten als Wand, offen oder mit Antrieb (einlaufende Riemann-Invariante aus der Welle, die auslaufende aus dem Innern); Spuren: höchster Stand und wann zuletzt nass |
| `Boden.java` | Kennwerte [G]: Manning aus der Rauheit (Strickler, n = k_s^(1/6) / (8,1 √g)), Durchlässigkeit aus d50 (Hazen mit d10 = 0,6 d50; unter der Sandgrenze auf Schlick 1e-9 m/s), ψ und Δθ für Sand nach Rawls u. a. (1983); seit S6c-3 (Seegang Werkbuch 80) dazu Böden, die nicht aus dem Korn folgen: Klei `K_KLEI` 1e-8 m/s, Schotter 1e-3, dicht 0; Manning kurze Grasnarbe 0,020, Gras 0,030, gesetzte Steine 0,018, glatt 0,013, Schotter 0,025 (nach Chow 1959). Die Zuordnung zu den Klassen des Geländes macht `lauf.Bodenkarte` |
| `demo/SwashCheck.java` | Prüfsteine von S6a; Attest `docs/status/S6a_Swash.md` |
| `gpu/SwashKarte.java` | seit S6b-1: derselbe Löser auf der Karte in float, je Stufe fünf Kernel (`swash_rand`, `_steigung`, `_fluss`, `_abfluss`, `_schritt`), dazu `swash_tempo` (größte Wellengeschwindigkeit je Zeile für den Zeitschritt); Grund relativ zum Ruhewasser; Antrieb je Platz in Ständen (`mix(a, b, w)`). Seit S6b-2: `wechsle(neu)` nimmt ein neues Rechteck derselben Größe und Seiten auf dieselbe Karte (dann `hoch`), `tempo` liegt im geteilten Speicher (der Lauf liest das zuletzt fertige ohne zu warten), `antriebPuffer` und `platz` für einen Antrieb, den die Karte selbst schreibt |
| `gpu/swash_stufe.comp` | seit S6b-1b: eine ganze Stufe in einer Abgabe, je Kachel 16 × 8 mit drei Zellen Rand im geteilten Speicher; Zustand im Wechsel zweier Puffer; Kacheln mit Marken (nass, Sickerwasser, beide Puffer gleich), trockene Kacheln übersprungen (L4b: der einzelne Weg kostete auf der 680M 3,6 ms je Schritt, durch den Speicher begrenzt) |
| `gpu/swash_gemein.glsl` | Bindungen 0–13 und die Formeln (Steigung, HLL, Kante, Green–Ampt, Stufe einer Zelle) für beide Wege |
| `demo/SwashKarteCheck.java` | seit S6b-1: die Fälle von `SwashCheck` auf der Karte gegen die Referenz (Zeitschritt und Antrieb aus der Referenz), Kosten des Rechtecks 1024 × 256; Attest `docs/status/S6b_SwashKarte.md` |

```java
Swash s = new Swash(256, 1024, 0.25, ost0, nord0, winkel);   // 64 m quer (x, W seewärts) × 256 m längs (y), gedreht
s.grund((x, y) -> gelaende(s.weltX(x, y), s.weltY(x, y)))
 .rauheit((x, y) -> Boden.manningSand(0.2e-3))
 .durchlaessigkeit((x, y) -> Boden.durchlaessigkeit(d50(x, y)))
 .grundwasser((x, y) -> pegel);
s.antrieb(Swash.W, (t, x, y, aus) -> { aus[0] = eta(t, x, y); aus[1] = uNormal(t, x, y); aus[2] = uLaengs(t, x, y); });
s.fuellen(pegel, null);
s.laufe(60);                       // dann s.tiefe(i, j), s.stand(i, j), s.etaMax, s.nassZeit, s.sick, s.auflauf()
```

```
java -cp dist\Renderer.jar com.dan.physics.swash.demo.SwashCheck              alle Teile, mit Attest
java -cp dist\Renderer.jar com.dan.physics.swash.demo.SwashCheck --teil 5     ein Teil, ohne Attest
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.physics.swash.demo.SwashKarteCheck   Karte, mit Attest
```

Auf der Karte (seit S6b-1):

```java
try (SwashKarte k = new SwashKarte(geraet, konto, s, 2, false)) {
    k.hoch();                                       // Grund, Felder, Zustand der Referenz
    Ablauf ab = new Ablauf(geraet);
    k.schritt(ab, dt, 0, 1);                        // Stufe 1 mit Antrieb aus Stand 0, Stufe 2 aus Stand 1
    k.tempo(ab);                                    // für den nächsten Zeitschritt
    geraet.rechnen.einmal(c -> ab.aufnehmen(c, null));
    k.runter();                                     // nur für Prüfungen
}
```

**Gegenüber Bay geändert (S6a, mit Ursache):**

- `hGeschw` 10 µm statt 1 mm: Mit 1 mm bleibt nach jedem Rücklauf ein unbeweglicher Film knapp unter 1 mm auf dem Hang
  liegen, und die nächste Zunge läuft auf nassen Grund (Teil 5: der Auflauf sank von Periode zu Periode um 13 %).
- Begrenzung des Abflusses: Mit Geschwindigkeit in dünnen Zellen hält der Zeitschritt nach Courant die Tiefe an trockenen
  Fronten in 2D nicht positiv (die Front läuft mit 2c, die Richtungen addieren sich; Teil 2: 1,7e-8 abgeschnitten, auch mit
  Courant 0,2). Eine Zelle gibt in einem Schritt höchstens ab, was sie hat; das Volumen bleibt genau. Die Wellengeschwindigkeiten
  nach Einfeldt statt Davis halfen nicht (schlechter).

**Grenzen (S6a):** Die dünne Spitze einer Zunge (nach Ritter wächst die Tiefe mit dem Quadrat des Abstands) konvergiert in
erster Ordnung: Bei 0,25 m Zellen ist die Front eine Stufe von rund 1 cm, der dünnere Film davor fehlt (Kontur 0,1 mm −30 %,
1 cm +6 %; Teil 6). Phasenaufgelöst, hydrostatisch, ohne Brechen in der Walze (das kommt als Stoß); kein Grundwasserstrom.

## Seit Seegang S6c-2 (Seegang Werkbuch 82, SF93 A): das Bild des Rechtecks

- `gpu/swash_bild.comp` (neu), `SwashKarte.bild(ablauf, kontur)`: nach den Schritten eines Bilds je Zelle vec4 (h, Stand
  relativ zum Ruhewasser oder ≥ 1e29 für Fels, Sekunden seit zuletzt nass oder −1, versickert oberhalb der Front) und je
  Zeile der Spülsaum (m quer bis zur Mitte der landwärtigsten Zelle, deren höchster Stand mindestens `kontur` über dem
  Grund lag; −1e30 ohne). Eine Gruppe je Zeile. `bildPuffer()`, `bildBytes(s)`, `bildLesen()`; dieselbe Regel in float auf
  dem Prozessor `SwashKarte.bildReferenz(s, zeit, kontur)` (nach `runter`, bitgleich).
- `demo.SwashKarteCheck` Teil 13 (SF95 A): dazu der Fall „flacher Fuß, niedrige Krone“ (`FLACH`: H_m0 0,88 m, Tp 6,6 s,
  2,8 m am Rand, Vorland 1:10 bis 1,3 m, Hang 1:2,6, R_c 0,92 m, Krone 2 m) gegen EurOtop mit H_m0 und T_m−1,0 am Fuß aus
  einer Rechnung ohne Bauwerk (`ueberlaufFlach`, Strand 1:30, einlaufend nach Guza u. a. 1984), Faktor 3. Container:
  Löser 0,42 (0,25 m), 0,50 (0,125 m) von EurOtop.

Kennt nur `java.*` (Schichtenvertrag, Werkbuch HalligHooge Teil A, Abschnitt 4).

## Seit Seegang S6c-2b (Seegang Werkbuch 83): das Bild mit der Stufe

- Befund L4o: `swash_bild` las je Bild alle Zellen (0,165 ms von 1,14 ms; Grenze 1 ms). Jetzt schreibt `swash_stufe` im
  letzten Schritt eines Bilds (Schub `bild` = 1, `SwashKarte.bildImSchritt`, von `lauf.Auflauf` gesetzt) jede Zelle, deren
  Stand sich ändert (in Stufe 2 gerechnet oder mit Sickerwasser, in Stufe 1 oder 2 aus dem anderen Puffer übernommen),
  über Bindung 14 (`bildZelle` in `swash_gemein.glsl`). `swash_bild.comp` füllt ganz nur nach dem Setzen (erster Aufruf von
  `SwashKarte.bild` nach `hoch`) und schiebt sonst je Bild nur den Spülsaum jeder Zeile weiter (`bildZeilen`, ein Faden je
  Zeile, ab der vorigen Grenze). Darum steht „zuletzt nass“ absolut in der Zeit der Karte (−1e30: nie); Gelände und Wasser
  bekommen die Zeit im Kopf. L4q mit Bits „erreicht“ aus der Stufe war 0,05 ms teurer in der Stufe und ist verworfen.
- Spülsaum seit S6c-2b: die Auflaufgrenze von der See her – von i = 0 die letzte Zelle einer ununterbrochenen Folge, deren
  höchster Stand die Kontur erreichte (Fels unterbricht), als Index + 1 in den Bits von x (0: keine); `saumAusBild`. Der
  höchste Stand steigt nur, also wandert die Grenze nur landwärts. Vorher die landwärtigste Zelle überhaupt; dann lag der
  Saum in Zeilen, in die Wasser von der Seite hinter die Krone lief, hinter der Krone. `bildReferenz` rechnet dieselbe Regel.
## Seit S6e (Seegang Werkbuch 85, A23): Kachel 16 × 16

- `swash_stufe.comp` rechnet mit Kacheln zu **16 × 16 Zellen** (bis S6d 16 × 8, jetzt `swash_stufe8.comp`; `-Dswash.kachel=8` oder `SwashKarte.kachelY` vor dem Bau der Karte).
  - Der Körper steht in `swash_stufe.glsl`, die beiden `.comp` setzen nur `KACHEL_Y`.
  - Weniger Rand je Zelle: Laden 1,89 statt 2,41, Kanten 1,56 statt 1,88, Flüsse 2,67 statt 3,03 je Zelle.
- θ liegt seit S6e in `sT[n].x`, sobald jede Zelle ihre eigenen Kanten in Registern hat. So bleibt die Kachel 16 × 16 bei 31,5 kB geteiltem Speicher, also zwei Gruppen je CU.
- Die Formeln sind dieselben. `SwashKarteCheck` Teil 10 (verschmolzen gegen einzeln, bitgleich) gilt für die neue Kachel; Teil 9 misst beide Kacheln im Wechsel.
