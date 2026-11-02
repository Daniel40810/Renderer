# com.dan.quake – Erdbeben

Ein Erdbeben an einer Störung: Bruch, der vom Herd über die Störung läuft, Laufzeiten der P- und S-Welle, Stärke am Ort nach dem Abminderungsgesetz BSSA14, eine synthetische Zeitreihe der Bodenbeschleunigung in drei Richtungen mit Arias-Intensität und Dauer, und die Folgen: Hangversagen nach Newmark, Seiche einer Bucht, Zittern einer Kamera, das Grollen als Schallspur.

Der Kern braucht nur Java 21; der Ordner `demo/` zusätzlich `FStyle.jar`. Es gibt keinen Ordner `gl/`: Das Beben selbst zeichnet man nicht, es bewegt anderes (Kamera, Boote, Bäume, Hänge). Zum Herauskopieren reicht `src/com/dan/quake/`.

**Stand: LituyaBay1958 Phase 9** (Werkbuch 5.1, 9.1, 9.2): seit Phase 9 Entfestigung im Hang.

## Was es kann

- **Störung (`Fault`):** Linienzug an der Oberfläche (x Ost, z Süd, m), senkrecht bis `bottom`; Herd mit Lage und Tiefe; Länge und größter Versatz aus der Magnitude nach WELLS94 für Blattverschiebungen (log10 L = −3,55 + 0,74 M, log10 D = −7,03 + 1,03 M); `forward` legt fest, wie viel des Bruchs vor dem Herd liegt; `ruptureTime(s)` mit `vr` = 0,8 vS; `rjb(x, z)` zum gebrochenen Teil.
- **Laufzeiten:** `tP`, `tS` vom Herd (vP 6,0 km/s, vS 3,5 km/s [G]); tS − tP ≈ D / 8,4 km/s.
- **Stärke (`Bssa14`):** Boore, Stewart, Seyhan und Atkinson 2014 (NGA-West2) für PGA, PGV, SA(0,2 s) und SA(1,0 s) mit Mechanismus, Rjb und Vs30 (linearer und nichtlinearer Bodenterm), σ aus τ und φ; `epsilon` verschiebt um Standardabweichungen.
- **Zeitreihe (`Quake.record`):** Teilflächen alle 1–5 km (Qualität) strahlen, wenn der Bruch sie erreicht, je einen Schwall gefilterten Rauschens ab (P schwach und vor allem senkrecht, S stark), abgeschwächt mit 1/R; die Summe wird auf den Median von BSSA14 skaliert. `accelerationAt(x, z, t)` → Ost, Süd, oben (m/s²); `arias()`, `significantDuration()` (D5–95), `along(t, Azimut)`; gleiche Saat, gleiche Reihe; gemerkt je 100 m.
- **Hang (`Slope`):** unendlicher Hang, FS = (c' + γ z cos²α tan φ') / (γ z sin α cos α), pseudo-statisch `fs(k)`, a_c = (FS − 1) g sin α, `cohesionFor(FS)`, Newmark-Verschiebung über eine Zeitreihe (nur hangabwärts) mit Zeit des Versagens (10 cm), JIBSON93 als Faustformel. Seit Phase 9 Entfestigung: `residual` (Rest der Kohäsion) und `softening` (m), c(D) = c_r + (c′ − c_r) e^(−D/D_s); fällt a_c auf null, geht der Hang durch (`Result.runaway`, `acMin`).
- **Bucht (`Basin`):** Eigenperioden nach Merian (geschlossen 2L/(n√(gh)), offen 4L/((2n−1)√(gh))), Anregung der ersten Eigenform als gedämpfter Schwinger durch die Bodenbeschleunigung längs der Achse, Ausschlag am Kopf.
- **Zittern (`Shake`):** Verschiebung und kleine Drehungen aus der Bodenbewegung am Ort (nachgiebiger Hochpass), `gain` für die Darstellung.
- **Grollen (`Rumble`):** Schallspur (PCM) aus der Hülle des Schüttelns auf Rauschen von 25 bis 110 Hz, Knacken mit dem Ruck; `wav(...)`.
- **Presets:** `fairweather1958()` (Mw 7,8, Herd 58,23° N 136,71° W 10 km aus ComCat, Spur aus Qfaults 2020 um den Ursprung von LituyaBay1958, Bruch ¾ nach Nordwesten [G]); `magnitude(M, Abstand)`.
- **Paketvertrag:** offene Felder, `seed`, `Quality` LOW bis ULTRA, `strength(x, z, t)` für das Platzhalterbild, Schnittstelle `Failure` für Hänge, die versagen.

## Benutzung

```java
Quake q = Quake.fairweather1958();
double[] a = q.accelerationAt(x, z, t);          // m/s², Ost, Süd, oben
Slope wand = new Slope();                         // 40°, φ' 35°, Platte 92 m
wand.cohesion = wand.cohesionFor(1.15);
Slope.Result r = wand.newmark(q.record(x, z));    // r.failTime(): wann der Hang geht
Shake s = new Shake(q, augeX, augeZ);             // s.at(t): Zittern der Kamera
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Quake` | Kern: Beben, Laufzeiten, Stärke, synthetische Zeitreihe, Presets |
| `Fault` | Störung, Bruch, WELLS94, Rjb |
| `Bssa14` | Abminderungsgesetz BSSA14 |
| `Slope` | Hang, Sicherheitsfaktor, Newmark, JIBSON93 |
| `Basin` | Seiche nach Merian, Anregung |
| `Shake` | Zittern |
| `Rumble` | Grollen als PCM, WAV |
| `demo/QuakeDemo` | Karte mit Feld, Störung, Fronten; Zeitreihen an fünf Orten, Newmark, Seiche; Fenster mit Zeit, `--bild datei.png [t]`, `--wav datei.wav` |
| `demo/QuakeCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Prüfung

`QuakeCheck` prüft:
- **Laufzeiten:** tS − tP = D (1/vS − 1/vP) ≈ D / 8,4 km/s.
- **WELLS94:** M 7,8 → 166,7 km, 10,1 m.
- **BSSA14** gegen die Formel von Hand: M 7,8, Blattverschiebung, Rjb 10 km: Fels 0,308 g, Vs30 400 m/s 0,391 g; Abnahme mit dem Abstand, σ.
- **Newmark** gegen die geschlossene Lösung für einen Rechteckimpuls (A − a_c) A T² / (2 a_c); Sicherheitsfaktor und pseudo-statischer Fall; JIBSON93.
- **Merian:** Lituya Bay 12 km, 130 m → 11,2 min.
- **Zeitreihe:** still vor der P-Welle, P schwächer als S, Spitze = BSSA14, gleiche Saat gleiche Reihe; Zittern und Grollen.
- **Preset:** `fairweather1958()` am Gilbert Inlet: Rjb 0,4 km, PGA 0,51 g, tS − tP 8,1 s.
- **Entfestigung:** ohne Rest wie bisher; mit Rest 20 % und 0,3 m geht die NE-Wand (FS 1,10) während des Bebens durch.

## Grenzen

- Die Zeitreihe ist synthetisch: Rauschen mit Hülle, keine Wellenausbreitung in einem Modell der Kruste, keine Richtwirkung des Bruchs auf die Amplitude (nur auf die Zeiten), kein Fling. Spektren folgen nur grob dem Durchlass von 0,15 bis 12 Hz.
- Die Bruchlänge nach WELLS94 (167 km bei M 7,8) ist ein Mittel; für 1958 nennt die Literatur längere Brüche [zu prüfen]. Damit dauert das starke Schütteln am Gilbert Inlet rund 30 s (D5–95), weniger als die 1 bis 4 min der Zeugen.
- Hang: unendlicher Hang mit einer Gleitfläche, keine Porenwasserdrücke, keine Abnahme der Festigkeit beim Gleiten.
- Seiche: nur die erste Eigenform, rechteckiges Becken.

## Seit welcher Phase

LituyaBay1958 Phase 8 (Werkbuch 5.1): neu.
- Newmark ist starr-plastisch: Verschiebung entsteht nur, solange es stark schüttelt. Auch mit Entfestigung versagt die Wand am Gilbert Inlet fast immer während des Bebens (22–33 s); die 60–150 s der Zeugen erreicht sie nur auf einer Messerschneide (FS 1,10, Rest 30 %, 1,15 m: 65 s). Ein späteres Versagen braucht Zeit im Stoff (Porenwasser, Kriechen) – die App löst dann von Hand aus.
