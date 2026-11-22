@echo off
rem P10d, Lauf A (Werkbuch HalligHooge Teil A, 12.11, Datei 44): Teil 1 - Licht aus der gehobenen Richtung (F100 A) und
rem Farbe der direkten Sonne nach der Luft. Die Messungen d, e, f liefen schon in der Arbeitsumgebung (ohne Karte).
rem 0 BildCheck mit dem alten Stand (dist\Renderer_vorP10d.jar, ohne Attest) - Bilder/s vorher fuer Pruefstein 8;
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 SonnenfarbeCheck (Pruefsteine 1, 4 zum Teil, 8);
rem 3 AirCheck (mit Teil 5: tiefe Sonne, Pruefsteine 2 und 3); 4 TerrainCheck; 5 WaterCheck; 6 BildCheck (Pruefsteine 4 und 8);
rem 7 KimmungKarteCheck (die Werte des Himmels haben jetzt 60 statt 56 Zahlen).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10dA_*.txt (UTF-8), Uebersicht in Status_Phase10dA_.txt.
rem Atteste: Renderer\docs\status\P10_Sonnenfarbe.md, P6_Himmel.md, P6_Gelaende.md, P6_Wasser.md, P6_Bild.md, P10d_KimmungKarte.md.
rem Erwartete Dauer: 15-25 Minuten. Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "AK=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "ZEIT=2026-10-18T08:00:00Z"
set "BLICK=462600,6046900,4,325,0,5.6"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "R0=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer_vorP10d.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase10dA_.txt"
set "L0=%ST%\Status_Phase10dA_0.txt"
set "L1=%ST%\Status_Phase10dA_1.txt"
set "L2=%ST%\Status_Phase10dA_2.txt"
set "L3=%ST%\Status_Phase10dA_3.txt"
set "L4=%ST%\Status_Phase10dA_4.txt"
set "L5=%ST%\Status_Phase10dA_5.txt"
set "L6=%ST%\Status_Phase10dA_6.txt"
set "L7=%ST%\Status_Phase10dA_7.txt"

>"%UE%" echo Lauf P10d A, gestartet %DATE% %TIME%
>"%L0%" echo Status_Phase10dA_0: BildCheck mit dem alten Stand (Bilder/s vorher)
>"%L1%" echo Status_Phase10dA_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase10dA_2: SonnenfarbeCheck
>"%L3%" echo Status_Phase10dA_3: AirCheck (mit Teil 5, tiefe Sonne)
>"%L4%" echo Status_Phase10dA_4: TerrainCheck
>"%L5%" echo Status_Phase10dA_5: WaterCheck
>"%L6%" echo Status_Phase10dA_6: BildCheck
>"%L7%" echo Status_Phase10dA_7: KimmungKarteCheck

rem der alte Stand: dist\Renderer.jar wie nach P10c, einmal beiseitegelegt
if not exist dist\Renderer_vorP10d.jar copy /y dist\Renderer.jar dist\Renderer_vorP10d.jar >nul
set "N=0 BildCheck mit dem alten Stand (Bilder/s vorher)"
set "L=%L0%"
set "C=%R0% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3 --ohne-attest"
call :lauf

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 SonnenfarbeCheck"
set "L=%L2%"
set "C=%R% com.dan.render.air.demo.SonnenfarbeCheck"
call :lauf

set "N=3 AirCheck (mit Teil 5, tiefe Sonne)"
set "L=%L3%"
set "C=%R% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=4 TerrainCheck"
set "L=%L4%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf

set "N=5 WaterCheck"
set "L=%L5%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=6 BildCheck"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=7 KimmungKarteCheck"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.KimmungKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --blick %BLICK% --name P10d_KimmungKarte"
call :lauf

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
