# P2 – GpuCheck

Attest für `com.dan.core.gpu`, Version 0.1. Stand 14.11.2026 18:18, OpenJDK 64-Bit Server VM 21.0.11, 16 Kerne.

40 Prüfsteine, 0 Verstöße. Die Grenzwerte standen vor der Messung fest; [G] heißt gesetzt.

| Teil | Größe | Wert | Grenze | Urteil |
|---|---|---|---|---|
| 1 Gerät | Gerät | AMD Radeon(TM) Graphics, Vulkan 1.4.315, Treiber AMD proprietary driver 26.10.1 (AMD proprietary shader compiler) |  | – |
| 1 Gerät | Vulkan 1.3 | 1.4.315 | ≥ 1.3 | ok |
| 1 Gerät | Pflicht: Synchronisation 2, Zeitleisten-Semaphoren, Deskriptor-Indizes | ja, ja, ja | alle ja | ok |
| 1 Gerät | Warteschlangen | Grafik (Familie 0), Rechnen (Familie 1) neben dem Bild, Übertragung (Familie 2) |  | – |
| 1 Gerät | Weitere Fähigkeiten | Strahlabfrage ja, float16 ja, Untergruppen-Arithmetik ja (Größe 32–64), Zeitstempel ja, Puffer-Adressen ja, dynamisches Rendern ja, Speicherbudget ja |  | – |
| 1 Gerät | Speicher | Heap 0: 32 GB gerätelokal, Budget 30 GB; Heap 1: 16 GB geteilt, vom Prozessor sichtbar, Budget 15 GB; Heap 2: 256,0 MB gerätelokal, vom Prozessor sichtbar, Budget 243,2 MB; größte Zuteilung 2,0 GB, größter Speicherpuffer 2,0 GB |  | – |
| 2 Speicher | Puffer KARTE: 1 Mio. Werte hin und zurück, ein Stück mit Versatz, mit Nullen gefüllt | 0 falsche Werte | bitgleich | ok |
| 2 Speicher | Puffer GETEILT: 1 Mio. Werte hin und zurück, ein Stück mit Versatz, mit Nullen gefüllt | 0 falsche Werte | bitgleich | ok |
| 2 Speicher | Speicherkonto: Zuteilung über die Grenze des Moduls | Speicherkonto GpuCheck klein: b (0,6 MB) geht über die Grenze von 1,0 MB (belegt 0,6 MB) | abgelehnt, Konto unverändert | ok |
| 2 Speicher | Speicherkonto nach der Freigabe | GpuCheck klein 0,0 MB von 1,0 MB, Spitze 0,6 MB | 0 belegt | ok |
| 2 Speicher | Puffer über der größten Zuteilung des Geräts | Puffer riesig mit 2,0 GB ist größer als eine Zuteilung dieses Geräts (2,0 GB); in Stücken führen | abgelehnt mit Hinweis | ok |
| 3 Shader und Kernel | Vorgebautes SPIR-V (Ant-Ziel spirv) gegen zur Laufzeit übersetzt, mit #include | 2084 und 2084 Byte | bytegleich | ok |
| 3 Shader und Kernel | GLSL nach SPIR-V mit #include "/com/dan/…" aus dem Klassenpfad | 2084 Byte, 127 ms | gültiger Kopf | ok |
| 3 Shader und Kernel | Vorgebautes SPIR-V gilt nur mit unveränderten Quellen (Shader und Eingebundenes) | unverändert ja, Eingebundenes geändert nein, Quelle fehlt nein, ohne Liste nein | ja, nein, nein, nein | ok |
| 3 Shader und Kernel | Fehler im Shader: Meldung nennt Datei und Stelle | kaputt.comp lässt sich nicht übersetzen: kaputt.comp:3: error: 'nichtda' : undeclared identifier | Datei und Name | ok |
| 3 Shader und Kernel | Shader des Bestands (GLSL 430, lose Uniforms) nach Vulkan: Push-Konstanten in derselben Reihenfolge | 3 Werte, 12 Byte | 3 Werte, 12 Byte | ok |
| 3 Shader und Kernel | Kernel saxpy, 1 000 003 Werte, gegen Java: größte Abweichung in float-Schritten | 0,00 | ≤ 0,00 | ok |
| 3 Shader und Kernel | Werte hinter dem Ende (die letzte Gruppe ist nicht voll) bleiben unberührt | 0 verändert | 0 | ok |
| 3 Shader und Kernel | Tabelle in float16, in Java gepackt, im Kernel über unpackHalf2x16 gelesen (geht auf jedem Gerät) | 0 von 4096 Werten anders | 0 | ok |
| 3 Shader und Kernel | Dieselbe Tabelle als float16_t im Puffer gelesen | 0 von 4096 Werten anders | 0 | ok |
| 3 Shader und Kernel | Kernel packt float nach float16: Abweichung in Schritten von float16 | 0,994 | ≤ 1,00 | ok |
| 3 Shader und Kernel | Rundung beim Packen | 2730 von 4096 Werten wie in Java (zum nächsten); die Karte rundet anders – Tabellen packt der Prozessor |  | – |
| 3 Shader und Kernel | Kernel mit Untergruppen-Summe: tatsächliche Größe der Untergruppe | 64 bis 64 | 1 bis 64 | ok |
| 3 Shader und Kernel | Feldtafel: Kernel wählt Felder über Nummern; ein Platz geräumt und neu belegt | 0 falsche Werte, Nummern 0, 1, 2, 1 | 0 falsch | ok |
| 3 Shader und Kernel | Kernel schreibt ein Speicherbild (R32F, 200 × 150); der Ablauf wechselt die Anordnung zum Schreiben und zum Lesen | 0 falsche Bildpunkte | 0 | ok |
| 4 Ablauf und Sperren | Sechs Durchgänge über fünf Puffer: Sperren nur, wo auf Schreiben gelesen oder auf Lesen geschrieben wird | 4 Sperrbefehle über 5 Puffer, 0 falsche Werte | 4 Sperrbefehle, 5 Puffer, 0 falsch | ok |
| 4 Ablauf und Sperren | Gegenprobe: zwei Durchgänge ohne gemeldete Zugriffe (also ohne Sperre) – die Validierung findet es | 2 Fehler gemeldet | ≥ 1 | ok |
| 4 Ablauf und Sperren | Ein Schritt eines Ausgleichs auf 200 × 150 Zellen gegen Java (Kernel gleich): float-Schritte | 0,00 | ≤ 0,00 | ok |
| 4 Ablauf und Sperren | 100 Schritte im Wechsel zweier Puffer in einem Befehlspuffer | größte Abweichung 0 float-Schritte, 0 NaN, 99 Sperrbefehle | kein NaN; je Schritt eine Sperre über beide Puffer | ok |
| 4 Ablauf und Sperren | 100 Schritte gegen Java: float-Schritte (nur +, −, · mit „precise“) | 0,00 | ≤ 0,00 | ok |
| 5 Zeitleiste und Fäden | Zeitleiste vor der ersten Arbeit | Stand 0, Warten auf 1 läuft nach 2 ms ab | Stand 0, Zeit abgelaufen | ok |
| 5 Zeitleiste und Fäden | Das Bild, zuerst abgeschickt, wartet auf der Karte auf die Physik | wartet | wartet | ok |
| 5 Zeitleiste und Fäden | Physik rechnet, das Bild wartet über die Zeitleiste und liest den fertigen Stand (zwei Warteschlangen) | 0 falsche Werte, Zeitleiste der Physik bei 1 | 0 falsch, Stand 1 | ok |
| 5 Zeitleiste und Fäden | Eine Zeitleiste wird geschlossen, die Warteschlangen arbeiten weiter | Die Zeitleiste ist geschlossen | Arbeit läuft; Zeitleiste meldet sich geschlossen | ok |
| 5 Zeitleiste und Fäden | Zwei Fäden: 200 Arbeiten der Physik ohne Warten, daneben 50 Übertragungen des Bilds | 0 falsche Werte, Zeitleiste bei 201 | 0 falsch, Stand 201 | ok |
| 6 Zeiten | Zeitstempel je Abschnitt: drei Abschnitte (je zwei Durchgänge) in einem gemessenen Ganzen | hin 0,52, her 0,28, noch 0,31, gesamt 1,15 ms; Wand 1,59 ms | Teile ≤ Ganzes ≤ Wand | ok |
| 6 Zeiten | Ring von vier Runden: sechs Runden ohne Lesen | 2 verloren, 4 gelesen | 2 verloren, 4 gelesen | ok |
| 7 Felder auf der Karte | Feld hoch und herunter: float bitgleich, double über float; je Feld eine Seite auf der Karte | 0 falsche Werte | 0 | ok |
| 7 Felder auf der Karte | Ein Modul rechnet water.eta auf der Karte (10 Kopplungsschritte); das Bild liest am Bus zwischen zwei Ständen | 0 falsche Werte, 4 Felder auf der Karte, Zeitleiste bei 10 | 0 falsch, 4 Felder, Stand 10 | ok |
| 8 Durchsatz | Ausgleich mit fünf Nachbarn, 1024² Zellen, 100 Schritte in einem Befehlspuffer | KARTE: 0,13 ns je Zellschritt (74,98·10⁸ Zellschritte/s); GETEILT: 0,14 ns je Zellschritt (71,48·10⁸ Zellschritte/s) |  | – |
| 9 Zeichnen (seit P6a) | Zeichner | 78 Dreiecke (72 im Netz über das Bild, 6 darüber), 96 × 80, Ziele R32F und D32F, Tiefe umgekehrt (größer gewinnt, gelöscht mit 0) |  | – |
| 9 Zeichnen (seit P6a) | Prüfstein 1 (P6a): Abdeckung gegen eine Rasterung in Java (Mitte des Bildpunkts, umgekehrte Tiefe), Bildpunkte weiter als 1/64 von jeder Kante | 0 von 7610 anders; 70 an Kanten nicht verglichen | gleich | ok |
| 9 Zeichnen (seit P6a) | Prüfstein 1 (P6a): Tiefe gegen die Rasterung in Java (affin im Bild) | größte Abweichung 2,13e-07 bei (80, 38), Dreieck 76, Gefälle der Tiefe 0,131 je Bildpunkt | ≤ 1e-6 [G] | ok |
| 9 Zeichnen (seit P6a) | Netz ohne Lücken: jeder Bildpunkt von einem Dreieck bedeckt, auch auf gemeinsamen Kanten | 0 Bildpunkte ohne Dreieck | 0 | ok |
| 9 Zeichnen (seit P6a) | Bild hochgeladen (R32F), als Textur über einen Abtaster gelesen (der Ablauf wechselt zur Anordnung für Texturen) | 0 von 851 anders | 0 | ok |
| 10 Aufräumen | Speicherkonto nach allen Teilen | GpuCheck 0,0 MB von 512,0 MB, Spitze 8,0 MB | 0 belegt | ok |
| 10 Aufräumen | Validierungsschicht 1.4.363 mit Prüfung der Synchronisation, über alle Teile | 0 Fehler | 0 Fehler | ok |

Alles in Ordnung.
