# com.dan.physics.hydro – Flachwasser mit Subgrid

**Stand: P4d (12.11.2026).** Grundlage ist das Werkbuch HalligHooge, Teil A, Abschnitte 7.1 und 12.5; Verfahren nach F12 A (versetzt, explizit, Subgrid), Querschnitt je Kante nach F25 A. Die Referenz in double auf dem Prozessor steht seit P4a, Bauwerke (Deich, Siel) und Schachtelung seit P4b, der Betrieb auf der Karte (`gpu`, eigenes README) seit P4c; dabei ist die Schachtelung neu gefasst (Werkbuch 12.5, Stand P4c). Seit P4d läuft die ganze Kette G2 → G3 → G4 auf der Karte über eine Tide (Fall 9), mit dem Rand aus der Vorgabe (`Vorgaberand`) und G4 aus dem Stapel (`core.gitter.Gebiet.auge`). Seit P5a rechnet `Hydro` auch geographische Gitter (G0) und koppelt einseitig über zwei Lagen (`Einseitig`). **Seit P5c (13.11.2026) ist hydro ein Modul am Bus** (`HydroModul`): Es rechnet alle Gitter G0 bis G4 (F27 A), liest `tide.bc`, `tide.eq` und `air.*` und schreibt `water.*` auf jedem Gitter; gerechnet wird auf dem Prozessor (`Rechnung.Prozessor`) oder auf der Karte (`gpu.KarteRechnung`). Verdrahtet wird es von der Schicht `com.dan.lauf`. **Seit P8c (`HydroModul` 0.4)** liest hydro auf G2 und G3 `waves.force` im Intervall (zur Mitte, wie den Wind) und rechnet sie zur Kraft an der Oberfläche dazu (`kraftX`, `kraftY`; mit dem Schub von ERA5 zusammen, sonst allein); die Kraft wächst ab dem Beginn des Seegangs über 1 h [G] auf 1. Am Rechenkern ist nichts geändert.

## Schicht

```
com.dan.physics.hydro       core (ohne gpu) + java.*    Referenz, Prüfung
com.dan.physics.hydro.gpu   core + core.gpu + LWJGL     Betrieb auf der Karte (seit P4c)
```

hydro kennt kein anderes Physikmodul und keine Aufbereitung; das Gebiet kommt über `core.gitter.Gebiet` (Gitter und Packung).

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Hydro` | ein Gitter: Masse, Impuls, Kräfte, Ränder, Bauwerke |
| `Ringe` | Flächen über alle Kanten außer Bauwerken, ihre Tiefe vom Rand aus; binnen eines Siels |
| `Kette` | geschachtelte Gitter (G2 → G3 → G4) in beide Richtungen, Volumen über alle genau |
| `Einseitig` | einseitige Kopplung (G0 → G1 → G2, seit P5): das feine Gitter bekommt am Rand den Wasserstand des groben, auch über zwei Lagen (geographisch → UTM) |
| `Vorgaberand` | Rand eines Gitters aus der Vorgabe der Tide (`core.gitter.Gebiet.Vorgabe`), mit Anlauf; bis P5, danach zum Vergleich |
| `HydroModul` | hydro als Modul am Bus (seit P5c): Stufen G0 \| G1 \| G2 ↔ G3 ↔ G4, Antrieb und Rand vom Bus, `water.*` auf jedem Gitter |
| `Rechnung` | wo das Modul rechnet: `Rechnung.Prozessor` (Referenz, double) oder `gpu.KarteRechnung` (Betrieb, float); gleiche Schritte je Kopplungsintervall |
| `demo.HydroCheck` | Prüfung A ohne Fenster, mit `--karte` auf der Karte |
| `demo.HydroLauf` | Fall 9 (Lauf über die Kette), Fall 10 über die Kette, Fall 14 (Zeiten) |
| `demo.StatusBild` | `docs/status/P4_Hydro.png` |
| `demo.HydroP5`, `demo.HydroP5Karte` | Fälle 15–18 (Kugel, Gleichgewichtstide, einseitige Schachtel, Speicher) und Fall 10 über G0 (seit P5a) |

## Verfahren (`Hydro`)

- **Unbekannte:** Wasserstand η und Volumen V je Zelle, Geschwindigkeit u normal je Kante (erst die Kanten quer zu x, dann quer zu y).
- **Tabellen** aus der Packung (float16, wie später auf der Karte): je Zelle V(η) und benetzte Fläche; je Kante die mittlere benetzte Tiefe h_A (Querschnitt A = Kantenlänge · h_A) und die wirksame Tiefe h_K (Leitwert K = Σ Δs/n · h_K^(5/3)). Die Tiefe kommt immer aus der Tabelle, nie aus η − b. Verlangt wird die Packung Version 2 (`HHSUBGR2`).
- **Schritt** (Stelling und Duinmeijer 2003):
  1. **Masse** mit dem alten u: Q_e = u_e · A_e(η_oben), V ← V − Δt Σ Q. Abfließendes Wasser ist auf das Volumen der Zelle begrenzt. Genau konservativ.
  2. **Impuls** aus dem neuen Stand mit denselben Q: Advektion impulserhaltend (q̄_R u*_R − q̄_L u*_L − u (q̄_R − q̄_L)) / (h̄ Δx) mit h̄ Δx L = V̄ (Mittel der neuen Volumen beider Zellen), wo die Strömung langsamer wird oder zusammenläuft und an der Front über trockenem Land; sonst energieerhaltend stromauf; quer dazu stromauf. Gefälle −g ∂η/∂x, Reibung halbimplizit: u ← (u* − Δt g ∂η/∂x) / (1 + Δt g |u| A²/K²).

  In dieser Reihenfolge bleibt der Impuls auch über die Zeit erhalten; die Lage eines Sprungs hängt nicht von der Courant-Zahl ab (Werkbuch 12.5, Stand P4a).
- **Gitter:** UTM oder geographisch (seit P5, G0). Auf der Kugel wechseln Zellfläche, Breite der Zellen und Länge der Kanten quer zu y mit der Zeile (`zeilenFlaeche`, `zeilenBreite`, `kantenLaengeY` usw.); dazu das metrische Glied u v tan φ / R mit Coriolis.
- **Kräfte:** Coriolis (f je Zeile), Gleichgewichtstide (`gleich`, m je Zelle, schon mit 1 + k₂ − h₂; das Gefälle wirkt auf η − η_eq, seit P5), Wind τ = ρ_L C_d |U| U mit C_d = (0,8 + 0,065 U) · 10⁻³ (Wu 1982), Luftdruckgefälle, Kraft an der Oberfläche (`kraftX`, `kraftY`, N/m²: seit P5e der Windschub vom Bus, wenn hydro ihn liest – dann ist der Wind null und die Formel nach Wu ruht; ab P8 auch die Kraft aus dem Seegang), Wirbelviskosität nach Smagorinsky.
- **Nass und trocken:** Eine Kante führt Wasser, wenn h_A beim Wasserstand stromauf mindestens `hTrocken` ist und die Zelle stromauf Wasser hat. Eine trockene Zelle hat η = tiefste Pixelhöhe. Trockene Kanten sind für Viskosität und Advektion quer Ufer ohne Haftung, wie der Rand.
- **Ränder** je Seite: `WAND`, `WASSERSTAND` (Geisterzelle mit η aus `randwert` im Abstand `randAbstand` Zellweiten, sonst 1; mit `randBoden` kann sie trocken sein), `DURCHFLUSS` (m²/s ins Gebiet).
- **Zeitschritt** (`dtCfl`, seit P5b nach F32 A für Wellen in zwei Richtungen): das Kleinere aus der Welle je Zelle, 1 / √(r_x² + r_y²) mit r das Größte von (|u| + √(g h)) / Δ über ihre Kanten in x und in y, und dem Speicher je Zelle, 2 / √λ mit λ = g Σ (A_e/Δ_e) (1/S_Zelle + 1/S_Nachbar), S = dV/dη (`speicher`, die Sehne der Volumentabelle; bis P4 die benetzte Fläche, die zwischen zwei Stufen linear gelegt wird und in einer Rinne zwischen geschlossenem Land bis zu 37-mal zu groß war: das Überschwingen an der Eider, Werkbuch 12.6). λ ist die Grenze nach Gerschgorin für die Schwingung von Masse und Impuls (gewichtet mit 1/S): Kein Eigenwert liegt darüber, auf ebenem Grund ist sie genau Δ / (√2 c). Beides mal `cfl` = 0,9. Gezählt werden nasse Kanten ohne Bauwerk, am Rand nur an Seiten mit Wasserstand. In einer Rinne, deren Querkanten trocken sind, gilt die Grenze in einer Richtung.
- **Kette abgeglichen** (`Kette.abgleich`, seit P5b): Jede feinere Ebene rechnet ⌈τ / Δt_k⌉ gleiche Unterschritte. Der Schritt der gröbsten Ebene ist darum Δt₀ oder ein Vielfaches m · Δt_k darunter, je nachdem, was am wenigsten Zellschritte je Sekunde kostet; jede Ebene bleibt in ihrer Grenze. Im Lauf aus Fall 9 rechnen G3 und G4 so rund 13 % weniger Schritte als mit Δt₀ allein.
- **Schritte ohne Sprung:** `lauf` verteilt den Rest bis zum Ziel auf gleiche Schritte. Springt der Schritt von voll auf kurz und zurück, wird das Verfahren mit Coriolis instabil (Werkbuch 12.6, Stand P5a); `Einseitig` und die Läufe rechnen darum je Kopplungsintervall in gleichen Schritten.
- **Bauwerke** (`deich`, `siel`, `bauwerke(Gebiet.Bauten)`): Deich- und Sielkanten ersetzen den Impuls. Q aus η beider Zellen am Anfang des Schritts:
  - Überfall je Stelle der Krone: (2/3) μ_P √(2g) Δs h_o^(3/2), unter Rückstau mal (1 − (h_u/h_o)^(3/2))^0,385 (Poleni, Villemonte), μ_P = `muPoleni`;
  - Siel dazu μ A √(2 g Δh), Δh = η_binnen − max(η_außen, Sohle), nur wenn binnen höher steht; binnen ist die tiefere Seite nach `Ringe`;
  - höchstens so viel, dass sich beide Seiten angleichen; u = 0 auf der Kante (der Strahl gibt keinen Impuls weiter, die Verluste stecken in Poleni und Villemonte).
- **Bilanz:** `zufluss`, `abfluss` über die Ränder und `abgeschnitten` (negatives Volumen, auf null gesetzt: Wasser, das dabei entsteht); `volumen()` ist der Bestand. Es gilt V = V₀ + Zufluss − Abfluss + abgeschnitten; Fall 9 und 13 zählen das Abgeschnittene als Fehler.
- **Schachtel, Zellen außen** (`Kette`, seit P4d): Flüsse über den Rand eines feinen Gitters hinein ziehen aus den groben Zellen außen höchstens deren Volumen (`aussenBegrenzen`, wie die Begrenzung je Zelle). Ohne das lief beim Trockenfallen eine Zelle außen leer, und das Abschneiden schuf Wasser (Fall 9 auf der 680M: 1,6 · 10⁶ m³ über eine Tide).

| Wert | | |
|---|---|---|
| `cfl` | 0,9 [G] | Faktor auf die Grenzen in zwei Richtungen (bis P5a 0,7 je Kante; F32 A) |
| `smagorinsky` | 0,1 [G] | C_s |
| `hWind` | 0,01 m [G] | darunter greifen Wind und Seegang nicht an |
| `hTrocken` | 1 · 10⁻⁴ m [G] | darunter ist eine Kante trocken |
| `muPoleni` | 0,6 [G] | Überfallbeiwert |
| `g`, `rhoW`, `rhoL` | 9,81 m/s², 1025 und 1,25 kg/m³ | |

## Modul am Bus (`HydroModul`, seit P5c)

- **Stufen.** Stufe 0 ist ein Gitter mit offenem Rand (G0); jede weitere bekommt ihren Rand einseitig aus der gröbsten Ebene der vorigen (`Einseitig`) und ist ein Gitter (G1) oder eine Kette (G2 ↔ G3 ↔ G4). Je Kopplungsintervall rechnet Stufe für Stufe bis zu dessen Ende, in gleichen Schritten; dazwischen der Stand der vorigen Stufe für die einseitige Kopplung.
- **Liest im Intervall** (`Anmeldung.liestImIntervall`; der Takt lässt tide und antrieb vorher bis zum Ende des Intervalls rechnen): `tide.bc` auf G0, in jedem Schritt linear zwischen den Ständen am Anfang und Ende des Intervalls (NaN: Randzelle ohne Wasser, die Geisterzelle ist trocken auf +10 m); `tide.eq` auf den Gittern mit Gleichgewichtstide; mit Antrieb `air.u10`, `air.v10`, `air.p` auf jedem Gitter (seit P5e, `HydroModul` 0.3, mit `schub` statt des Windes `air.taux`, `air.tauy`) (der Bus rechnet bilinear vom Raster von ERA5 um), zur Mitte des Intervalls und einmal je Intervall auf die Karte. Wind auf Gittern in UTM wird um die Meridiankonvergenz in Gitterrichtung gedreht.
- **Schreibt** am Ende jedes Intervalls auf jedem Gitter (float): `water.eta`, `water.h` (V / benetzte Fläche), `water.u`, `water.v` (in der Zelle, Gitterachsen), `water.q` (Fluss je Kante als Zeitmittel über das Intervall aus `qSumme`, Σ Fluss · Δt über alle Massen; gesperrte Kanten unter einem feineren Gitter 0) und `water.wetAge` (s; nass: wie lange schon, trocken: negativ, wie lange schon; nass ab 0,1 mm mittlerer Tiefe über die Zelle [G]).
- **Bilanz** je Stufe (`bilanz(s)`): Volumen am Anfang und jetzt, Zufluss und Abfluss am Rand der gröbsten Ebene, Abgeschnittenes.

```java
List<Hydro[]> stufen = List.of(new Hydro[] {h0}, new Hydro[] {h1}, new Hydro[] {h2, h3, h4});
HydroModul m = new HydroModul(stufen, new KarteRechnung(), 300, true, Set.of("G0", "G1"));
bus.anmelden(m);                                         // tide und antrieb vorher oder nachher: der Takt ordnet
```

## Benutzung

```java
Gebiet ge = Gebiet.lies(new File("D:\\Daten\\HalligHooge\\gebiet"));
Gitter g3 = ge.gitter("G3");
Hydro h = new Hydro(g3, ge.packung("G3"));               // in double rund 1 KB je Zelle: ganz G3 knapp 2 GB
h.fuellen(0.5);                                         // ruhender See bei η = 0,5 m NHN
h.rand[Hydro.WEST] = Hydro.Rand.WASSERSTAND;
h.randwert[Hydro.WEST] = (t, x, y) -> 1.2 * Math.sin(2 * Math.PI * t / 44714);
h.lauf(3600, 5);                                        // eine Stunde, Schritte nach CFL, höchstens 5 s
double eta = h.eta[g3.index(i, j)], v = h.volumen();
h.bauwerke(ge.bauwerke("G3"));                          // Deiche und Siele; gibt die Siele ohne Binnenseite zurück
```

Geschachtelt (das feine Gitter liegt auf ganzen Zellen des groben; seine Ränder setzt die Kette):

```java
Kette k = new Kette(g2, g3, g4);                        // Hydro je Ebene
k.lauf(44714, 30);                                      // eine Tide, Schritte der gröbsten Ebene nach CFL
double v = k.volumen();                                 // grobe Zellen außerhalb der feinen und alle feinen
```

- **Kette** (neu gefasst in P4c): Die feinen Zellen ersetzen die groben darunter; die groben Kanten unter und an dem feinen Gitter sind gesperrt (kein Fluss, kein Impuls, ihr u folgt am Ende den feinen Durchflüssen), grob unter fein ist η das Mittel der feinen (gewichtet mit der benetzten Fläche). Je feiner Randzelle führt eine Kante zu den groben Zellen außen: Wasserstand linear längs ihrer Reihe (nur nasse), Gefälle über (r + 1) / 2 feine Zellweiten bis zur groben Mitte, ihr Fluss geht im selben Verhältnis in diese Zellen. Ein Schritt ist symmetrisch geteilt: grobe Masse über Δt / 2, das feine Gitter über Δt in m = ⌈Δt / Δt_CFL⌉ Unterschritten nach Verlet (Impuls h / 2, Masse und Impuls h, zuletzt Impuls h / 2), grobe Masse über Δt / 2, grober Impuls über Δt; rekursiv für weitere Ebenen. So bleibt die Kette auch bei m = 10 stabil (die Fassung aus P4b ließ Rundungsrauschen wachsen). Eine Seite des feinen Gitters auf dem Rand des groben muss Wand sein.

Mit der Vorgabe der Tide am Rand (Aufbereitung, Schritt `vorgabe`) und G4 aus dem Stapel:

```java
Gebiet.Vorgabe v = ge.vorgabe();                         // M2 je Randkante von G2 und an den Pegeln
Hydro h2 = new Hydro(ge.gitter("G2"), ge.packung("G2"));
double eta0 = Vorgaberand.setze(h2, v, 3 * 3600, 0);     // Anlauf 3 h ohne Knick; gibt η₀
Gebiet.Auge au = ge.auge();                              // G4 an der Stelle aus dem Rezept, gebacken aus dem Stapel
Hydro h4 = new Hydro(au.gitter(), au.packung());
for (Hydro h : new Hydro[] {h2, h3, h4}) h.fuellen(eta0);
try (KetteKarte k = new KetteKarte(geraet, konto, h2, h3, h4)) { k.lauf(44714, 1e9); }
```

## Prüfung

```bash
java -cp dist\Renderer.jar com.dan.physics.hydro.demo.HydroCheck [--gebiet D:\Daten\HalligHooge\gebiet] [--karte] [--fall n] [--ohne-attest]
```

`HydroCheck` prüft die Fälle 1–8 und 11–13 der Prüfung A (Werkbuch 12.5) ohne Fenster, endet mit „Alles in Ordnung.“ und schreibt das Attest nach `docs/status/P4_Hydro.md`; mit `--karte` dieselben Fälle auf der Karte, dazu Fall 10 und die Zeiten (Attest `P4_HydroKarte.md`, siehe `gpu/README.md`), mit `--fall n` nur Fall n (ohne Attest). Mit `--karte --gebiet` kommen Fall 10 über die Kette (überflutet geprüft; beim Trockenfallen zur Auskunft), Fall 9 (eine Tide über G2 → G3 → G4, auf der 680M gut eine Stunde), Fall 14 mit den Zeiten und das Status-Bild `docs/status/P4_Hydro.png` dazu; `--ohne-bild` lässt das Bild aus. Mit `--gebiet` kommen dazu: binnen der Siele am Ring Hooge über ganz G3 und der ruhende See über einem Ausschnitt von G3 um den Ring Hooge mit seinen Deich- und Sielkanten. Stand in der Arbeitsumgebung:

| Fall | Ergebnis | Grenze [G] |
|---|---|---|
| 1 Ruhender See über erzeugtem Gelände und G3 (mit Bauwerken) | \|u\| = 0, Volumen unverändert | < 10⁻¹⁰ m/s |
| 1 Siele am Ring Hooge | binnen der Ring | alle |
| 2 Thacker-Schüssel, 240² Zellen zu 100 m, nach 3 Perioden | 98,27 % (200 m: 96,95 %) | ±2 % |
| 3 Dammbruch nach Ritter, 0,5 m | +0,59 % (2 m −5,75 %, 1 m −2,20 %) | ±2 % |
| 3 Dammbruch nach Stoker, 0,5 m | −0,64 % (2 m −1,75 %, 1 m −1,04 %) | ±2 % |
| 4 Kelvin-Welle | +2,66 % | ±3 % |
| 5 Gezeitenwelle mit Reibung | +0,60 %, +0,9° | ±3 %, ±5° |
| 6 Subgrid 20 m gegen durchgehend 1 m | −2,37 %, Bilanz 4 · 10⁻¹⁶ | ±5 %, 10⁻¹² |
| 7 Wehr frei / gestaut | −0,16 % / −0,68 % | ±2 % |
| 8 Siel: Zustrom; Abfluss gegen μ A √(2 g Δh) | 0; 6 · 10⁻¹³ | 0 (genau); ±1 % |
| 11 Windstau | +0,25 % | ±2 % |
| 12 Luftdruck | +0,04 % | ±1 % |
| 13 Schachtel 200 m / 20 m: Volumen, Rückwurf; ruhender See durch die Schachtel | 2,4 · 10⁻¹⁴; 0,50 %; \|u\| = 0 | 10⁻¹²; 5 %; 10⁻¹⁰ m/s |
| 13 Rauschen ±0,5 mm in der Schachtel, 4 h ohne Reibung: größtes √(η²) gegen den Anfang | 1,00 | ≤ 2 |
| 10 über die Kette (nur Karte): ein Schritt von G2 mit G3 und G4, Karte gegen Referenz, überflutet (+3 m, 1 h Tide) | G2 \|Δη\| 5,0 · 10⁻⁶ m, \|Δu\| 3,1 · 10⁻⁶ m/s; G3 5,1 · 10⁻⁶ / 2,9 · 10⁻⁶; G4 2,1 · 10⁻⁶ / 3,1 · 10⁻⁶ | 10⁻⁵ (Grenze A) |
| 9 Lauf über G2 → G3 → G4 (nur Karte; in der Arbeitsumgebung verkleinert, 3 h): G4 gegen G3; Bilanz (Abgeschnittenes als Fehler); NaN | ≤ 0,29 %; 3,6 · 10⁻⁹, nichts abgeschnitten; keins | 0,5 %; 10⁻⁵; keins |

## Grenzen

- **Erste Ordnung im Raum.** Fronten über trockenem Land laufen um O(Δx) zu langsam, ein wanderndes Ufer dämpft (Thacker: 200 m −3,1 %, 100 m −1,7 %, 50 m −1,0 % nach drei Perioden). Mit Reibung über Land zählt die Auflösung an der Front wenig.
- **Explizit:** Δt nach CFL über das ganze Gitter; die tiefste Rinne bestimmt den Schritt (G3 rund 1 s).
- **Bauwerke** nur auf inneren Kanten; `bauwerke(Bauten, i0, j0)` setzt die Bauwerke in einem Fenster ab Zelle (i0, j0) (seit P5d, vorher in `HydroCheck`); `bauwerke(Bauten)` lässt Kanten auf dem Rand aus und zählt sie (`bauwerkeAmRand`; in G3 von Hooge eine am Ostrand bei O 478 km, N 6 056,7 km, P4d). Eine Deichkante, die genau auf der Grenze zweier Ebenen liegt, sieht das feine Gitter nicht als Bauwerk (dort zählt sein Gelände). Ein Siel, dessen Seiten in derselben Fläche liegen, bleibt zu. Das Siel ist ein Querschnitt A ohne Höhe der Öffnung.
- **Geschwindigkeit in der Zelle** (`geschwindigkeit`) an Bauwerken ohne den Strahl (u = 0 auf der Bauwerkskante).
- **Schachtel:** Das grobe Gitter rechnet je Schritt die Masse in zwei Hälften. Auf der 680M rechnet die ganze Kette G2 → G3 → G4 eine Periode in 64 min (Zeitraffer 11,6, 13,7 ns je Zellschritt), ohne G4 mit Zeitraffer 25 (P4d).
- **Stabilität:** Bis P5a lag der Schritt mit `cfl` = 0,7 auf der Welle je Kante bei rund 99 % der Grenze des Verfahrens in zwei Richtungen; Sprünge zwischen langen und kurzen Schritten wurden mit Coriolis instabil (Werkbuch 12.6, Stand P5a). Seit P5b (F32 A) rechnen beide Grenzen in zwei Richtungen, mit 10 % Abstand; die Läufe rechnen weiter in gleichen Schritten.
- **Rand aus der Vorgabe** (Fall 9): Der Wasserstand am Rand von G2 ist vorgegeben (eine Partialtide aus EasyGSH); was aus dem Gebiet hinausläuft, wird dort reflektiert. Im Lauf über den Bus (P5c) bekommt G2 seinen Rand einseitig aus G1, G1 aus G0, G0 aus `tide.bc`.
- **Einseitig:** Was aus einem feinen Gitter hinausläuft, sieht das grobe nicht; am Rand des feinen gilt der Wasserstand des groben.
- **Siele unter G4** sind zu: G4 sieht Deiche nur im Subgrid (1-m-Pixel mit Mindesthöhe), das Tor eines Siels hat dort die Krone der Umgebung; das Wasser im Ring läuft über die Siele außerhalb von G4 ab.
- **Noch nicht:** Spiralströmung (P9, F26), Archiv der Läufe (Teil B).

## Quellen

- Stelling, G. S., Duinmeijer, S. P. A. (2003): A staggered conservative scheme for every Froude number in rapidly varied shallow water flows. Int. J. Numer. Meth. Fluids 43.
- Casulli, V. (2009): A high-resolution wetting and drying algorithm for free-surface hydrodynamics. Int. J. Numer. Meth. Fluids 60.
- Poleni, G. (1717): De motu aquae mixto. · Villemonte, J. R. (1947): Submerged weir discharge studies. Engineering News-Record 139.
- Wu, J. (1982): Wind-stress coefficients over sea surface from breeze to hurricane. J. Geophys. Res. 87.
- Smagorinsky, J. (1963): General circulation experiments with the primitive equations. Monthly Weather Review 91.
- Thacker, W. C. (1981): Some exact solutions to the nonlinear shallow-water wave equations. J. Fluid Mech. 107. · Stoker, J. J. (1957): Water Waves. · Ritter, A. (1892): Die Fortpflanzung der Wasserwellen. Z. VDI 36.

## Seit P9c (Hydro 0.2, HydroModul 0.5): Boden, Rauheit und Spirale vom Bus

- `Hydro.boden(dz)`: verschiebt die Tabelle jeder Zelle um die Bodenänderung gegen die Packung, die der Kante um die größere Änderung ihrer beiden Zellen (geplant war das Mittel, F81 A; so bleibt die Schwelle jeder Kante mindestens so hoch wie der Grund beider Zellen). Das Volumen des Wassers bleibt, η geht mit dem Boden. `Hydro.basis()` gibt die Basis dazu für die Karte, `HydroKarte.bodenHoch()` bringt Basis, η und Bezugsvolumen hinauf, `Rechnung.boden(s)` sagt es der Rechnung.
- `Hydro.rauheit(n)`: Σ Δs/n der Kante aus Manning-n je Zelle (Länge / n, gemittelt über beide Zellen; ohne Wert die Packung).
- `HydroModul`: liest auf den Gittern aus `boden` zu Beginn des Intervalls `bed.dz` und, mit `rauheit`, `surf.z0` (Strickler: n = k_s^(1/6) / 26 mit k_s = 30 z0, wenigstens `nMin`); schreibt auf den Gittern aus `spirale` `water.spiral` = h · |u| · κ (Krümmung der Stromlinien, Gleichgewicht).
- Bauwerke, gesperrte Kanten und die anderen Gitter der Kette behalten ihren Boden. Geprüft in `lauf.demo.MorphoCheck` (Prüfsteine 1, 2, 6, 7 von P9c).

## Seit 0.6 (P11c1): Stufe 0 und Boden von außen

`HydroModul.vorgabe0` (vor dem Anmelden): Stufe 0 wird nicht gerechnet, ihr Stand kommt je Intervall von außen
(`Vorgabe.stand(t)` als `Einseitig.Stand`) und geht einseitig an Stufe 1; Stufe 0 liest dann weder `tide.bc` noch den
Antrieb und schreibt nichts. `HydroModul.bodenquelle`: `bed.dz` für die Gitter aus `boden` von außen statt vom Bus.
Ohne beide rechnet das Modul wie 0.5. Gebraucht von `com.dan.lauf.Stroemung` (G3 allein mit dem Rand aus einem
abgelegten G2).
`KarteRechnung` bricht seit 0.6 auch für eine Stufe aus einem Gitter ab, wenn der Schritt unter `DT_MIN` (0,02 s) fällt
(bisher nur in der Kette).

## Seit 0.7 (Steuerhaus Y2, Werkbuch HalligHooge 153): Aufschlag am Rand der innersten Stufe

`HydroModul.aufschlagZiel` (m, aus jedem Faden setzbar) hebt den Wasserstand am Rand der letzten Stufe; hydro fährt es in der Modellzeit mit `aufschlagRampe` (1 m je Stunde [G]) an und setzt ihn je Intervall an Anfang und Ende in `Einseitig.zusatz(t, z)`; dazwischen linear. An einer trockenen Geisterzelle geht der Boden mit, sie bleibt trocken; am nassen Rand bleibt der Boden −10³⁰. `aufschlag()` gibt den wirksamen Wert. Ohne Ziel (nie ≠ 0 gesetzt) bleibt alles bitgleich. Prozessor und Karte gleich: Die Randwerte der feinen Stufe kommen aus `Einseitig` über `Hydro.randwert`.
