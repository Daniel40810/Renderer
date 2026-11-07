# com.dan.shutter – die Belichtung

Das Paket belichtet ein Bild so, wie eine Filmkamera es tut. Eine Umlaufblende öffnet das Fenster für einen Teil jeder Bildzeit (180° bei 24 Bildern je Sekunde: 1/48 s), ihre Kante läuft in einigen Millisekunden über das Fenster, und in dieser Zeit fällt das Licht durch jeden Punkt der Pupille. Zwei Wege führen dahin:

- **Die Kino-Stufe**: Jedes Filmbild wird aus Teilbildern zusammengesetzt, verteilt über die Zeit der offenen Blende, über die Pupille und über den Bildpunkt. Was in der Schärfenebene liegt, fällt in allen Teilbildern auf denselben Ort; was davor oder dahinter liegt, wird zum Bild der Blende. Was sich bewegt, verwischt um seinen Weg in 1/48 s. Das gilt für alles, auch für Gischt, Nebel, Staub, Regen und Spiegelbilder, die keine eigene Tiefe haben. Für den Film ohne Fenster und für Standbilder.
- **In Echtzeit**: die Bewegungsunschärfe aus der Bewegung der Kamera nach McGuire u. a. (I3D 2012), gerechnet aus der Tiefe und dem Blick des vorigen Bilds.

Dazu das Wagenrad, die Zeitlupe (eine Kamera mit mehr Bildern je Sekunde) und die Rechnung, wie weit etwas verwischt.

Seit LituyaBay1958, Werkbuch Welle W9c „Die Belichtung“ (07.11.2026).

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet mit JOGL (GL 4.3, Vollbild-Durchgänge). Das Paket kennt keine Szene: Die App rechnet je Teilbild ihre Szene mit der Kamera, die es ihr nennt, und gibt das HDR-Bild zum Sammeln. Zum Herauskopieren reicht `src/com/dan/shutter/`.

## Physik (mit Quellen; [G] = gestaltet)

| Was | Wie | Quelle |
|---|---|---|
| Umlaufblende | Belichtung T = Winkel/360 · 1/Bildrate: 180° bei 24 B/s = 1/48 s; 172,8° bei 25 B/s gegen das Flackern der Netzfrequenz | SMPTE; die Praxis der Kameraleute |
| Öffnungsgang | Die Kante ist ein Halbmesser um die Achse: Ein Punkt (x, y) des Fensters öffnet, wenn sie seinen Polarwinkel atan2(x, y + R) erreicht, t = φ / (2π · Bildrate). Für 24,9 × 10,4 mm und R = 38 mm [G] läuft sie in 4,8 ms über das Fenster. Auf dem Film steht das Bild kopfüber: Im fertigen Bild öffnet die rechte Seite zuerst | – |
| Flanke | Die Kante schneidet den Lichtkegel eines Bildpunkts (am Ort der Scheibe d/N breit, d = 3 mm [G]) in (d/N)/(2π R · Bildrate): bei f/2 0,26 ms, gut 1 % der Belichtung; so weich ist der Rand des Zeitfensters | – |
| Pupille | Die Teilbilder sehen die Szene von Orten der Eintrittspupille (Durchmesser D = f/N), in der Form der Irisblende (Lamellen, Rundung, Drehung) und, anamorphotisch, gestaucht; Fläche wie ein Kreis derselben Blendenzahl | – |
| Schärfenebene | Die Kamera rückt um e, die Projektion wird geschert: P₀₂ −= P₀₀ e/s. Ein Punkt in s bleibt stehen (Abweichung 2,5 · 10⁻¹⁰), einer in d wandert um e (1/s − 1/d) f; der Kreis aller Orte ist der der dünnen Linse (0,0670 gegen 0,0673 mm für 50 mm f/2,8 auf 10 m, 40 m) | – |
| Katzenauge | Die Kreise der Fassungen wandern mit dem Feldwinkel aus der Pupille; ein Teilbild zählt an einem Bildpunkt nur, wenn sein Ort in der Pupille in beiden liegt (die App gibt die Kreise, etwa aus `com.dan.lens`) | Hullin u. a. 2011 |
| Verteilung | Zeit, Bildpunkt aus einer Kronecker-Folge in fünf Maßen (R_d, Roberts 2018), die Zeit geschichtet; die Pupille aus R₂ über das umschreibende Rechteck, behalten, was in der Blende liegt; je Filmbild verschoben (Cranley-Patterson). 256 Punkte auf 16 Felder gleicher Fläche: χ² = 2,3 (Zufall um 15) | M. Roberts, „The unreasonable effectiveness of quasirandom sequences“ (2018) |
| Bildpunkt | Versatz nach einem Zelt über ±1 Bildpunkt (Rekonstruktion mit dem Zeltfilter) | – |
| Bewegungsunschärfe | Halbe Bewegung je Bildpunkt V = ½ · Weg · T/Δt (höchstens die Kachel k = 20 Bildpunkte bei 1 080 Zeilen); Kacheln mit der längsten, 3 × 3 Nachbarn; sammeln längs der längsten mit Kegel- und Zylindergewichten und weichem Tiefenvergleich, 15 Punkte. Gegen einen Kastenfilter beim gleichmäßigen Schwenk: im Mittel 0,011 | McGuire, Hennessy, Bukowski, Osman, I3D 2012; McGuire u. a. 2013; Jimenez 2014 |
| Wagenrad | Von Bild zu Bild zählt nur die Drehung modulo eines Speichenabstands: 8 Speichen bei 3 U/s stehen bei 24 B/s, bei 2,9 U/s laufen sie mit 0,1 U/s rückwärts | – |
| Zeitlupe | Eine Kamera mit n Bildern je Sekunde, mit 24 vorgeführt: die Szene läuft 24/n so schnell, die Belichtung ist Winkel/360 · 1/n (192 B/s: 1/8, 1/384 s) | – |

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Shutter` | die Umlaufblende: Bildrate, Winkel, Öffnungsgang, Flanke, Fenster; `openS`, `openAt`, `openRange`, `windowS`, `rampS`, `apparentRevPerS`, `blurPx`, `describe` |
| `Pupil` | die Fläche der Pupille: Lamellen, Rundung, Drehung, Stauchung; `sample`, `inside`, `radius` |
| `SubFrames` | die Teilbilder eines Filmbilds (`plan`), die Kamera je Teilbild (`offsetView`, `shearProjection`), `imageShift` |
| `MotionBlur` | die Einstellungen der Bewegungsunschärfe in Echtzeit; `scale` (T/Δt), `tileFor` |
| `gl/ShutterGl` | die Kino-Stufe: `begin`, `add(View)` je Teilbild, `resolve` → Textur mit Mip-Stufen |
| `gl/MotionBlurGl` | die Bewegungsunschärfe: `apply(View)` → Textur |
| `demo/ShutterDemo` | Wagenrad, Schwenk über Pfähle mit vier Verschlüssen (mit Öffnungsgang: die Pfähle neigen sich), Bokeh aus 1 024 Orten der Pupille mit Katzenauge; `--bild datei.png` |
| `demo/ShutterCheck` | 16 Prüfungen ohne Grafikkarte |

## Einbau

```java
Shutter shutter = new Shutter(24, 180);
ShutterGl acc = new ShutterGl(shutter);  acc.init(gl);
// je Filmbild in der Kino-Stufe:
SubFrames.Sample[] plan = SubFrames.plan(shutter, new Pupil(8, 0.35, 0, 1), 64, frame, true);
acc.begin(gl, w, h);
double last = shutter.openRange()[0];
for (SubFrames.Sample s : plan) {
    szene.weiter(s.tauS() - last); last = s.tauS();                 // die Szene läuft
    float[] view = SubFrames.offsetView(blick, s.u() * D / 2, s.v() * D / 2);
    float[] proj = SubFrames.shearProjection(projektion, s.u() * D / 2, s.v() * D / 2, fokusM, s.jx(), s.jy(), w, h);
    int hdr = szene.zeichnen(view, proj);
    ShutterGl.View v = new ShutterGl.View(); v.hdrTexture = hdr; v.width = w; v.height = h;
    v.tauS = s.tauS(); v.pupilU = s.u(); v.pupilV = s.v(); v.catEye = katzenauge; v.tanPerPx = pitch / f;
    acc.add(gl, v);
}
int bild = acc.resolve(gl, hdr);                                      // weiter: Objektiv ohne Tiefenschärfe, Film
```

In Echtzeit:

```java
MotionBlur mb = new MotionBlur(); MotionBlurGl mbGl = new MotionBlurGl(mb); mbGl.init(gl);
MotionBlurGl.View v = new MotionBlurGl.View();
v.colorTexture = hdr; v.depthTexture = tiefe; v.width = w; v.height = h;
v.reproj = vorigerBlick · inverse(jetzigerBlick);   // NDC jetzt → Clip vorher
v.scale = mb.scale(shutter.openS(), szenenzeitSeitDemLetztenBild);
int bild = mbGl.apply(gl, v);
```

## Prüfung

`java -cp build/classes:src com.dan.shutter.demo.ShutterCheck` – Belichtung, Öffnungsgang, Flanke, Wagenrad, Pupille (Fläche, Lamellen, Stauchung), Gleichmaß, Schichtung der Zeit, Scherung gegen die dünne Linse, Länge der Bewegungsunschärfe. Endet mit „Alles in Ordnung.“ Auf der Grafikkarte (Mesa): ein stehendes Bild durch 64 Teilbilder kommt unverändert heraus (4 · 10⁻⁶), der Öffnungsgang belichtet zuerst eine Seite, das Katzenauge schneidet zum Rand, die Bewegungsunschärfe eines Schwenks ist ein Kastenfilter (0,011 im Mittel).

## Grenzen

- Die Bewegungsunschärfe in Echtzeit kennt nur die Bewegung der Kamera; was sich in der Szene bewegt (Welle, Boote, Staub), verwischt nur in der Kino-Stufe.
- Kleine helle Lichter brauchen viele Teilbilder: Mit 64 sind ihre Kreise körnig, ab 256 glatt.
- Die sphärische Aberration und der Längsfarbfehler des Objektivs fehlen in der Kino-Stufe (alle Orte der Pupille treffen dieselbe Ebene, alle Farben auch); das gesammelte Bokeh von `com.dan.lens` hat sie.
- Die Geometrie der Scheibe (R, d, Lage der Achse) ist gestaltet; echte Kameras der 1950er sind [zu prüfen].
