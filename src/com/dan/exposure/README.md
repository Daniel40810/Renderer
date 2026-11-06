# com.dan.exposure – Augenanpassung

**Stand: LituyaBay1958 W6 Runde 6 (WF19 A).** Die Belichtung folgt langsam der Helligkeit im Bild, wie Auge oder Kamera. Gedacht für Szenen, die ihr Licht schon vorbelichten (etwa nach dem Himmel aus `com.dan.sky`), in denen aber ein dichter Wald, eine Schlucht oder ein Innenraum viel dunkler ist als das offene Land. Das Package kennt keine Szene. Zum Herauskopieren reicht `src/com/dan/exposure/`. Der Kern braucht nur Java 21, `gl/` JOGL (OpenGL 4.4: Compute-Shader, dauerhaft abgebildete Puffer).

## Rechnung

| Schritt | Wie |
|---|---|
| Messen | Histogramm der Leuchtdichte L = 0,2126 R + 0,7152 G + 0,0722 B des HDR-Bilds in log2, 64 Fächer von 2⁻¹⁴ bis 2⁴, in x und y jeder achte Bildpunkt (`step`), nur jedes zweite Bild neu gezählt (`every`); zur Bildmitte bis viermal so schwer. Mit Schritt 4 in jedem Bild kostete das Zählen in Lituya rund 0,9 ms (Radeon, 1080p) |
| Mittel | Mittel von log2 L zwischen den Perzentilen 40 und 92 (Schatten und Himmel zwischen den Kronen zählen nicht) |
| Ziel | ev* = Stärke · log2(Bezug / Mittel), Stärke 0,6, Bezug 0,12, von −0,5 bis +2 Blenden [G]. Unter 1: ein dunkler Wald bleibt dunkler als die Lichtung, wie ihn das Auge sieht |
| Zeit | exponentiell, ins Dunkle 3 s, ins Helle 0,8 s (die Dunkeladaptation ist langsamer) [G] |
| Wirkung | `weight` 0..1 von der App, etwa nach der Höhe über Grund; im Flug bleibt die Belichtung fest |
| Sprung | `snap()`: die nächste Messung gilt sofort (Schnitt, Standbild, Probe) |

## Klassen

| Klasse | Aufgabe |
|---|---|
| `EyeAdaptation` | Zustand und Rechnung: `update(histogramm, dt)`, `step(dt)`, `ev()`, `factor()`, `snap()`; Felder für Bereich, Bezug, Stärke, Grenzen, Zeiten, Perzentile, Wirkung |
| `gl/LuminanceGl` | zählt das Histogramm auf der Grafikkarte (`luminance.comp`), drei Puffer im Ring, dauerhaft abgebildet, mit Zaun: gelesen wird eine fertige Messung eines früheren Bilds aus der Abbildung, ohne zu warten. Seit Runde 6b: vorher las `glGetBufferSubData` und glich damit auf der Radeon jedes Bild mit der Grafikkarte ab (3 bis 8 ms Bildzeit, obwohl der Zaun schon gefallen war). Vier Kopien der Fächer im geteilten Speicher gegen den Stau der Atomics im dunklen Wald |
| `demo/ExposureCheck` | Prüfung ohne Grafikkarte |

## Einbau

```java
EyeAdaptation auge = new EyeAdaptation();
LuminanceGl lum = new LuminanceGl(auge);  lum.init(gl);
// je Bild, nach der Szene, vor der Nachbearbeitung:
auge.weight = 1 - smooth(30, 120, augeHoeheUeberGrund);
auge.update(lum.measure(gl, hdrTex, w, h), dt);
post.set(gl, "uExposure", belichtung * auge.factor());
// bei einem Schnitt oder Sprung der Kamera:
auge.snap();
```

## Prüfung

`ExposureCheck`: Mittel eines Fachs, Perzentile (dunkelste 30 % und hellste 7 % zählen nicht), Ziel mit Stärke und Grenzen, nach einer Zeitkonstante 63 % des Wegs in beide Richtungen (ins Helle schneller), Wirkung 0, `snap()`. Der Shader wurde mit Mesa gegen eine Zählung auf dem Prozessor geprüft (gleiche Fächer).

## Grenzen

Bezug, Stärke und Zeiten sind gestaltet [G], nicht aus einem Modell des Sehens mit absoluten Leuchtdichten (die Szene liefert vorbelichtete Werte). Keine örtliche Anpassung (das ganze Bild bekommt einen Faktor). Die Messung kommt zwei bis vier Bilder zu spät (Ring, jedes zweite Bild). Der Prozessor sollte nur messen lassen, wenn die Anpassung wirkt (`weight` > 0): Lituya misst im Flug nicht.
