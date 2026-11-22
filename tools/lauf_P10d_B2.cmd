@echo off
rem P10d, Lauf B2 (Werkbuch HalligHooge Teil A, 12.11, Datei 45): AirCheck noch einmal. In Lauf B lief Teil 6 ueber das Speicherkonto
rem der Pruefung (256 MB; J fuer Wasser und Land brauchen 189 MB dazu), jetzt 512 MB; Himmel.close mit halb angelegten Bildern.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 AirCheck.
rem Ausgaben: HalligHooge\docs\status\Status_Phase10dB2_*.txt (UTF-8). Attest: Renderer\docs\status\P6_Himmel.md.
rem Erwartete Dauer: 2-3 Minuten (J fuer 0,06 liegt seit Lauf B auf der Platte).
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase10dB2_.txt"
set "L1=%ST%\Status_Phase10dB2_1.txt"
set "L2=%ST%\Status_Phase10dB2_2.txt"

>"%UE%" echo Lauf P10d B2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10dB2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase10dB2_2: AirCheck

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 AirCheck"
set "L=%L2%"
set "C=%R% com.dan.render.air.demo.AirCheck"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
goto :eof

:abbruch

set "N=2 AirCheck (mit Teil 6, J gemischt)"
set "L=%L2%"
set "C=%R% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=3 TerrainCheck"
set "L=%L3%"
set "C=%R% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf

set "N=4 WaterCheck"
set "L=%L4%"
set "C=%R% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=5 BildCheck"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=6 BuehneCheck"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf

set "N=7 Renderer.jar ins Projekt, HalligHooge bauen, Anwendung 40 s"
set "L=%L7%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L7%" 2>&1
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
