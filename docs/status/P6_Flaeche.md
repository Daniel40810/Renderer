# P6a – FlaecheCheck

Attest für `com.dan.core.gpu`, Version 0.1. Stand 14.11.2026 15:12, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

4 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| Fläche im Fenster | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)) |  | – |
| Fläche im Fenster | Fläche aus dem Fenster-Handle des Canvas (JAWT), Swapchain | 800 × 450, Format 43, FIFO | angelegt | ok |
| Fläche im Fenster | Bilder gezeigt | 240 gezeigt, 0 ohne Bild (Neuaufbau), 57,0 Bilder/s (FIFO) | alle bis auf den Neuaufbau | ok |
| Fläche im Fenster | Größe geändert: Swapchain neu gebaut | 1× neu, jetzt 950 × 525 | ≥ 1 | ok |
| Fläche im Fenster | Keine Meldung der Validierung | nicht eingeschaltet | 0 | ok |

Alles in Ordnung.
