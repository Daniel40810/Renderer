@echo off
rem P9c, Lauf C (Feinplan P9c): Teil 2 bauen, Feldvergleiche der Morphologie, Bild, Projekt.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 Aufbereitung, Schritt morpho (EasyGSH-Bathymetrie 1996 und 2016 nach
rem gebiet\pruef); 3 MorphoFeldCheck (Pruefsteine 13, 14, 15) und SedimentLaufCheck --nur 2,3 auf den Archiven M2 und M1
rem (Stroemung gegen EasyGSH, Truebung gegen Sentinel-2 ueber tiefem Wasser, Bild, Bilder/s, Status-Bilder P9c_M_*.png);
rem 4 WaterCheck, TerrainCheck, BuehneCheck; 5 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s mit --szenario morpho.
rem Atteste: Renderer\docs\status\P9c_Feld.md, P9c_M.md.
rem Ausgaben: HalligHooge\docs\status\Status_Phase9cC*.txt (UTF-8), Uebersicht in Status_Phase9cC.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A9=D:\Daten\HalligHooge\_p9b\archiv"
set "A9R=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "M1=D:\Daten\HalligHooge\_p9c\archiv_m1"
set "M2=D:\Daten\HalligHooge\_p9c\archiv_m2"
set "F3=400,850,700,400"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SEE=--stuetzen 20,18 --seegang-gitter 1000,200"
set "UE=%ST%\Status_Phase9cC.txt"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"
set "L1=%ST%\Status_Phase9cC1.txt"
set "L2=%ST%\Status_Phase9cC2.txt"
set "L3=%ST%\Status_Phase9cC3.txt"

>"%UE%" echo Lauf P9c C, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase9cC1: Renderer bauen, Aufbereitung morpho
>"%L2%" echo Status_Phase9cC2: MorphoFeldCheck, SedimentLaufCheck --nur 2,3 auf M2 und M1
>"%L3%" echo Status_Phase9cC3: WaterCheck, TerrainCheck, BuehneCheck, Projekt, Anwendung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 Aufbereitung, Schritt morpho (EasyGSH-Bathymetrie 1996 und 2016)"
set "L=%L1%"
set "C=java %U% -Xmx6g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte morpho"
call :lauf

set "N=3 MorphoFeldCheck (Pruefsteine 13, 14, 15)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.MorphoFeldCheck --gebiet %G% --m1 %M1% --m2 %M2%"
call :lauf

set "N=3 SedimentLaufCheck --nur 2,3 auf M2 und M1 (Feldvergleiche, Bild)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SedimentLaufCheck --gebiet %G% --archiv %M2% --archiv-ruhig %M1% --fenster3 %F3% --nur 2,3 --attest P9c_M --fahne 466000,6046500,4000,0,-85 --rippel 468990,6045410,250 --rippelzeit 2026-10-15T10:30:00Z"
call :lauf

set "N=4 WaterCheck"
set "L=%L3%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=4 TerrainCheck"
set "L=%L3%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G% --ohne-attest"
call :lauf

set "N=4 BuehneCheck"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=5 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario morpho --sekunden 40"
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
