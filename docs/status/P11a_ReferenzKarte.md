# P11a – ReferenceKarteCheck

Attest für `com.dan.render.reference.gpu`, Version 0.1. Stand 19.11.2026 00:28, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

3 Prüfsteine, 2 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
|  | Gerät | AMD Radeon(TM) Graphics (AMD proprietary driver 26.10.1 (AMD proprietary shader compiler)), Validierung aus |  | – |
| 4 Spektrum und Tafeln auf der Karte (Prüfstein 4) | Tafeln des Spektrums auf der Karte gegen Java an 4096 Wellenlängen (größte Abweichung durch größten Wert) | x̄ 6,0e-07; ȳ 6,7e-07; z̄ 6,7e-07; Sonne 4,9e-06; reines Wasser 1,2e-06; Ozon 5,1e-07; Rayleigh (Formel) 2,6e-07; Reflexion zu RGB (0,3; 0,5; 0,7) 1,0e-06;  | ≤ 1 · 10⁻⁵ [G] | ok |
| 4 Spektrum und Tafeln auf der Karte (Prüfstein 4) | Fournier-Forand auf der Karte: Mittel von 1/p über gezogene Richtungen gegen 4π (Ziehen in 1 − cos θ, F und 1 − F; Dichte je Stufe), 15990784 Richtungen | 12,55032 gegen 12,56637, Abweichung -1,3e-03, σ relativ 1,0e-03; Mittel von 1 − cos θ 0,07212 (Java: b_b/b der Tafel 0,0190) | ≤ 3 σ, σ ≤ 1 · 10⁻³ relativ [G] | **fehlt** |
| 1 Ofentest auf der Karte (Prüfstein 1) | Ablauf | IllegalStateException: vkQueueSubmit2: VK_ERROR_DEVICE_LOST | ohne Fehler | **fehlt** |

2 Verstöße.
