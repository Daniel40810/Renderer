@echo off
rem P7l (F66 B: Bodenalbedo 0,23) und Abnahme P7 ueber Nacht auf dem Ryzen. Wartet auf das Ende von lauf_P7k.cmd.
rem Ausgaben: HalligHooge\docs\status\Status_Phase7l*.txt und Status_Abnahme7*.txt (UTF-8), Uebersicht in Status_Phase7l.txt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "ST=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "J=java %U% -Xmx8g -cp dist\Renderer.jar"
set "V=-Dcore.gpu.validierung=true"
set "UE=%ST%\Status_Phase7l.txt"
set "L0=%ST%\Status_Phase7l0.txt"
set "L1=%ST%\Status_Phase7l1.txt"
set "B1=%ST%\Status_Abnahme7_1.txt"
set "B2=%ST%\Status_Abnahme7_2.txt"
set "B3=%ST%\Status_Abnahme7_3.txt"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

:warte
findstr /C:"FERTIG" "%ST%\Status_Phase7k.txt" >nul 2>nul && goto :los
echo [%TIME%] Warte auf das Ende von lauf_P7k.cmd ...
timeout /t 30 /nobreak >nul
goto :warte

:los
>"%UE%" echo Lauf P7l und Abnahme P7, gestartet %DATE% %TIME%
>"%L0%" echo Status_Phase7l0: Bauen (ant clean jar)
>"%L1%" echo Status_Phase7l1: P7l - AirCheck, ReferenzCheck (64 Pfade, ohne Attest)
>"%B1%" echo Status_Abnahme7_1: ReferenceCheck, FlaecheCheck, SaeuleCheck, LuftCheck
>"%B2%" echo Status_Abnahme7_2: WaterCheck, TerrainCheck, FrameCheck, BildCheck ohne und mit Validierung
>"%B3%" echo Status_Abnahme7_3: ReferenzCheck --pfade 256 --bis-rauschen 1

set "N=1 Bauen (clean jar)"
set "L=%L0%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 P7l: AirCheck (Validierung)"
set "L=%L1%"
set "C=%R% %V% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=3 P7l: ReferenzCheck (64 Pfade, ohne Attest)"
set "L=%L1%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --pfade 64 --ohne-attest"
call :lauf

set "N=4 Abnahme: ReferenceCheck"
set "L=%B1%"
set "C=%J% com.dan.render.reference.demo.ReferenceCheck"
call :lauf

set "N=5 Abnahme: FlaecheCheck"
set "L=%B1%"
set "C=%J% com.dan.render.reference.demo.FlaecheCheck"
call :lauf

set "N=6 Abnahme: SaeuleCheck"
set "L=%B1%"
set "C=%J% com.dan.render.reference.demo.SaeuleCheck"
call :lauf

set "N=7 Abnahme: LuftCheck (1 000 000 Pfade)"
set "L=%B1%"
set "C=%J% com.dan.render.reference.demo.LuftCheck --pfade 1000000"
call :lauf

set "N=8 Abnahme: WaterCheck (Validierung)"
set "L=%B2%"
set "C=%R% %V% com.dan.render.water.demo.WaterCheck --gebiet %G%"
call :lauf

set "N=9 Abnahme: TerrainCheck (Validierung)"
set "L=%B2%"
set "C=%R% %V% com.dan.render.terrain.demo.TerrainCheck --gebiet %G%"
call :lauf

set "N=10 Abnahme: FrameCheck (Validierung)"
set "L=%B2%"
set "C=%R% %V% com.dan.render.frame.demo.FrameCheck"
call :lauf

set "N=11 Abnahme: BildCheck --nur 2,3"
set "L=%B2%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=12 Abnahme: BildCheck --nur 2,3 --bilder 60 (Validierung)"
set "L=%B2%"
set "C=%R% %V% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3 --bilder 60"
call :lauf

set "N=13 Abnahme: ReferenzCheck --pfade 256 --bis-rauschen 1"
set "L=%B3%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx8g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --pfade 256 --bis-rauschen 1"
call :lauf

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
pause
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
pause
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
