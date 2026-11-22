@echo off
rem P13a, Lauf A1 (Werkbuch HalligHooge Teil A, 12.14, Datei 85): ERA5 Dezember 2013 fuer die Sturmflut Xaver.
rem 1 Renderer bauen; 2 ERA5 aus dem Climate Data Store laden (Wind und Druck, Schub, Seegang; je rund 45 MB, die
rem Wartezeit im Climate Data Store bestimmt die Dauer); 3 Aufbereitung, Schritte antrieb und seegang fuer den Zeitraum
rem 201312 (nach D:\Daten\HalligHooge\gebiet\antrieb\201312); 4 XaverCheck, Teil antrieb (Pruefstein A1).
rem Ausgaben: HalligHooge\docs\status\Status_Phase13aA1_*.txt; Attest Renderer\docs\status\P13a_Xaver_Antrieb.md.
rem Erwartete Dauer: rund 10 Minuten, dazu die Warteschlange des Climate Data Store (meist wenige Minuten).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "PY=python"
where python >nul 2>nul || set "PY=py"

set "UE=%ST%\Status_Phase13aA1_.txt"
set "L1=%ST%\Status_Phase13aA1_1.txt"
set "L2=%ST%\Status_Phase13aA1_2.txt"
set "L3=%ST%\Status_Phase13aA1_3.txt"
set "L4=%ST%\Status_Phase13aA1_4.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13a A1, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13aA1_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13aA1_2: ERA5 Dezember 2013 laden (Kartenwerkzeug)
>"%L3%" echo Status_Phase13aA1_3: Aufbereitung antrieb, seegang fuer den Zeitraum 201312
>"%L4%" echo Status_Phase13aA1_4: XaverCheck, Teil antrieb

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=2 ERA5 Dezember 2013 laden (era5_cds, era5_cds_schub, era5_cds_seegang)"
set "L=%L2%"
pushd "%HH%"
set "C=%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen era5_cds,era5_cds_schub,era5_cds_seegang --monate 201312 --ziel D:\Daten\HalligHooge"
call :lauf
popd
if not "%E%"=="0" goto :abbruch
dir D:\Daten\HalligHooge\wetter\era5_cds D:\Daten\HalligHooge\wetter\era5_cds_schub D:\Daten\HalligHooge\wetter\era5_cds_seegang >>"%L2%" 2>&1

set "N=3 Aufbereitung antrieb, seegang (Zeitraum 201312)"
set "L=%L3%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte antrieb,seegang --antrieb.zeitraum 201312"
call :lauf
if not "%E%"=="0" goto :abbruch
dir /s D:\Daten\HalligHooge\gebiet\antrieb >>"%L3%" 2>&1

set "N=4 XaverCheck, Teil antrieb (Pruefstein A1)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.XaverCheck --teil antrieb --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
exit /b 1

:lauf
if not exist "%~dp0STOPP.txt" goto :lauf2
echo [%DATE% %TIME%] STOPP  %N%
>>"%UE%" echo [%DATE% %TIME%] STOPP  %N%  - uebersprungen
set "E=99"
goto :eof
:lauf2
echo [%DATE% %TIME%] Start  %N%
>>"%UE%" echo [%DATE% %TIME%] Start  %N%
>>"%L%" echo.
>>"%L%" echo ^>%C%
%C% >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
>>"%UE%" echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
goto :eof
