@echo off
rem P10c, Lauf B (Werkbuch HalligHooge Teil A, 12.11, Datei 41): Sandfegen im Bild (F96 A).
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 SandfegenKarteCheck (Pruefstein 17, Status-Bild); 3 derselbe
rem mit Validierung (960 x 540); 4 TerrainCheck, WaterCheck, BuehneCheck; 5 Renderer.jar ins Projekt, HalligHooge bauen,
rem Anwendung 40 s mit --szenario flugsand.
rem Ausgaben: HalligHooge\docs\status\Status_Phase10cB_*.txt (UTF-8), Uebersicht in Status_Phase10cB_.txt.
rem Atteste: Renderer\docs\status\P10c_Sandfegen.md (mit .png), P10c_SandfegenValidierung.md.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "AK=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "ZEIT=2026-10-17T16:00:00Z"
set "ORT=465850,6042890"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "RV=java %U% -Dcore.gpu.validierung=true --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\szenen\Hooge.szenen"
set "APP=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10cB_.txt"
set "L1=%ST%\Status_Phase10cB_1.txt"
set "L2=%ST%\Status_Phase10cB_2.txt"
set "L3=%ST%\Status_Phase10cB_3.txt"
set "L4=%ST%\Status_Phase10cB_4.txt"
set "L5=%ST%\Status_Phase10cB_5.txt"

>"%UE%" echo Lauf P10c B, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10cB_1: Renderer bauen (ant clean jar, mit check-packages)
>"%L2%" echo Status_Phase10cB_2: SandfegenKarteCheck
>"%L3%" echo Status_Phase10cB_3: SandfegenKarteCheck mit Validierung
>"%L4%" echo Status_Phase10cB_4: TerrainCheck, WaterCheck, BuehneCheck
>"%L5%" echo Status_Phase10cB_5: HalligHooge bauen, Anwendung

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 SandfegenKarteCheck (Karte, Gebiet, Kosten)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SandfegenKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --ort %ORT% --richtung 20"
call :lauf

set "N=3 SandfegenKarteCheck mit Validierung (960 x 540)"
set "L=%L3%"
set "C=%RV% com.dan.lauf.demo.SandfegenKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --ort %ORT% --richtung 20 --breite 960 --hoehe 540 --name P10c_SandfegenValidierung"
call :lauf

set "N=4 TerrainCheck, WaterCheck, BuehneCheck"
set "L=%L4%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G% --ohne-attest"
call :lauf
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G% --ohne-attest"
call :lauf
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ% --ohne-attest"
call :lauf

set "N=5 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L5%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L5%" 2>&1
cd /d "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
set "C=%APP% --szenario flugsand --sekunden 40"
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
