@echo off
rem P9b, Lauf C (Werkbuch HalligHooge Teil A, 12.10): Renderer bauen, Feldvergleiche und Bild aus den Archiven, Projekt bauen und starten.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 Aufbereitung sentinel und SedimentLaufCheck --nur 2,3 (Pruefsteine 4 bis 8, 10 bis 12, Status-Bilder);
rem 3 WaterCheck, TerrainCheck, BuehneCheck (ohne Attest); 4 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9bC*.txt (UTF-8), Uebersicht in Status_Phase9bC.txt.
rem Atteste: Renderer\docs\status\P9b_Sediment.md; Bilder P9b_Sediment.png, P9b_Bild.png.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "UE=%ST%\Status_Phase9bC.txt"
set "L1=%ST%\Status_Phase9bC1.txt"
set "L2=%ST%\Status_Phase9bC2.txt"
set "L3=%ST%\Status_Phase9bC3.txt"

>"%UE%" echo Lauf P9b C, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9bC1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase9bC2: SedimentLaufCheck --nur 2,3 (Feldvergleiche und Bild aus den Archiven)
>"%L3%" echo Status_Phase9bC3: WaterCheck, TerrainCheck, BuehneCheck, HalligHooge bauen, Anwendung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Aufbereitung, Schritt sentinel (Schwebstoff aus Sentinel-2 nach gebiet\pruef)"
set "L=%L1%"
set "C=java %U% -Xmx6g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte sentinel"
call :lauf

set "N=2 SedimentLaufCheck, Teile 2 und 3 (Pruefsteine 4 bis 8, 10 bis 12)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %A9% --archiv-ruhig %A9R% --fenster3 %F3% --nur 2,3 --fahne 466600,6043700,500,350,-30 --rippel 468990,6045410,250 --rippelzeit 2026-10-30T11:20:00Z"
call :lauf

set "N=3 WaterCheck, TerrainCheck und BuehneCheck"
set "L=%L3%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G% --ohne-attest"
call :lauf
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=4 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario sediment --sekunden 40"
call :lauf
cd /d "%~dp0.."

>>"%UE%" echo.
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
goto :eof

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
