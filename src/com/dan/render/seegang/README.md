# com.dan.render.seegang – die Oberfläche des Seegangs auf der Karte

**Stand: S3b nach L2 (23.11.2026), Version 0.1.** Rechenteil der Schicht `render` (Seegang Werkbuch, Datei 10; SF19–SF21 A).
`SeegangKarte` rechnet die Synthese von `core.szene.Oberflaeche` (Referenz in double) auf der Karte in float: an
beliebigen Punkten und in einer Clipmap um die Kamera. Netz und Bild kommen in S3c (`render.water` liest dann
Verschiebung und Normale aus den Puffern der Clipmap).

## Schicht

```
com.dan.render.seegang        core + core.gpu + LWJGL      SeegangKarte, Kernel see_*.comp
com.dan.render.seegang.demo   dazu FStyle                  SeegangKarteCheck (Karte gegen Referenz)
```

## Ablauf

| Wann | Was |
|---|---|
| neuer Stand des Nahfelds (`eingaben`) | `Oberflaeche.vorbereiten`, dann auf die Karte: Varianz je Zelle und Stützstelle (nach der Richtung verteilt), Kennwerte je Zelle (m0, Steilheit, Energie und k je Band), Tiefe und Gefälle, Phasenfeld δΦ, δT; je Band die kleinste Wellenzahl der Dispersion (`Phasenfeld.kMin`) |
| Wechsel des Phasenfelds (`phasen`) | nur δΦ, δT einer Stufe neu; der Lauf blendet Stände auf dem Prozessor |
| je Bild (`bezug`, `zeit`) | Bezugspunkt B nahe der Kamera; Phase und Laufzeit jeder Stützstelle bei B auf dem Prozessor in double (mod 2π, mod Periode der Hülle), Fernfeld bei B, Mitten der Stufen |
| `see_stufe.comp` | je Stufe L der Clipmap (n², Abstand d₀ · 2^L, Mitte auf 2 d_L gerastet) Verschiebung und Höhe (ξx, ξy, η, \|Z'\|); alle Stufen in einer Abgabe (`eineAbgabe`, B38) |
| `see_normale.comp` | Normale und J aus zentralen Differenzen (am Rand einseitig), ebenso in einer Abgabe |
| `see_punkte.comp` | dasselbe an beliebigen Punkten (Prüfung, später Auftrieb und Brandung) |
| `Seegangbild` (S3c) | Bildteil vor dem Wasser: je Bild `zeit` und `bild` in den Ablauf des Rahmens (die Werte über `vkCmdUpdateBuffer`, nicht sofort: ein früheres Bild liest sie vielleicht noch); legt `Seegang/Netz` (Kopf: Stufen, n, innerer Bereich, je Stufe Lage und d_L), `Seegang/Versatz`, `Seegang/Normale`, `Seegang/Aufgeloest` für `render.water` ab, das dieses Package nicht kennt |

**Seit S3c:** `see_stufe` schreibt dazu je Stelle die aufgelöste Neigungsvarianz Σ k² E g² und die Varianz der Höhe
Σ E g² (Puffer 18; `Oberflaeche.Punkt.neigung`, `.gezeichnet`), für das Glitzern und die Schaumköpfe des Netzes. Im
äußeren Band der äußersten Stufe (`randFern` = 16 Stellen [G]) geht die Synthese linear ins Fernfeld über (`nah`,
`Oberflaeche.punkt(…, nah)`): am Rand sind es die 72 Wellen, die der Weg jenseits der Clipmap zeichnet (N4: 0,04 σ RMS in
der Prüfszene). Das Fernfeld der Karte gilt für B (nicht je Ort wie in der Referenz); wo sich Hs über 2 km stark ändert,
bleibt am Rand ein Sprung der Höhe.

**Ablage (B38):** Varianz und Kennwerte liegen auf der Karte je Stützstelle bzw. Kennwert (Index j · nc + c), die
Phasen je Stützstelle (bq · nc + c): Benachbarte Fäden lesen dann benachbarte Zellen. Je Zelle abgelegt (wie bis L2c)
kostete das Lesen der Varianz auf der 680M 4,2 von 7,6 ms; `energieJeZelle` und `phaseJeZelle` gibt es noch zur Messung.

**Schwelle (SF23 A):** Eine Stützstelle zählt, wo ihre Varianz über 10⁻³ m0 **oder** ihr Beitrag zur Neigung über
10⁻³ der Neigung liegt (`Oberflaeche.E_REL`, `S_REL`, `faktor`); zwischen der Schwelle und ihrem Doppelten blendet sie
linear ein [G], damit keine Welle an einer Linie aufspringt. In der Mitte des feinen Gitters der Prüfszene: 72 statt bisher 90
Stützstellen, 1,2 % von m0 und 1,1 % der Neigung gehen ins Glitzern.

**Liste der aktiven Stützstellen (B41):** Die Referenz sammelt die Stützstellen, die in irgendeiner Zelle über einer der
Schwellen liegen (`Oberflaeche.aktiv`); die Karte legt sie je Band ab (Puffer 17) und geht nur diese durch. Referenz und
Karte zählen dieselben. Ohne die Liste wäre es fast dasselbe Bild: Die Neigungsschwelle ist zwischen den Zellen nicht
bilinear, eine Stützstelle kann dort knapp darüber liegen, ohne es an einer Ecke zu tun (Faktor nahe 0).

**Im Lauf (SF23 A):** `bild` rechnet die Stufen 0–4 jedes Bild, die Stufen 5–7 jedes zweite (`ferneAb`); ihre Mitten
und Puffer bleiben dazwischen stehen. `stufen` rechnet alle (Prüfungen).

Positionen auf der Karte sind relativ zu B; was in float nicht hält (k₀ s über Kilometer, Zeiten über Tage), rechnet
der Prozessor vorher in double. Die Puffer (`see_gemein.glsl`, Bindungen 0–16) stehen am Kopf der Shader.

## Auflösung

Jede Welle zählt mit g = clamp(λ / 4d − 1, 0, 1), λ = 2π / max(|∇Φ|, kMin) (Befund B34): unter 4 d nichts, über 8 d
voll. kMin je Band ist die kleinste Wellenzahl der Dispersion im Feld; wo das Phasenfeld nachgibt (fast trocken, wenig
Energie), wird eine Welle so nicht länger, als die Physik erlaubt. Ist 2π / kMin kleiner als 4 d, übergeht die Stufe das
ganze Band (die groben Stufen rechnen dadurch nur die langen Bänder).

## Speicher (Prüfszene, 8 Stufen zu 128²)

84 MB: Hüllen 16,8 MB, Phasenfelder fein und grob je 20,5 MB, Varianz 2 × 10,2 MB, Clipmap (Verschiebung, Normale) 2 × 2,1 MB, Rest klein.

## Grenzen

- Rechenzeit auf der 680M für alle Stufen mit Normale (Budget 1,5 ms): L2 14,2 ms (je Stufe eine Abgabe), L2c 7,6 ms
  (eine Abgabe; Befund B38). Zur Messung gibt es `diagnose` (ohne Hülle, Phase, Ecken der Energie), `ohneFein`,
  `ohneGrob`, `phaseJeZelle` und `energieJeZelle` (andere Ablage, dasselbe Bild), `schwelle` (andere Schwelle als
  E_REL, dann weicht das Bild ab), `alleStuetzstellen` (ohne Liste), `stufenVon`/`stufenBis` (nur ein Teil der Stufen).
  L2d: 3,1 ms mit der Ablage je Stützstelle; L2e: 3,0 ms; L2f 2,75 ms mit der Liste. Budget seit SF23 A: 2,2 ms je
  Bild im Lauf (`bild`). `neigung` setzt die Neigungsschwelle zur Messung (0: nur die Varianz).
- `tanh` nur mit Klemme (`th`, Befund B37): Der Treiber der 680M gibt sonst im tiefen, ruhigen Wasser NaN.
- Die gebundene lange Welle ist da, aber im Bild aus (SF20 A); seit S6b-2 nur im Antrieb des Auflaufs an (SF62 A).
- Am trockenen Rand des groben Gitters (50 m) läuft das Phasenfeld in wenigen Zellen mit wenig Energie rückwärts
  (Befund B35, offen); das feine Gitter liegt in S3c über der Brandungslinie.

Prüfung: `render.seegang.demo.SeegangKarteCheck` (5 Teile, 22 Prüfsteine), Attest `docs/status/S3b_SeegangKarte.md`. Das Netz im Bild prüft `lauf.demo.SeegangNetzCheck`.

**Seit S3d (Seegang Werkbuch 28, SF35 A):** `SeegangKarte.nahGewicht` (0 … 1, Werte `F_NAH`) gewichtet das Nahfeld
gegen das Fernfeld (`see_gemein.glsl`, `punkt`); die Anwendung blendet damit beim Wechsel der Fläche über, sonst 1
(bitgleich wie vorher). `fernOst`/`fernNord`: der Ort, an dem das Fernfeld seine Werte nimmt (NaN: am Bezug B); die
Fläche setzt ihn aufs Auge, die Anwendung führt ihn je Bild mit.

**Seit S3d-2 (Seegang Werkbuch 31):** `Seegangbild` rechnet nach einem Sprung der Zeit (mehr als 600 s [G] von Bild zu
Bild) alle Stufen im nächsten Bild (`SeegangKarte.neuerLauf`); sonst mischte es die fernen Stufen (jedes zweite Bild,
SF23 A) aus der alten Zeit mit den nahen aus der neuen (L2n: +1,6 % Helligkeit für ein Bild).

**Seit S4a (Seegang Werkbuch 35, SF42–SF45 A): das Schaumfeld**

- `Schaumfeld` (neu, 0.1): Schaum mit Gedächtnis in zwei Rastern um das Auge [G, SF43 A], fein 1024² zu 0,25 m (256 m)
  und grob 512² zu 2 m (1 km), als Ring im Puffer (die absolute Zelle (I, J) liegt auf (I mod n, J mod n); wandert das
  Fenster, beginnen neue Zellen leer). Je Bild `schritt` nach der Clipmap, Kernel `see_schaum.comp` je Raster:
  Transport mit der Drift im Flussverfahren (aufwind; die Masse bleibt bis auf den Rand erhalten, an Konvergenz steigt f),
  höchstens 0,4 Zellen je Teilschritt und 4 Teilschritte je Bild (bei großem Zeitraffer wird der Weg gekappt); Zerfall
  1/e nach 4 s offen und 12 s im Watt unter 2 m Tiefe [G, SF45 A]; Quelle f = max(f, 1), wo ein Kamm bricht: tief und
  mittel, wenn (1 − J)/λ = Σ a k coth(kh) die lineare Schwelle zu u_s/c = 0,85 (Barthelemy u. a. 2018) erreicht,
  B̊ₜ(kh)/1,04 nach Hulin u. a. (2025, Coastal Engineering 197), im Tiefen rund 0,34, nur für kh über 2π/6; flach, wenn
  2 |Z| die Grenze nach Battjes und Janssen (1978) erreicht, nur am Kamm (η ≥ 0,7 |Z|) [G]. Sprung der Zeit über 60 s
  oder rückwärts: leer neu. Für das Wasser unter `Seegang/Schaum` (Kopf 128 Byte, zwei Kopien; Aufbau in der Klasse).
  Zur Prüfung: `schalter` (Quelle, Zerfall, Transport, Prüfdrift), `lies`, `setze`, `masse`, `zuletzt`.
- `see_gemein.glsl`: `punktK` gibt dazu k_p h aus; `punkt` wie bisher.
- `see_stufe.comp` schreibt je Stelle den Kamm (λ der Verschiebung, k_p h; Puffer 19, `SeegangKarte.kamm(l)`).
- `SeegangKarte`: Bindung 19 der Kamm; `drift(fein, grob)` die Drift des Schaums je Zelle des Nahfelds (vec2, die
  Anwendung rechnet sie: `lauf.Seegangsflaeche.drift`), `hatDrift`, `driftMax`; die Belegung des Schaumfelds (0–22).
- `Seegangbild.schaum`: hängt ein Feld an, rechnet es nach der Clipmap einen Schritt mit dieser Karte und legt es ab.

Prüfung: `lauf.demo.SchaumCheck` (Attest `docs/status/S4a_Schaum.md`, Bilder `S4a_Schaum_*.png`).

**Seit S4a-2 und S4b (Seegang Werkbuch 36): die Windsee und die Optik**

- Befund L2q: Über 120 s Sturm lag keine Stelle der Fläche über der Schwelle (der Fläche fehlen die kurzen, steilen
  Wellen der Windsee), und dwc des Nahfelds war bei 16–22 m/s Wind fast 0. Seit S4a-2 setzt der Kernel deshalb dazu die
  Köpfe der unaufgelösten Windsee (`Schaumfeld.WINDSEE`): wie die Schaumköpfe aus P13c auf den Kämmen
  (X = C0 η/√m0 + Muster), ganze Bedeckung W_Z = √(W_Callaghan · W_Monahan) aus U10 wie F161 A, aktiv davon
  W_Z / (1 + τ / (T_p / 4)) [G]. Der Puffer der Drift trägt dafür je Zelle vier Werte (Drift, U10).
- Die Schwelle der aufgelösten Kämme nimmt kh der aufgelösten Wellen (k_eff aus Puffer 18), nicht k_p h.
- Seit S4b steht die Zeit (Pause, `STEHT`), bekommen nur Zellen Schaum, die neu ins Fenster kommen.
- `Schaumfeld` 0.2.

**Seit S5b-4 (Seegang Werkbuch 46, SF51 A): die Kämme am Fuß der Kanten**

- `Fusssuche` (neu) und `see_fuss.comp` (neu): Je Bild nach der Clipmap sucht die Karte an den Füßen, die die Brandung
  zuletzt gemeldet hat (höchstens 512), die Fläche mit der Auflösung der Stufe, die den Fuß zeichnet: θ = arg Z, η,
  H_s = 4 √m0, T_m01 = 2π Σ E / Σ E ω, und drei Kämme (vorig, letzter, nächster): Zeit des Durchgangs Δ gegen die
  Zeit der Karte, H = 2 |Z'|, Weißwasser, lineare Höhe 2 |Z|. Den Kamm sucht das Sekantenverfahren auf θ(Δ) = 0, θ
  fallend (erste Steigung −2π/T_m01, höchstens acht Schritte, |θ| < 0,003); nicht gefunden: H = −1. Je Faden ein Fuß und
  ein Kamm. Ring zu 4 wie `Gischtsuche`; der Prozessor liest drei Bilder später (`lies`), `sofort` für Prüfungen.
- `see_gemein.glsl`: Mit `#define ZEITVERSATZ` vor dem Einbinden rechnet `punktK` zur Zeit der Karte + `zeitVersatz`
  (Phase − ω_b Δ, Hülle τ − Δ, Fernfeld − ω(k) Δ) und gibt m0 und Σ E, Σ E ω aus. Ohne den Schalter fallen die Terme im
  Präprozessor weg; `see_stufe`, `see_schaum`, `see_gischt`, `see_punkte` übersetzen Byte für Byte wie vorher.
- `SeegangKarte.lesenSynthese()`: was `punktK` liest (für die Schranken des Ablaufs).
- `Seegangbild.fuss`, `.fuesse`: hängen sie an, geht je Bild das Ergebnis an die Folge (`core.szene.Fusswellen`), dann
  sucht die Karte die gemeldeten Füße.

Prüfung: `demo.FusssucheCheck` (Attest `docs/status/S5b4_Fusssuche.md`; Szene von `SeegangKarteCheck`) und
`lauf.demo.WalzeCheck` (Teil „Blick auf eine Kante“, Bilder `S5b4_Kante_*.png`).

Seit S5b-4b (Seegang Werkbuch 47, Befund L3a: 1,66 ms für 500 Füße auf der 680M; jeder Faden wartete Schleife für
Schleife auf den Speicher): je Fuß und Kamm eine Gruppe von 32 Fäden. Mit `ZV_PARALLEL` (`ZV_LANES`) nimmt in `punktK`
jeder Faden jede 32. Stützstelle und Welle des Fernfelds, danach summiert die Gruppe im geteilten Speicher (`zvSummen`);
alle Fäden rufen gemeinsam auf, Faden 0 schreibt. Ohne den Schalter bleiben die Schleifen Zeichen für Zeichen gleich
(die übrigen Kernel übersetzen wieder Byte für Byte gleich). Prüfstein in `FusssucheCheck`: höchstens 0,5 ms auf der Karte.

Seit S5b-4c (Seegang Werkbuch 48, Befund L3b: 1,44 ms – die Suche ist durch die verstreuten Lesezugriffe begrenzt,
jeder Fuß liest seine eigenen Zellen; sie kostet je Auswertung): `Fusssuche.teile` = 4, je Bild sucht die Karte nur
jeden vierten Fuß (reihum nach der Nummer des Abschnitts), und nur den letzten Kamm (`alleKaemme` für Prüfungen); ist
er nicht zu finden, sucht dieselbe Gruppe den vorigen (Platz 0). Ein Kamm ohne Wellen (Z = 0, wo die Stufe nichts
auflöst) gilt nicht. Dazu die H_s der gezeichneten Wellen am Fuß, 4 √(Σ E g²) (fünfter Wert, y), als Maßstab für 2 |Z|.

Seit S5b-5/5b (Seegang Werkbuch 50, 51): `core.szene.Lippenpruefung` wertet die Lippen der Brandung im Netz Bild für Bild
aus dem Protokoll aus (sichtbare Kante je sichtbares Ende, Weg von Bild zu Bild); `FusssucheCheck` Teil 6 legt die
Füße wie die Kantenkarte (steilster Punkt je Zelle von 5 m, Zickzack am Hang, Richtung gestreut), `WalzeCheck` prüft an
den Kanten des Gebiets über alle Bilder und zeichnet dazu ein Bild von oben (`S5b5_Lippen_*_oben.png`).

Seit S5b-6 (Seegang Werkbuch 55): die Karte braucht nichts Neues – die gekoppelten Stützstellen lesen ihre Hülle
aus derselben Ablage (der `Profilpuffer` teilt sie vor dem Hochladen), Richtung und Konstanten stecken im Phasenfeld.
`demo.SeegangKarteCheck` baut seine Szene wie die Anwendung mit der Kopplung (Karte gegen Referenz unverändert grün).
Seit S5b-6b: dieselbe Szene mit `Reflexion.partner(grob, fein)` und `wand` (Karte braucht weiter nichts Neues).

**Seit S6b-2 (Seegang Werkbuch 67/68, SF61–SF63 A): der Antrieb des Auflaufs**

- `see_gemein.glsl`: Mit `#define TIEFENMITTEL` rechnet `punktK` dazu den tiefengemittelten Durchfluss je Welle
  q = (ω / k) η k̂ (Stützstellen und Fernfeld; SF61 A) und den Stand des Antriebs: η ohne die gebundene lange Welle des
  Bilds, mit der gebundenen langen Welle nach Longuet-Higgins und Stewart (SF62 A: nur hier an) und deren Durchfluss
  (mit c_g), dazu η₂ mit c. Ohne den Schalter übersetzen die übrigen Kernel Byte für Byte wie vorher. Referenz in double:
  `core.szene.Oberflaeche.Punkt.qx`, `.qy`, `.etaAntrieb`, `.lwAntrieb`.
- `Randwelle` (neu) mit `see_antrieb.comp` (je Punkt und Zeit eine Gruppe von 32 Fäden, `ZV_PARALLEL`) und
  `see_antrieb_rand.comp`: `punkte` (UTM, relativ zu B neu, wenn B wechselt), `ziele` (je Randzelle des Lösers: Platz,
  Lage längs der Punkte, Grund, Normale und Längsrichtung), `rechne` (Stand und Durchfluss an den Punkten, eine oder
  zwei Zeiten), `verteilen` (linear zwischen den Punkten, u = q / max(5 cm, η − Grund) [G], η relativ zum Ruhewasser des
  Lösers) in einen beliebigen Puffer; `lies` für Prüfungen. Die Schicht kennt `physics.swash` nicht: welcher Puffer und
  welcher Platz, sagt `lauf.Auflauf`.
- `Seegangbild.Anhang` (neu): ein Teil, der nach der Clipmap (und nach Schaum, Gischt und Fuß) im Ablauf des Bilds mit
  dieser Karte und der Zeit des Bilds rechnet (`Seegangbild.anhang`); die Anwendung hängt dort den Auflauf an.
- Die gebundene lange Welle gilt nur, wo die Synthese Wasser hat: an trockenen Punkten (nur Fernfeld) wird sie groß
  (A7); der Auflauf setzt seinen Rand deshalb nicht dorthin.

**Seit S6b-3b (Seegang Werkbuch 73):** Ziele mit einer Art (vierter Wert): 0 die Welle, 1 trockenes Außen (Stand
tief unter jedem Grund: der Löser lässt Wasser nur hinaus), 2 ruhendes Wasser am Spiegel. `lauf.Auflauf` gibt so allen
vier Seiten einen Außenzustand. Seit Datei 74 (F6) dazu 3: die Zelle innen (`see_antrieb_rand` schreibt (0, 0, 0, 1),
`physics.swash.gpu.swash_rand` nimmt dann die erste Zelle innen).

Prüfung: `demo.RandwelleCheck` (Karte gegen Referenz an Punkten, Massenbilanz und lange Welle als Auskunft, Verteilen
gegen dieselbe Rechnung in Java, Kosten für 129 Punkte ≤ 0,25 ms auf der 680M); Attest `docs/status/S6b_Randwelle.md`.

**Seit S6c-1b (Seegang Werkbuch 78, SF85 A): Rand mit erhaltener Richtung**

- Befund (Datei 77): Der Löser rechnet Flachwasser ohne Dispersion und hat bei ω die Wellenzahl ω/√(g h). Die Synthese
  zeichnet mit der linearen Wellenzahl (an der Küste mit noch größerer, A15); schräge Anteile wechseln längs des Rands zu
  schnell und klingen am Rand ab. Am Deich liefen 53 %, am Deckwerk 41 % der Energie hinein.
- `see_gemein.glsl` mit `#define RANDRICHTUNG` (nur in `see_antrieb.comp`, mit `TIEFENMITTEL`): je Stützstelle Phase und
  Laufzeit auch am Bezugspunkt (`randPhase`) und längs der Punkte gestreckt, Φ′ = Φ_m + s (Φ − Φ_m) mit
  s = (ω/√(g h_R)) / |∇Φ_m|, T′ = T_m + (T − T_m) / (|∇T_m| √(g h_R)); Durchfluss je Welle mit √(g h_R) statt ω/k; die
  Wellen des Fernfelds ebenso. Die übrigen Kernel übersetzen Byte für Byte wie vorher (im Container geprüft).
- `Randwelle.bezug(ost, nord, tiefe)` (Mitte der Punkte, Tiefe des Rands; `punkte` setzt ihn zurück), `bezugTiefe()`,
  Push-Konstanten von `see_antrieb` 32 statt 16 Byte. `Randwelle.RICHTUNG` (`-Dseegang.randrichtung=aus`: wie bis S6c-1).
- Referenz: `core.szene.Oberflaeche.punktRand`. Prüfung: `demo.RandwelleCheck` Teil 5 (129 Punkte auf einer Geraden
  im feinen Gitter, Normale 45° gegen den Seegang: 5a Karte gegen Referenz wie Teil 1; 5b frei laufend im Löser bis auf
  eine Zelle der Auflösung ≥ 97 % [G]; im Container 99,3 %, ohne Bezug 80,6 %).

**Seit S7b-1 (Seegang Werkbuch 88): das Wasser unter den Schwimmern**

- `Koerpersuche` (neu) und `see_koerper.comp` (neu): Je Bild nach der Clipmap rechnet die Karte an den Punkten der
  neuesten Anfrage (`core.szene.Wasserpunkte`, je Körper die Säulen seiner Proben) die Fläche über dem festen Ort
  (feste Punktiteration wie `Oberflaeche.euler`, bis 1 mm oder 8 Schritte) und am festen Ort die rohen Summen der
  linearen Wellen je Band: Σ |Z|, Σ |Z| k, C0 = Σ Z, K1 = Σ k Z, G = Σ k⃗ Z, U = Σ ω coth(kh) k̂ Z, W = Σ ω Z (Wellen des
  Fernfelds im Band mit dem nächsten ω). Ring zu 4 wie `Fusssuche`; der Prozessor liest drei Bilder später.
- `see_gemein.glsl`: Mit `#define BAENDER` sammelt `punktK` die Summen, solange `bdAn` gilt. Ohne den Schalter fallen die
  Terme im Präprozessor weg; die übrigen Kernel übersetzen Byte für Byte wie vorher (geprüft beim Bau).
- `Seegangbild.koerper`, `.wasserpunkte`: hängen sie an, geht je Bild das Ergebnis an die Wasserpunkte, dann rechnet die
  Karte die neueste Anfrage.
- Referenz: `core.szene.Oberflaeche.baender` (double). Prüfung: `lauf.demo.SchwimmerCheck` (Prüfsteine 15, 16).

**Seit S7b-4 und S7d (Seegang Werkbuch 89): Körpersuche 0.2, Kielwasser**

- Befund L5c: `see_koerper` kostete 1,0–2,7 ms je Bild (Grenze 0,5 ms); die Kosten folgen der Zahl der Auswertungen von
  `punktK` (wie Befund L3b). `Koerpersuche` 0.2: Passt die Anfrage zur vorigen (dieselben Körper, dieselben Zahlen), beginnt
  die Suche über dem festen Ort bei ξ des letzten Bilds (Puffer `Start`, Bindung 23) und rechnet höchstens 3 Schritte;
  η gilt am letzten Ort der Rechnung (ohne die letzte eigene Auswertung). Dazu weniger Punkte (`lauf.Flotte`: die Fähre im
  Raster von 2 m). Der Kopf je Punkt hat 12 Werte (`core.szene.Wasserpunkte.KOPF`): dazu Kielwasser, Wellenzahl, ξ.
- `Kielwasserkarte` (neu) und `see_kiel.comp` (neu): zwei Kacheln um das Auge (256 m und 2048 m, je 256² Zellen) mit Höhe,
  Schaum und mittlerer Wellenzahl der Schiffe in Fahrt, wie `core.szene.Kielwasser.kachel` in double (Tafeln der
  stationären Phase bei endlicher Tiefe, Spur der Bugs). `see_kiel_am.glsl` liest sie (bilinear, nah in weit überblendet;
  wer gröber fragt als 1,5 Zellen, bekommt die Kachel nicht).
- `see_kiel_dazu.comp` (neu, über `SeegangKarte.kiel`): nach `see_stufe` und vor `see_normale` die Höhe zu η der Stufen,
  die die Abgabe gerechnet hat, und der Schaum hinter dem Heck als Weißwasser des Kamms (das Schaumfeld nimmt ihn auf).
  `see_stufe` und die übrigen Kernel bleiben Byte für Byte gleich.
- `Seegangbild.kielkarte`, `.kielwasser`: die Kacheln je Bild vor der Clipmap; `see_koerper` liest die Höhe am Ort (Bindung 24).
- Prüfung: `lauf.demo.SchwimmerCheck` (S7b4), `lauf.demo.KielwasserCheck` (Prüfsteine 25–29).

**Seit S7d2 (Seegang Werkbuch 90): Körpersuche 0.3, Kielwasserkarte 0.2**

- Befund L5d: mit 234 statt 920 Punkten kostete `see_koerper` noch 0,96–2,08 ms. Mit wenigen Punkten ist die Karte fast leer;
  die Zeit ist die Latenz eines Fadens, der rund 3,4-mal über alle Stützstellen summiert (wie Befund L3a bei den Füßen).
  `see_koerper_rumpf.glsl` (neu) ist der Rumpf; `see_koerper.comp` rechnet ihn mit einem Faden je Punkt, `see_koerper16.comp`
  und `see_koerper64.comp` mit 16 und 64 (`ZV_PARALLEL`). In `see_gemein.glsl` teilen sich die Fäden mit `BAENDER` und
  `ZV_PARALLEL` auch die Summen je Band (geteilter Speicher, Faden 0 schreibt; die Wellen des Fernfelds über `bdFernTeil`).
  Ohne `ZV_PARALLEL` bleibt alles wie bisher; die übrigen Kernel bleiben Byte für Byte gleich. `Koerpersuche.faeden`
  (Vorgabe 64) wählt.
- `see_kiel.comp` in Lagen: Lage 0 alle Quellen (Höhe, Schaum, mittleres K, Σ g), Lage 1 + q die Quelle q allein (Höhe,
  Schaum, Σ K g, Σ g). `see_kiel_am.glsl`: `kielAmOhne(p, d, q)`. An den Punkten eines Schiffs lässt `see_koerper` dessen
  eigenes Kielwasser weg (Eingabe z: Lage der Quelle, `Kielwasserkarte.lage`, `core.szene.Wasserpunkte.Anfrage.quelle`) [G].
- Prüfung: `lauf.demo.SchwimmerCheck` Teil 1 (1, 16, 64 Fäden: dasselbe Ergebnis, Zeit), `lauf.demo.KielwasserCheck` Teil 3
  (ohne das eigene gegen double).

**Seit S7d3 (Seegang Werkbuch 91, SF119 A): Körpersuche 0.4**

- Befund L5d2: 64 Fäden je Punkt halbierten die Kosten in der Anwendung (0,42–0,94 ms), 16 waren in der Prüfung am
  schnellsten. Vorgabe jetzt 16; mit Startwert nur ein Schritt der Suche, und bleibt er über 2 cm, die volle Suche (R15).
- Der Kopf je Punkt trägt im siebten Wert die Gewichte von feinem und grobem Gitter (`Koerpersuche.gitter`): Befund L5d2 2,
  das Wasser unter den Körpern hängt womöglich vom Ort des Auges ab.
- `see_kiel.comp`: Die Lage einer Quelle schreibt nur die Zellen bis eine Länge plus zwei Zellen um ihre Mitte.


**Seit S7d4 (Seegang Werkbuch 92, SF121 A, SF123 A): Körpersuche 0.5**

- Befund L5d3 1: Das Wasser unter den Körpern hing vom Auge ab; im selben Seegang rollte der Kutter bis 10,4° oder bis 23,5°.
  Die **Körperfläche** ist eine zweite Fläche (grob 5 km, fein 1 km), fest um den Hafen (`lauf.Seegangsflaeche.baueKoerper`,
  der Flächenwart baut sie nach der Fläche des Auges, nicht bei jedem neuen Blick). `see_koerper*.comp` binden sie mit
  `KOERPERFLAECHE` (Bindungen 25–31: Kopf, Varianz, Zelle, Kennwerte je fein und grob). In `see_gemein.glsl`, `punktK`:
  - Außerhalb des feinen Gitters des Auges kommt die Energie aus der Körperfläche, Anteil sK = w_K (1 − w_fein).
  - Wo das Auge grob zeichnet, behalten die Wellen dessen Phasen. So liegen die Schiffe auf den gezeichneten Wellen.
  - Was das Auge ans Fernfeld abgibt (Rand, Überblenden), rechnet sie als ebene Wellen derselben Stützstellen (δΦ = 0).
  - Ohne Körperfläche (Gewicht 0) ist alles wie bisher.
- Die Liste der Stützstellen ist dann die beider Flächen (`Koerpersuche.aktivVereint`). Der Kopf je Punkt trägt im siebten
  Wert vier Gewichte zu 6 Bit: fein und grob des Auges, fein und grob der Körperfläche (`Koerpersuche.gitter`).
- Befund L5d3 3: `see_kiel.comp` wählt je Block von 8 × 8 Zellen die Stücke der Spur vor, deren Lotbereich den Block treffen
  kann (u ist affin, die Ecken reichen). Jede Zelle findet dasselbe Stück wie vorher: dasselbe Ergebnis. Im Container
  geprüft: 153 600 Zellen ohne Unterschied, im Mittel 3,8 von 95 Stücken.
- Prüfung: `lauf.demo.SchwimmerCheck` Teil 1 (dieselbe Fläche als Körperfläche; ein Viertel der Energie) und Teil 4 (das
  Wasser an den Schiffen aus einem feinen Gitter, in jeder Ansicht); `lauf.demo.KielwasserCheck` Teil 4 (Kosten einzeln).

**Seit S7d5 (Seegang Werkbuch 93, SF127–SF130 A): Kielwasserkarte 0.3**

- Befund L5d4 2: Mitten im Wechsel der Fläche des Auges läuft die Energie der Körperfläche über ebene Wellen. Deren Phase
  hing am Ursprung der Fläche des Auges und sprang mit ihr; der Kutter kenterte. Seit S7d4b liegt sie am Ursprung der
  Körperfläche (`Koerpersuche.phaseVersatz`, Bindung 32 von `see_koerper*`). Wellen mit den Phasen des Auges ohne Energie
  rechnet `punktK` nicht mehr.
- Befund L5d4 4 (SF128 A): Die nahe Kachel des Kielwassers ist 512 m groß (2 m je Zelle) und liegt 200 m vor dem Auge längs
  des Blicks (`Kielwasserkarte.rechne` mit der Richtung, aus `Seegangbild`). Die weite Kachel trägt nur noch den Schaum
  (vierter Wert ihres Kopfs 1). Körper außerhalb der nahen Kachel spüren kein Kielwasser.

**Seit S7d6 (Seegang Werkbuch 94, SF132–SF134 A): Körpersuche 0.6, Kielwasserkarte 0.4**

- Befunde L5d5 6 und 7 (SF133 A): Die Körper lesen das Kielwasser nicht mehr aus den Kacheln. `see_koerper*` rechnet es an
  jedem Punkt direkt aus den Spuren der Quellen (Bindung 24: `Kielwasserkarte.quellen`), mit der größten Wellenzahl
  `Koerpersuche.KIEL_KMAX` = 2π / 3 m [G] und ohne die eigene Quelle (Eingabe z: ihre Nummer im Puffer). Genau (wie
  `core.szene.Kielwasser.am` in double) und vom Auge unabhängig. Mit 16 oder 64 Fäden je Punkt teilen sich die Fäden die
  Stücke der Spur; Faden 0 nimmt das nächste. Quellen, Muster und Suche stehen in `see_kiel_spur.glsl` (für `see_kiel` und
  `see_koerper`). Die Lagen der Quellen in den Kacheln und `kielAmOhne` sind weg.
- SF134 A: `see_kiel` rechnet die Kacheln im Wechsel, je Bild eine (Push-Konstante `erste`); nach einer Pause ohne Schiff
  in Fahrt und in `Kielwasserkarte.sofort` beide. `see_kiel_dazu` schreibt nur, wo die Kacheln Höhe oder Schaum haben.
- SF132 A: die Fähre im Raster 4 m längs × 2 m quer (`Koerper.kartenRaster(längs, quer)`), der Kutter wieder je Säule.
