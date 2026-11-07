# com.dan.lens – das Objektiv

Das Paket setzt vor jedes Bild ein echtes Objektiv. Es rechnet nicht mit Effekten, die wie ein Objektiv aussehen sollen, sondern mit einem Linsenschnitt: einem Doppel-Gauß 50 mm f/2 mit 10 Glasflächen, Gläsern mit Dispersion und einer Vergütung – und für kurze und lange Brennweiten mit einem Weitwinkel (Nakamura, ±38°) und einem Tele (250 mm), je nach Brennweite von selbst. Aus diesem Schnitt kommen die Tiefenschärfe mit Bokeh in der Form der Blende, das Katzenauge zum Bildrand, die hellen Ränder hinter und der weiche Kern vor dem Fokus, die Farbsäume, die Fokusatmung, die Verzeichnung, der Randabfall, die 45 Geisterbilder der Sonne und ihr Stern. Wer die Blende schließt, sieht, was ein Kameramann sieht: Die Schärfe wird tiefer, das Katzenauge verschwindet, die Reflexe werden kleiner, schärfer und eckig, um die Sonne wächst ein Stern mit so vielen Strahlen, wie die Blende gerade Kanten hat (bei ungerader Zahl doppelt so viele).

Seit LituyaBay1958, Werkbuch Welle W9a „Das Objektiv“ (07.11.2026; Runde 2: die beiden anderen Schnitte, Lichter vereinzelt, Tabellen im Hintergrund).

Der Kern braucht nur Java 21. Der Ordner `gl/` rechnet mit JOGL (GL 4.3, ein Compute-Shader, ein indirekter Aufruf). Das Paket kennt keine Szene: Es bekommt ein HDR-Bild mit Tiefe und gibt das Bild auf dem Sensor zurück, in denselben Einheiten. Zum Herauskopieren reicht `src/com/dan/lens/`.

## Physik (mit Quellen; [G] = gestaltet)

| Was | Wie | Quelle |
|---|---|---|
| Linsenschnitt | Doppel-Gauß f/2, 22° halbes Feld, auf 50 mm gebracht; 11 Flächen (10 Glas, die Blende), Brechzahlen aus dem Patent; Gläser mit diesen Brechzahlen aus dem Schott-Katalog (N-BAF10, SF15, F5, N-SSK5, N-LAF3) für die Abbe-Zahlen [G]. Gerechnet: f = 50,33 mm, f/2,03 | W. Tronnier, US 2 673 491; W. J. Smith, *Modern Lens Design*; derselbe Schnitt in pbrt |
| Weitwinkel, Tele | Weitwinkel nach Nakamura (±38°, f = 15,48 mm, f/1,89, negativer Meniskus vorn, Tonne −6,1 % bei 38°); Tele 250 mm (verkitteter Achromat vorn, negativer hinten, f = 249,9 mm, f/5,43, Televerhältnis 0,83). Gläser nach n_d aus dem Katalog (N-LAF34, F2, N-LAK8, SF6, F3, N-SK14), wo der Schnitt nur n_d nennt und es nicht eindeutig ist, mit angenommener Abbe-Zahl [G]. Gewählt nach der Brennweite: unter 35 mm Weitwinkel, ab 130 mm Tele (`Lens.design` legt fest) | W. J. Smith, *Modern Lens Design*; dieselben Schnitte in pbrt („wide.22mm“, „telephoto.250mm“; der Weitwinkel rechnet 15,5 mm [zu prüfen]) |
| Dispersion | n(λ) = A + B/λ² mit n_d und n_F − n_C = (n_d − 1)/V_d genau | Cauchy |
| Paraxial | y-nu-Verfahren; Spiegelung als Brechung mit n' = −n (Abstände und Brechzahlen danach negativ), dieselben Formeln für Hin- und Rückweg | wie in Programmen der Optikrechnung |
| Echte Strahlen | Snellius in Vektorform an Kugelflächen, Fassungen als Halbmesser; Hauptstrahl durch die Mitte der Blende (Sekantenverfahren) | – |
| Tiefenschärfe | dünne Linse: c = D · f · (d − s) / (d · (s − f)), D = f/N, mit Vorzeichen (hinter dem Fokus +) | – |
| Fokussieren | das ganze Objektiv fährt vom Sensor weg; daraus die **Fokusatmung**: auf 1 m wird der Bildwinkel 4,8 % enger | – |
| Katzenauge | jede Fassung als Kreis in der Eintrittspupille: Halbmesser a_k / (\|A_k\| r_EP), Versatz (B_0/A_0 − B_k/A_k)/r_EP je rad; die engsten nach innen und außen schneiden die Pupille. Gegen echte Strahlen: bei 10° 0,721 gegen 0,738, bei 18° 0,432 gegen 0,438 | Hullin u. a. 2011 (Matrixform) |
| Helligkeit im Kreis | Querabweichung TA(ρ) echter Randstrahlen über die ganze Pupille; Landepunkte L(ρ) = αρ + TA(ρ), α = −c/2; für 8 Unschärfen je Seite als Verteilung der Landepunkte (16 Ringe). Der Doppel-Gauß hat eine unterkorrigierte Zone (−0,011 mm bei 70 %) und einen überkorrigierten Rand (+0,039 mm): hinter dem Fokus helle Ränder, davor ein weicher Kern. Gegen ein Strahlenbündel aus einem Punkt: Anteil im äußeren Fünftel 0,146 gegen 0,142 (vor) und 0,581 gegen 0,622 (hinter) | H. H. Nasse, *Depth of field and bokeh* (Zeiss 2010) zur Deutung |
| Farbfehler | Längsfarbfehler aus der Schnittweite bei 610, 550 und 465 nm (Blau −0,14 mm, Rot +0,06 mm): je Farbkanal ein eigener Kreis (Säume im Bokeh); Farbquerfehler aus echten Hauptstrahlen je Wellenlänge | – |
| Verzeichnung | echte Hauptstrahlen gegen f · tan θ: −1,1 % bei 20°; Tonne, gegen leere Ecken ein Zoom um denselben Anteil [G] | – |
| Randabfall | Anteil der Pupille durch alle Fassungen (echte Strahlen) · cos⁴ θ. Bildkreis: die Fassungen so vergrößert, dass der Schnitt das Bild deckt (24 mm hoch, 16 : 9) [G] | – |
| Vergütung | eine Schicht: r = (r01 + r12 e^(−2iδ)) / (1 + r01 r12 e^(−2iδ)), δ = 2π n d / λ; MgF₂ λ/4 bei 550 nm auf BK7: 1,28 % (unvergütet 4,22 %), Rot und Blau stärker: purpurne Reflexe. Mehrschicht heute als flache Kurve 0,3 % [G] | Smakula 1935; Airy |
| Geisterbilder | je Doppelspiegelung j &gt; i ein Bild der Blende: auf dem Sensor y_S = C·y + D·θ, an der Blende y_B = A·y + B·θ → Vergrößerung C/A, Lage (D − C·B/A)·θ; Fluss E · π r_ein² · R_i R_j Π(1 − R) über die Fläche π ρ²; je Farbkanal eigene Lage, Größe, Stärke; mindestens so groß wie das Bild der Sonne darin. Zusammen 0,04 % des Lichts | Hullin u. a. 2011; Lee und Eisemann 2013 |
| Stern | Fraunhofer an den geraden Kanten: je Kante ein Strahl senkrecht zu ihr, Energie je Länge L λ f / (4π² x² A) (gegen die FFT der Blende: 103 %); bei gerader Lamellenzahl liegen zwei Kanten auf einem Strahl (n Strahlen), bei ungerader nicht (2n). Gefaltet mit der Sonnenscheibe (Profil = Sehne). Mit gerundeten Lamellen nur der gerade Anteil [G] | Born und Wolf, *Principles of Optics* |
| Schleierlicht | 0,8 % allen Lichts über das Bild, Kern ∝ r^−2,75 aus 7 Stufen (13-Punkte-Filter, Zeltfilter) [G; Runde 2: vorher 1,5 % und r^−2,5, die Sonne stand milchig] | Jimenez 2014 (Filter) |
| Schärfezieher | in Dioptrien, kritisch gedämpft (x(t) = x_Z + (c1 + c2 t) e^(−ωt), ω = 5,8 / Zeit), höchstens 4 dpt/s [G]; Autofokus auf das 65. Perzentil von 5 × 5 Messpunkten in der Bildmitte [G] | – |

## Was es kann

- **Bokeh**: Sammeln in halber Auflösung in Ringen (so viele, wie der größte Kreis ringsum braucht: Abstand der Punkte rund 1,6 halbe Bildpunkte, höchstens 3 bis 6 Ringe nach der Stufe; seit W9c), ob ein Punkt den Empfänger trifft, entscheidet die Pupille selbst (q = −2(p − s)/c: Form der Blende, Katzenauge, je Farbkanal). Hintergrund läuft nicht über schärferen Vordergrund; unscharfer Vordergrund wird eigens gesammelt, mit seiner Deckung darübergelegt, auch über sich selbst (sein Rand wird von beiden Seiten weich). Helle unscharfe Punkte (über dem Doppelten von Weiß, und viermal heller als ihr Umkreis: vereinzelte Lichter, nicht der helle Himmel) werden **gestreut**: Ein Compute-Shader sammelt sie (höchstens 4096), ein indirekter Aufruf zeichnet jedes als Kreis der Blende mit seiner Energie. Helle Flächen sammelt das Bokeh selbst, das ist dort richtig und kostet nicht je Licht einen großen Kreis. Kleine Kreise (unter 10 Bildpunkten) in voller Auflösung.
- **Sonne**: Geisterbilder und Stern mit der Sichtbarkeit aus der Tiefe (13 Punkte auf der Scheibe) und dem Anteil durch Wolken.
- **Optik als Zahlen** (`Lens.optics`): Brennweite, T-Blende, Pupille, Schärfentiefe (`dofM`, `hyperfocalM`), Atmung, Tabellen über den Bildradius, Geisterbilder, Strahlen des Sterns.
- **Drei Schnitte**: Weitwinkel, Doppel-Gauß, Tele, je auf die Brennweite gebracht; was ein Schnitt über sein gerechnetes Feld hinaus decken muss, überträgt er gestaucht (`fieldScale`) [G].
- **Zeiten der Teile** (`timing = true`): Zeitstempel auf der Grafikkarte, zwei Bilder später gelesen, ohne zu warten; `timingLine()` → „Sammeln 3,1 · Schärfe 0,4 · …“.
- **Ohne Tiefenschärfe** (`depthOfField = false`): für eine Kino-Stufe, in der die Tiefenschärfe aus der Pupille kommt (`com.dan.shutter`); Reflexe, Stern, Schleier, Verzeichnung, Farbsäume und Randabfall bleiben.
- **Tabellen im Hintergrund**: Ändert sich Brennweite oder Blende, rechnet ein eigener Faden die Tabellen (echte Strahlen, rund 25 ms) neu; bis dahin gelten die alten (`background = false` für Prüfungen und Standbilder).

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Glass`, `Coating`, `Surface` | Glas mit Dispersion, Vergütung (Airy), eine Fläche |
| `LensSystem` | der Schnitt: paraxial (Brennweite, Pupillen, Fokus, Atmung, Fassungen, Geisterbilder) und mit echten Strahlen (Hauptstrahl, Querabweichung, Vignettierung); `doubleGauss50`, `wideAngle`, `telephoto250`, `scaled`, `covering`, `coated` |
| `Aperture` | Lamellen, Rundung, Drehung; Form, Fläche, Kanten, Richtungen der Strahlen, Maske |
| `Lens` | die Einstellungen (Brennweite, Blende, Fokus, Schnitt, Schalter, Stärken, Stufe) und `optics(fokus)` → `Optics` |
| `FocusPuller` | der Schärfezieher auf dem Prozessor (der auf der Grafikkarte rechnet dasselbe) |
| `Spectrum` | CIE 1931 nach Wyman, Sloan und Shirley 2013, XYZ → sRGB, die drei Wellenlängen |
| `gl/LensGl` | die Grafik: `init`, `apply(gl, View)` → Textur, `focusM()`, `optics()`, `dispose` |
| `demo/LensDemo` | Kai mit Lichterkette, Kasten, Pfahl und Sonne; Fenster mit Reglern oder `--bild datei.png` (vier Einstellungen) |
| `demo/LensCheck` | 29 Prüfungen ohne Grafikkarte |

## Einbau

```java
Lens lens = new Lens();                 // 50 mm, f/2,8, Autofokus, 8 Lamellen
LensGl lensGl = new LensGl(lens);
lensGl.init(gl);
// je Bild, nach der Kantenglättung, vor Partikeln mit eigener Unschärfe:
LensGl.View v = new LensGl.View();
v.colorTexture = hdr; v.depthTexture = depth; v.width = w; v.height = h;
v.reversedZ = true; v.near = 0.3; v.far = 400_000;
v.viewProj = viewProj;                  // für die Lage der Sonne
v.sunDir = sunDir; v.sunIrradiance = sunRgb; v.sunVisible = wolken;
v.exposure = belichtung; v.dt = dt; v.snap = schnitt;
int bild = lensGl.apply(gl, v);         // weiter wie bisher (Belichtung, Filmkurve)
```

Die Brennweite gilt für 24 mm Bildhöhe (`sensorHeightMm`). Eine App mit eigener Unschärfe (Regen, Linsentropfen) nimmt `optics().pupilMm()` und `focusM()`.

## Prüfung

`java -cp build/classes:src com.dan.lens.demo.LensCheck` – Glas und Vergütung gegen die Formeln, der Doppel-Gauß gegen Smith, paraxial gegen echte Strahlen, Katzenauge gegen echte Strahlen, Helligkeit im Kreis gegen ein Strahlenbündel, Stern gegen die FFT der Blende, Schärfentiefe, Schärfezieher, Weitwinkel und Tele, Wahl des Schnitts, Energie der Geisterbilder. Endet mit „Alles in Ordnung.“

## Grenzen

- Drei Schnitte, kein Zoom: zwischen ihnen springt der Charakter (Bokeh, Verzeichnung, Reflexe) an 35 und 130 mm.
- Anamorphotische Optik nur als Stauchung (`squeeze`): ovales Bokeh; die waagerechten Streifen und die ovalen Reflexe folgen.
- Reflexe nur, wenn die Sonne im Bild steht (die Tiefe sagt, ob sie frei ist); eine Sonne knapp außerhalb wirft bei echten Objektiven auch Reflexe.
- Die Geisterbilder sind Bilder der ganzen Blende; dass andere Fassungen sie beschneiden, fehlt.
- Bewegungsunschärfe und Belichtung über den Verschluss kommen mit W9c (Kino-Stufe).
