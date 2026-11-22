# com.dan.physics.aeolian

Flugsand (Werkbuch HalligHooge Teil A, 7.6 und 12.11). Stand P10c Teil 1 (Aeolian 0.1, AeolianModul 0.1): die Rechnung in
doppelter Genauigkeit auf dem Prozessor und das Modul am Bus. Eine Fassung auf der Grafikkarte gibt es nicht (ein
Kopplungsintervall für 280 000 Zellen kostet rund 20 ms).

| Datei | Inhalt |
|---|---|
| `Aeolian.java` | Schwelle nach Bagnold, feucht nach Belly (1964); gesättigter Fluss nach Kawamura (1951), zum Vergleich Lettau und Lettau; was eine Zelle weitergibt (Wasser, loser Sand, zu nasser Sand, fester Boden); Bilanz nach Exner über die Kanten, Durchlauf von Luv nach Lee; Massenbilanz |
| `AeolianModul.java` | Modul `aeolian` am Bus, getaktet mit 300 s: liest `surf.wet`, `water.h`, `water.wetAge` (Zellen) und `air.u10`, `air.v10` (Säulen wie atmos); schreibt `bed.dz.aeolian` und `aeol.q`; Boden aus dem Kornfeld des Gebiets |
| `demo/AeolianCheck.java` | Prüfsteine 1–9 aus 12.11 (Datei 41); Attest `docs/status/P10c_Aeolian.md` |

Der Nachlauf aus einem Archiv steht in `com.dan.lauf` (`Nachlauf` 0.2, zusammen mit atmos), die Prüfung im Gebiet in
`com.dan.lauf.demo.AeolianLaufCheck`.

```java
Aeolian ae = new Aeolian(nx, ny, 20, 20);
// d50, poros, schlick, fest je Zelle setzen; je Schritt u, v (Wind in 10 m), wet (surf.wet), h (Wassertiefe)
ae.anfang();
ae.schritt(300);
// ae.dz (bed.dz.aeolian), ae.qx, ae.qy (aeol.q), ae.abtrag, ae.auftrag, ae.ausflug, ae.rest()
```

```
java -cp dist\Renderer.jar com.dan.physics.aeolian.demo.AeolianCheck                 alle Teile, mit Attest
java -cp dist\Renderer.jar com.dan.physics.aeolian.demo.AeolianCheck --teil 7        ein Teil, ohne Attest
```

**Formeln** (an der Quelle gelesen am 18.11.2026):

- Schwelle trocken: u*t = A √((ρs − ρ) / ρ · g d), A = 0,1.
- Schwelle feucht: mal max(1; 1,8 + 0,6 log₁₀ w), w in Prozent des Trockengewichts; w = 100 · Sättigung · θ ρ_w / (ρs (1 − θ)).
  Unter 0,046 % ist der Faktor 1 (dort schneidet Bellys Formel die 1); über `wMax` = 4 % nimmt der Wind nichts auf.
- Fluss: q = C (ρ / g)(u* − u*t)(u* + u*t)², C = 2,78.
- u* = κ U₁₀ / ln(10 m / z0), z0 = 1 mm.

**Was eine Zelle weitergibt:** Wasser nichts; loser Sand max(q_auf; min(q_ein; q_trag)); zu nasser Sand
min(q_ein; q_trag) · exp(−L / 100 m); fester Boden und Schlick min(q_ein; q_trag) · exp(−L / 20 m). q_auf mit der feuchten,
q_trag mit der trockenen Schwelle; L ist der Weg des Winds durch die Zelle.

**Quellen.** Belly, P.-Y. (1964), Sand movement by wind, U.S. Army CERC Technical Memorandum 1; Sherman, D. J. u. a.
(2013), Recalibrating aeolian sand transport models (Formeln von Kawamura und Lettau, Bagnolds A, neu geeichte Beiwerte);
Andreotti, B., Claudin, P., Pouliquen, O. (2010), Geomorphology 123 (Sättigungslänge); AeoLiS, Modellbeschreibung.

**Gesetzt [G].** z0 = 1 mm für alle Zellen; `wMax` = 4 % (Bellys Messbereich); Längen 100 m (nasser Sand) und 20 m
(fester Boden); fest ab der Hälfte festen Bodens oder über 10 % Schlick; mittlere Richtung für den ganzen Schritt, wenn
im Gitter das Vorzeichen einer Windkomponente wechselt; Dichte der Luft 1,25 kg/m³.

**Grenzen.** Der Wind kennt das Relief nicht: Dünen wachsen und wandern nicht. Stundenmittel des Winds, keine Böen. Ein
Korn je Zelle, kein Schill, keine Panzerung; der Vorrat ist unbegrenzt; kein Hangrutsch. A = 0,1 gilt streng für Sand
über 0,2 mm. Kawamuras Formel sagt gegen Feldmessungen rund das Dreifache voraus. Auf dem Gitter hängt die bewegte
Masse von der Richtung ab: Ein runder Fleck gibt schräg zum Gitter 14 % mehr ab als längs (Treppenrand; Prüfstein 7).
