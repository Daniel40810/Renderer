# com.dan.physics.sediment

Sand, Schlick, Rippel und Boden, tiefengemittelt (Werkbuch HalligHooge Teil A, 7.4 und 12.10). Stand P9b (Sediment
0.2): die Referenz in doppelter Genauigkeit, dasselbe Sediment auf der Grafikkarte in float und das Modul am Bus
(`sediment`: liest `water.*` und `waves.*`, schreibt `sed.c`, `bed.*`, `surf.z0.sediment`). Der Boden von hydro bleibt
fest; die Rückwirkung kommt mit P9c.

| Datei | Inhalt |
|---|---|
| `Sediment.java` | Referenz: Schubspannung aus Strom und Welle (Soulsby 1997), Sand (Absinken und Schwelle nach Soulsby, Geschiebe und Bezugskonzentration nach van Rijn 2007, Formfaktor nach van Rijn 1984, Anpassungszeit nach Galappatti 1983), Schlick (Partheniades, Krone, Konsolidierung [G]), Exner mit Porosität, Böschungsrutsch, Rippel (Soulsby und Whitehouse 2005), Bestand und Fluss über den Rand |
| `gpu/sediment.glsl` | Karte: Werte des Schritts und die Formeln in float; Summe mit Ausgleich (Kahan) für die Bodenänderung |
| `gpu/zelle.comp` | Karte: je Zelle Schubspannung, Gleichgewicht, Raten, Geschiebe, Rippel |
| `gpu/transport.comp` | Karte: Schwebstoff (Advektion, Diffusion), Austausch mit dem Boden, Exner; ein Faden je Zelle rechnet seine vier Kanten |
| `gpu/rutsch.comp` | Karte: ein Durchgang des Böschungsrutsches |
| `gpu/SedimentKarte.java` | Puffer, Kernel und Schritt auf der Karte; `eingang()`, `hoch()`, `runter()` |
| `SedimentModul.java` | Das Modul am Bus auf dem Gitter von hydro (G3): Volumen der Zelle aus `water.eta` und der Tabelle des Subgrids, Fluss aus `water.q`, Tiefe und Strom für die Schubspannung aus `water.h`, `.u`, `.v`, Seegang aus `waves.ubot`, `.tp`, `.dir`; Boden am Anfang aus `core.gitter.Kornfeld`; `Rechner` (Prozessor oder Karte); Bilanz und Abgleich der Tiefe für die Prüfung |
| `gpu/SedimentRechner.java` | Der `Rechner` auf der Karte: eigenes Gerät, der Zustand bleibt auf der Karte |
| `demo/SedimentCheck.java` | Prüfsteine 1–9 aus 12.10 und die Zeiten der Referenz; Attest `docs/status/P9a_Sediment.md` |
| `demo/SedimentKarteCheck.java` | Prüfsteine 10 (Karte gegen Referenz) und 11 (Zeiten auf der Karte, Vorgabe); Attest `docs/status/P9a_SedimentKarte.md` |

Seit 0.2 trennt `Sediment` das Volumen der Zelle von der Tiefe des Stroms: `h` ist Volumen durch Zellfläche und trägt
die Masse; `tiefe`, `ug`, `vg` (Tiefe über der benetzten Fläche und Geschwindigkeit von hydro) geben die Schubspannung;
der benetzte Anteil `h/tiefe` und der Anteil losen Bodens `frei` mindern Geschiebe, Aufnahme und Erosion der Zelle.
Fester Boden (Salzwiese, Fenne, Deich, Warft, Deckwerk) gibt nichts ab; ablagern kann sich überall etwas. `porosZ` ist
die Porosität je Zelle, `teileMax` und `hRate` begrenzen die Zahl der Teilschritte (am Bus 150 je Intervall, Zellen ab
0,2 m Tiefe zählen; flachere schützt die Begrenzung des Abflusses).

Eingang je Schritt: Tiefe `h` zu Beginn, Fluss je Breite auf den Kanten `qx`, `qy` (m²/s, Zeitmittel wie `water.q`),
Seegang (`ubot` als quadratisches Mittel, `tp`, Richtung), Grund und Korn. Die Tiefe am Ende folgt aus der
Kontinuität. Zustand: `cs`, `cm` (kg/m³; zusammen `sed.c`), `dzS`, `dzM` (m; zusammen `bed.dz.sediment`), Schlick der
Mischschicht und seine Liegezeit, Rippel (Höhe, Länge, Richtung).

```java
Sediment s = new Sediment(nx, ny, dx, dy);
// grund, d50, h, qx, qy (und ubot, tp, wcos, wsin) setzen, dann
s.schritt(300);                       // Teilschritte nach der Courant-Zahl
try (SedimentKarte k = SedimentKarte.mitGeraet(s)) {
    k.eingang();                      // nach jeder Änderung von h, q, Seegang
    k.schritt(300);
    k.runter();                       // Zustand in s
}
```

Am Bus (`lauf.Lauf.Einstellung.sediment`): Kopplung 300 s; zu Beginn jedes Intervalls gilt die Tiefe V(η) / Zellfläche,
im Intervall folgt sie der Kontinuität mit `water.q`; der Unterschied am Ende ist ein Prüfstein, und was er an
Schwebstoff ausmacht, steht als Abgleich in der Bilanz. Am Rand des Gitters kommt Sand mit der
Gleichgewichtskonzentration der Randzelle herein, Schlick mit 20 g/m³ [G]. `bed.ripple` trägt Höhe, Länge und die
Richtung, in die der formende Strom lief (Grad von Gitternord im Uhrzeigersinn). Der Lauf prüft sich mit
`com.dan.lauf.demo.SedimentLaufCheck` (Attest `docs/status/P9b_Sediment.md`).

```
java -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentCheck                 alle Teile, mit Attest
java -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentCheck --teil 6        ein Teil, ohne Attest
java --enable-native-access=ALL-UNNAMED -cp dist\Renderer.jar com.dan.physics.sediment.demo.SedimentKarteCheck
```

Gesetzt [G]: eine Sand- und eine Schlickfraktion; die Welle rührt, der Strom trägt (Geschiebe mit u* aus τ_m und dem
Überschuss aus τ_max); Mischschicht 5 cm; Werte des Schlicks (M 10⁻⁴ kg/(m² s), τ_e 0,15–0,5 N/m² über 7 Tage, w_s
0,5 mm/s, τ_d 0,1 N/m²); Ruhewinkel Sand 30°, Schlick 45°; Anpassungszeit der Rippel aus dem Geschiebe; Diffusion
nach Elder, gedeckelt auf ¼ (1 − Courant) Δ²/Δt.

Grenzen: tiefengemittelt; Sand ohne Grenze des Vorrats; nur Rippel des Stroms (keine Wellenrippel); der Böschungsrutsch
prüft die Kanten des Gitters, nicht die Diagonale; am Bus liest das Sediment den Seegang zu Beginn des Intervalls und
Tiefe und Strom in seiner Mitte (über das Intervall fest); Zellen mit weniger als 5 cm Wasser über die Zellfläche
gelten als trocken, auch wenn ein schmaler Priel darin Wasser führt. Bekannte
Abweichungen stehen im Werkbuch, 12.10.

`Sediment` kennt nur `java.*`, `SedimentModul` den Kern, `gpu/` dazu `com.dan.core.gpu` (Schichtenvertrag, Werkbuch 4).

## Seit P9c (Sediment 0.3)

- **Morphologischer Faktor** (`faktor`; Lesser u. a. 2004, Roelvink 2006) auf die Bodenänderung aus Austausch und Geschiebe; Wasser, Transport und Schubspannung rechnen unverändert. `sandWirklich`, `schlickWirklich` halten, was der Boden wirklich aufgenommen hat; die Bilanz des Moduls rechnet damit.
- **Deckel** (`deckel`, `deckelMin`): Der Sandboden einer Zelle ändert sich je Schritt höchstens um einen Anteil der Tiefe; darüber läuft ihre Bodenzeit langsamer, gezählt in `gedeckelt`.
- **Lockere Auflage** (`mitAuflage`, `mAuflage`, Katalogfeld `bed.fluff`; van Kessel u. a. 2011): Ablagerung geht bis auf den Anteil `anteilTief` in die Auflage; Wiederaufnahme über τ_a mit min(m M₁, M₀) (τ/τ_a − 1). Beiwerte gesetzt [G].
- **Spirale** (`spiral` aus `water.spiral`): Das Geschiebe dreht um tan δ = α_I I / |u| zur Innenkurve, α_I = 2/κ² E_s (1 − ½ √c_D / κ); die Form wie im Handbuch von Delft3D, aus dem Gedächtnis – nicht an der Quelle nachgelesen.
- **`SedimentModul`**: mit `rueck` liest es `bed.dz` und rechnet das Wasser der Zelle aus V(η − dz); im Randstreifen (`rand` Zellen) geht die Bodenänderung am Bus auf null; `surf.z0.sediment` ist auf festem Boden 0; `anfang(...)` setzt den Boden aus einem früheren Lauf.
- Karte: `Werte` um drei Vierer länger, Schalter STROM und AUFLAGE, `fest[3c + 2].z` trägt `water.spiral`, `bett[2c + 1].z` die Auflage, `abg[4c + 2].z` die Rate der Auflage, neuer Puffer `Wirk` (Bindung 12 in `transport.comp`).
- Geprüft in `lauf.demo.MorphoCheck` (Prüfsteine 3–6, 8 von P9c) und `demo.SedimentKarteCheck` (Fall P9c).
