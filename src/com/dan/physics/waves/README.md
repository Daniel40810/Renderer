# com.dan.physics.waves

Der Seegang als Bilanz der spektralen Wirkungsdichte (Werkbuch HalligHooge Teil A, 7.3 und 12.9). Stand P8b (Waves
0.2, WavesModul 0.1): die Referenz in doppelter Genauigkeit und derselbe Seegang auf der Grafikkarte in float, mit
Strömung und allen Quellen; seit P8b das Modul `waves` am Bus (einseitig: liest Wasser und Wind, schreibt `waves.*`),
der Rand aus ERA5 und das Randspektrum je Randzelle. **Seit P8c** liest hydro `waves.force` im Intervall (Rückwirkung; der Takt rechnet den Seegang dafür ein Intervall voraus, `WavesModul.beginn` liest das Wasser erst, wenn es liegt), und nach dem Schritt der Quellen kappt `Waves.kappung` (Referenz und Karte gleich) das Spektrum einer Zelle auf H_rms = √(8 m0) ≤ γ · d (γ aus Battjes–Janssen, 0,73); das Abgezogene bucht die Bilanz unter „Brechen“ und zählt zu `diss` (W7 aus P8b).

| Datei | Inhalt |
|---|---|
| `Waves.java` | Referenz: Ausbreitung (x, y, θ, σ; Flussform, aufwind; in θ und σ zweite Ordnung, begrenzt nach van Leer), Quellen (Wind, Schaumkronen, Vierwellen DIA, Bodenreibung, Brechen), Bilanz der Wirkung, Kenngrößen (`hs`, `tp`, `richtung`, `ubot`, `mss`, `kraft`) |
| `WavesModul.java` | Modul `waves` (P8b): eigene Gitter in UTM (G2w über G2 in 500 m, G3w über G3 in 100 m), das feinere einseitig aus dem gröberen (Spektrum an den Geisterzellen, linear in der Zeit); Kopplung 1 200 s, Schritte zu 60 s; liest `water.h`, `.u`, `.v`, `air.u10`, `.v10` und mit Schub `air.taux`, `.tauy` (u* = √(\|τ\| / ρ_L)); schreibt `waves.hs`, `.tp`, `.dir` (woher, von Gitternord), `.ubot`, `.mss`, `.diss`, `.force`. `Rechner`: `Prozessor` oder `gpu.WavesRechner` |
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
