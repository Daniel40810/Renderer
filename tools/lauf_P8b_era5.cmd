@echo off
rem P8b (Werkbuch HalligHooge Teil A, 12.9, F74 A): Seegang aus ERA5 laden (Climate Data Store, Konto in %USERPROFILE%\.cdsapirc).
rem Ziel D:\Daten\HalligHooge\wetter\era5_cds_seegang\202610.grib; Ausgabe HalligHooge\docs\status\Status_Phase8b_era5.txt
setlocal EnableExtensions
chcp 65001 >nul
set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "S=%HH%\docs\status\Status_Phase8b_era5.txt"
set "PY=python"
where python >nul 2>nul || set "PY=py"
set PYTHONIOENCODING=utf-8
cd /d "%HH%"
>"%S%" echo Status_Phase8b_era5: gestartet %DATE% %TIME%
echo [%TIME%] ERA5 Seegang laden (kann im Climate Data Store einige Minuten warten) ...
%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen era5_cds_seegang --monate 202610 --ziel D:\Daten\HalligHooge >>"%S%" 2>&1
>>"%S%" echo Exit %ERRORLEVEL%  FERTIG %DATE% %TIME%
echo [%TIME%] Fertig. Dieses Fenster schliesst sich in 20 s.
timeout /t 20 >nul
