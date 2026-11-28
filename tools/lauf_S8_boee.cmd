@echo off
rem Seegang S8 (Seegang Werkbuch, Datei 104, SF156 A): Boee fg10 aus ERA5 laden (Climate Data Store, Konto in
rem %USERPROFILE%\.cdsapirc). Oktober 2026 (Sturmflut, ruhiger Tag) und Dezember 2013 (Xaver).
rem Ziel D:\Daten\HalligHooge\wetter\era5_cds_boee\202610.grib und 201312.grib (unter 1 MB je Monat).
rem Ausgabe HalligHooge\docs\status\Status_S8_boee.txt. Erwartete Dauer: wenige Minuten, dazu die Warteschlange des
rem Climate Data Store. Gestartet von Claude per Explorer.
setlocal EnableExtensions
chcp 65001 >nul
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "S=%HH%\docs\status\Status_S8_boee.txt"
set "PY=python"
where python >nul 2>nul || set "PY=py"
set PYTHONIOENCODING=utf-8
cd /d "%HH%"
>"%S%" echo Status_S8_boee: gestartet %DATE% %TIME%
echo [%TIME%] ERA5 Boee fg10 laden (kann im Climate Data Store einige Minuten warten) ...
%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen era5_cds_boee --monate 202610,201312 --ziel D:\Daten\HalligHooge >>"%S%" 2>&1
>>"%S%" echo Exit %ERRORLEVEL%
dir D:\Daten\HalligHooge\wetter\era5_cds_boee >>"%S%" 2>&1
>>"%S%" echo FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
