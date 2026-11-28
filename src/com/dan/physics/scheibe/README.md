# com.dan.physics.scheibe – Tropfen auf einer Scheibe

**Seit Seegang S8a (Seegang Werkbuch, Datei 104, SF158 A), Version 0.1.** `Scheibe` ist der Kern von `com.dan.wetglass.Wetglass`
(Lighthouse, seit Brocken im Renderer), Zeile für Zeile in die Schicht physics geholt: Tropfen als Kugelkappen mit 60°
Randwinkel, Haften und Laufen ab `A_CRIT`, Verschmelzen, Spuren, Salz, Überspülung. Gezeichnet wird in Vulkan
(`render.frame.Linse`); die Anwendung führt die Scheibe vor der Kamera (`com.dan.lauf.Linsenglas`).

Änderungen am Kern gehören in beide Kopien.
