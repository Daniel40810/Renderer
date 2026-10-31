# com.dan.firework – Feuerwerk

Bomben aus Mörsern und vom Ponton, Steigzeit und Zerlegungshöhe nach Kaliber, Sterne mit Masse und Abbrand, Farbe aus dem Spektrum der Metallsalze, Funkenschweife und Glitzern, Rauch im Wind, Licht je Bombe auf Rauch, Wolken und Wasser, der Knall mit Laufzeit.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3), der Ordner `demo/` braucht zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/firework/`.

**Stand: Timmendorf Phase 12** (F16 A; in Phase 12 die Spiegelung mit Wassermaske) – das Feuerwerk ist echt: Sterne, Spektrum, Licht, Rauch, Langzeitbelichtung, Darstellung auf der Grafikkarte. Vorher seit Phase 1 eine Hülle nach `muster`.

## Was es kann

- **Bahn mit Luftwiderstand und Wind:** m·dv/dt = m·g − ½·ρ·c_w·A·|v − w|·(v − w) für Bombe und Sterne. Die Mündungsgeschwindigkeit wird je Kaliber so gesucht (Bisektion), dass die Zerlegung bei 1,2 m je mm liegt (75 mm → rund 90 m, Praxisregel „100 ft je Zoll“) [Kosanke 1990]. `apex(mm)`, `burstHeight(mm)`, `muzzleSpeed(mm)`.
- **Sterne mit Masse und Abbrand:** Durchmesser (8 + 0,08·mm) mm, brennen von außen ab (Masse linear, Durchmesser mit der dritten Wurzel), werden leichter und bremsen stärker. Ausstoßgeschwindigkeit so, dass das Bild beim Erlöschen rund 0,84 m je mm misst. Neun Effekte: Päonie, Chrysantheme (Funkenschweif), Weide und Kamuro (lang brennend, gold), Palme, Ring (geneigt), Crossette (teilt sich in vier), Komet, Fächer.
- **Farbe als Spektrum** (`Spectrum`): Banden der Salze [Lage nach Kosanke und Shimizu] – SrCl 636/661/674 nm, CaCl 593/606/618 nm, Na-D 589 nm, BaCl 513–532 nm, CuCl 428–526 nm, Violett aus Sr und Cu – plus Glühen nach Planck (Weiß 3 300 K, Gold 1 900 K). Über die Normspektralwerte CIE 1931 (Näherung Wyman, Sloan und Shirley 2013) zu XYZ und linearem sRGB; was außerhalb von sRGB liegt, wird zum Weißpunkt geschoben, Farbton bleibt.
- **Licht:** Lichtstärke je Stern (farbig um 1 500 cd, Weiß 6 000 cd, Gold 400 cd [G nach Shimizu]), Anstieg 60 ms, Erlöschen im letzten Zehntel, Glitzern als Blinken. `lights()` fasst je Bombe zusammen (Schwerpunkt, RGB-Summe in cd) plus der Blitz der Zerlegung – ein Licht je Bombe statt Tausender (Werkbuch, Risiko). `illuminance(p, n)`: am Ufer in 700 m gut 1 lx, mehr als Vollmond.
- **Spuren und Langzeitbelichtung:** je Stern ein Ring der letzten `trailSeconds` s (Punkt je 1/30 s mit Lichtstärke). 0,45 s Funkenschweif, 4 s für ein Foto mit offenem Verschluss.
- **Rauch:** Wolken aus dem Abbrand, wachsen mit `smokeDiffusion`, steigen mit `smokeRise`, ziehen mit dem Wind; `opacity()` nimmt ab.
- **Zündplan als Text:** `load(x, y, z, plan)` mit Zeilen „Zeit Kaliber Effekt Salz [neigung= richtung=]“; Fehler nennen die Zeile. `demo(x, z)`: zwölf Bomben in 30 s. `travemuenderWoche(x, z)`: 91 Bomben in gut fünf Minuten mit Finale.
- **Schall:** `soundSpeed(T)`, `soundDelay(d, T)`: aus 700 m bei 18 °C gut 2 s. Ereignisse `onLaunch`, `onBurst`.
- **Darstellung** (`gl/FireworkGl`): Sterne als Gauß-Tafeln, deren Fläche genau I/d² trägt (gleich hell bei jeder Größe); Funkenschweife als Linien; Rauch als weiche Scheiben, auf der CPU von allen Bomben beleuchtet und hinten nach vorn sortiert; Spiegelung im Wasser mit Schnittebene, seit Timmendorf Phase 12 mit der Tiefe der Szene nur dort, wo Wasser ist. Linear, vorbelichtet, additiv in den HDR-Puffer.
- **Paketvertrag:** offene Felder, `seed`, Qualitätsstufen `LOW` bis `ULTRA` (Sterne je 100-mm-Bombe 40, 80, 150, 300).

## Benutzung

```java
Firework fw = Firework.travemuenderWoche(0, -700);
fw.wind = (x, y, z, t, o) -> { o[0] = 3; o[1] = 0; o[2] = 0; };
fw.events = new Firework.Events() {
    @Override public void onBurst(Firework.Shell s, double x, double y, double z, double st, double t) {
        klang.knall(x, y, z, t + Firework.soundDelay(abstand, 18));
    }
};
fw.step(dt);
for (Firework.Light l : fw.lights()) wolken.punktlicht(l.x(), l.y(), l.z(), l.r(), l.g(), l.b());

// auf der Grafikkarte, nach allem Undurchsichtigen
FireworkGl gl = new FireworkGl();  gl.init(gl4);  gl.waterY = 0;
gl.prepare(fw, auge);  gl.upload(gl4);
FireworkGl.View v = new FireworkGl.View();   // viewProj, eye, pxPerRad, preExposure
gl.drawReflection(gl4, v);                   // vor dem Wasser oder in dessen Spiegelpuffer
gl.draw(gl4, v);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Firework` | Kern: Bomben, Sterne, Spuren, Rauch, Licht, Zündplan, Schall |
| `Spectrum` | Banden und Glühen je Salz, CIE 1931, lineares sRGB |
| `gl/FireworkGl` | Darstellung mit JOGL; Shader `firework.vert/.frag` (Sterne, Rauch) und `firework_trail.vert/.frag` (Schweife) als Ressourcen im Package |
| `demo/FireworkPicture` | Dasselbe Bild auf der CPU (für Standbilder ohne Grafikkarte) |
| `demo/FireworkDemo` | Fenster mit Regler; `--bild datei.png` schreibt ein Standbild (drei Augenblicke, Farbtafel) |
| `demo/FireworkCheck` | Prüfung ohne Fenster (Sterne, Spektrum, Licht, Rauch, Darstellung), endet mit „Alles in Ordnung.“ |

## Grenzen

- Lichtstärken und Brenndauern sind Literatur-Faustwerte [G], nicht gemessen.
- Rauch ohne Selbstschatten; beleuchtet nur von den zusammengefassten Lichtern der Bomben.
- Die Spiegelung ist eine glatte Spiegelung mit festem Anteil; Wellen verzieren sie erst über das Wasser der Szene.
- Keine Bodenfeuerwerke, keine Raketen mit Leitstab.

## Seit welcher Phase

- **Timmendorf Phase 1:** Hülle nach `muster`.
- **Timmendorf Phase 14:** Rauch dünner und nicht mehr vom eigenen Licht überstrahlt (`smokeMaxOpacity` 0,35, `smokeLightDistance` 25 m; Befund aus der Bildfolge von Phase 13).
- **Timmendorf Phase 12:** Spiegelung mit der Tiefe der Szene nur auf dem Wasser (`View.depthTexture`, `waterTolerance`), damit die Wasserfläche ihre Spiegelung nicht verdeckt.
- **Timmendorf Phase 11:** Sterne mit Masse und Abbrand, Farbe als Spektrum, Glühen und Glitzern, Licht je Bombe, Rauch im Wind, Langzeitbelichtung, Zündplan der Travemünder Woche, Darstellung auf der Grafikkarte (F16 A).
