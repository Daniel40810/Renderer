@echo off
rem P13a, Laeufe A1 und A2 hintereinander (Werkbuch HalligHooge Teil A, 12.14, Datei 85; F145 A).
rem A1: ERA5 Dezember 2013 laden und aufbereiten (rund 10 min, dazu die Warteschlange des Climate Data Store);
rem A2: die Sturmflut Xaver rechnen, pruefen, Bilder (rund 1,5 bis 2 h). A2 startet nur, wenn A1 den Zeitraum 201312
rem abgelegt hat. Zusammen rund 1 3/4 bis 2 1/4 Stunden.
setlocal EnableExtensions
call "%~dp0lauf_P13a_A1.cmd"
if not exist "D:\Daten\HalligHooge\gebiet\antrieb\201312\era5.txt" goto :ohne
if not exist "D:\Daten\HalligHooge\gebiet\antrieb\201312\seegang.txt" goto :ohne
call "%~dp0lauf_P13a_A2.cmd"
goto :eof
:ohne
echo A1 hat den Zeitraum 201312 nicht abgelegt - A2 startet nicht. Siehe Status_Phase13aA1_.txt
timeout /t 60 >nul
exit /b 1
