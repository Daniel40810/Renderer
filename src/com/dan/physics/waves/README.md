# com.dan.physics.waves

Der Seegang als Bilanz der spektralen Wirkungsdichte (Werkbuch HalligHooge Teil A, 7.3 und 12.9). Stand P8b (Waves
0.2, WavesModul 0.1): die Referenz in doppelter Genauigkeit und derselbe Seegang auf der Grafikkarte in float, mit
Strömung und allen Quellen; seit P8b das Modul `waves` am Bus (einseitig: liest Wasser und Wind, schreibt `waves.*`),
der Rand aus ERA5 und das Randspektrum je Randzelle. **Seit P8c** liest hydro `waves.force` im Intervall (Rückwirkung; der Takt rechnet den Seegang dafür ein Intervall voraus, `WavesModul.beginn` liest das Wasser erst, wenn es liegt), und nach dem Schritt der Quellen kappt `Waves.kappung` (Referenz und Karte gleich) das Spektrum einer Zelle auf H_rms = √(8 m0) ≤ γ · d (γ aus Battjes–Janssen, 0,73); das Abgezogene bucht die Bilanz unter „Brechen“ und zählt zu `diss` (W7 aus P8b).

| Datei | Inhalt |
|---|---|
| `Waves.java` | Referenz: Ausbreitung (x, y, θ, σ; Flussform, aufwind; in θ und σ zweite Ordnung, begrenzt nach van Leer), Quellen (Wind, Schaumkronen, Vierwellen DIA, Bodenreibung, Brechen), Bilanz der Wirkung, Kenngrößen (`hs`, `tp`, `richtung`, `ubot`, `mss`, `kraft`) |
| `WavesModul.java` | Modul `waves` (P8b): eigene Gitter in UTM (G2w über G2 in 500 m, G3w über G3 in 100 m), das feinere einseitig aus dem gröberen (Spektrum an den Geisterzellen, linear in der Zeit); Kopplung 1 200 s, Schritte der Quellen zu 10 s (seit S3c-5, SF30 A; vorher 60 s); liest `water.h`, `.u`, `.v`, `air.u10`, `.v10` und mit Schub `air.taux`, `.tauy` (u* = √(\|τ\| / ρ_L)); schreibt `waves.hs`, `.tp`, `.dir` (woher, von Gitternord), `.ubot`, `.mss`, `.diss`, `.force`. `Rechner`: `Prozessor` oder `gpu.WavesRechner` |
| `Randspektrum.java` | Rand aus ERA5 (P8b, F74 A): Windsee und Dünung je als JONSWAP (γ 3,3) mit T_m−1,0 von ERA5 und cos²ˢ aus der Richtungsbreite, an den Geisterzellen bilinear, in der Zeit linear |
| `gpu/WavesRechner.java` | Rechner des Moduls auf der Karte: je Gitter eine `WavesKarte` auf eigenem Gerät |
| `gpu/ausbreitung.comp` | Karte: ein Teilschritt der Ausbreitung mit Strömung (Transport mit c_g + U, Drehung an Tiefe und Strom, Verschiebung der Frequenz); ein Faden je Zelle und Frequenz |
| `gpu/quellen.comp` | Karte: ein Schritt der Quellen; eine Gruppe je Zelle, die Vierwellen-Wechselwirkung sammelt je Stufe ein (ohne atomare Addition) |
| `gpu/WavesKarte.java` | Puffer, Kernel und Schritt auf der Karte; Spektrum hoch und herunter; `felder()` nach einer Änderung von Tiefe, Strömung oder Wind |
| `demo/WavesKarteCheck.java` | Prüfstein 6 (dieselben Fälle wie `WavesCheck`, Karte gegen Referenz) und 8 (Zeiten auf der Karte, Vorgabe für die Auflösung); Attest `docs/status/P8a_WavesKarte.md` |
| `demo/WavesCheck.java` | Prüfsteine 1, 2, 3, 4, 5, 7 aus 12.9 und die Zeiten der Referenz; Attest `docs/status/P8a_Waves.md` |

Gespeichert ist E(f, θ) in m²/(Hz·rad) (`waves.spec`), Richtung 0 = +x, gegen den Uhrzeigersinn, wohin die Wellen
laufen. Üblich: 28 Frequenzen von 0,04 bis 0,6 Hz × 36 Richtungen. Die Karte nimmt bis 32 Frequenzen und 72 Richtungen.

```java
Waves w = new Waves(nx, ny, dx, dy, 28, 0.04, 0.6, 36);
// Tiefe, Strömung, Wind setzen, dann
w.vorbereiten();
try (WavesKarte k = new WavesKarte(geraet, konto, w, randSpektrum, 1 /* West */, false)) {
    k.schritt(60);          // Ausbreitung in Teilschritten nach Courant, dann die Quellen
    k.runter();             // Spektrum in w.e; Kenngrößen über w.hs(c), w.tp(c), …
}
```

```
java -cp dist\Renderer.jar com.dan.physics.waves.demo.WavesCheck                  alle Teile, mit Attest
java -cp dist\Renderer.jar com.dan.physics.waves.demo.WavesCheck --teil 3         ein Teil, ohne Attest
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.physics.waves.demo.WavesKarteCheck   Karte, mit Attest
```

Am Bus (P8b, verdrahtet von `com.dan.lauf.Lauf` mit `Einstellung.seegang`):

```java
WavesModul m = new WavesModul(List.of(g2w, g3w), 28, 36, Wetterfeld.lies(gebiet, Wetterfeld.SEEGANG), start, new WavesRechner(), 1200, true);
m.ab = 30 * 3600;                 // davor legt der Seegang null ab
bus.anmelden(m);
```

`Waves.uStern` (u* je Zelle), `Waves.diss` (Schaumkronen und Brechen, W/m²) und `Waves.randAusFeld` (Spektrum je
Randzelle in der Folge von `Gitter.rand`) gibt es seit P8b; die Karte rechnet sie mit.

Grenzen: phasengemittelt (keine Beugung, keine einzelne Welle), keine Dreiwellen-Wechselwirkung, Wind ohne Abzug
der Strömung. Die Bilanz bucht nur die Referenz. Wasser und Tiefe liest das Modul bilinear vom Gitter von hydro (an den
Mitten seiner Zellen, ohne Mittel über die Fläche). Bekannte Abweichungen stehen im Werkbuch, 12.9.

`Waves` kennt nur `java.*`, `WavesModul` und `Randspektrum` dazu den Kern, `gpu/` dazu `com.dan.core.gpu` (Schichtenvertrag, Werkbuch 4).

## Seit Waves 0.3 und WavesModul 0.2 (Seegang Werkbuch, S1; SF6 A): die Diagnose

Nur lesend, die Rechnung ist bitgleich mit 0.2:

| Teil | Was |
|---|---|
| `Waves.partitionen(c)` | Windsee und Dünung: ein Anteil E(f, θ) gehört zur Windsee, wo 1,2 · 28 u* / c · cos(θ − θ_w) > 1 (WAM, `BETA_WINDSEE`), der Rest ist Dünung; je Teil Hs, Tp (Parabel wie `tp`), Richtung (wohin, wie `richtung`) und Richtungsbreite σ_θ = √(2 (1 − r)) (Kuik u. a. 1988). Die Summe der beiden m₀ ist m₀ der Zelle |
| `Waves.dissipation(c)` | die Senken als Raten aus dem jetzigen Spektrum (W/m²): Schaumkronen, Brechen an der Tiefe, Bodenreibung; dieselben Formeln wie im Schritt der Quellen, ohne den halb impliziten Nenner und ohne die Kappung |
| `Waves.jonswap(c, hs, tp, wohin, s)` | setzt das Spektrum einer Zelle auf einen JONSWAP (γ 3,3) mit cos^(2s)((θ − θ_m)/2), m₀ genau hs²/16 (Anfang eines Nachlaufs) |
| `Waves.woher`, `Waves.wohin` | Richtung wohin (0 = +x, gegen den Uhrzeigersinn) und woher (von Gitternord im Uhrzeigersinn, wie `waves.dir`) |
| `WavesModul.Rechner.hoch(w)` | das Spektrum vom Prozessor auf den Rechner (Karte: `WavesKarte.hoch`) |
| `demo/DiagnoseCheck` | Prüfsteine ohne Karte: Anfangsspektrum, Trennung (Windsee mit dem Wind, Dünung gegen ihn), Senken gegen den Verlust eines Schritts von 0,01 s (± 1 %), Richtungen; Attest `docs/status/S1_Diagnose.md` |

**Halb implizit:** In einer stark brechenden Zelle verliert ein Schritt von 60 s weniger als die Rate mal 60 s, und nach
dem Schritt liegt das Spektrum nahe an der Grenze. Was die Brandung wirklich verliert, ist `diss` des Schritts; der
Nachlauf (`lauf.SeegangNachlauf`) legt darum `waves.dbr` als `diss` − Schaumkronen ab.

```
java -cp dist\Renderer.jar com.dan.physics.waves.demo.DiagnoseCheck
```


## Nahfeld 0.2 (Seegang Werkbuch, S2; SF1 A): `physics.waves.nah`

Die Wirkungsbilanz auf einem feinen Gitter um das Auge, als Referenz in doppelter Genauigkeit (die Karte folgt mit S3).
Was das Nahfeld anders macht als `Waves`:

| | `Waves` (G2w, G3w) | `Nahfeld` |
|---|---|---|
| Frequenz | relativ (σ), mit Fluss in σ bei Strömung | **absolut** (ω), kein Fluss in der Frequenz; k je Zelle aus ω = σ(k, h) + k U_θ. Die Oberfläche (S3) braucht je Band ein festes ω |
| Blockieren | – | ohne Lösung (Gegenstrom über der Gruppengeschwindigkeit) ist die Stützstelle blockiert, die Wirkung geht verloren und wird gebucht |
| Beugung | – | phasenentkoppelt nach Holthuijsen, Herman und Booij (2003): c_g → s c_g, dazu eine Drehung, s = √(1 + δ), δ aus der Krümmung von √E über rund L/4 |
| Kanten | trocken heißt kein Fluss | Reflexion in die gespiegelte Richtung, K_r = min(1, 0,1 ξ²) (Battjes 1974), an Bauwerken (`wand`) 1 |
| Pflanzen | – | Mendez und Losada (2004), Eingaben C_D b_v N_v und Halmhöhe je Zelle |
| Vierwellen | DIA | keine [G]. Für Dünung reicht das, junge Windsee im Sturm wächst ohne sie zu langsam (B20): Im tiefen Wasser bindet das Nahfeld deshalb an das Spektrum von G3w, dessen Form aus der DIA kommt |
| Quellen | einmal je Schritt | nach jedem Teilschritt der Ausbreitung (`quellenJeTeilschritt`; abschaltbar für den Vergleich): das Gleichgewicht einer Zelle hängt dann nicht vom Schritt ab |
| Gitter | fest | `verschieben(di, dj)` zieht mit dem Auge (was bleibt, bleibt bitgleich), `schachtel(grob, fein)` für zwei Stufen |
| Bindung (0.2) | – | `bindungSpektrum` (Varianz von G3w je Stützstelle): jede Stützstelle implizit mit der Zeit `bindungZeit` zum Ziel, N ← (N + a Z/σ)/(1 + a), a = Δt/τ (B20). Ohne sie `bindungM0` (m₀ von G3w je Zelle): je Block (`bindungBlock`, 100 m) auf das Mittel des Ziels geführt, die Form des Spektrums bleibt. Gebunden wird nur im tiefen, gleichmäßigen Wasser (SF17 A): frei bleiben Blöcke, die im Mittel höchstens `bindungTiefe` (2 m) tief sind oder deren Tiefe um mehr als `bindungStreuung` (25 %) des Mittels streut [G] (Watt, Priele, Kanten: dort rechnet der feine Grund Shoaling, Reibung und Brechen selbst), dazu Blöcke mit oder neben einer trockenen Zelle oder Wand (Lee) oder nach `ohneBindung` |
| Anlauf (0.2) | – | `anlauf(runden, toleranz, dtMax)`: zum Gleichgewicht in Pseudozeit, je Stützstelle ein eigener Schritt (Courant 0,8); derselbe Zustand wie mit `schritt`, bucht nicht. Die Senken sind dabei um den Zustand nach den Quellen der Runde davor linearisiert (B22: sonst schwingt das Brechen bei großem Schritt von Runde zu Runde) |
| Rand (0.2) | – | `partitionen(sea, swell)`: das Spektrum aus `waves.sea` und `waves.swell` (je Hs, Tp, woher, Breite) als zwei JONSWAP mit cos²ˢ, s aus der Breite nach Kuik |

Gespeichert ist die Wirkung je Stützstelle (m²·s, über Band und Δθ genommen); die Varianz der Stützstelle ist σ N.
Bänder logarithmisch zwischen `fMin` und `fMax`, Richtungen 0 = +x, gegen den Uhrzeigersinn, wohin.

```java
Nahfeld n = new Nahfeld(nx, ny, dx, 8, 0.07, 0.6, 32);
// Grund, Spiegel, Strömung, Wind (und Pflanzen, Wände) setzen, dann
n.vorbereiten();
Nahfeld.Ziel ziel = (x, y, e) -> System.arraycopy(n.jonswap(hs, tp, wohin, 10), 0, e, 0, e.length);
n.setze(ziel);                  // Anfang
n.rand = n.randAus(ziel);       // oder Nahfeld.schachtel(grob, n)
n.schritt(1.0);                 // Beugung, Ausbreitung in Teilschritten nach Courant, Quellen
double hs = n.hs(c);            // dazu richtung, energie(c, b, q), wellenzahl(c, b, q), dwc, dbr, dbf, dveg, bilanz
```

| Datei | Inhalt |
|---|---|
| `nah/Nahfeld.java` | die Referenz: Dispersion mit Strömung, Ausbreitung, Beugung, Kanten, Quellen, Bilanz (10 Posten), Ziel, Rand, Schachtel, Verschieben |
| `nah/demo/NahfeldCheck.java` | 18 Teile, 36 Prüfsteine gegen Lehrbuch und `Waves`; Attest `docs/status/S2_Nahfeld.md`. Einzelne Teile: Nummern als Argumente |

**Karte (S2b):** `gpu/NahfeldKarte` rechnet dasselbe in float auf der Karte, mit den Shadern `gpu/nah_*.comp`
(gemeinsam `nah_gemein.glsl`): Stützstellen (`nah_vorbereiten`), Beugung (`nah_beugung_a`, `nah_glaetten`,
`nah_beugung_s`), Geschwindigkeiten und Rate (`nah_tempo`), Ausbreitung (`nah_ausbreitung`), Quellen (`nah_quellen`, eine
Gruppe von 256 Fäden je Zelle), Bindung (`nah_binden`, eine Gruppe je Block; an m₀ oder an das Spektrum, dann mit dem
Puffer `Ziel` je Stützstelle), Verschieben und Einsetzen. Die Schalter
und Größen liest jeder Schritt aus der Referenz; nach einer Änderung der Eingaben `Nahfeld.zellen()`, dann
`NahfeldKarte.felder()`. Der Rand ist Varianz je Geisterzelle (`Nahfeld.randVarianz(ziel)`). Die Bilanz bucht nur die
Referenz.

```java
Nahfeld n = …;                          // Eingaben setzen
n.zellen();                              // nass, Gefälle (die Stützstellen rechnet die Karte)
try (NahfeldKarte k = new NahfeldKarte(geraet, konto, n, false)) {
    k.rand(n.randVarianz(ziel));
    k.anlauf(2000, 1e-5, 600);           // eingeschwungen
    k.schritt(0.5);                      // dann mit der Zeit
    k.verschieben(di, dj); /* Eingaben neu */ n.zellen(); k.felder(); k.einsetzen(ziel);
}
```

| Datei | Inhalt |
|---|---|
| `gpu/NahfeldKarte.java` | Puffer, Kernel, Schritt, Anlauf, Verschieben auf der Karte |
| `nah/demo/NahfeldKarteCheck.java` | Karte gegen Referenz in 10 Teilen (Grenze 10⁻³ relativ); Attest `docs/status/S2_NahfeldKarte.md` |

```
java -cp dist\Renderer.jar com.dan.physics.waves.nah.demo.NahfeldCheck            alle Teile (rund 1–2 min auf 16 Fäden), mit Attest
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.physics.waves.nah.demo.NahfeldKarteCheck   Karte gegen Referenz, mit Attest
java -cp dist\Renderer.jar com.dan.physics.waves.nah.demo.NahfeldCheck 11         nur die Beugung
```

**Quellen (0.2):** Nach jedem Teilschritt kommen die Raten γ (und der exponentielle Teil des Winds) aus dem Zustand
vor der Ausbreitung: e₁ = (e* + Δt q) / (1 + Δt γ(e)). Dann ist das Gleichgewicht γ(e) e = T + q vom Schritt
unabhängig; mit e* (nach der Ausbreitung) stiege die Senke mit Δt. Einmal je Schritt (`quellenJeTeilschritt = false`)
rechnet es wie `Waves` mit dem Zustand danach.

Grenzen der Referenz: phasengemittelt; eine einzelne, unaufgelöste Richtung dreht um rund 0,2 Δθ zu langsam
(Aufwind in θ an der Spitze), bei 32 Richtungen und einem schmalen Spektrum (σ_θ 8°) rund 1,5°; bei 72 Richtungen
unter 0,2°. Der Wind kennt keinen Abzug der Strömung. `Nahfeld` kennt nur `java.*` und `Waves`; `gpu/NahfeldKarte` dazu den Kern und `com.dan.core.gpu`.

**Karte und Treiber (L2, Befund B37):** Der Treiber der 680M rechnet `tanh` über exp(2x) und gibt ab x ≈ 44 NaN;
lavapipe klemmt selbst. Darum nehmen die Kernel des Nahfelds `th(x)` = tanh(clamp(x, −15, 15)) (in float ab x ≈ 9
ohnehin 1). Die Dämpfung durch Pflanzen rechnet sinh³/cosh³ ab kh = 20 als e^{3(k h_v − kh)}, ohne Überlauf.

**Zeit je Kernel (B39):** Mit `NahfeldKarte.uhr` (eine `Stoppuhr`) misst `schritt` jeden Kernel auf der Karte und
wartet nach jedem Abschicken auf die Zeiten; NahfeldKarteCheck Teil 10 gibt sie je Schritt aus (128² zu 2 m und 100²
zu 10 m wie das feine Nahfeld).

**Ein Faden je Stützstelle (B39, nach L2d):** `nah_ausbreitung`, `nah_tempo` und `nah_beugung_a` rechneten je Zelle
und Band mit einer Schleife über die Richtungen. Auf der 680M lasen benachbarte Fäden so weit auseinander liegende
Werte, und `nah_ausbreitung` hielt dazu eine Reihe von 72 Werten je Faden im langsamen Speicher: 44 ms je Schritt im
feinen Gitter, die Quellen (schon je Stützstelle) 1,4 ms. Jetzt rechnet jeder Faden eine Stützstelle; die Reflexion holt
jede Richtung aus ihrer gespiegelten, `nah_tempo` sucht das Größte erst in der Gruppe, `nah_beugung_a` summiert in der
Gruppe in derselben Reihenfolge wie vorher. Abgabe zweidimensional (`fadenNummer`). Auf lavapipe ist es langsamer
(Barrieren auf dem Prozessor); das zählt nicht.

**Seit S3d (Seegang Werkbuch 28):** `NahfeldKarte.anlaufInStuecken` gibt einen `Anlauf`, der mit `weiter(z)` je
Aufruf höchstens z Runden rechnet (ein Stück endet spätestens an der nächsten Prüfung, alle 16 Runden). Dieselben Runden
wie `anlauf`, bitgleich (NahfeldKarteCheck, Teil 8); die Anwendung rechnet so den Anlauf zwischen den Bildern.
